package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import org.bukkit.inventory.ItemStack;

/** Example items for menus (strain items get the first strain). */
final class CatalogIcons {

    private CatalogIcons() {
    }

    static ItemStack sample(ItemType t) {
        return t.strainBound() ? Items.strainItem(t, KushCraft.get().strains().getOrDefault(null), 3, 1) : Items.create(t);
    }
}
