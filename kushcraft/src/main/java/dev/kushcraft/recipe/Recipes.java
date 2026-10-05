package dev.kushcraft.recipe;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;

import java.util.ArrayList;
import java.util.List;

/** Crafting table recipes for machines and supplies (all vanilla ingredients). */
public final class Recipes {

    /** For the guide book: item -> human readable recipe. */
    public record Info(ItemType result, int amount, String[] shape, String legend) {
    }

    private static final List<NamespacedKey> KEYS = new ArrayList<>();
    private static final List<Info> INFO = new ArrayList<>();

    private Recipes() {
    }

    public static List<NamespacedKey> keys() {
        return KEYS;
    }

    public static List<Info> info() {
        return INFO;
    }

    public static void register(KushCraft plugin) {
        unregister();
        RecipeChoice planks = new RecipeChoice.MaterialChoice(Tag.PLANKS);

        shaped(plugin, ItemType.LAB_STATION, 1, "P=Glass Bottle, B=Brewing Stand, I=Iron Ingot, C=Cauldron",
                new String[]{"PBP", "ICI", "I I"},
                'P', Material.GLASS_BOTTLE, 'B', Material.BREWING_STAND, 'I', Material.IRON_INGOT, 'C', Material.CAULDRON);
        shaped(plugin, ItemType.GROW_LAMP, 1, "I=Iron Ingot, G=Glowstone Dust, L=Redstone Lamp",
                new String[]{"III", "GLG", " I "}, 'I', Material.IRON_INGOT, 'G', Material.GLOWSTONE_DUST,
                'L', Material.REDSTONE_LAMP);
        shaped(plugin, ItemType.PLANTER_BOX, 2, "W=any Planks, D=Dirt, B=Bone Meal",
                new String[]{"WBW", "WDW", "WWW"}, 'W', planks, 'D', Material.DIRT, 'B', Material.BONE_MEAL);
        shaped(plugin, ItemType.DEALER, 1, "E=Emerald, W=any Planks, C=Chest",
                new String[]{"EEE", "WCW", "WWW"}, 'E', Material.EMERALD, 'W', planks, 'C', Material.CHEST);
        shaped(plugin, ItemType.BONG, 1, "G=Glass, B=Glass Bottle, I=Iron Nugget",
                new String[]{" G ", " GI", "GBG"}, 'G', Material.GLASS, 'B', Material.GLASS_BOTTLE, 'I', Material.IRON_NUGGET);

        shapeless(plugin, ItemType.ROLLING_PAPERS, 6, "3 Paper + 1 Sugar Cane",
                Material.PAPER, Material.PAPER, Material.PAPER, Material.SUGAR_CANE);
        shapeless(plugin, ItemType.BLUNT_WRAP, 3, "Paper + Cocoa Beans + Dried Kelp",
                Material.PAPER, Material.COCOA_BEANS, Material.DRIED_KELP);
        shapeless(plugin, ItemType.FERTILIZER, 3, "2 Bone Meal + Rotten Flesh",
                Material.BONE_MEAL, Material.BONE_MEAL, Material.ROTTEN_FLESH);
        shapeless(plugin, ItemType.LAB_SOLVENT, 2, "Glass Bottle + Sugar + Redstone + Gunpowder",
                Material.GLASS_BOTTLE, Material.SUGAR, Material.REDSTONE, Material.GUNPOWDER);
        shapeless(plugin, ItemType.CATALYST, 2, "Amethyst Shard + Glowstone Dust + Redstone",
                Material.AMETHYST_SHARD, Material.GLOWSTONE_DUST, Material.REDSTONE);
        shapeless(plugin, ItemType.GROWER_GUIDE, 1, "Book + Wheat Seeds",
                Material.BOOK, Material.WHEAT_SEEDS);
    }

    public static void unregister() {
        for (NamespacedKey k : KEYS) {
            Bukkit.removeRecipe(k);
        }
        KEYS.clear();
        INFO.clear();
    }

    private static void shaped(KushCraft plugin, ItemType type, int amount, String legend, String[] shape, Object... pairs) {
        NamespacedKey key = new NamespacedKey(plugin, type.id());
        ItemStack result = Items.create(type, amount);
        ShapedRecipe r = new ShapedRecipe(key, result);
        r.shape(shape);
        for (int i = 0; i < pairs.length; i += 2) {
            char c = (Character) pairs[i];
            Object v = pairs[i + 1];
            if (v instanceof Material m) {
                r.setIngredient(c, m);
            } else {
                r.setIngredient(c, (RecipeChoice) v);
            }
        }
        r.setCategory(CraftingBookCategory.MISC);
        r.setGroup("kushcraft");
        if (Bukkit.addRecipe(r)) {
            KEYS.add(key);
            INFO.add(new Info(type, amount, shape, legend));
        }
    }

    private static void shapeless(KushCraft plugin, ItemType type, int amount, String legend, Material... in) {
        NamespacedKey key = new NamespacedKey(plugin, type.id());
        ShapelessRecipe r = new ShapelessRecipe(key, Items.create(type, amount));
        for (Material m : in) {
            r.addIngredient(m);
        }
        r.setCategory(CraftingBookCategory.MISC);
        r.setGroup("kushcraft");
        if (Bukkit.addRecipe(r)) {
            KEYS.add(key);
            INFO.add(new Info(type, amount, null, legend));
        }
    }
}
