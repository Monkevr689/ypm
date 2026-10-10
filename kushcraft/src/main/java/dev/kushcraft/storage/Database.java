package dev.kushcraft.storage;

import dev.kushcraft.KushCraft;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * The KushCraft database: one SQLite file (plugins/KushCraft/kushcraft.db).
 *
 * Everything goes through one connection on one thread ("KushCraft-DB"), so
 * statements never race each other and the server thread never waits for the
 * disk: {@link #run} queues work, {@link #call} waits for a result (only used
 * on start-up, shutdown and by admin commands like the reset). WAL mode keeps
 * commits cheap; every write batch is one transaction (see Persistence).
 *
 * Why SQLite and not MySQL: one Paper server is one writer, SQLite on the
 * server's own disk handles thousands of small transactions a second with no
 * setup, no network hop and no password in the config. MySQL only pays off
 * when several servers have to share one economy.
 */
public final class Database {

    /** Bumped when the tables change; {@link #migrate} brings older files up to date. */
    public static final int SCHEMA = 1;

    @FunctionalInterface
    public interface SqlWork {
        void run(Connection c) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlCall<T> {
        T call(Connection c) throws SQLException;
    }

    private final KushCraft plugin;
    private final File file;
    private ExecutorService exec;
    private volatile Thread dbThread;
    private Connection conn;

    public Database(KushCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kushcraft.db");
    }

    public File file() {
        return file;
    }

    // ------------------------------------------------------------------
    // open / close
    // ------------------------------------------------------------------

    /** Loads the driver, opens the file and creates or updates the tables. */
    public void open() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("The SQLite driver (org.sqlite.JDBC) is missing - Paper normally ships it.", e);
        }
        plugin.getDataFolder().mkdirs();
        exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "KushCraft-DB");
            t.setDaemon(true);
            dbThread = t;
            return t;
        });
        call(c -> {
            return null;
        });
    }

    private Connection connection() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement s = conn.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                // NORMAL: a commit survives the server process crashing (not a power cut mid-checkpoint)
                s.execute("PRAGMA synchronous=NORMAL");
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA busy_timeout=10000");
            }
            migrate(conn);
        }
        return conn;
    }

    /** Waits for queued work, then closes the connection. */
    public void close() {
        if (exec == null) {
            return;
        }
        try {
            call(c -> {
                try (Statement s = c.createStatement()) {
                    s.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                }
                c.close();
                conn = null;
                return null;
            });
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Closing the database", e);
        }
        exec.shutdown();
        try {
            if (!exec.awaitTermination(30, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("The database thread did not finish in 30 seconds.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        exec = null;
    }

    public boolean isOpen() {
        return exec != null && !exec.isShutdown();
    }

    // ------------------------------------------------------------------
    // running work
    // ------------------------------------------------------------------

    /** True on the database thread. */
    public boolean onDbThread() {
        return Thread.currentThread() == dbThread;
    }

    /** Queues work on the database thread (never blocks the server). */
    public CompletableFuture<Void> run(SqlWork work) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        exec.execute(() -> {
            try {
                work.run(connection());
                f.complete(null);
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    /** Runs work on the database thread and waits for the result (start-up, shutdown, admin commands). */
    public <T> T call(SqlCall<T> work) {
        if (onDbThread()) {
            try {
                return work.call(connection());
            } catch (SQLException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
        CompletableFuture<T> f = new CompletableFuture<>();
        exec.execute(() -> {
            try {
                f.complete(work.call(connection()));
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        try {
            return f.get(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            throw new IllegalStateException(c.getMessage(), c);
        } catch (TimeoutException e) {
            throw new IllegalStateException("the database did not answer in 5 minutes", e);
        }
    }

    /** Waits until everything queued so far is done. */
    public void drain() {
        call(c -> null);
    }

    /** Runs the work in one transaction: all of it is saved, or none of it. */
    public static void transaction(Connection c, SqlWork work) throws SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            work.run(c);
            c.commit();
        } catch (SQLException | RuntimeException e) {
            try {
                c.rollback();
            } catch (SQLException ignored) {
                // the original error matters more
            }
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    // ------------------------------------------------------------------
    // tables
    // ------------------------------------------------------------------

    private void migrate(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
        int have = Integer.parseInt(meta(c, "schema", "0"));
        if (have >= SCHEMA) {
            return;
        }
        transaction(c, t -> {
            try (Statement s = t.createStatement()) {
                // one row per player who ever joined: money in cents (never rounding errors), rank, playtime
                s.execute("""
                        CREATE TABLE IF NOT EXISTS players (
                          uuid TEXT PRIMARY KEY,
                          name TEXT NOT NULL DEFAULT '?',
                          balance INTEGER NOT NULL DEFAULT 0,
                          sales INTEGER NOT NULL DEFAULT 0,
                          rank INTEGER NOT NULL DEFAULT 1,
                          ranked_at INTEGER NOT NULL DEFAULT 0,
                          playtime INTEGER NOT NULL DEFAULT 0,
                          first_seen INTEGER NOT NULL DEFAULT 0,
                          last_seen INTEGER NOT NULL DEFAULT 0,
                          flags INTEGER NOT NULL DEFAULT 0,
                          season INTEGER NOT NULL DEFAULT 0)""");
                // every economy event: who, what, how much, the balance after it, when.
                // agg = key of a row that adds up many small events (wages, Runner sales) per minute
                s.execute("""
                        CREATE TABLE IF NOT EXISTS ledger (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          ts INTEGER NOT NULL,
                          player TEXT,
                          type TEXT NOT NULL,
                          amount INTEGER NOT NULL DEFAULT 0,
                          balance INTEGER,
                          other TEXT,
                          detail TEXT,
                          count INTEGER NOT NULL DEFAULT 1,
                          season INTEGER NOT NULL DEFAULT 0,
                          agg TEXT UNIQUE)""");
                s.execute("CREATE INDEX IF NOT EXISTS ledger_player ON ledger(player, ts)");
                s.execute("CREATE INDEX IF NOT EXISTS ledger_type ON ledger(type, ts)");
                s.execute("CREATE INDEX IF NOT EXISTS ledger_ts ON ledger(ts)");
                s.execute("""
                        CREATE TABLE IF NOT EXISTS workers (
                          id TEXT PRIMARY KEY,
                          owner TEXT NOT NULL,
                          type TEXT NOT NULL,
                          world TEXT NOT NULL,
                          x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL,
                          name TEXT,
                          level INTEGER NOT NULL DEFAULT 1,
                          paused INTEGER NOT NULL DEFAULT 0,
                          jobs INTEGER NOT NULL DEFAULT 0,
                          wages INTEGER NOT NULL DEFAULT 0,
                          recipe TEXT,
                          hired INTEGER NOT NULL DEFAULT 0,
                          away_since INTEGER NOT NULL DEFAULT 0,
                          knocked_until INTEGER NOT NULL DEFAULT 0,
                          satchel BLOB)""");
                s.execute("CREATE INDEX IF NOT EXISTS workers_owner ON workers(owner)");
                s.execute("""
                        CREATE TABLE IF NOT EXISTS plants (
                          pos TEXT PRIMARY KEY,
                          kind TEXT NOT NULL,
                          strain TEXT,
                          growth REAL NOT NULL,
                          fert INTEGER NOT NULL DEFAULT 0,
                          owner TEXT,
                          wild_until INTEGER NOT NULL DEFAULT 0,
                          grown_at INTEGER NOT NULL DEFAULT 0)""");
                s.execute("""
                        CREATE TABLE IF NOT EXISTS machines (
                          pos TEXT PRIMARY KEY,
                          type TEXT NOT NULL,
                          yaw REAL NOT NULL,
                          owner TEXT,
                          level INTEGER NOT NULL DEFAULT 1,
                          job TEXT,
                          job_start INTEGER NOT NULL DEFAULT 0,
                          job_end INTEGER NOT NULL DEFAULT 0,
                          output BLOB,
                          racks TEXT)""");
                s.execute("CREATE TABLE IF NOT EXISTS cartels (id TEXT PRIMARY KEY, bank INTEGER NOT NULL DEFAULT 0, data TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS awards (uuid TEXT PRIMARY KEY, data TEXT NOT NULL)");
                // small whole-system states (market prices, Trade prices)
                s.execute("CREATE TABLE IF NOT EXISTS docs (name TEXT PRIMARY KEY, data TEXT NOT NULL)");
                // Drug Lab blocks to clear when their chunk loads again (after a reset)
                s.execute("CREATE TABLE IF NOT EXISTS cleanup (pos TEXT PRIMARY KEY, what TEXT NOT NULL)");
            }
            setMeta(t, "schema", String.valueOf(SCHEMA));
            if (meta(t, "season", null) == null) {
                setMeta(t, "season", "1");
                setMeta(t, "season-start", String.valueOf(System.currentTimeMillis()));
            }
        });
        plugin.getLogger().info("Database ready: " + file.getName() + " (schema " + SCHEMA + ").");
    }

    // ------------------------------------------------------------------
    // meta values (season number, import done, ...)
    // ------------------------------------------------------------------

    public static String meta(Connection c, String key, String def) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT value FROM meta WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : def;
            }
        }
    }

    public static void setMeta(Connection c, String key, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO meta(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    public String meta(String key, String def) {
        return call(c -> meta(c, key, def));
    }

    public void setMetaNow(String key, String value) {
        call(c -> {
            setMeta(c, key, value);
            return null;
        });
    }

    /** Row counts of every table (backup check, /kush db). */
    public static Map<String, Long> counts(Connection c) throws SQLException {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String t : new String[]{"players", "ledger", "workers", "plants", "machines", "cartels", "awards", "docs"}) {
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + t)) {
                out.put(t, rs.next() ? rs.getLong(1) : 0);
            }
        }
        return out;
    }

    /** Sum of every balance in cents (backup check, reset summary). */
    public static long totalMoney(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(balance), 0) FROM players")) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    /** Logs a failed write loudly (the server keeps running on what's in memory and retries). */
    public void warn(String what, Throwable t) {
        plugin.getLogger().log(Level.SEVERE, "Database: " + what + " failed - " + t.getMessage()
                + (Bukkit.isPrimaryThread() ? "" : " (will retry)"), t);
    }
}
