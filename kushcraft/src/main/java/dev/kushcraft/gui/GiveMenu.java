package dev.kushcraft.gui;

import dev.kushcraft.catalog.Catalog;
import dev.kushcraft.item.Items;
import dev.kushcraft.util.InventoryUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Admin: click any KushCraft item to get it. */
public final class GiveMenu extends ListMenu {

    private List<ItemStack> current = List.of();

    public GiveMenu(Player player) {
        super(player, "Give Items (admin)");
    }

    @Override
    protected List<ItemStack> entries() {
        List<ItemStack> out = new ArrayList<>();
        for (Catalog.Entry e : Catalog.entries(null)) {
            out.add(CatalogIcons.sample(e.type()));
        }
        // workers are not in the catalog
        for (dev.kushcraft.worker.WorkerType t : dev.kushcraft.worker.WorkerType.values()) {
            out.add(dev.kushcraft.item.Items.create(t.item()));
        }
        current = out;
        return out;
    }

    @Override
    protected ItemStack header() {
        return Items.icon("ui_crown", "<red>Give items", "<gray>Click: get 1", "<gray>Shift-click: get a stack");
    }

    @Override
    protected void clickEntry(int index, ClickType click) {
        if (!player.hasPermission("kushcraft.admin") || index >= current.size()) {
            return;
        }
        ItemStack it = current.get(index).clone();
        it.setAmount(click.isShiftClick() ? it.getMaxStackSize() : 1);
        InventoryUtil.give(player, it);
        successSound();
    }
}
