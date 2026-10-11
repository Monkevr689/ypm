package dev.kushcraft.storage;

import dev.kushcraft.KushCraft;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Backups of everything KushCraft keeps: a consistent copy of the database
 * (SQLite VACUUM INTO, taken on the database thread between two snapshots),
 * config.yml, strains.yml, menus.yml and the old YAML files. Every backup is
 * checked before anyone relies on it: the copy is opened on its own, must
 * pass SQLite's integrity check and must hold exactly as many rows as the
 * live database had when it was taken. The result (row counts, total money,
 * SHA-256 of the file) is written to manifest.txt next to it.
 *
 * Backups live in plugins/KushCraft/backups/&lt;time&gt;-&lt;reason&gt;/.
 */
public final class Backups {

    /** A finished, checked backup. */
    public record Backup(File dir, File db, long size, String sha256, Map<String, Long> counts, long money,
                         boolean verified, String problem) {
    }

    private final KushCraft plugin;
    private final Database db;

    public Backups(KushCraft plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public File folder() {
        return new File(plugin.getDataFolder(), "backups");
    }

    /**
     * Writes everything changed so far, then copies and checks it. Server thread (it waits for the
     * database; a big database takes a moment). Never throws: a failed backup says why.
     */
    public Backup make(String reason, String by) {
        if (!plugin.persistence().flushNow()) {
            return new Backup(null, null, 0, null, Map.of(), 0, false, "the latest changes could not be saved first");
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File dir = new File(folder(), stamp + "-" + reason.replaceAll("[^A-Za-z0-9_-]", ""));
        File copy = new File(dir, "kushcraft.db");
        try {
            Files.createDirectories(dir.toPath());
        } catch (IOException e) {
            return new Backup(dir, copy, 0, null, Map.of(), 0, false, "can't create " + dir + ": " + e.getMessage());
        }
        Map<String, Long> live;
        long money;
        try {
            Object[] r = db.call(c -> {
                // the counts are read right after the copy on the same thread: nothing can write in between
                try (Statement s = c.createStatement()) {
                    s.execute("VACUUM INTO '" + copy.getAbsolutePath().replace("'", "''") + "'");
                }
                return new Object[]{Database.counts(c), Database.totalMoney(c)};
            });
            @SuppressWarnings("unchecked")
            Map<String, Long> counts = (Map<String, Long>) r[0];
            live = counts;
            money = (Long) r[1];
        } catch (RuntimeException e) {
            return new Backup(dir, copy, 0, null, Map.of(), 0, false, "copying the database failed: " + e.getMessage());
        }
        // the files around it
        for (String name : new String[]{"config.yml", "strains.yml", "menus.yml"}) {
            copyFile(new File(plugin.getDataFolder(), name), new File(dir, name));
        }
        File legacy = new File(plugin.getDataFolder(), "legacy-yaml");
        if (legacy.isDirectory()) {
            copyTree(legacy, new File(dir, "legacy-yaml"));
        }
        // check it: open the copy by itself, integrity, the same rows
        String problem = null;
        Map<String, Long> got = Map.of();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + copy.getAbsolutePath())) {
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("PRAGMA integrity_check")) {
                String ok = rs.next() ? rs.getString(1) : "no answer";
                if (!"ok".equalsIgnoreCase(ok)) {
                    problem = "integrity check: " + ok;
                }
            }
            got = Database.counts(c);
            if (problem == null && !got.equals(live)) {
                problem = "row counts differ: live " + live + ", backup " + got;
            }
            if (problem == null && Database.totalMoney(c) != money) {
                problem = "total money differs";
            }
        } catch (SQLException e) {
            problem = "can't open the copy: " + e.getMessage();
        }
        long size = copy.length();
        String sha = sha256(copy);
        writeManifest(dir, reason, by, live, money, size, sha, problem);
        Backup b = new Backup(dir, copy, size, sha, live, money, problem == null && size > 0, problem);
        plugin.getLogger().info("Backup " + (b.verified() ? "made and verified: " : "FAILED: ") + dir.getName()
                + " (" + size / 1024 + " KB, " + live + ")" + (problem == null ? "" : " - " + problem));
        return b;
    }

    private void writeManifest(File dir, String reason, String by, Map<String, Long> counts, long money, long size,
                               String sha, String problem) {
        List<String> lines = new ArrayList<>();
        lines.add("KushCraft backup");
        lines.add("time: " + new Date());
        lines.add("reason: " + reason);
        lines.add("by: " + by);
        lines.add("season: " + plugin.season());
        lines.add("database: kushcraft.db, " + size + " bytes, sha256 " + sha);
        counts.forEach((t, n) -> lines.add("rows " + t + ": " + n));
        lines.add("total money: " + plugin.economy().format(money / 100.0));
        lines.add("verified: " + (problem == null ? "yes (integrity ok, same rows, same money)" : "NO - " + problem));
        lines.add("");
        lines.add("To restore: stop the server, copy kushcraft.db (and the .yml files if you want them) back into");
        lines.add("plugins/KushCraft/, delete kushcraft.db-wal and kushcraft.db-shm there, start the server.");
        try {
            Files.write(new File(dir, "manifest.txt").toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write the backup manifest: " + e.getMessage());
        }
    }

    private static void copyFile(File from, File to) {
        if (!from.isFile()) {
            return;
        }
        try {
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // the manifest still lists the database
        }
    }

    private static void copyTree(File from, File to) {
        File[] files = from.listFiles();
        if (files == null) {
            return;
        }
        to.mkdirs();
        for (File f : files) {
            if (f.isDirectory()) {
                copyTree(f, new File(to, f.getName()));
            } else {
                copyFile(f, new File(to, f.getName()));
            }
        }
    }

    public static String sha256(File f) {
        try (InputStream in = Files.newInputStream(f.toPath())) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return "?";
        }
    }

    /** Existing backups, newest first. */
    public List<File> list() {
        File[] dirs = folder().listFiles(File::isDirectory);
        if (dirs == null) {
            return List.of();
        }
        List<File> out = new ArrayList<>(Arrays.asList(dirs));
        out.sort(Comparator.comparing(File::getName).reversed());
        return out;
    }
}
