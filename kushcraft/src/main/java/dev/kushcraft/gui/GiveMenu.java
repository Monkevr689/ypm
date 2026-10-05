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

    private List<Catalog.Entry> current = List.of();

    public GiveMenu(Player player) {
        super(player, "Give Items (admin)");
    }

    @Override
    protected List<ItemStack> entries() {
        current = Catalog.entries(null);
        List<ItemStack> out = new ArrayList<>();
        for (Catalog.Entry e : current) {
            out.add(CatalogIcons.sample(e.type()));
        }
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
        ItemStack it = CatalogIcons.sample(current.get(index).type());
        it.setAmount(click.isShiftClick() ? it.getMaxStackSize() : 1);
        InventoryUtil.give(player, it);
        successSound();
    }
}
