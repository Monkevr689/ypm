package dev.kushcraft.storage;

import dev.kushcraft.KushCraft;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every player's row (balance, sales, rank, playtime), kept in memory and
 * saved through {@link Persistence}. Money and rank changes go out with the
 * next flush; playtime and names with the next full save.
 */
public final class PlayerStore implements Persistence.Source {

    private final KushCraft plugin;
    private final Database db;
    private final Map<UUID, PlayerRecord> players = new HashMap<>();
    private final Set<PlayerRecord> changed = new LinkedHashSet<>();
    private final Set<PlayerRecord> soft = new LinkedHashSet<>();

    public PlayerStore(KushCraft plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    /** Reads every row (start-up and after a reset). */
    public void load() {
        players.clear();
        changed.clear();
        soft.clear();
        db.call(c -> {
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM players")) {
                while (rs.next()) {
                    UUID id;
                    try {
                        id = UUID.fromString(rs.getString("uuid"));
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    PlayerRecord r = new PlayerRecord(this, id);
                    r.name = rs.getString("name");
                    r.balance = rs.getLong("balance");
                    r.sales = rs.getLong("sales");
                    r.rank = Math.max(1, rs.getInt("rank"));
                    r.rankedAt = rs.getLong("ranked_at");
                    r.playtime = rs.getLong("playtime");
                    r.firstSeen = rs.getLong("first_seen");
                    r.lastSeen = rs.getLong("last_seen");
                    r.flags = rs.getInt("flags");
                    r.season = rs.getInt("season");
                    players.put(id, r);
                }
            }
            return null;
        });
    }

    public PlayerRecord get(UUID id) {
        return players.get(id);
    }

    /** The row, made (with the defaults and no money) when the player never had one. */
    public PlayerRecord getOrCreate(UUID id, String name) {
        PlayerRecord r = players.get(id);
        if (r == null) {
            r = new PlayerRecord(this, id);
            r.name = name == null ? "?" : name;
            r.rankedAt = System.currentTimeMillis();
            players.put(id, r);
            changed(r, true);
        }
        return r;
    }

    public Collection<PlayerRecord> all() {
        return Collections.unmodifiableCollection(players.values());
    }

    void changed(PlayerRecord r, boolean important) {
        if (important) {
            r.dirty = true;
            changed.add(r);
        } else if (!r.dirty) {
            r.soft = true;
            soft.add(r);
        }
    }

    // ------------------------------------------------------------------
    // saving
    // ------------------------------------------------------------------

    private record Row(String id, String name, long balance, long sales, int rank, long rankedAt, long playtime,
                       long firstSeen, long lastSeen, int flags, int season) {
    }

    @Override
    public void collect(Persistence.Batch b, boolean full) {
        if (changed.isEmpty() && (!full || soft.isEmpty())) {
            return;
        }
        List<PlayerRecord> recs = new ArrayList<>(changed);
        if (full) {
            for (PlayerRecord r : soft) {
                if (!r.dirty) {
                    recs.add(r);
                }
            }
            soft.clear();
        }
        changed.clear();
        List<Row> rows = new ArrayList<>(recs.size());
        for (PlayerRecord r : recs) {
            r.dirty = false;
            r.soft = false;
            rows.add(new Row(r.id().toString(), r.name == null ? "?" : r.name, r.balance, r.sales, r.rank, r.rankedAt,
                    r.playtime, r.firstSeen, r.lastSeen, r.flags, r.season));
        }
        b.write(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO players(uuid, name, balance, sales, rank, ranked_at, playtime, first_seen, last_seen, flags, season)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, balance=excluded.balance, sales=excluded.sales,
                      rank=excluded.rank, ranked_at=excluded.ranked_at, playtime=excluded.playtime,
                      first_seen=excluded.first_seen, last_seen=excluded.last_seen, flags=excluded.flags,
                      season=excluded.season""")) {
                for (Row r : rows) {
                    ps.setString(1, r.id());
                    ps.setString(2, r.name());
                    ps.setLong(3, r.balance());
                    ps.setLong(4, r.sales());
                    ps.setInt(5, r.rank());
                    ps.setLong(6, r.rankedAt());
                    ps.setLong(7, r.playtime());
                    ps.setLong(8, r.firstSeen());
                    ps.setLong(9, r.lastSeen());
                    ps.setInt(10, r.flags());
                    ps.setInt(11, r.season());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
        b.onFailure(() -> recs.forEach(r -> changed(r, true)));
    }

    /** Admin/test helper: forget a row completely (self test players). */
    public void delete(UUID id) {
        PlayerRecord r = players.remove(id);
        if (r != null) {
            changed.remove(r);
            soft.remove(r);
        }
        db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM players WHERE uuid=?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
        });
    }

    /** Count of rows (status). */
    public int size() {
        return players.size();
    }
}
