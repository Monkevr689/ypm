package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.shop.Shop;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop: seeds of every strain (cheapest first) and the other seeds in rows
 * 1-3, gear in row 4, and selling: click product below, or Sell all.
 * Layout: tools/gui.py shop().
 */
public final class ShopMenu extends TabMenu {

    static final int FIRST_SEED = 9;
    static final int SEEDS = 27;
    static final int FIRST_GEAR = 36;
    static final int GEAR = 9;
    static final int SELL_ALL = at(5, 4);

    private final boolean atDealer;
    private int ticks;

    public ShopMenu(Player player) {
        this(player, false);
    }

    /** atDealer: opened from a Dealer Stand (needed when market.anywhere is false). */
    public ShopMenu(Player player, boolean atDealer) {
        super(player, Tab.SHOP);
        this.atDealer = atDealer;
    }

    private boolean allowed() {
        return atDealer || player.hasPermission("kushcraft.admin")
                || KushCraft.get().getConfig().getBoolean("market.anywhere", true);
    }

    private Shop shop() {
        return KushCraft.get().shop();
    }

    @Override
    protected void page() {
        KushCraft plugin = KushCraft.get();
        if (!allowed()) {
            set(at(2, 4), Items.icon("machine_dealer", "<yellow>Find a Dealer Stand",
                    "<gray>This server only trades at a Dealer Stand."));
            return;
        }
        double bal = plugin.economy().balance(player);
        List<Shop.BuyEntry> seeds = shop().seeds();
        for (int i = 0; i < SEEDS && i < seeds.size(); i++) {
            set(FIRST_SEED + i, entryIcon(seeds.get(i), bal));
        }
        List<Shop.BuyEntry> gear = shop().gear();
        for (int i = 0; i < GEAR && i < gear.size(); i++) {
            set(FIRST_GEAR + i, entryIcon(gear.get(i), bal));
        }
        double value = Selling.allValue(player);
        List<String> sell = new ArrayList<>();
        sell.add("<gray>Or click product below to sell it.");
        ItemType hot = plugin.market().hot();
        if (hot != null) {
            sell.add("<gold>Hot: " + hot.display() + " +" + Math.round(
                    (plugin.getConfig().getDouble("market.hot-item-bonus", 1.5) - 1) * 100) + "%");
        }
        double bonus = shop().bonus(player) - 1;
        if (bonus > 0.001) {
            sell.add("<green>Your bonus: +" + Math.round(bonus * 100) + "%");
        }
        set(SELL_ALL, Items.glint(Items.icon("ui_sell", value > 0 ? "<green><bold>Sell all</bold> <gold>" + money(value)
                : "<gray>Sell all", sell), value > 0));
    }

    private ItemStack entryIcon(Shop.BuyEntry e, double bal) {
        ItemStack show = shop().create(e);
        ItemMeta meta = show.getItemMeta();
        List<String> lore = new ArrayList<>();
        Strain s = e.strain() == null ? null : KushCraft.get().strains().get(e.strain());
        if (s != null) {
            lore.add(s.rarity().colored() + " <dark_gray>·</dark_gray> <white>" + s.potency() + "% THC"
                    + " <dark_gray>·</dark_gray> " + s.type().colored());
            lore.add(Items.climateLine(s));
        }
        String price = e.price() <= 0 ? "FREE" : money(e.price()) + (e.amount() > 1 ? " for " + e.amount() : "");
        lore.add((bal >= e.price() ? "<gold>" : "<red>") + price + " <dark_gray>· Shift: buy 5");
        meta.lore(Text.lines(lore));
        show.setItemMeta(meta);
        return show;
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        if (!allowed()) {
            return;
        }
        if (slot == SELL_ALL) {
            if (Selling.all(player)) {
                render();
            } else {
                failSound();
            }
            return;
        }
        List<Shop.BuyEntry> list = slot < FIRST_GEAR ? shop().seeds() : shop().gear();
        int idx = slot < FIRST_GEAR ? slot - FIRST_SEED : slot - FIRST_GEAR;
        if (idx >= 0 && idx < list.size() && idx < (slot < FIRST_GEAR ? SEEDS : GEAR)) {
            buy(list.get(idx), click.isShiftClick() ? 5 : 1);
        }
    }

    private void buy(Shop.BuyEntry e, int times) {
        KushCraft plugin = KushCraft.get();
        int bought = 0;
        for (int i = 0; i < times; i++) {
            if (!plugin.economy().withdraw(player, e.price())) {
                break;
            }
            InventoryUtil.give(player, shop().create(e));
            bought++;
        }
        if (bought == 0) {
            player.sendActionBar(Text.mm("<red>You need " + money(e.price())));
            failSound();
        } else {
            player.playSound(player.getLocation(), "minecraft:entity.villager.yes", SoundCategory.PLAYERS, 0.7f, 1.1f);
            player.sendActionBar(Text.mm("<green>Bought " + (bought * e.amount()) + "x " + e.type().display()
                    + " <gray>for <gold>" + money(bought * e.price())));
        }
        render();
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (!allowed()) {
            return;
        }
        if (Selling.clicked(player, slot, item, click)) {
            render();
        } else if (Items.isCustom(item)) {
            player.sendActionBar(Text.mm("<gray>The Shop doesn't buy that."));
        }
    }

    @Override
    public void tick() {
        // the hot item and prices change while the menu is open
        if (++ticks % 15 == 0) {
            render();
        }
    }
}
