package dev.kushcraft.trade;

import dev.kushcraft.KushCraft;
import dev.kushcraft.menus.TradeWindow;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player-to-player trading (/trade): requests, the open trade windows and the safety nets (quit,
 * death, timeout, shutdown all cancel a trade and hand everything back).
 */
public final class Trades implements Listener {

    private record Request(UUID from, String fromName, long expires) {
    }

    private final KushCraft plugin;
    private final Map<UUID, Request> requests = new HashMap<>();
    private final Map<UUID, TradeSession> sessions = new HashMap<>();
    private final Map<UUID, List<ItemStack>> waiting = new HashMap<>();

    public Trades(KushCraft plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("trade.enabled", true);
    }

    public TradeSession of(Player p) {
        return sessions.get(p.getUniqueId());
    }

    public int open() {
        return new java.util.HashSet<>(sessions.values()).size();
    }

    // ------------------------------------------------------------------
    // requests
    // ------------------------------------------------------------------

    /** Asks someone to trade (or accepts their request to you). Returns an error message or null. */
    public String request(Player from, Player to) {
        if (!enabled()) {
            return "Player trading is turned off on this server.";
        }
        if (to == null || !to.isOnline()) {
            return "That player isn't online.";
        }
        if (from.getUniqueId().equals(to.getUniqueId())) {
            return "You can't trade with yourself.";
        }
        if (of(from) != null) {
            return "You are already in a trade.";
        }
        if (of(to) != null) {
            return to.getName() + " is in another trade.";
        }
        Request theirs = requests.get(from.getUniqueId());
        if (theirs != null && theirs.from().equals(to.getUniqueId()) && theirs.expires() > System.currentTimeMillis()) {
            return accept(from, to.getName());
        }
        if (!plugin.rates().allow(from.getUniqueId(), "trade-request", 2_000L)) {
            return "Slow down a little.";
        }
        long seconds = Math.max(10, plugin.getConfig().getLong("trade.request-seconds", 60));
        requests.put(to.getUniqueId(), new Request(from.getUniqueId(), from.getName(), System.currentTimeMillis() + seconds * 1000L));
        from.sendMessage(Text.msg("<gray>Trade request sent to <white>" + Text.escape(to.getName()) + "</white>."));
        to.sendMessage(Text.msg("<gold>" + Text.escape(from.getName()) + "</gold> <white>wants to trade with you.</white> "
                + "<click:run_command:'/trade accept " + from.getName() + "'><green><bold>[Accept]</bold></green></click> "
                + "<click:run_command:'/trade decline'><red>[Decline]</red></click> <dark_gray>(" + seconds + "s)"));
        to.playSound(to.getLocation(), "minecraft:block.note_block.pling", 0.7f, 1.4f);
        return null;
    }

    /** Accepts the request of {@code fromName} (null = whoever asked last). Returns an error or null. */
    public String accept(Player to, String fromName) {
        Request r = requests.get(to.getUniqueId());
        if (r == null || r.expires() < System.currentTimeMillis()
                || (fromName != null && !r.fromName().equalsIgnoreCase(fromName))) {
            return "Nobody is asking to trade with you (or it ran out).";
        }
        Player from = Bukkit.getPlayer(r.from());
        requests.remove(to.getUniqueId());
        if (from == null || !from.isOnline()) {
            return "They left.";
        }
        if (of(to) != null || of(from) != null) {
            return "One of you is already in a trade.";
        }
        start(from, to);
        return null;
    }

    public void decline(Player to) {
        Request r = requests.remove(to.getUniqueId());
        if (r == null) {
            to.sendMessage(Text.msg("<gray>Nobody is asking to trade with you."));
            return;
        }
        to.sendMessage(Text.msg("<gray>Declined."));
        Player from = Bukkit.getPlayer(r.from());
        if (from != null) {
            from.sendMessage(Text.msg("<gray>" + Text.escape(to.getName()) + " declined your trade."));
        }
    }

    private void start(Player a, Player b) {
        TradeSession s = new TradeSession(plugin, this, a, b);
        sessions.put(a.getUniqueId(), s);
        sessions.put(b.getUniqueId(), s);
        for (Player p : new Player[]{a, b}) {
            TradeWindow w = new TradeWindow(p, s, s.side(p));
            s.window(s.side(p), w);
            w.open();
        }
    }

    // ------------------------------------------------------------------
    // ending
    // ------------------------------------------------------------------

    void finished(TradeSession s) {
        sessions.values().removeIf(x -> x == s);
    }

    /** Items for someone who isn't online (never expected; they get them when they join). */
    void holdForOffline(UUID id, ItemStack[] items) {
        if (items.length > 0) {
            waiting.computeIfAbsent(id, k -> new ArrayList<>()).addAll(List.of(items));
        }
    }

    /** Every second: old requests go, idle trades are cancelled. */
    public void tick() {
        long now = System.currentTimeMillis();
        requests.values().removeIf(r -> r.expires() < now);
        long idle = Math.max(30, plugin.getConfig().getLong("trade.timeout-seconds", 180)) * 1000L;
        for (TradeSession s : new java.util.HashSet<>(sessions.values())) {
            if (now - s.touched() > idle) {
                s.cancel("Nobody did anything for a while.");
            }
        }
    }

    /** Cancels everything (reload, reset, shutdown). */
    public void shutdown() {
        for (TradeSession s : new java.util.HashSet<>(sessions.values())) {
            s.cancel("The server is busy.");
        }
        requests.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        requests.remove(e.getPlayer().getUniqueId());
        requests.values().removeIf(r -> r.from().equals(e.getPlayer().getUniqueId()));
        TradeSession s = of(e.getPlayer());
        if (s != null) {
            s.cancel(e.getPlayer().getName() + " left.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        TradeSession s = of(e.getPlayer());
        if (s != null) {
            s.cancel(e.getPlayer().getName() + " died.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        List<ItemStack> items = waiting.remove(e.getPlayer().getUniqueId());
        if (items != null) {
            InventoryUtil.give(e.getPlayer(), items.toArray(new ItemStack[0]));
            e.getPlayer().sendMessage(Text.msg("<gray>Your items from a cancelled trade are back."));
        }
    }
}
