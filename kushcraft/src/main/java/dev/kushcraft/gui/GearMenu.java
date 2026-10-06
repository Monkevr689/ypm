package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.Items;
import dev.kushcraft.shop.Shop;
import dev.kushcraft.util.Text;
import dev.kushcraft.worker.WorkerType;
import dev.kushcraft.worker.Workers;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop > Gear &amp; Workers: supplies and blocks on two racks (rows 1-2),
 * workers for hire on the "hiring" board (3,2) and (3,6), back to the
 * seeds (5,0) and your workers (5,4). Layout: tools/gui.py gear().
 */
public final class GearMenu extends TabMenu {

    static final int FIRST_GEAR = 9;
    static final int GEAR = 18;
    static final int[] HIRE = {at(3, 2), at(3, 6)};
    static final int SEEDS = at(5, 0);
    static final int MINE = at(5, 4);

    public GearMenu(Player player) {
        super(player, Tab.SHOP, "gear", "Gear & Workers");
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
        Workers ws = plugin.workers();
        List<Shop.BuyEntry> hires = shop().hires();
        for (int i = 0; i < HIRE.length && i < hires.size(); i++) {
            Shop.BuyEntry e = hires.get(i);
            WorkerType t = WorkerType.of(e.type());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + t.job());
            lore.add("<gray>" + t.tip());
            lore.add("<gray>Wage: <gold>" + money(ws.wage(t)) + " <gray>a job, from your wallet.");
            lore.add((bal >= e.price() ? "<gold>" : "<red>") + money(e.price()) + " <dark_gray>· then right-click the ground");
            set(HIRE[i], Items.icon(e.type().model(), t.color() + "<bold>Hire a " + t.display(), lore));
        }
        set(SEEDS, Items.icon("ui_back", "<gray>Back to seeds"));
        int mine = ws.of(player.getUniqueId()).size();
        set(MINE, Items.icon("ui_workers", "<green>Your workers <gray>(" + mine + "/" + ws.maxPerPlayer() + ")",
                mine == 0 ? "<dark_gray>You haven't hired anyone yet." : "<gray>Click to see what they're doing."));
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        if (slot == SEEDS) {
            clickSound();
            new ShopMenu(player).open();
            return;
        }
        if (slot == MINE) {
            openChild(new MyWorkersMenu(player));
            return;
        }
        for (int i = 0; i < HIRE.length; i++) {
            if (slot == HIRE[i] && i < shop().hires().size()) {
                if (!KushCraft.get().workers().enabled()) {
                    player.sendActionBar(Text.mm("<red>Workers are turned off on this server."));
                    failSound();
                    return;
                }
                ShopMenu.buy(player, shop().hires().get(i), 1);
                render();
                return;
            }
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
