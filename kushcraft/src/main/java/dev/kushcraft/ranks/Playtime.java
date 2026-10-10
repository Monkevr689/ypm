package dev.kushcraft.ranks;

import dev.kushcraft.KushCraft;
import dev.kushcraft.storage.PlayerRecord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Active playtime for the rank time-gates. A minute only counts when the
 * player did something in the last ranks.afk-minutes: turned their head,
 * clicked, broke or placed a block, used a command. Walking alone doesn't
 * count, so standing in a water stream (an AFK pool) earns nothing.
 */
public final class Playtime implements Listener {

    private final KushCraft plugin;

    public Playtime(KushCraft plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::minute, 1200L, 1200L);
    }

    private long afkMillis() {
        return (long) (Math.max(1, plugin.getConfig().getDouble("ranks.afk-minutes", 5)) * 60_000L);
    }

    /** Once a minute: every active player gets 60 seconds. */
    private void minute() {
        long now = System.currentTimeMillis();
        long afk = afkMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerRecord r = plugin.players().get(p.getUniqueId());
            if (r != null && now - r.lastActive <= afk) {
                r.addPlaytime(60);
            }
        }
    }

    /** True while the player counts as active. */
    public boolean active(Player p) {
        PlayerRecord r = plugin.players().get(p.getUniqueId());
        return r != null && System.currentTimeMillis() - r.lastActive <= afkMillis();
    }

    private void touch(Player p) {
        PlayerRecord r = plugin.players().get(p.getUniqueId());
        if (r != null) {
            r.lastActive = System.currentTimeMillis();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location a = e.getFrom(), b = e.getTo();
        // only looking around counts (water streams and pistons move you without it)
        if (a.getYaw() != b.getYaw() || a.getPitch() != b.getPitch()) {
            touch(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            touch(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        touch(e.getPlayer());
    }
}
