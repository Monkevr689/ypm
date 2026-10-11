package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.Items;
import dev.kushcraft.economy.Economy;
import dev.kushcraft.economy.Exchange;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
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
 * Trade: spend the money from your product on vanilla items. Pick a shelf
 * in row 1 or row 5 (lab ingredients, ores, farming, wood, building,
 * colours, decoration, redstone, tools, mob drops, the Nether), buy from
 * rows 2-4. Trade never buys anything back - money only comes from selling
 * drugs (your product can still be sold by clicking it below). Layout
 * matches tools/gui.py trade().
 */
public final class TradeMenu extends TabMenu {

    static final int[] SHELVES = {at(1, 0), at(1, 1), at(1, 2), at(1, 3), at(1, 4), at(1, 5), at(1, 6), at(1, 7),
            at(1, 8), at(5, 0), at(5, 1), at(5, 2), at(5, 3), at(5, 4), at(5, 5), at(5, 6), at(5, 7), at(5, 8)};
    static final int FIRST = at(2, 0);
    static final int SLOTS = 27;
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
        for (int i = 0; i < SLOTS && i < offers.size(); i++) {
            Exchange.Offer o = offers.get(i);
            double each = ex().buyPrice(o);
            ItemStack show = new ItemStack(o.material(), Math.min(o.amount(), o.material().getMaxStackSize()));
            List<String> lore = new ArrayList<>();
            lore.add((bal >= each * o.amount() ? "<gold>" : "<red>") + eco().format(each * o.amount())
                    + " <gray>for " + o.amount() + " <dark_gray>· " + ex().trend(o.material()));
            lore.add("<dark_gray>Shift-click: buy " + o.amount() * 4);
            show.editMeta(m -> {
                m.lore(Text.lines(lore));
                m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            });
            set(FIRST + i, show);
        }
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        if (!ex().enabled()) {
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
        if (!eco().withdraw(player, total, dev.kushcraft.economy.Tx.TRADE, amount + "x " + o.material().getKey().getKey())) {
            player.sendActionBar(Text.mm("<red>You need " + eco().format(total) + " <gray>- sell some drugs first."));
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
        if (Selling.clicked(player, slot, item, click)) {
            render();
            return;
        }
        if (item != null && !item.getType().isAir()) {
            player.sendActionBar(Text.mm("<gray>Trade only sells. <white>You make money by selling drugs</white> (click them)."));
        }
    }
}
