package dev.kushcraft.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.function.Predicate;

/** Counting / removing / giving items in a player's main inventory. */
public final class InventoryUtil {

    private InventoryUtil() {
    }

    /** Adds items, dropping whatever doesn't fit at the player's feet. */
    public static void give(Player p, ItemStack... items) {
        Map<Integer, ItemStack> rest = p.getInventory().addItem(items);
        for (ItemStack left : rest.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    public static int count(Player p, Predicate<ItemStack> match) {
        return count(p.getInventory(), match);
    }

    public static int count(Inventory inv, Predicate<ItemStack> match) {
        int n = 0;
        for (ItemStack it : inv.getStorageContents()) {
            if (it != null && !it.getType().isAir() && match.test(it)) {
                n += it.getAmount();
            }
        }
        return n;
    }

    public static int count(Player p, Material m) {
        return count(p, it -> it.getType() == m && !dev.kushcraft.items.Items.isCustom(it));
    }

    /** Removes up to amount matching items. Returns how many were removed. */
    public static int remove(Player p, Predicate<ItemStack> match, int amount) {
        return remove(p.getInventory(), match, amount);
    }

    public static int remove(Inventory inv, Predicate<ItemStack> match, int amount) {
        ItemStack[] contents = inv.getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType().isAir() || !match.test(it)) {
                continue;
            }
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            left -= take;
            contents[i] = it.getAmount() <= 0 ? null : it;
        }
        inv.setStorageContents(contents);
        return amount - left;
    }

    public static int remove(Player p, Material m, int amount) {
        return remove(p, it -> it.getType() == m && !dev.kushcraft.items.Items.isCustom(it), amount);
    }

    /** First stack in the inventory (hotbar first) that matches. */
    public static ItemStack first(Player p, Predicate<ItemStack> match) {
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && !it.getType().isAir() && match.test(it)) {
                return it;
            }
        }
        return null;
    }
}
