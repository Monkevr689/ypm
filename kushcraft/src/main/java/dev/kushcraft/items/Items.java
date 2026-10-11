package dev.kushcraft.items;

import dev.kushcraft.KushCraft;
import dev.kushcraft.Keys;
import dev.kushcraft.effects.EffectType;
import dev.kushcraft.strains.Look;
import dev.kushcraft.strains.Strain;
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
    public static final int VAPE_HITS = 10;

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
            lore.add("<dark_gray>Place it. Punch it to pick it up.");
        }
        meta.lore(Text.lines(lore));
        meta.setMaxStackSize(type.maxStack());
        meta.getPersistentDataContainer().set(Keys.ID, PersistentDataType.STRING, type.id());
        hideExtras(meta);
        item.setItemMeta(meta);
        return item;
    }

    /** A machine (or worker) item that remembers its upgrade level. */
    public static ItemStack machine(ItemType type, int level) {
        ItemStack it = create(type);
        if (level > 1) {
            it.editMeta(m -> {
                m.getPersistentDataContainer().set(Keys.LEVEL, PersistentDataType.INTEGER, level);
                List<net.kyori.adventure.text.Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
                lore.add(0, Text.mm("<gold>Level " + level));
                m.lore(lore);
                m.setMaxStackSize(1);
            });
        }
        return it;
    }

    public static int level(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 1;
        }
        Integer l = item.getPersistentDataContainer().get(Keys.LEVEL, PersistentDataType.INTEGER);
        return l == null ? 1 : l;
    }

    /** An item tied to a strain (seeds, buds, joints, ...). quality = 1..5 stars. */
    public static ItemStack strainItem(ItemType type, Strain strain, int quality, int amount) {
        return strainItem(type, strain, quality, amount, maxHits(type));
    }

    /** Joints, blunts and vape pens are used up a hit at a time. */
    public static int maxHits(ItemType type) {
        return switch (type) {
            case JOINT -> JOINT_HITS;
            case BLUNT -> BLUNT_HITS;
            case VAPE_PEN -> VAPE_HITS;
            default -> 0;
        };
    }

    public static ItemStack strainItem(ItemType type, Strain strain, int quality, int amount, int hits) {
        quality = Math.max(1, Math.min(5, quality));
        ItemStack item = new ItemStack(type.base(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(Keys.model(type.model()));
        meta.itemName(Text.mm(strain.colored() + " <white>" + type.display()));
        List<String> lore = new ArrayList<>();
        lore.add(strain.type().colored() + " <dark_gray>·</dark_gray> <white>" + strain.potency() + "% THC"
                + " <dark_gray>·</dark_gray> " + strain.rarity().colored()
                + (type != ItemType.SEED_PACK ? " <dark_gray>·</dark_gray> " + Text.stars(quality) : ""));
        StringBuilder eff = new StringBuilder();
        for (EffectType e : strain.effects()) {
            if (!eff.isEmpty()) {
                eff.append("<dark_gray>, </dark_gray>");
            }
            eff.append(e.colored());
        }
        if (!eff.isEmpty()) {
            lore.add(eff.toString());
        }
        if (type == ItemType.SEED_PACK || type == ItemType.BUD_FRESH || type == ItemType.BUD_DRIED) {
            lore.add(climateLine(strain));
        }
        if (hits > 0) {
            int max = Math.max(1, maxHits(type));
            lore.add("<gray>Hits " + Text.bar(hits / (double) max, max, "green", "dark_gray"));
        }
        if (strain.isCustom() && strain.creatorName() != null) {
            lore.add("<dark_gray>Bred by " + Text.escape(strain.creatorName()));
        }
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
            look(meta, strain.look(), type == ItemType.BUD_DRIED);
        }
        hideExtras(meta);
        item.setItemMeta(meta);
        return item;
    }

    /** "Cold climate · Mint · Tiger stripes" */
    public static String climateLine(Strain s) {
        Look l = s.look();
        return s.climate().colored() + " <gray>climate" + (s.flavor().isEmpty() ? ""
                : " <dark_gray>·</dark_gray> <gray>" + Text.escape(s.flavor()))
                + (l.pattern() == dev.kushcraft.strains.BudPattern.NONE ? ""
                : " <dark_gray>·</dark_gray> <color:" + Text.hex(Strain.brighten(l.accent())) + ">" + l.pattern().display() + "</color>");
    }

    /**
     * Colours (bud, leaf, hairs, accent) and strings (bud shape, Mythic look,
     * accent pattern) read by the resource pack: tools/buds.py.
     */
    public static void look(ItemMeta meta, Look l, boolean dried) {
        tint(meta, l.bud(), dried ? l.driedLeaf() : l.leaf(), dried ? l.driedPistil() : l.pistil(), l.accent());
        strings(meta, l.shape().id(), l.exotic().id(), l.pattern().id());
    }

    public static void strings(ItemMeta meta, String... values) {
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of(values));
        meta.setCustomModelDataComponent(cmd);
    }

    /** Sets the colours used by the resource pack's custom_model_data tints (index 0, 1, ...). */
    public static void tint(ItemMeta meta, int... rgb) {
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        List<Color> colors = new ArrayList<>();
        for (int c : rgb) {
            colors.add(Color.fromRGB(c & 0xFFFFFF));
        }
        cmd.setColors(colors);
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

    /*
     * The look-ups below read the item's data without copying its meta (getItemMeta() copies everything,
     * and workers look at thousands of items a second).
     */
    public static ItemType type(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getPersistentDataContainer().get(Keys.ID, PersistentDataType.STRING);
        return ItemType.parse(id);
    }

    public static boolean is(ItemStack item, ItemType type) {
        return type(item) == type;
    }

    public static boolean isCustom(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        io.papermc.paper.persistence.PersistentDataContainerView pdc = item.getPersistentDataContainer();
        return pdc.has(Keys.ID, PersistentDataType.STRING) || pdc.has(Keys.ICON, PersistentDataType.BYTE);
    }

    public static Strain strain(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getPersistentDataContainer().get(Keys.STRAIN, PersistentDataType.STRING);
        return KushCraft.get().strains().get(id);
    }

    public static int quality(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 3;
        }
        Integer q = item.getPersistentDataContainer().get(Keys.QUALITY, PersistentDataType.INTEGER);
        return q == null ? 3 : q;
    }

    public static int hits(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer h = item.getPersistentDataContainer().get(Keys.HITS, PersistentDataType.INTEGER);
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
