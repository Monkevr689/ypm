package dev.kushcraft.commands;

import dev.kushcraft.KushCraft;
import dev.kushcraft.economy.Economy;
import dev.kushcraft.economy.Ledger;
import dev.kushcraft.economy.Tx;
import dev.kushcraft.storage.Backups;
import dev.kushcraft.storage.Database;
import dev.kushcraft.storage.PlayerRecord;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /kush rank, playtime, log, backup, backups, db - the admin side of the
 * economy. Every change made here is in the transaction log as ADMIN with
 * the admin's name.
 */
final class AdminCommands {

    private AdminCommands() {
    }

    static void run(KushCraft plugin, CommandSender s, String sub, String[] args) {
        switch (sub) {
            case "rank" -> rank(plugin, s, args);
            case "playtime" -> playtime(plugin, s, args);
            case "log" -> log(plugin, s, args);
            case "backup" -> {
                s.sendMessage(Text.msg("<gray>Saving and backing up..."));
                Backups.Backup b = plugin.backups().make("manual", s.getName());
                s.sendMessage(Text.msg(b.verified() ? "<green>Backup made and checked ✔ <white>backups/" + b.dir().getName()
                        + " <gray>(" + b.size() / 1024 + " KB, " + b.counts() + ")" : "<red>Backup failed: " + b.problem()));
            }
            case "backups" -> {
                List<File> all = plugin.backups().list();
                s.sendMessage(Text.msg("<gold>Backups <gray>(" + all.size() + ", newest first) in plugins/KushCraft/backups/"));
                for (File d : all.subList(0, Math.min(15, all.size()))) {
                    File db = new File(d, "kushcraft.db");
                    s.sendMessage(Text.mm(" <white>" + d.getName() + " <gray>" + db.length() / 1024 + " KB"
                            + (new File(d, "manifest.txt").exists() ? " <green>manifest ✔" : " <red>no manifest")));
                }
            }
            case "db" -> db(plugin, s);
            default -> {
            }
        }
    }

    private static OfflinePlayer player(KushCraft plugin, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer o = Bukkit.getOfflinePlayerIfCached(name);
        return o != null && plugin.economy().known(o.getUniqueId()) ? o : null;
    }

    /** /kush rank <player> <n> */
    private static void rank(KushCraft plugin, CommandSender s, String[] args) {
        if (args.length < 3) {
            s.sendMessage(Text.msg("<red>/kush rank <player> <1-" + plugin.ranks().top() + ">"));
            return;
        }
        OfflinePlayer t = player(plugin, args[1]);
        if (t == null) {
            s.sendMessage(Text.msg("<red>Unknown player."));
            return;
        }
        try {
            int n = Integer.parseInt(args[2]);
            plugin.ranks().set(t.getUniqueId(), n, s.getName());
            s.sendMessage(Text.msg("<green>" + t.getName() + " is now rank " + plugin.ranks().of(t.getUniqueId()).number()
                    + " " + plugin.ranks().of(t.getUniqueId()).colored()));
        } catch (NumberFormatException e) {
            s.sendMessage(Text.msg("<red>Not a number."));
        }
    }

    /** /kush playtime [player] [hours] */
    private static void playtime(KushCraft plugin, CommandSender s, String[] args) {
        OfflinePlayer t = args.length >= 2 ? player(plugin, args[1]) : s instanceof Player p ? p : null;
        if (t == null) {
            s.sendMessage(Text.msg("<red>Unknown player."));
            return;
        }
        PlayerRecord r = plugin.economy().account(t.getUniqueId());
        if (args.length >= 3) {
            try {
                r.setPlaytime(Math.round(Double.parseDouble(args[2]) * 3600));
                plugin.economy().ledger().log(t.getUniqueId(), Tx.ADMIN, 0, r.balance(), s.getName(),
                        "playtime set to " + args[2] + "h");
            } catch (NumberFormatException e) {
                s.sendMessage(Text.msg("<red>Not a number."));
                return;
            }
        }
        boolean active = t instanceof Player p && plugin.playtime().active(p);
        s.sendMessage(Text.msg("<gray>" + Text.escape(String.valueOf(t.getName())) + ": <white>"
                + Text.duration(r.playtime() * 1000L) + " <gray>active playtime this season"
                + (t.isOnline() ? (active ? " <green>(active now)" : " <yellow>(AFK now)") : "")));
    }

    /** /kush log [player] [page] */
    private static void log(KushCraft plugin, CommandSender s, String[] args) {
        UUID who = null;
        int page = 0;
        int i = 1;
        if (args.length > i) {
            try {
                page = Math.max(0, Integer.parseInt(args[i]) - 1);
            } catch (NumberFormatException e) {
                OfflinePlayer t = player(plugin, args[i]);
                if (t == null) {
                    s.sendMessage(Text.msg("<red>Unknown player."));
                    return;
                }
                who = t.getUniqueId();
                i++;
                if (args.length > i) {
                    try {
                        page = Math.max(0, Integer.parseInt(args[i]) - 1);
                    } catch (NumberFormatException ignored) {
                        // page 1
                    }
                }
            }
        }
        final int shown = page + 1;
        final UUID filter = who;
        plugin.economy().ledger().read(who, page, 12, rows -> {
            s.sendMessage(Text.msg("<gold>Transaction log<gray>" + (filter == null ? "" : " of "
                    + plugin.players().get(filter).name()) + ", page " + shown + " (newest first)"));
            SimpleDateFormat f = new SimpleDateFormat("MM-dd HH:mm:ss");
            for (Ledger.Row r : rows) {
                String name = r.player() == null ? "server" : nameOf(plugin, r.player());
                String amount = r.amount() == 0 ? "" : (r.amount() > 0 ? " <green>+" : " <red>")
                        + plugin.economy().format(Economy.dollars(r.amount()));
                s.sendMessage(Text.mm(" <dark_gray>" + f.format(new Date(r.ts())) + " <white>" + Text.escape(name) + " <gray>"
                        + r.type().toLowerCase().replace('_', ' ') + (r.count() > 1 ? " x" + r.count() : "") + amount
                        + (r.balance() == null ? "" : " <dark_gray>→ " + plugin.economy().format(Economy.dollars(r.balance())))
                        + (r.detail() == null ? "" : " <gray>" + Text.escape(r.detail()))));
            }
            if (rows.isEmpty()) {
                s.sendMessage(Text.mm(" <gray>Nothing here."));
            }
        });
    }

    private static String nameOf(KushCraft plugin, String id) {
        try {
            PlayerRecord r = plugin.players().get(UUID.fromString(id));
            return r == null ? id.substring(0, 8) : r.name();
        } catch (IllegalArgumentException e) {
            return id;
        }
    }

    /** /kush db */
    private static void db(KushCraft plugin, CommandSender s) {
        Map<String, Long> counts = plugin.db().call(Database::counts);
        File f = plugin.db().file();
        double[] t = plugin.workers().timing();
        s.sendMessage(Text.msg("<gold>Database <gray>" + f.getName() + " " + plugin.db().size() / 1024 + " KB, season "
                + plugin.season()));
        s.sendMessage(Text.mm(" <gray>Rows: <white>" + counts));
        s.sendMessage(Text.mm(" <gray>Writes: <white>" + plugin.persistence().commits() + "</white> snapshots, last "
                + String.format("%.2f", plugin.persistence().lastCommitMillis()) + " ms, " + plugin.persistence().inFlight()
                + " queued, <" + (plugin.persistence().failures() == 0 ? "green" : "red") + ">"
                + plugin.persistence().failures() + " failed"));
        s.sendMessage(Text.mm(" <gray>Money on the server: <gold>" + plugin.economy().format(plugin.economy().total())
                + " <gray>· workers tick avg <white>" + String.format("%.3f", t[0]) + " ms<gray>, worst <white>"
                + String.format("%.2f", t[1]) + " ms"));
        s.sendMessage(Text.mm(" <gray>Last reset: <white>" + plugin.reset().lastReset()));
    }
}
