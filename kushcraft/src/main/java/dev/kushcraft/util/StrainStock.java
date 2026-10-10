package dev.kushcraft.util;

import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.strains.Strain;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Groups strain items (e.g. dried buds) in an inventory by strain + quality. */
public final class StrainStock {

    public record Group(Strain strain, int quality, int count, ItemStack sample) {
        public boolean is(String strainId, int q) {
            return strain.id().equals(strainId) && quality == q;
        }
    }

    private StrainStock() {
    }

    public static List<Group> groups(Player p, ItemType type) {
        return groups(p.getInventory(), type);
    }

    public static List<Group> groups(Inventory inv, ItemType type) {
        Map<String, Group> map = new LinkedHashMap<>();
        for (ItemStack it : inv.getStorageContents()) {
            if (it == null || Items.type(it) != type) {
                continue;
            }
            Strain s = Items.strain(it);
            if (s == null) {
                continue;
            }
            int q = Items.quality(it);
            String k = s.id() + "#" + q;
            Group g = map.get(k);
            map.put(k, new Group(s, q, (g == null ? 0 : g.count()) + it.getAmount(), g == null ? it : g.sample()));
        }
        return new ArrayList<>(map.values());
    }

    /** The preferred group if it has enough, otherwise the first group that has enough. */
    public static Group pick(Player p, ItemType type, int needed, String preferStrain, int preferQuality) {
        return pick(p.getInventory(), type, needed, preferStrain, preferQuality);
    }

    public static Group pick(Inventory inv, ItemType type, int needed, String preferStrain, int preferQuality) {
        List<Group> groups = groups(inv, type);
        if (preferStrain != null) {
            for (Group g : groups) {
                if (g.is(preferStrain, preferQuality) && g.count() >= needed) {
                    return g;
                }
            }
        }
        for (Group g : groups) {
            if (g.count() >= needed) {
                return g;
            }
        }
        return null;
    }

    public static int take(Player p, ItemType type, Group g, int amount) {
        return take(p.getInventory(), type, g, amount);
    }

    public static int take(Inventory inv, ItemType type, Group g, int amount) {
        return InventoryUtil.remove(inv, it -> Items.type(it) == type && Items.strain(it) != null
                && g.is(Items.strain(it).id(), Items.quality(it)), amount);
    }
}
