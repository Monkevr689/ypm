package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.shop.Economy;
import dev.kushcraft.shop.Market;
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
 * The Market: click to buy at the top, click your own items to sell.
 * Layout matches tools/gui.py dealer().
 */
public final class DealerMenu extends Menu {

    private static final int INFO = 0;
    private static final int ORDERS = 2;
    private static final int WALLET = 4;
    private static final int HOT = 6;
    private static final int SELL_ALL = 8;
    private static final int FIRST = 9;
    private static final int PER_PAGE = 36;
    private static final int BACK = 45;
    private static final int PREV = 48;
    private static final int PAGE = 49;
    private static final int NEXT = 50;

    private int page;

    public DealerMenu(Player player) {
        super(player, 6, "dealer", "Market");
    }

    private Shop shop() {
        return KushCraft.get().shop();
    }

    private Economy eco() {
        return KushCraft.get().economy();
    }

    private Market market() {
        return KushCraft.get().market();
    }

    @Override
    public void render() {
        inv.clear();
        backButton(BACK);
        List<Shop.BuyEntry> entries = shop().buyEntries();
        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        double bal = eco().balance(player);
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
            lore.add(Text.mm("<gray>Price: " + (e.price() <= 0 ? "<green>FREE" : "<gold>" + eco().format(e.price()))
                    + (e.amount() > 1 ? " <dark_gray>for " + e.amount() : "")));
            lore.add(Text.mm(bal >= e.price() ? "<green>Click to buy  <yellow>Shift-click: buy 5" : "<red>You can't afford this"));
            meta.lore(lore);
            show.setItemMeta(meta);
            set(FIRST + i, show);
        }
        set(INFO, Items.icon("ui_info", "<green>The Market",
                "<gray><white>Buy:</white> click items above.",
                "<gray><white>Sell:</white> click your products below.",
                "<gray><yellow>Shift-click</yellow> sells the whole stack.",
                "",
                "<gray>Selling a lot of one thing drops its",
                "<gray>price for a while - sell a mix!",
                "<gray>Better quality <gold>★</gold> and stronger strains",
                "<gray>always sell for more."));
        set(ORDERS, Items.icon("ui_orders", "<gold>Daily Orders",
                "<gray>Hand in a batch for <gold>bonus cash</gold>.",
                "<gray>" + market().orders().size() + " open orders",
                "",
                "<yellow>Click to open"));
        set(WALLET, Items.icon("ui_wallet", "<gold>Balance: <white>" + eco().format(bal),
                eco().usingVault() ? "<dark_gray>Vault economy" : "<dark_gray>KushCraft wallet"));
        ItemType hot = market().hot();
        if (hot != null) {
            set(HOT, Items.icon("ui_fire", "<gold>Hot right now: <white>" + hot.display(),
                    "<gray>Sells for <gold>" + (int) Math.round(KushCraft.get().getConfig()
                            .getDouble("market.hot-item-bonus", 1.5) * 100) + "%</gold> of the normal price",
                    "<gray>for another " + market().hotMinutesLeft() + " min."));
        }
        double total = sellAllValue();
        set(SELL_ALL, Items.icon("ui_sell", "<green>Sell everything",
                "<gray>Sells every KushCraft product in",
                "<gray>your inventory (not tools or blocks).",
                "",
                "<gray>Value: <gold>" + eco().format(total)));
        if (pages > 1) {
            set(PREV, Items.icon("ui_back", "<gray>Previous page"));
            set(NEXT, Items.icon("ui_arrow", "<gray>Next page"));
        }
        set(PAGE, Items.amount(Items.icon("ui_info", "<gray>Page " + (page + 1) + "/" + pages), page + 1));
    }

    private boolean sellable(ItemStack it) {
        return it != null && shop().sellPrice(it) > 0;
    }

    private double sellAllValue() {
        double total = 0;
        for (ItemStack it : player.getInventory().getStorageContents()) {
            if (sellable(it)) {
                total += shop().sellPrice(it) * it.getAmount();
            }
        }
        return total;
    }

    @Override
    public void click(int slot, ClickType click) {
        switch (slot) {
            case PREV -> {
                page--;
                clickSound();
                render();
            }
            case NEXT -> {
                page++;
                clickSound();
                render();
            }
            case SELL_ALL -> sellAll();
            case ORDERS -> openChild(new OrdersMenu(player));
            default -> {
                if (slot >= FIRST && slot < FIRST + PER_PAGE) {
                    int idx = page * PER_PAGE + (slot - FIRST);
                    List<Shop.BuyEntry> entries = shop().buyEntries();
                    if (idx < entries.size()) {
                        buy(entries.get(idx), click.isShiftClick() ? 5 : 1);
                    }
                }
            }
        }
    }

    private void buy(Shop.BuyEntry e, int times) {
        int bought = 0;
        for (int i = 0; i < times; i++) {
            if (!eco().withdraw(player, e.price())) {
                break;
            }
            InventoryUtil.give(player, shop().create(e));
            bought++;
        }
        if (bought == 0) {
            player.sendActionBar(Text.mm("<red>Not enough money! <gray>You need " + eco().format(e.price())));
            failSound();
        } else {
            player.playSound(player.getLocation(), "minecraft:entity.villager.yes", SoundCategory.PLAYERS, 0.7f, 1.1f);
            player.sendActionBar(Text.mm("<green>Bought " + (bought * e.amount()) + "x " + e.type().display()
                    + " <gray>for <gold>" + eco().format(bought * e.price())));
        }
        render();
    }

    private void sellAll() {
        PlayerInventory pi = player.getInventory();
        ItemStack[] contents = pi.getStorageContents();
        double total = 0;
        int count = 0;
        Map<ItemType, Integer> sold = new EnumMap<>(ItemType.class);
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (sellable(it)) {
                total += shop().sellPrice(it) * it.getAmount();
                count += it.getAmount();
                sold.merge(Items.type(it), it.getAmount(), Integer::sum);
                contents[i] = null;
            }
        }
        if (count == 0) {
            player.sendActionBar(Text.mm("<red>You have nothing the market wants."));
            failSound();
            return;
        }
        pi.setStorageContents(contents);
        eco().deposit(player, total);
        sold.forEach((t, n) -> market().sold(t, n));
        cashSound();
        player.sendActionBar(Text.mm("<green>Sold " + count + " items for <gold>" + eco().format(total)));
        render();
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (!sellable(item)) {
            if (Items.isCustom(item)) {
                player.sendActionBar(Text.mm("<gray>The market doesn't buy that."));
            }
            return;
        }
        int amount = click.isShiftClick() || click.isRightClick() ? item.getAmount() : 1;
        double each = shop().sellPrice(item);
        double total = each * amount;
        ItemStack inSlot = player.getInventory().getItem(slot);
        if (inSlot == null || !inSlot.isSimilar(item)) {
            return;
        }
        ItemType type = Items.type(item);
        inSlot.setAmount(inSlot.getAmount() - amount);
        player.getInventory().setItem(slot, inSlot.getAmount() <= 0 ? null : inSlot);
        eco().deposit(player, total);
        market().sold(type, amount);
        cashSound();
        player.sendActionBar(Text.mm("<green>Sold " + amount + "x for <gold>" + eco().format(total)
                + " <dark_gray>(market " + Text.plain(Text.mm(market().trend(type))) + ")"));
        render();
    }

    private void cashSound() {
        player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", SoundCategory.PLAYERS, 0.7f, 1.4f);
        player.playSound(player.getLocation(), "minecraft:block.chain.place", SoundCategory.PLAYERS, 0.6f, 1.8f);
    }
}
