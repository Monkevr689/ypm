package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.shop.Shop;
import dev.kushcraft.util.Text;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumMap;
import java.util.Map;

/** Selling product from your inventory - used by the Shop and Trade tabs. */
final class Selling {

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
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (sellable(it)) {
                total += shop().sellPrice(it, p) * it.getAmount();
            }
        }
        return total;
    }

    /** Sells every bud, drug and harvest in the inventory. */
    static boolean all(Player p) {
        KushCraft plugin = KushCraft.get();
        PlayerInventory pi = p.getInventory();
        ItemStack[] contents = pi.getStorageContents();
        double total = 0;
        int count = 0;
        Map<ItemType, Integer> sold = new EnumMap<>(ItemType.class);
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (sellable(it)) {
                total += shop().sellPrice(it, p) * it.getAmount();
                count += it.getAmount();
                sold.merge(Items.type(it), it.getAmount(), Integer::sum);
                contents[i] = null;
            }
        }
        if (count == 0) {
            p.sendActionBar(Text.mm("<red>Nothing to sell."));
            return false;
        }
        pi.setStorageContents(contents);
        paid(p, total);
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
        double total = shop().sellPrice(item, p) * amount;
        ItemStack inSlot = p.getInventory().getItem(slot);
        if (inSlot == null || !inSlot.isSimilar(item)) {
            return true;
        }
        ItemType type = Items.type(item);
        inSlot.setAmount(inSlot.getAmount() - amount);
        p.getInventory().setItem(slot, inSlot.getAmount() <= 0 ? null : inSlot);
        paid(p, total);
        plugin.market().sold(type, amount);
        p.sendActionBar(Text.mm("<green>Sold " + amount + "x for <gold>" + plugin.economy().format(total)));
        return true;
    }

    private static void paid(Player p, double total) {
        KushCraft plugin = KushCraft.get();
        plugin.economy().deposit(p, total);
        plugin.ranks().sold(p, total);
        p.playSound(p.getLocation(), "minecraft:entity.experience_orb.pickup", SoundCategory.PLAYERS, 0.7f, 1.4f);
        p.playSound(p.getLocation(), "minecraft:block.chain.place", SoundCategory.PLAYERS, 0.6f, 1.8f);
    }
}
