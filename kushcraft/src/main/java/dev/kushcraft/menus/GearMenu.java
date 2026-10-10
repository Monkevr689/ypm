package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.Items;
import dev.kushcraft.economy.Shop;
import dev.kushcraft.util.Text;
import dev.kushcraft.workers.WorkerType;
import dev.kushcraft.workers.Workers;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop > Gear &amp; Workers: supplies and blocks on two racks (rows 1-2),
 * the four workers for hire on the "hiring" board (3,1) (3,3) (3,5) (3,7),
 * back to the seeds (5,0), your workers (5,4) and the auto-buy switch (5,8). Layout: tools/gui.py gear().
 */
public final class GearMenu extends TabMenu {

    static final int FIRST_GEAR = 9;
    static final int GEAR = 18;
    static final int[] HIRE = {at(3, 1), at(3, 3), at(3, 5), at(3, 7)};
    static final int SEEDS = at(5, 0);
    static final int MINE = at(5, 4);
    static final int AUTO_BUY = at(5, 8);

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
            lore.add(t == dev.kushcraft.workers.WorkerType.RUNNER
                    ? "<gray>Pay: <gold>" + Math.round(ws.runnerCut() * 100) + "% <gray>of what they sell."
                    : "<gray>Wage: <gold>" + money(ws.wage(t)) + " <gray>a job, from your wallet.");
            lore.add((bal >= e.price() ? "<gold>" : "<red>") + money(e.price()) + " <dark_gray>· then right-click the ground");
            set(HIRE[i], Items.icon(e.type().model(), t.color() + "<bold>Hire a " + t.display(), lore));
        }
        set(SEEDS, Items.icon("ui_back", "<gray>Back to seeds"));
        int mine = ws.of(player.getUniqueId()).size();
        String limit = ws.maxPerPlayer() > 0 ? "/" + ws.maxPerPlayer() : "";
        set(MINE, Items.icon("ui_workers", "<green>Your workers <gray>(" + mine + limit + ")",
                mine == 0 ? "<dark_gray>You haven't hired anyone yet." : "<gray>Click to see what they're doing.",
                "<dark_gray>Hire as many as you like. Workers near",
                "<dark_gray>each other hand things along by themselves."));
        set(AUTO_BUY, autoBuyIcon(ws));
    }

    /** Whether your workers buy their own seeds, fertilizer and ingredients with your money. */
    private ItemStack autoBuyIcon(Workers ws) {
        boolean on = ws.autoBuy(player.getUniqueId());
        List<String> lore = new ArrayList<>();
        if (on) {
            lore.add("<gray>When your workers can't find seeds,");
            lore.add("<gray>fertilizer or an ingredient in your chests,");
            lore.add("<gray>they buy it with your money, right away.");
            lore.add("<dark_gray>(Never Mythic seeds: those are your call.)");
        } else {
            lore.add("<gray>Switched off: your workers only use");
            lore.add("<gray>what's in your chests and their satchels.");
        }
        if (!ws.autoBuyAllowed()) {
            lore.add("<red>Turned off on this server.");
        } else {
            lore.add("<yellow>Click to switch " + (on ? "off" : "on"));
        }
        return Items.glint(Items.icon("ui_wallet", on ? "<green><bold>Workers auto-buy: ON"
                : "<yellow><bold>Workers auto-buy: OFF", lore), on);
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
        if (slot == AUTO_BUY) {
            Workers ws = KushCraft.get().workers();
            if (!ws.autoBuyAllowed()) {
                player.sendActionBar(Text.mm("<red>Workers can't buy things on this server."));
                failSound();
                return;
            }
            boolean on = !ws.autoBuy(player.getUniqueId());
            ws.setAutoBuy(player.getUniqueId(), on);
            player.sendActionBar(Text.mm(on ? "<green>Your workers buy what they need again."
                    : "<yellow>Your workers only use what's in your chests now."));
            clickSound();
            render();
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
