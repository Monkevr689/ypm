package dev.kushcraft.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player cooldowns for actions that move money (rank-ups, /pay, selling,
 * hiring...). Even when a player meets every requirement, an action can't be
 * repeated faster than its limit - no double clicks, no macro spam, no timing
 * tricks between two clicks in the same tick. Server thread only.
 */
public final class RateLimit {

    private final Map<UUID, Map<String, Long>> last = new HashMap<>();

    /** True (and remembered) when the action may run now; false while it's still cooling down. */
    public boolean allow(UUID player, String action, long millis) {
        long now = System.currentTimeMillis();
        Map<String, Long> m = last.computeIfAbsent(player, k -> new HashMap<>());
        Long at = m.get(action);
        if (at != null && now - at < millis) {
            return false;
        }
        m.put(action, now);
        return true;
    }

    /** Milliseconds until the action is allowed again (0 = now). */
    public long left(UUID player, String action, long millis) {
        Map<String, Long> m = last.get(player);
        Long at = m == null ? null : m.get(action);
        return at == null ? 0 : Math.max(0, millis - (System.currentTimeMillis() - at));
    }

    /** The player left: forget their cooldowns (the slow ones are kept in the database, e.g. rank-ups). */
    public void forget(UUID player) {
        last.remove(player);
    }
}
