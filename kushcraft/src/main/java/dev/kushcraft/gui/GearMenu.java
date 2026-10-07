package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.Items;
import dev.kushcraft.shop.Shop;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Shop &gt; Gear: supplies and blocks on four racks (rows 1-4), back to the
 * seeds (5,0) and the Workers tab (5,4). Layout: tools/gui.py gear().
 */
public final class GearMenu extends TabMenu {

    static final int FIRST_GEAR = 9;
    static final int GEAR = 36;
    static final int SEEDS = at(5, 0);
    static final int WORKERS = at(5, 4);

    public GearMenu(Player player) {
        super(player, Tab.SHOP, "gear", "Gear");
    }

    @Override
    protected boolean subPage() {
        return true;
    }

    private static Shop shop() {
        return KushCraft.get().shop();
    }

    @Override
    protected void page() {
        KushCraft plugin = KushCraft.get();
        double bal = plugin.economy().balance(player);
        List<Shop.BuyEntry> gear = shop().gear();
        for (int i = 0; i < GEAR && i < gear.size(); i++) {
            set(FIRST_GEAR + i, ShopMenu.entryIcon(gear.get(i), bal));
        }
        set(SEEDS, Items.icon("ui_back", "<gray>Back to seeds"));
        int mine = plugin.workers().of(player.getUniqueId()).size();
        set(WORKERS, Items.icon("tab_workers", "<gold><bold>Workers</bold> <gray>(" + mine + " hired)",
                "<gray>Hire Farmhands, Dryers, Cooks,", "<gray>Runners and Suppliers in their tab."));
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        if (slot == SEEDS) {
            clickSound();
            new ShopMenu(player).open();
            return;
        }
        if (slot == WORKERS) {
            clickSound();
            Tab.WORKERS.open(player);
            return;
        }
        int idx = slot - FIRST_GEAR;
        if (idx >= 0 && idx < GEAR && idx < shop().gear().size()) {
            ShopMenu.buy(player, shop().gear().get(idx), click.isShiftClick() ? 5 : 1);
            render();
        }
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (Selling.clicked(player, slot, item, click)) {
            render();
        }
    }
}
