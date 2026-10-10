package dev.kushcraft.economy;

import dev.kushcraft.KushCraft;
import dev.kushcraft.storage.Database;
import dev.kushcraft.storage.Persistence;
import org.bukkit.Bukkit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The transaction log: every economy event (player, type, amount, the balance
 * after it, the other side, a short detail, the time). Written in the same
 * database transaction as the balance it describes, so the log and the money
 * can never disagree.
 *
 * Events that happen many times a minute (worker wages, Runner sales, workers
 * buying supplies) are added up per player, worker and minute into one row
 * with a count, instead of thousands of rows an hour.
 */
public final class Ledger implements Persistence.Source {

    /** One log row. Amounts in cents (negative = money out). */
    public static final class Entry {
        long ts;
        final String player;
        final Tx type;
        long amount;
        Long balance;
        final String other;
        String detail;
        int count = 1;
        final String agg;
        final long minute;
        boolean changed = true;

        Entry(long ts, String player, Tx type, long amount, Long balance, String other, String detail, String agg) {
            this.ts = ts;
            this.player = player;
            this.type = type;
            this.amount = amount;
            this.balance = balance;
            this.other = other;
            this.detail = detail;
            this.agg = agg;
            this.minute = ts / 60_000L;
        }
    }

    /** A row read back for /kush log. */
    public record Row(long ts, String player, String type, long amount, Long balance, String other, String detail,
                      int count) {
    }

    private final KushCraft plugin;
    private final Database db;
    private final List<Entry> queue = new ArrayList<>();
    private final Map<String, Entry> open = new LinkedHashMap<>();
    private int season = 1;
    private long purgedAt;

    public Ledger(KushCraft plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void season(int s) {
        season = s;
    }

    public int season() {
        return season;
    }

    private static String id(UUID u) {
        return u == null ? null : u.toString();
    }

    /** Logs one event. */
    public void log(UUID player, Tx type, long amount, Long balanceAfter, String other, String detail) {
        queue.add(new Entry(System.currentTimeMillis(), id(player), type, amount, balanceAfter, other, clip(detail), null));
    }

    /** Adds an event to this minute's running total for (player, type, key). */
    public void sum(UUID player, Tx type, long amount, Long balanceAfter, String key, String detail) {
        long now = System.currentTimeMillis();
        String agg = type.name() + ":" + id(player) + ":" + key + ":" + (now / 60_000L);
        Entry e = open.get(agg);
        if (e == null) {
            e = new Entry(now, id(player), type, amount, balanceAfter, key, clip(detail), agg);
            open.put(agg, e);
            return;
        }
        e.ts = now;
        e.amount += amount;
        e.balance = balanceAfter;
        e.count++;
        if (detail != null) {
            e.detail = clip(detail);
        }
        e.changed = true;
    }

    private static String clip(String s) {
        return s == null || s.length() <= 200 ? s : s.substring(0, 200);
    }

    // ------------------------------------------------------------------
    // saving
    // ------------------------------------------------------------------

    private record Snap(long ts, String player, String type, long amount, Long balance, String other, String detail,
                        int count, String agg) {
    }

    private static Snap snap(Entry e, int season) {
        return new Snap(e.ts, e.player, e.type.name(), e.amount, e.balance, e.other, e.detail, e.count, e.agg);
    }

    @Override
    public void collect(Persistence.Batch b, boolean full) {
        long minute = System.currentTimeMillis() / 60_000L;
        List<Entry> taken = new ArrayList<>(queue);
        queue.clear();
        List<Entry> sums = new ArrayList<>();
        for (Iterator<Entry> it = open.values().iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (e.changed) {
                sums.add(e);
                e.changed = false;
            }
            if (e.minute < minute) {
                it.remove(); // that minute is over: its row is final
            }
        }
        if (taken.isEmpty() && sums.isEmpty()) {
            return;
        }
        int s = season;
        List<Snap> rows = new ArrayList<>(taken.size());
        taken.forEach(e -> rows.add(snap(e, s)));
        List<Snap> aggs = new ArrayList<>(sums.size());
        sums.forEach(e -> aggs.add(snap(e, s)));
        b.write(c -> {
            if (!rows.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO ledger(ts, player, type, amount, balance, other, detail, count, season) VALUES(?,?,?,?,?,?,?,?,?)")) {
                    for (Snap r : rows) {
                        bind(ps, r, s);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            if (!aggs.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO ledger(ts, player, type, amount, balance, other, detail, count, season, agg) VALUES(?,?,?,?,?,?,?,?,?,?)
                        ON CONFLICT(agg) DO UPDATE SET ts=excluded.ts, amount=excluded.amount, balance=excluded.balance,
                          detail=excluded.detail, count=excluded.count""")) {
                    for (Snap r : aggs) {
                        bind(ps, r, s);
                        ps.setString(10, r.agg());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        });
        b.onFailure(() -> {
            queue.addAll(0, taken);
            for (Entry e : sums) {
                e.changed = true;
                open.putIfAbsent(e.agg, e);
            }
        });
    }

    private static void bind(PreparedStatement ps, Snap r, int season) throws java.sql.SQLException {
        ps.setLong(1, r.ts());
        ps.setString(2, r.player());
        ps.setString(3, r.type());
        ps.setLong(4, r.amount());
        if (r.balance() == null) {
            ps.setNull(5, Types.INTEGER);
        } else {
            ps.setLong(5, r.balance());
        }
        ps.setString(6, r.other());
        ps.setString(7, r.detail());
        ps.setInt(8, r.count());
        ps.setInt(9, season);
    }

    // ------------------------------------------------------------------
    // reading and cleaning up
    // ------------------------------------------------------------------

    /** Reads a page of a player's log (newest first) off the server thread, then hands it back on it. */
    public void read(UUID player, int page, int perPage, Consumer<List<Row>> then) {
        db.run(c -> {
            List<Row> out = new ArrayList<>();
            String sql = player == null
                    ? "SELECT * FROM ledger ORDER BY id DESC LIMIT ? OFFSET ?"
                    : "SELECT * FROM ledger WHERE player=? ORDER BY id DESC LIMIT ? OFFSET ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                int i = 1;
                if (player != null) {
                    ps.setString(i++, player.toString());
                }
                ps.setInt(i++, perPage);
                ps.setInt(i, Math.max(0, page) * perPage);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long bal = rs.getLong("balance");
                        out.add(new Row(rs.getLong("ts"), rs.getString("player"), rs.getString("type"), rs.getLong("amount"),
                                rs.wasNull() ? null : bal, rs.getString("other"), rs.getString("detail"), rs.getInt("count")));
                    }
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> then.accept(out));
        });
    }

    /** Once a day: rows older than storage.log-days go (0 = keep forever). */
    public void purgeOld() {
        int days = plugin.getConfig().getInt("storage.log-days", 180);
        long now = System.currentTimeMillis();
        if (days <= 0 || now - purgedAt < 86_400_000L) {
            return;
        }
        purgedAt = now;
        long before = now - days * 86_400_000L;
        db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM ledger WHERE ts < ?")) {
                ps.setLong(1, before);
                int n = ps.executeUpdate();
                if (n > 0) {
                    plugin.getLogger().info("Transaction log: removed " + n + " rows older than " + days + " days.");
                }
            }
        });
    }
}
