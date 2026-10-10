package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.economy.Shop;
import dev.kushcraft.util.Text;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumMap;
import java.util.Map;

/** Selling product from your inventory - used by the Shop and Trade tabs. */
public final class Selling {

    private Selling() {
    }

    private static Shop shop() {
        return KushCraft.get().shop();
    }

    static boolean sellable(ItemStack it) {
        return it != null && shop().sellPrice(it) > 0;
    }

    /** What "Sell all" would pay right now. */
    static double allValue(Player p) {
        double total = 0;
        Map<ItemType, Integer> sold = new EnumMap<>(ItemType.class);
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (sellable(it)) {
                total += value(p, it, it.getAmount(), sold);
            }
        }
        return total;
    }

    /** n of this stack, after the items in {@code sold} went first (prices drop as you sell). */
    private static double value(Player p, ItemStack it, int n, Map<ItemType, Integer> sold) {
        ItemType t = Items.type(it);
        int before = sold.getOrDefault(t, 0);
        sold.put(t, before + n);
        return shop().sellPrice(it, p) * n * KushCraft.get().market().bulkFactor(t, before, n);
    }

    /** Sells every bud, drug and harvest in the inventory. */
    public static boolean all(Player p) {
        KushCraft plugin = KushCraft.get();
        PlayerInventory pi = p.getInventory();
        ItemStack[] contents = pi.getStorageContents();
        double total = 0;
        int count = 0;
        Map<ItemType, Integer> sold = new EnumMap<>(ItemType.class);
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (sellable(it)) {
                total += value(p, it, it.getAmount(), sold);
                count += it.getAmount();
                contents[i] = null;
            }
        }
        if (count == 0) {
            p.sendActionBar(Text.mm("<red>Nothing to sell."));
            return false;
        }
        pi.setStorageContents(contents);
        total = Math.round(total * 100) / 100.0;
        StringBuilder what = new StringBuilder();
        sold.forEach((t, n) -> what.append(what.isEmpty() ? "" : ", ").append(n).append("x ").append(t.display()));
        paid(p, total, what.toString());
        sold.forEach((t, n) -> plugin.market().sold(t, n));
        p.sendActionBar(Text.mm("<green>Sold " + count + " items for <gold>" + plugin.economy().format(total)));
        return true;
    }

    /**
     * Sells a clicked stack of product: click = 1, shift/right-click = the whole stack.
     * Returns false when it isn't product.
     */
    static boolean clicked(Player p, int slot, ItemStack item, ClickType click) {
        if (!sellable(item)) {
            return false;
        }
        KushCraft plugin = KushCraft.get();
        int amount = click.isShiftClick() || click.isRightClick() ? item.getAmount() : 1;
        double total = value(p, item, amount, new EnumMap<>(ItemType.class));
        ItemStack inSlot = p.getInventory().getItem(slot);
        if (inSlot == null || !inSlot.isSimilar(item)) {
            return true;
        }
        ItemType type = Items.type(item);
        inSlot.setAmount(inSlot.getAmount() - amount);
        p.getInventory().setItem(slot, inSlot.getAmount() <= 0 ? null : inSlot);
        paid(p, total, amount + "x " + type.display());
        plugin.market().sold(type, amount);
        p.sendActionBar(Text.mm("<green>Sold " + amount + "x for <gold>" + plugin.economy().format(total)));
        return true;
    }

    private static void paid(Player p, double total, String what) {
        KushCraft plugin = KushCraft.get();
        plugin.economy().deposit(p, total, dev.kushcraft.economy.Tx.SELL, what);
        plugin.titles().sold(p, total);
        p.playSound(p.getLocation(), "minecraft:entity.experience_orb.pickup", SoundCategory.PLAYERS, 0.7f, 1.4f);
        p.playSound(p.getLocation(), "minecraft:block.chain.place", SoundCategory.PLAYERS, 0.6f, 1.8f);
    }
}
