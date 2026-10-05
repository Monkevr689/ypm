package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.Items;
import dev.kushcraft.shop.Economy;
import dev.kushcraft.shop.Exchange;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Trade: buy and sell vanilla resources (ores, food, wood...). Categories
 * along the bottom, click to buy, click your own items to sell.
 * Layout matches tools/gui.py trade().
 */
public final class TradeMenu extends TabMenu {

    private static final int FIRST = 9;
    private static final int PER_PAGE = 36;
    private static final int PREV = at(5, 0);
    private static final int FIRST_TAB = at(5, 1);
    private static final int TABS = 7;
    private static final int NEXT = at(5, 8);

    private int tab;
    private int page;

    public TradeMenu(Player player) {
        super(player, Tab.TRADE);
    }

    private Exchange ex() {
        return KushCraft.get().exchange();
    }

    private Economy eco() {
        return KushCraft.get().economy();
    }

    private List<Exchange.Offer> offers() {
        List<Exchange.Category> cats = ex().categories();
        if (cats.isEmpty()) {
            return List.of();
        }
        tab = Math.max(0, Math.min(tab, cats.size() - 1));
        return cats.get(tab).offers();
    }

    @Override
    protected void page() {
        List<Exchange.Category> cats = ex().categories();
        List<Exchange.Offer> offers = offers();
        for (int i = 0; i < Math.min(TABS, cats.size()); i++) {
            Exchange.Category c = cats.get(i);
            ItemStack t = new ItemStack(c.icon());
            boolean on = i == tab;
            t.editMeta(m -> {
                m.itemName(Text.mm((on ? "<green>▶ " : "<yellow>") + Text.escape(c.name())));
                m.lore(Text.lines(List.of("<gray>" + c.offers().size() + " items", on ? "<green>Showing" : "<gray>Click to show")));
                m.setEnchantmentGlintOverride(on ? Boolean.TRUE : null);
                m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
            });
            set(FIRST_TAB + i, t);
        }
        double bal = eco().balance(player);
        double ratio = KushCraft.get().getConfig().getDouble("exchange.sell-ratio", 0.5);
        int pages = Math.max(1, (offers.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= offers.size()) {
                break;
            }
            Exchange.Offer o = offers.get(idx);
            double each = ex().buyPrice(o);
            ItemStack show = new ItemStack(o.material(), Math.min(o.amount(), o.material().getMaxStackSize()));
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Buy: <gold>" + eco().format(each) + "</gold> each <dark_gray>(" + eco().format(each * o.amount())
                    + " for " + o.amount() + ")");
            lore.add("<gray>Sell: <green>" + eco().format(ex().sellPrice(o.material())) + "</green> each");
            lore.add("<gray>Price now: " + ex().trend(o.material()));
            lore.add("");
            lore.add("<dark_gray>Sell: click it in your inventory");
            lore.add("<dark_gray>(shift-click sells all of it).");
            lore.add(bal >= each * o.amount() ? "<green>Click: buy " + o.amount() + "  <yellow>Shift: buy " + (o.amount() * 4)
                    : "<red>You can't afford " + o.amount() + ".");
            show.editMeta(m -> {
                m.lore(Text.lines(lore));
                m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
            });
            set(FIRST + i, show);
        }
        if (pages > 1) {
            set(PREV, Items.icon("ui_back", "<gray>Previous page", "<dark_gray>Page " + (page + 1) + "/" + pages));
            set(NEXT, Items.icon("ui_arrow", "<gray>Next page", "<dark_gray>Page " + (page + 1) + "/" + pages));
        }
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        if (slot >= FIRST_TAB && slot < FIRST_TAB + TABS) {
            if (slot - FIRST_TAB < ex().categories().size()) {
                tab = slot - FIRST_TAB;
                page = 0;
                clickSound();
                render();
            }
        } else if (slot == PREV || slot == NEXT) {
            page += slot == NEXT ? 1 : -1;
            clickSound();
            render();
        } else if (slot >= FIRST && slot < FIRST + PER_PAGE) {
            List<Exchange.Offer> offers = offers();
            int idx = page * PER_PAGE + (slot - FIRST);
            if (idx < offers.size()) {
                Exchange.Offer o = offers.get(idx);
                buy(o, click.isShiftClick() ? o.amount() * 4 : o.amount());
            }
        }
    }

    private void buy(Exchange.Offer o, int amount) {
        double each = ex().buyPrice(o);
        double total = Math.round(each * amount * 100) / 100.0;
        if (!eco().withdraw(player, total)) {
            player.sendActionBar(Text.mm("<red>Not enough money! <gray>" + amount + "x costs " + eco().format(total)));
            failSound();
            return;
        }
        int left = amount;
        List<ItemStack> stacks = new ArrayList<>();
        while (left > 0) {
            int n = Math.min(left, o.material().getMaxStackSize());
            stacks.add(new ItemStack(o.material(), n));
            left -= n;
        }
        InventoryUtil.give(player, stacks.toArray(new ItemStack[0]));
        ex().bought(o.material(), amount);
        player.playSound(player.getLocation(), "minecraft:entity.villager.yes", SoundCategory.PLAYERS, 0.7f, 1.1f);
        player.sendActionBar(Text.mm("<green>Bought " + amount + "x <lang:" + o.material().translationKey()
                + "> <gray>for <gold>" + eco().format(total)));
        render();
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (!ex().sellable(item)) {
            if (item != null && !item.getType().isAir() && !Items.isCustom(item)) {
                player.sendActionBar(Text.mm(ex().offer(item.getType()) != null
                        ? "<gray>Only plain items can be sold (no names, enchants or damage)."
                        : "<gray>The exchange doesn't trade that."));
            }
            return;
        }
        Material m = item.getType();
        int amount;
        if (click.isShiftClick()) {
            amount = InventoryUtil.remove(player, it -> it.getType() == m && ex().sellable(it), Integer.MAX_VALUE);
        } else {
            ItemStack inSlot = player.getInventory().getItem(slot);
            if (inSlot == null || !inSlot.isSimilar(item)) {
                return;
            }
            amount = inSlot.getAmount();
            player.getInventory().setItem(slot, null);
        }
        if (amount <= 0) {
            return;
        }
        double total = Math.round(ex().sellPrice(m) * amount * 100) / 100.0;
        eco().deposit(player, total);
        ex().sold(m, amount);
        player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", SoundCategory.PLAYERS, 0.7f, 1.4f);
        player.sendActionBar(Text.mm("<green>Sold " + amount + "x <lang:" + m.translationKey() + "> <gray>for <gold>"
                + eco().format(total)));
        render();
    }
}
