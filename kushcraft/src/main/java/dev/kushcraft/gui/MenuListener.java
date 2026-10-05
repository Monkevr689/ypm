package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Routes clicks to menus and ticks open menus once per second. */
public final class MenuListener implements Listener {

    private static final Set<Menu> OPEN = new HashSet<>();

    static void opened(Menu m) {
        OPEN.add(m);
    }

    public static void start(KushCraft plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Menu m : new ArrayList<>(OPEN)) {
                if (!m.player().isOnline() || m.player().getOpenInventory().getTopInventory() != m.getInventory()) {
                    OPEN.remove(m);
                    continue;
                }
                m.tick();
            }
        }, 20L, 20L);
    }

    public static void closeAll() {
        for (Menu m : new ArrayList<>(OPEN)) {
            if (m.player().getOpenInventory().getTopInventory() == m.getInventory()) {
                m.player().closeInventory();
            }
        }
        OPEN.clear();
    }

    private static Menu menu(Inventory top) {
        return top != null && top.getHolder(false) instanceof Menu m ? m : null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        Menu m = menu(e.getView().getTopInventory());
        if (m == null) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() == null) {
            return;
        }
        int raw = e.getRawSlot();
        try {
            if (raw < e.getView().getTopInventory().getSize()) {
                if (raw == m.backSlot()) {
                    m.goBack();
                    return;
                }
                m.click(raw, e.getClick());
            } else if (e.getClickedInventory() == e.getWhoClicked().getInventory()) {
                m.clickOwn(e.getSlot(), e.getCurrentItem(), e.getClick());
            }
        } catch (RuntimeException ex) {
            KushCraft.get().getLogger().warning("Menu error: " + ex);
            ex.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (menu(e.getView().getTopInventory()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Menu m = menu(e.getInventory());
        if (m != null) {
            OPEN.remove(m);
            m.closed();
        }
    }
}
