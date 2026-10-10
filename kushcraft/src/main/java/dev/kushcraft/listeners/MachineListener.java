package dev.kushcraft.listeners;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.machines.Machine;
import dev.kushcraft.util.BlockKey;
import dev.kushcraft.util.Protection;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/** Placing machines (they are barrier items) and creative-mode breaking. */
public final class MachineListener implements Listener {

    private final KushCraft plugin;

    public MachineListener(KushCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        ItemType t = Items.type(e.getItemInHand());
        if (t == null) {
            return;
        }
        if (t.machine() == null || !e.getPlayer().hasPermission("kushcraft.use")) {
            e.setCancelled(true); // never place other KushCraft items as blocks
            return;
        }
        if (plugin.plants().at(BlockKey.of(e.getBlockPlaced())) != null) {
            e.setCancelled(true);
            return;
        }
        plugin.machines().place(e.getPlayer(), e.getBlockPlaced(), t.machine(), Items.level(e.getItemInHand()));
        plugin.persistence().took(e.getPlayer()); // the item became a machine in the database
        if (t == ItemType.LAB_STATION) {
            plugin.awards().labPlaced(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (Protection.isChecking()) {
            return;
        }
        Machine m = plugin.machines().at(e.getBlock());
        if (m == null) {
            return;
        }
        e.setCancelled(true);
        // survival players punch machines (handled on click); creative players break them directly
        if (e.getPlayer().getGameMode() == GameMode.CREATIVE) {
            plugin.machines().breakMachine(m, e.getPlayer());
        }
    }
}
