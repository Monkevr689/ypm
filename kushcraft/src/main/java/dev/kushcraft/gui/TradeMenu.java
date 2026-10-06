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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trade: turn the money from your product into vanilla resources. Pick a
 * shelf in row 1 (ores, farming, wood, building, mob drops, nether & end,
 * rare), buy from rows 2-4. Click your product below to sell it right here,
 * or spare resources to sell them back. Layout matches tools/gui.py trade().
 */
public final class TradeMenu extends TabMenu {

    static final int[] SHELVES = {at(1, 1), at(1, 2), at(1, 3), at(1, 4), at(1, 5), at(1, 6), at(1, 7)};
    static final int FIRST = at(2, 0);
    static final int SLOTS = 27;
    static final int SELL_ALL = at(5, 4);
    private static final Map<UUID, Integer> LAST_SHELF = new HashMap<>();

    private int shelf;

    public TradeMenu(Player player) {
        super(player, Tab.TRADE);
        shelf = LAST_SHELF.getOrDefault(player.getUniqueId(), 0);
    }

    private Exchange ex() {
        return KushCraft.get().exchange();
    }

    private Economy eco() {
        return KushCraft.get().economy();
    }

    private List<Exchange.Offer> shown() {
        List<Exchange.Category> cats = ex().categories();
        if (cats.isEmpty()) {
            return List.of();
        }
        shelf = Math.max(0, Math.min(shelf, cats.size() - 1));
        return cats.get(shelf).offers();
    }

    @Override
    protected void page() {
        if (!ex().enabled()) {
            set(at(2, 4), Items.icon("ui_cancel", "<gray>Trade is turned off on this server."));
            return;
        }
        List<Exchange.Offer> offers = shown();
        List<Exchange.Category> cats = ex().categories();
        for (int i = 0; i < cats.size() && i < SHELVES.length; i++) {
            Exchange.Category c = cats.get(i);
            ItemStack icon = new ItemStack(c.icon());
            boolean on = i == shelf;
            icon.editMeta(m -> {
                m.itemName(Text.mm((on ? "<green>" : "<gray>") + c.name()));
                m.lore(Text.lines(List.of("<dark_gray>" + c.offers().size() + " items")));
                m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                m.setEnchantmentGlintOverride(on ? Boolean.TRUE : null);
            });
            set(SHELVES[i], icon);
        }
        double bal = eco().balance(player);
        double ratio = KushCraft.get().getConfig().getDouble("exchange.sell-ratio", 0.2);
        for (int i = 0; i < SLOTS && i < offers.size(); i++) {
            Exchange.Offer o = offers.get(i);
            double each = ex().buyPrice(o);
            ItemStack show = new ItemStack(o.material(), Math.min(o.amount(), o.material().getMaxStackSize()));
            List<String> lore = new ArrayList<>();
            lore.add((bal >= each * o.amount() ? "<gold>" : "<red>") + eco().format(each) + " <dark_gray>each · "
                    + ex().trend(o.material()));
            lore.add("<gray>Buy " + o.amount() + ": <white>" + eco().format(each * o.amount())
                    + " <dark_gray>· Shift: " + o.amount() * 4);
            if (ratio > 0) {
                lore.add("<dark_gray>Sells back for " + eco().format(ex().sellPrice(o.material())));
            }
            show.editMeta(m -> {
                m.lore(Text.lines(lore));
                m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            });
            set(FIRST + i, show);
        }
        double value = Selling.allValue(player);
        set(SELL_ALL, Items.glint(Items.icon("ui_sell", value > 0 ? "<green><bold>Sell all product</bold> <gold>"
                + eco().format(value) : "<gray>Sell all product",
                "<gray>Click items below to sell them."), value > 0));
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        if (!ex().enabled()) {
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
        for (int i = 0; i < SHELVES.length; i++) {
            if (slot == SHELVES[i] && i < ex().categories().size()) {
                shelf = i;
                LAST_SHELF.put(player.getUniqueId(), i);
                clickSound();
                render();
                return;
            }
        }
        int idx = slot - FIRST;
        List<Exchange.Offer> offers = shown();
        if (idx >= 0 && idx < SLOTS && idx < offers.size()) {
            Exchange.Offer o = offers.get(idx);
            buy(o, click.isShiftClick() ? o.amount() * 4 : o.amount());
        }
    }

    private void buy(Exchange.Offer o, int amount) {
        double each = ex().buyPrice(o);
        double total = Math.round(each * amount * 100) / 100.0;
        if (!eco().withdraw(player, total)) {
            player.sendActionBar(Text.mm("<red>You need " + eco().format(total)));
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
        KushCraft.get().awards().traded(player, o.material());
        player.playSound(player.getLocation(), "minecraft:entity.villager.yes", SoundCategory.PLAYERS, 0.7f, 1.1f);
        player.sendActionBar(Text.mm("<green>Bought " + amount + "x <lang:" + o.material().translationKey()
                + "> <gray>for <gold>" + eco().format(total)));
        render();
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (!ex().enabled()) {
            return;
        }
        if (Selling.clicked(player, slot, item, click)) {
            render();
            return;
        }
        if (!ex().sellable(item)) {
            if (item != null && !item.getType().isAir() && !Items.isCustom(item)) {
                player.sendActionBar(Text.mm(ex().offer(item.getType()) != null
                        ? "<gray>Only plain items can be sold." : "<gray>Trade doesn't buy that."));
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
