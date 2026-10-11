package dev.kushcraft.storage;

import dev.kushcraft.KushCraft;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Saves KushCraft's state to the database, all of it together.
 *
 * <b>How it stays atomic.</b> Everything KushCraft owns (balances, ranks,
 * workers and their satchels, plants, labs, cartel banks, the log) lives in
 * memory and is only ever changed on the server thread, one change at a time
 * - so a purchase, a rank-up or a sale can never be half done or race with
 * another one. Every few ticks ({@code storage.flush-ticks}) the server thread
 * takes a snapshot of what changed and the database thread writes the whole
 * snapshot in ONE transaction. The database therefore always holds a state
 * that really existed at one moment: never money taken without the rank, a
 * worker hired without the money paid, or a sale logged without the cash.
 *
 * <b>Player inventories</b> are saved by the server, not by us, so the order
 * of the two saves decides what a crash can do:
 * <ul>
 * <li>a player who HANDED something to KushCraft (sold product, put items in a
 * satchel or a lab) has their player file saved BEFORE the snapshot that
 * pays them is written ({@link #took});</li>
 * <li>a player who GOT something from KushCraft (bought items, took a
 * satchel's contents, a worker contract back) is saved AFTER that snapshot is
 * in the database ({@link #gave}).</li>
 * </ul>
 * A crash at any moment therefore either keeps the last consistent state or
 * (in the millisecond between the two saves) loses the value in flight - it
 * never ends up both in the database and in the player's saved inventory.
 *
 * If a write fails (full disk...) nothing is lost from memory: every row in
 * the failed snapshot is marked changed again and the next flush retries.
 */
public final class Persistence {

    /** Something that keeps rows in the database (economy, workers, plants, ...). */
    public interface Source {

        /**
         * Server thread: adds writes for whatever changed since the last call and forgets those
         * change marks. {@code full} = also the slow, unimportant changes (plant growth, playtime).
         * Writes must only use values copied now (the database thread runs them later).
         */
        void collect(Batch batch, boolean full);
    }

    /** One snapshot: written in one transaction. */
    public static final class Batch {
        private final List<Database.SqlWork> writes = new ArrayList<>();
        private final List<Runnable> failed = new ArrayList<>();

        /** A write, run on the database thread inside the snapshot's transaction. */
        public void write(Database.SqlWork w) {
            writes.add(w);
        }

        /** Runs on the server thread if the snapshot couldn't be written (mark the rows changed again). */
        public void onFailure(Runnable r) {
            failed.add(r);
        }

        public boolean isEmpty() {
            return writes.isEmpty();
        }

        public int size() {
            return writes.size();
        }
    }

    private final KushCraft plugin;
    private final Database db;
    private final List<Source> sources = new ArrayList<>();
    private final Set<UUID> before = new HashSet<>();
    private final Set<UUID> after = new HashSet<>();
    private BukkitTask task;
    private long ticks;
    private long lastFull = System.currentTimeMillis();
    private int paused;
    private int inFlight;
    private int failures;
    private long commits;
    private volatile long lastCommitNanos;

    public Persistence(KushCraft plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void register(Source s) {
        sources.add(s);
    }

    /** Self test: takes a source out again. */
    public void unregister(Source s) {
        sources.remove(s);
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private int flushTicks() {
        return Math.max(1, plugin.getConfig().getInt("storage.flush-ticks", 5));
    }

    private long fullMillis() {
        return Math.max(5, plugin.getConfig().getLong("storage.full-save-seconds", 30)) * 1000L;
    }

    private void tick() {
        if (paused > 0 || ++ticks % flushTicks() != 0) {
            return;
        }
        boolean full = System.currentTimeMillis() - lastFull >= fullMillis();
        if (full) {
            lastFull = System.currentTimeMillis();
        }
        flush(full);
    }

    // ------------------------------------------------------------------
    // the save order around player inventories
    // ------------------------------------------------------------------

    /** The player handed value to KushCraft: their inventory is saved before the snapshot that pays them. */
    public void took(Player p) {
        if (p != null) {
            before.add(p.getUniqueId());
        }
    }

    /** The player got value from KushCraft: their inventory is saved once the snapshot is in the database. */
    public void gave(Player p) {
        if (p != null) {
            after.add(p.getUniqueId());
        }
    }

    private static void save(Set<UUID> ids) {
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.saveData();
            }
        }
    }

    // ------------------------------------------------------------------
    // flushing
    // ------------------------------------------------------------------

    /** Snapshot what changed and queue it (server thread). */
    public void flush(boolean full) {
        if (!db.isOpen()) {
            return;
        }
        if (!before.isEmpty()) {
            save(before);
            before.clear();
        }
        Batch b = new Batch();
        for (Source s : sources) {
            try {
                s.collect(b, full);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Database: collecting changes failed", e);
            }
        }
        Set<UUID> post = after.isEmpty() ? Set.of() : new HashSet<>(after);
        after.clear();
        if (b.isEmpty()) {
            save(post);
            return;
        }
        inFlight++;
        db.run(c -> write(c, b)).whenComplete((ok, err) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> committed(b, post, err));
        });
    }

    private void write(Connection c, Batch b) throws SQLException {
        long start = System.nanoTime();
        Database.transaction(c, t -> {
            for (Database.SqlWork w : b.writes) {
                w.run(t);
            }
        });
        lastCommitNanos = System.nanoTime() - start;
    }

    private void committed(Batch b, Set<UUID> post, Throwable err) {
        inFlight--;
        if (err == null) {
            commits++;
            save(post);
            return;
        }
        failures++;
        db.warn("saving " + b.size() + " changes", err);
        for (Runnable r : b.failed) {
            try {
                r.run();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Database: re-queueing failed changes", e);
            }
        }
        // what these players got isn't saved yet: keep their inventory save for after the retry
        after.addAll(post);
    }

    /**
     * Writes everything that changed right now and waits for it (shutdown, reset, backups).
     * Returns false when the write failed.
     */
    public boolean flushNow() {
        if (!db.isOpen()) {
            return false;
        }
        save(before);
        before.clear();
        Batch b = new Batch();
        for (Source s : sources) {
            s.collect(b, true);
        }
        Set<UUID> post = new HashSet<>(after);
        after.clear();
        lastFull = System.currentTimeMillis();
        try {
            if (!b.isEmpty()) {
                db.call(c -> {
                    write(c, b);
                    return null;
                });
                commits++;
            } else {
                db.drain();
            }
            save(post);
            return true;
        } catch (RuntimeException e) {
            failures++;
            db.warn("saving " + b.size() + " changes", e);
            b.failed.forEach(Runnable::run);
            after.addAll(post);
            return false;
        }
    }

    /** No automatic flushes until {@link #resume} (the reset rewrites the tables underneath). */
    public void pause() {
        paused++;
    }

    public void resume() {
        paused = Math.max(0, paused - 1);
    }

    // ------------------------------------------------------------------
    // status (/kush db)
    // ------------------------------------------------------------------

    public int inFlight() {
        return inFlight;
    }

    public int failures() {
        return failures;
    }

    public long commits() {
        return commits;
    }

    public double lastCommitMillis() {
        return lastCommitNanos / 1e6;
    }
}
