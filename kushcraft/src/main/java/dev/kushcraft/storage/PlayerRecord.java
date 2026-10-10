package dev.kushcraft.storage;

import java.util.UUID;

/**
 * One player's row in the database: money (in cents, so it never rounds),
 * lifetime sales, rank, active playtime and a few flags. Only changed on the
 * server thread; the balance is volatile so other threads (Vault calls from
 * other plugins) can read it safely.
 */
public final class PlayerRecord {

    /** Saw the welcome menu this season. */
    public static final int ONBOARDED = 1;
    /** Got the starter kit this season. */
    public static final int GOT_KIT = 2;
    /** Switched worker auto-buy off. */
    public static final int AUTO_BUY_OFF = 4;

    private final UUID id;
    private final PlayerStore store;
    String name;
    volatile long balance;
    long sales;
    int rank = 1;
    long rankedAt;
    long playtime;
    long firstSeen;
    long lastSeen;
    int flags;
    int season;

    /** Last time they moved their head, typed or clicked (playtime only counts while active). */
    public transient long lastActive;
    /** Last rank-up attempt (spam guard). */
    public transient long lastRankTry;

    boolean dirty;
    boolean soft;

    PlayerRecord(PlayerStore store, UUID id) {
        this.store = store;
        this.id = id;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void name(String n) {
        if (n != null && !n.equals(name)) {
            name = n;
            store.changed(this, false);
        }
    }

    /** Money in cents. */
    public long balance() {
        return balance;
    }

    /** Only Economy calls this (it logs every change). */
    public void balance(long cents) {
        balance = Math.max(0, cents);
        store.changed(this, true);
    }

    /** Lifetime sales in cents (this season). */
    public long sales() {
        return sales;
    }

    public void sales(long cents) {
        sales = Math.max(0, cents);
        store.changed(this, true);
    }

    public int rank() {
        return rank;
    }

    /** Epoch millis of the last rank-up (or when the season started for them). */
    public long rankedAt() {
        return rankedAt;
    }

    /** Only RankLadder calls this (rank-ups are logged there). */
    public void rank(int rank, long at) {
        this.rank = Math.max(1, rank);
        this.rankedAt = at;
        store.changed(this, true);
    }

    /** Active playtime in seconds (this season). */
    public long playtime() {
        return playtime;
    }

    /** Admin: set active playtime (seconds). */
    public void setPlaytime(long seconds) {
        playtime = Math.max(0, seconds);
        store.changed(this, true);
    }

    public void addPlaytime(long seconds) {
        playtime += Math.max(0, seconds);
        store.changed(this, false);
    }

    public long firstSeen() {
        return firstSeen;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public void seen(long now) {
        if (firstSeen == 0) {
            firstSeen = now;
        }
        lastSeen = now;
        store.changed(this, false);
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public void set(int flag, boolean on) {
        int f = on ? flags | flag : flags & ~flag;
        if (f != flags) {
            flags = f;
            store.changed(this, true);
        }
    }

    /** The season their row was last reset for (an inventory wipe catches up on their next join). */
    public int season() {
        return season;
    }

    public void season(int s) {
        season = s;
        store.changed(this, true);
    }
}
