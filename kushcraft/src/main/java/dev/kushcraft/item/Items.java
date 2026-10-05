package dev.kushcraft.item;

import dev.kushcraft.KushCraft;
import dev.kushcraft.Keys;
import dev.kushcraft.effect.EffectType;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.Text;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Creates and reads KushCraft items. */
public final class Items {

    public static final int JOINT_HITS = 3;
    public static final int BLUNT_HITS = 5;

    private Items() {
    }

    public static ItemStack create(ItemType type) {
        return create(type, 1);
    }

    public static ItemStack create(ItemType type, int amount) {
        if (type.strainBound()) {
            Strain s = KushCraft.get().strains().all().iterator().next();
            return strainItem(type, s, 3, amount);
        }
        ItemStack item = new ItemStack(type.base(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(Keys.model(type.model()));
        meta.itemName(Text.mm("<white>" + type.display()));
        List<String> lore = new ArrayList<>(type.lore());
        if (type.machine() != null) {
            lore.add("");
            lore.add("<dark_gray>Place it like a block.");
            lore.add("<dark_gray>Punch it to pick it back up.");
        }
        meta.lore(Text.lines(lore));
        meta.setMaxStackSize(type.maxStack());
        meta.getPersistentDataContainer().set(Keys.ID, PersistentDataType.STRING, type.id());
        hideExtras(meta);
        item.setItemMeta(meta);
        return item;
    }

    /** An item tied to a strain (seeds, buds, joints, ...). quality = 1..5 stars. */
    public static ItemStack strainItem(ItemType type, Strain strain, int quality, int amount) {
        return strainItem(type, strain, quality, amount, type == ItemType.JOINT ? JOINT_HITS
                : type == ItemType.BLUNT ? BLUNT_HITS : 0);
    }

    public static ItemStack strainItem(ItemType type, Strain strain, int quality, int amount, int hits) {
        quality = Math.max(1, Math.min(5, quality));
        ItemStack item = new ItemStack(type.base(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(Keys.model(type.model()));
        meta.itemName(Text.mm(strain.colored() + " <white>" + type.display()));
        List<String> lore = new ArrayList<>();
        lore.add(strain.type().colored() + " <dark_gray>•</dark_gray> <gray>THC <white>" + strain.potency() + "%");
        if (type != ItemType.SEED_PACK) {
            lore.add("<gray>Quality " + Text.stars(quality));
        }
        StringBuilder eff = new StringBuilder();
        for (EffectType e : strain.effects()) {
            if (!eff.isEmpty()) {
                eff.append("<dark_gray>, </dark_gray>");
            }
            eff.append(e.colored());
        }
        if (!eff.isEmpty()) {
            lore.add("<gray>Effects: " + eff);
        }
        if (hits > 0) {
            int max = type == ItemType.BLUNT ? BLUNT_HITS : JOINT_HITS;
            lore.add("<gray>Hits left: " + Text.bar(hits / (double) max, max, "green", "dark_gray"));
        }
        if (strain.isCustom() && strain.creatorName() != null) {
            lore.add("<dark_gray>Bred by " + Text.escape(strain.creatorName()));
        }
        lore.add("");
        lore.addAll(type.lore());
        meta.lore(Text.lines(lore));
        meta.setMaxStackSize(type.maxStack());
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.ID, PersistentDataType.STRING, type.id());
        pdc.set(Keys.STRAIN, PersistentDataType.STRING, strain.id());
        if (type != ItemType.SEED_PACK) {
            pdc.set(Keys.QUALITY, PersistentDataType.INTEGER, quality);
        }
        if (hits > 0) {
            pdc.set(Keys.HITS, PersistentDataType.INTEGER, hits);
        }
        if (type.tinted()) {
            tint(meta, strain.color());
        }
        hideExtras(meta);
        item.setItemMeta(meta);
        return item;
    }

    /** Sets the colour used by the resource pack's custom_model_data tint. */
    public static void tint(ItemMeta meta, int rgb) {
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setColors(List.of(Color.fromRGB(rgb & 0xFFFFFF)));
        meta.setCustomModelDataComponent(cmd);
    }

    private static void hideExtras(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    /** A menu / display icon using one of our models. */
    public static ItemStack icon(String model, String name, String... lore) {
        return icon(model, name, Arrays.asList(lore));
    }

    public static ItemStack icon(String model, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(Keys.model(model));
        meta.itemName(Text.mm(name));
        meta.lore(Text.lines(lore));
        meta.getPersistentDataContainer().set(Keys.ICON, PersistentDataType.BYTE, (byte) 1);
        meta.setMaxStackSize(99);
        hideExtras(meta);
        item.setItemMeta(meta);
        return item;
    }

    /** Copy of an item with a different amount (for menu previews). */
    public static ItemStack amount(ItemStack item, int amount) {
        ItemStack c = item.clone();
        c.setAmount(Math.max(1, Math.min(99, amount)));
        return c;
    }

    public static ItemStack tintedIcon(String model, int rgb, String name, List<String> lore) {
        ItemStack item = icon(model, name, lore);
        ItemMeta meta = item.getItemMeta();
        tint(meta, rgb);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack glint(ItemStack item, boolean on) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(on ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    // --------------------------------------------------------------------
    // reading
    // --------------------------------------------------------------------

    public static ItemType type(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(Keys.ID, PersistentDataType.STRING);
        return ItemType.parse(id);
    }

    public static boolean is(ItemStack item, ItemType type) {
        return type(item) == type;
    }

    public static boolean isCustom(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(Keys.ID, PersistentDataType.STRING) || pdc.has(Keys.ICON, PersistentDataType.BYTE);
    }

    public static Strain strain(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(Keys.STRAIN, PersistentDataType.STRING);
        return KushCraft.get().strains().get(id);
    }

    public static int quality(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 3;
        }
        Integer q = item.getItemMeta().getPersistentDataContainer().get(Keys.QUALITY, PersistentDataType.INTEGER);
        return q == null ? 3 : q;
    }

    public static int hits(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer h = item.getItemMeta().getPersistentDataContainer().get(Keys.HITS, PersistentDataType.INTEGER);
        return h == null ? 0 : h;
    }

    /** True when both stacks are the same KushCraft item (same strain + quality). */
    public static boolean sameKind(ItemStack a, ItemStack b) {
        ItemType ta = type(a);
        if (ta == null || ta != type(b)) {
            return false;
        }
        if (!ta.strainBound()) {
            return true;
        }
        Strain sa = strain(a), sb = strain(b);
        return sa != null && sb != null && sa.id().equals(sb.id()) && quality(a) == quality(b)
                && hits(a) == hits(b);
    }
}
