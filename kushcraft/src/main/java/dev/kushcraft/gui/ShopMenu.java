package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.shop.Market;
import dev.kushcraft.shop.Shop;
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
 * Shop: buy seeds and gear (rows 1-3), hand in daily orders (row 4) and
 * sell your product (click it below, or Sell all). Layout: tools/gui.py shop().
 */
public final class ShopMenu extends TabMenu {

    static final int FIRST = 9;
    static final int SLOTS = 27;
    static final int[] ORDERS = {at(4, 2), at(4, 4), at(4, 6)};
    static final int SELL_ALL = at(5, 4);

    private final boolean atDealer;
    private List<Market.Order> shownOrders = List.of();
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
        List<Shop.BuyEntry> entries = shop().buyEntries();
        double bal = plugin.economy().balance(player);
        for (int i = 0; i < SLOTS && i < entries.size(); i++) {
            Shop.BuyEntry e = entries.get(i);
            ItemStack show = shop().create(e);
            ItemMeta meta = show.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            String price = e.price() <= 0 ? "FREE" : money(e.price()) + (e.amount() > 1 ? " for " + e.amount() : "");
            lore.add(Text.mm((bal >= e.price() ? "<gold>" : "<red>") + price));
            lore.add(Text.mm("<dark_gray>Click: buy · Shift: buy 5"));
            meta.lore(lore);
            show.setItemMeta(meta);
            set(FIRST + i, show);
        }
        List<Market.Order> orders = plugin.market().orders();
        shownOrders = List.copyOf(orders.subList(0, Math.min(orders.size(), ORDERS.length)));
        for (int i = 0; i < shownOrders.size(); i++) {
            set(ORDERS[i], orderIcon(shownOrders.get(i)));
        }
        double value = Selling.allValue(player);
        List<String> sell = new ArrayList<>();
        sell.add("<gray>Or click product below to sell it.");
        ItemType hot = plugin.market().hot();
        if (hot != null) {
            sell.add("<gold>Hot: " + hot.display() + " +" + Math.round(
                    (plugin.getConfig().getDouble("market.hot-item-bonus", 1.5) - 1) * 100) + "%");
        }
        set(SELL_ALL, Items.glint(Items.icon("ui_sell", value > 0 ? "<green><bold>Sell all</bold> <gold>" + money(value)
                : "<gray>Sell all", sell), value > 0));
    }

    private ItemStack orderIcon(Market.Order o) {
        int have = InventoryUtil.count(player, it -> Items.type(it) == o.type());
        long mins = Math.max(0, (o.expires() - System.currentTimeMillis()) / 60_000L);
        ItemStack it = CatalogIcons.sample(o.type());
        it.setAmount(Math.max(1, Math.min(99, o.amount())));
        boolean ok = have >= o.amount();
        it.editMeta(m -> {
            m.itemName(Text.mm("<yellow>Order: <white>" + o.amount() + "x " + o.type().display()));
            m.lore(Text.lines(List.of(
                    "<gold>Pays " + money(o.reward()),
                    (ok ? "<green>" : "<gray>") + have + "/" + o.amount() + " <dark_gray>· " + mins + " min left")));
        });
        return Items.glint(it, ok);
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
        for (int i = 0; i < shownOrders.size(); i++) {
            if (ORDERS[i] == slot) {
                if (KushCraft.get().market().complete(player, shownOrders.get(i))) {
                    successSound();
                } else {
                    failSound();
                }
                render();
                return;
            }
        }
        int idx = slot - FIRST;
        List<Shop.BuyEntry> entries = shop().buyEntries();
        if (idx >= 0 && idx < SLOTS && idx < entries.size()) {
            buy(entries.get(idx), click.isShiftClick() ? 5 : 1);
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
        // order timers and the hot item change while the menu is open
        if (++ticks % 15 == 0) {
            render();
        }
    }
}
