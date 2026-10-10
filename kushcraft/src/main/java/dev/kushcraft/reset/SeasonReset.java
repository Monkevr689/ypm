package dev.kushcraft.reset;

import dev.kushcraft.KushCraft;
import dev.kushcraft.economy.Economy;
import dev.kushcraft.economy.Tx;
import dev.kushcraft.menus.MenuListener;
import dev.kushcraft.storage.Backups;
import dev.kushcraft.storage.Database;
import dev.kushcraft.storage.PlayerRecord;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Season resets: /kush reset [economy|kushcraft|everything].
 *
 * Two steps, so nothing is wiped by accident and never without a backup:
 * <ol>
 * <li>{@code /kush reset <scope>} saves everything, makes a backup, checks it
 * (integrity, same rows, same money) and shows where it is, its size, its
 * SHA-256 and the row counts - plus a one-time code;</li>
 * <li>{@code /kush reset confirm <code>} (same person, within 5 minutes)
 * makes one more checked backup of the very last state and only then wipes,
 * in ONE database transaction: it all happens or none of it does.</li>
 * </ol>
 *
 * What each scope wipes:
 * <ul>
 * <li><b>economy</b>: money (back to the starting balance), ranks (back to 1,
 * the wait starts now), playtime, workers and their satchels;</li>
 * <li><b>kushcraft</b> (the default): economy, plus every KushCraft plant and
 * Drug Lab, cartels, awards, lifetime sales (dealer titles) and market and
 * Trade prices;</li>
 * <li><b>everything</b>: kushcraft, plus every player's inventory, ender chest
 * and XP (online players right away, everyone else when they next join) and
 * strains bred by players.</li>
 * </ul>
 * Builds and chests in the world are never touched (a map reset is a new
 * world folder). The transaction log is kept: it's history.
 */
public final class SeasonReset {

    public enum Scope {
        ECONOMY("money, ranks, playtime, workers and their satchels"),
        KUSHCRAFT("money, ranks, playtime, workers, KushCraft plants and Drug Labs, cartels, awards, lifetime sales"
                + " and market prices"),
        EVERYTHING("all of KushCraft plus every player's inventory, ender chest and XP, and bred strains");

        private final String wipes;

        Scope(String wipes) {
            this.wipes = wipes;
        }

        public String wipes() {
            return wipes;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Scope parse(String s) {
            try {
                return valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                return null;
            }
        }
    }

    private record Pending(String by, Scope scope, String code, long expires, String backup) {
    }

    private final KushCraft plugin;
    private Pending pending;

    public SeasonReset(KushCraft plugin) {
        this.plugin = plugin;
    }

    public Scope defaultScope() {
        Scope s = Scope.parse(plugin.getConfig().getString("reset.default-scope", "kushcraft"));
        return s == null ? Scope.KUSHCRAFT : s;
    }

    // ------------------------------------------------------------------
    // the command
    // ------------------------------------------------------------------

    /** /kush reset ... (console, or a player with kushcraft.reset). */
    public void command(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kushcraft.reset")) {
            sender.sendMessage(Text.msg("<red>You need kushcraft.reset to reset the season."));
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("confirm")) {
            confirm(sender, args[2]);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.msg("<gold>Season reset <gray>- makes a checked backup first, then asks you to confirm."));
            for (Scope s : Scope.values()) {
                sender.sendMessage(Text.mm(" <white>/kush reset " + s.id() + (s == defaultScope() ? " <green>(default)" : "")
                        + " <gray>- " + s.wipes()));
            }
            sender.sendMessage(Text.mm(" <dark_gray>Builds and chests are never touched. The transaction log is kept."));
            return;
        }
        Scope scope = args[1].equalsIgnoreCase("default") ? defaultScope() : Scope.parse(args[1]);
        if (scope == null) {
            sender.sendMessage(Text.msg("<red>Unknown scope. Use economy, kushcraft or everything."));
            return;
        }
        prepare(sender, scope);
    }

    /** Step 1: backup, check, show it, hand out a code. */
    public void prepare(CommandSender sender, Scope scope) {
        sender.sendMessage(Text.msg("<gray>Saving everything and making a backup..."));
        Backups.Backup b = plugin.backups().make("before-reset-" + scope.id(), sender.getName());
        if (!b.verified()) {
            sender.sendMessage(Text.msg("<red>The backup failed, so nothing will be reset: " + b.problem()));
            return;
        }
        showBackup(sender, b);
        String code = code();
        pending = new Pending(sender.getName(), scope, code, System.currentTimeMillis() + 5 * 60_000L, b.dir().getName());
        sender.sendMessage(Text.msg("<gold>Ready to reset: <white>" + scope.id() + "</white> <gray>(" + scope.wipes() + ")."));
        sender.sendMessage(Text.mm(" <gray>Online players: " + Bukkit.getOnlinePlayers().size() + ", known players: "
                + plugin.players().size() + ", workers: " + plugin.workers().all().size()
                + (scope != Scope.ECONOMY ? ", plants: " + plugin.plants().all().size() + ", Drug Labs and blocks: "
                + plugin.machines().all().size() + ", cartels: " + plugin.cartels().all().size() : "")));
        sender.sendMessage(Text.mm(" <red>To go ahead type <white>/kush reset confirm " + code + "</white> within 5 minutes."));
    }

    private static void showBackup(CommandSender to, Backups.Backup b) {
        to.sendMessage(Text.msg("<green>Backup made and checked ✔"));
        to.sendMessage(Text.mm(" <gray>Folder: <white>plugins/KushCraft/backups/" + b.dir().getName()));
        to.sendMessage(Text.mm(" <gray>kushcraft.db: <white>" + b.size() / 1024 + " KB <gray>sha256 <white>"
                + b.sha256().substring(0, 16) + "…"));
        StringBuilder rows = new StringBuilder();
        b.counts().forEach((t, n) -> rows.append(rows.isEmpty() ? "" : ", ").append(t).append(' ').append(n));
        to.sendMessage(Text.mm(" <gray>Rows: <white>" + rows + " <gray>· money " + KushCraft.get().economy().format(b.money() / 100.0)));
        to.sendMessage(Text.mm(" <gray>Integrity ok, same rows and money as the live database. manifest.txt has it all."));
    }

    private static String code() {
        String abc = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            b.append(abc.charAt(r.nextInt(abc.length())));
        }
        return b.toString();
    }

    /** Step 2. */
    private void confirm(CommandSender sender, String code) {
        Pending p = pending;
        if (p == null || System.currentTimeMillis() > p.expires()) {
            pending = null;
            sender.sendMessage(Text.msg("<red>Nothing to confirm (or it expired). Start with /kush reset <scope>."));
            return;
        }
        if (!p.code().equalsIgnoreCase(code) || !p.by().equals(sender.getName())) {
            sender.sendMessage(Text.msg("<red>Wrong code, or someone else started this reset."));
            return;
        }
        pending = null;
        String error = run(p.scope(), sender.getName(), sender);
        if (error != null) {
            sender.sendMessage(Text.msg("<red>The reset did not happen: " + error));
        }
    }

    // ------------------------------------------------------------------
    // the reset itself
    // ------------------------------------------------------------------

    /**
     * Wipes the scope. Returns an error (nothing changed) or null. Server thread.
     * Also used by /kush selftest on a test server.
     */
    public String run(Scope scope, String by, CommandSender report) {
        Backups.Backup last = plugin.backups().make("final-before-reset-" + scope.id(), by);
        if (!last.verified()) {
            return "the last backup failed (" + last.problem() + ")";
        }
        plugin.persistence().pause();
        try {
            MenuListener.closeAll();
            int newSeason = plugin.season() + 1;
            long now = System.currentTimeMillis();
            long start = Economy.cents(plugin.economy().startingBalance());
            long moneyBefore = Math.round(plugin.economy().total() * 100);
            int players = plugin.players().size();
            // take the KushCraft things out of the world first (the data goes below)
            plugin.workers().shutdown();
            List<String> labBlocks = new ArrayList<>();
            if (scope != Scope.ECONOMY) {
                plugin.plants().shutdown();
                labBlocks.addAll(plugin.machines().removeAllBlocks());
            }
            String detail = "scope " + scope.id() + " by " + by + ", backup " + last.dir().getName() + ", " + players
                    + " players, money before " + plugin.economy().format(moneyBefore / 100.0);
            plugin.db().call(c -> {
                Database.transaction(c, t -> {
                    try (Statement s = t.createStatement()) {
                        s.executeUpdate("DELETE FROM workers");
                        if (scope != Scope.ECONOMY) {
                            for (String table : new String[]{"plants", "machines", "cartels", "awards", "docs"}) {
                                s.executeUpdate("DELETE FROM " + table);
                            }
                            s.executeUpdate("UPDATE players SET sales=0");
                        }
                    }
                    try (PreparedStatement ps = t.prepareStatement("UPDATE players SET balance=?, rank=1, ranked_at=?, "
                            + "playtime=0, flags=flags & ?")) {
                        ps.setLong(1, start);
                        ps.setLong(2, now);
                        ps.setInt(3, ~(PlayerRecord.ONBOARDED | PlayerRecord.GOT_KIT));
                        ps.executeUpdate();
                    }
                    if (!labBlocks.isEmpty()) {
                        try (PreparedStatement ps = t.prepareStatement(
                                "INSERT INTO cleanup(pos, what) VALUES(?, 'lab') ON CONFLICT(pos) DO NOTHING")) {
                            for (String pos : labBlocks) {
                                ps.setString(1, pos);
                                ps.addBatch();
                            }
                            ps.executeBatch();
                        }
                    }
                    try (PreparedStatement ps = t.prepareStatement("INSERT INTO ledger(ts, player, type, amount, balance, "
                            + "other, detail, count, season) VALUES(?, NULL, ?, ?, NULL, ?, ?, 1, ?)")) {
                        ps.setLong(1, now);
                        ps.setString(2, Tx.RESET.name());
                        ps.setLong(3, -moneyBefore);
                        ps.setString(4, by);
                        ps.setString(5, detail);
                        ps.setInt(6, newSeason);
                        ps.executeUpdate();
                    }
                    Database.setMeta(t, "season", String.valueOf(newSeason));
                    Database.setMeta(t, "season-start", String.valueOf(now));
                    Database.setMeta(t, "last-reset", scope.id() + " " + now + " " + by);
                    if (scope == Scope.EVERYTHING) {
                        Database.setMeta(t, "inventory-wipe-season", String.valueOf(newSeason));
                    }
                });
                return null;
            });
            // read the new state back
            plugin.season(newSeason);
            if (scope == Scope.EVERYTHING) {
                plugin.inventoryWipeSeason(newSeason);
                plugin.strains().removeBred();
            }
            plugin.reloadData(scope != Scope.ECONOMY);
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.onboarding().afterReset(p);
            }
            String summary = "Season " + newSeason + " started: " + scope.id() + " reset by " + by + ". Backup: backups/"
                    + last.dir().getName();
            plugin.getLogger().info(summary);
            appendLog(last, summary + " | " + detail);
            if (report != null) {
                report.sendMessage(Text.msg("<green>" + summary));
            }
            Bukkit.broadcast(Text.msg("<gold><bold>A new season has started!</bold> <gray>Everyone starts from zero. "
                    + "<white>/menu</white> <gray>shows how it works."));
            return null;
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Reset failed", e);
            // nothing was written (one transaction): put the world things back from the data
            plugin.reloadData(scope != Scope.ECONOMY);
            return e.getMessage();
        } finally {
            plugin.persistence().resume();
        }
    }

    private void appendLog(Backups.Backup b, String line) {
        try {
            Files.writeString(new java.io.File(plugin.backups().folder(), "resets.log").toPath(),
                    new java.util.Date() + " " + line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // the ledger has it too
        }
    }

    /** For the admin: what the last reset was. */
    public String lastReset() {
        return plugin.db().meta("last-reset", "never");
    }
}
