package dev.kushcraft.util;

import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.strain.Strain;
import org.bukkit.entity.Player;
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
        Map<String, Group> map = new LinkedHashMap<>();
        for (ItemStack it : p.getInventory().getStorageContents()) {
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
        List<Group> groups = groups(p, type);
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
        return InventoryUtil.remove(p, it -> Items.type(it) == type && Items.strain(it) != null
                && g.is(Items.strain(it).id(), Items.quality(it)), amount);
    }
}
