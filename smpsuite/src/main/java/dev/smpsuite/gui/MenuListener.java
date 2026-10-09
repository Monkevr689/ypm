package dev.smpsuite.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Routes clicks to SMPSuite menus and keeps their items in place. */
public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Menu m)) {
            return;
        }
        if (m.storage()) {
            return; // the gem rules (no gems in storage) are checked by the gem listener
        }
        e.setCancelled(true);
        if (e.getClickedInventory() == e.getView().getTopInventory()) {
            m.click(e.getSlot(), e.getClick());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu m && !m.storage()) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder(false) instanceof Menu m) {
            m.closed();
        }
    }
}
