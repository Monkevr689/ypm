package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.shop.Shop;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Shop: buy seeds, supplies and blocks at the top; click your own
 * product below to sell it. Sales count towards your dealer rank.
 * Layout matches tools/gui.py shop().
 */
public final class ShopMenu extends TabMenu {

    private static final int FIRST = 9;
    private static final int PER_PAGE = 36;
    private static final int PREV = at(5, 0);
    private static final int NEXT = at(5, 1);
    private static final int HOT = at(5, 7);
    private static final int SELL_ALL = at(5, 8);

    private int page;
    private final boolean atDealer;

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
                    "<gray>On this server you can only buy and",
                    "<gray>sell at a <green>Dealer Stand</green> block."));
            return;
        }
        List<Shop.BuyEntry> entries = shop().buyEntries();
        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        double bal = plugin.economy().balance(player);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= entries.size()) {
                break;
            }
            Shop.BuyEntry e = entries.get(idx);
            ItemStack show = shop().create(e);
            ItemMeta meta = show.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Text.mm(""));
            lore.add(Text.mm("<gray>Price: " + (e.price() <= 0 ? "<green>FREE" : "<gold>" + money(e.price()))
                    + (e.amount() > 1 ? " <dark_gray>for " + e.amount() : "")));
            lore.add(Text.mm(bal >= e.price() ? "<green>Click to buy  <yellow>Shift-click: buy 5" : "<red>You can't afford this"));
            meta.lore(lore);
            show.setItemMeta(meta);
            set(FIRST + i, show);
        }
        if (pages > 1) {
            set(PREV, Items.icon("ui_back", "<gray>Previous page"));
            set(NEXT, Items.icon("ui_arrow", "<gray>Next page"));
        }
        ItemType hot = plugin.market().hot();
        if (hot != null) {
            set(HOT, Items.glint(Items.icon(hot.model(), "<gold>Hot: <white>" + hot.display(),
                    "<gray>Sells for <gold>+" + Math.round((plugin.getConfig().getDouble("market.hot-item-bonus", 1.5) - 1) * 100)
                            + "%</gold> for " + plugin.market().hotMinutesLeft() + " min."), true));
        }
        double bonus = plugin.ranks().of(player).bonus();
        set(SELL_ALL, Items.icon("ui_sell", "<green><bold>Sell all product",
                "<gray>Sells every drug, bud and harvest",
                "<gray>in your inventory (not tools or blocks).",
                "",
                "<gray>Value: <gold>" + money(sellAllValue()),
                bonus > 0 ? "<gray>Includes your rank bonus: <green>+" + Math.round(bonus * 100) + "%" : "",
                "",
                "<dark_gray>Selling a lot of one thing drops its",
                "<dark_gray>price for a while - sell a mix!"));
    }

    private boolean sellable(ItemStack it) {
        return it != null && shop().sellPrice(it) > 0;
    }

    private double sellAllValue() {
        double total = 0;
        for (ItemStack it : player.getInventory().getStorageContents()) {
            if (sellable(it)) {
                total += shop().sellPrice(it, player) * it.getAmount();
            }
        }
        return total;
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        if (!allowed()) {
            return;
        }
        if (slot == PREV || slot == NEXT) {
            page += slot == NEXT ? 1 : -1;
            clickSound();
            render();
        } else if (slot == SELL_ALL) {
            sellAll();
        } else if (slot >= FIRST && slot < FIRST + PER_PAGE) {
            int idx = page * PER_PAGE + (slot - FIRST);
            List<Shop.BuyEntry> entries = shop().buyEntries();
            if (idx < entries.size()) {
                buy(entries.get(idx), click.isShiftClick() ? 5 : 1);
            }
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
            player.sendActionBar(Text.mm("<red>Not enough money! <gray>You need " + money(e.price())));
            failSound();
        } else {
            player.playSound(player.getLocation(), "minecraft:entity.villager.yes", SoundCategory.PLAYERS, 0.7f, 1.1f);
            player.sendActionBar(Text.mm("<green>Bought " + (bought * e.amount()) + "x " + e.type().display()
                    + " <gray>for <gold>" + money(bought * e.price())));
        }
        render();
    }

    private void sellAll() {
        KushCraft plugin = KushCraft.get();
        PlayerInventory pi = player.getInventory();
        ItemStack[] contents = pi.getStorageContents();
        double total = 0;
        int count = 0;
        Map<ItemType, Integer> sold = new EnumMap<>(ItemType.class);
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (sellable(it)) {
                total += shop().sellPrice(it, player) * it.getAmount();
                count += it.getAmount();
                sold.merge(Items.type(it), it.getAmount(), Integer::sum);
                contents[i] = null;
            }
        }
        if (count == 0) {
            player.sendActionBar(Text.mm("<red>You have nothing the Shop buys."));
            failSound();
            return;
        }
        pi.setStorageContents(contents);
        paid(total);
        sold.forEach((t, n) -> plugin.market().sold(t, n));
        player.sendActionBar(Text.mm("<green>Sold " + count + " items for <gold>" + money(total)));
        render();
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (!allowed()) {
            return;
        }
        if (!sellable(item)) {
            if (Items.isCustom(item)) {
                player.sendActionBar(Text.mm("<gray>The Shop doesn't buy that."));
            }
            return;
        }
        int amount = click.isShiftClick() || click.isRightClick() ? item.getAmount() : 1;
        double total = shop().sellPrice(item, player) * amount;
        ItemStack inSlot = player.getInventory().getItem(slot);
        if (inSlot == null || !inSlot.isSimilar(item)) {
            return;
        }
        ItemType type = Items.type(item);
        inSlot.setAmount(inSlot.getAmount() - amount);
        player.getInventory().setItem(slot, inSlot.getAmount() <= 0 ? null : inSlot);
        paid(total);
        KushCraft.get().market().sold(type, amount);
        player.sendActionBar(Text.mm("<green>Sold " + amount + "x for <gold>" + money(total)
                + " <dark_gray>(price " + plain(KushCraft.get().market().trend(type)) + ")"));
        render();
    }

    private void paid(double total) {
        KushCraft plugin = KushCraft.get();
        plugin.economy().deposit(player, total);
        plugin.ranks().sold(player, total);
        player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", SoundCategory.PLAYERS, 0.7f, 1.4f);
        player.playSound(player.getLocation(), "minecraft:block.chain.place", SoundCategory.PLAYERS, 0.6f, 1.8f);
    }
}
