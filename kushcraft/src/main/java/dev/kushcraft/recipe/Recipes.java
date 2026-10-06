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

    /**
     * For the guide and the recipe viewer. grid: 9 ingredient names (a lower
     * case material name, "planks" or null for an empty cell); shapeless
     * recipes fill it from the top-left.
     */
    public record Info(ItemType result, int amount, String[] shape, String legend, String[] grid) {

        /** Same format as the "sig" written by tools/recipe_images.py. */
        public String signature() {
            List<String> cells = new ArrayList<>();
            for (String c : grid) {
                if (shape != null) {
                    cells.add(c == null ? "-" : c);
                } else if (c != null) {
                    cells.add(c);
                }
            }
            if (shape == null) {
                java.util.Collections.sort(cells);
            }
            return "craft:kush:" + result.id() + "x" + amount + ":" + String.join(",", cells);
        }
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

        // everything is shapeless: put the ingredients anywhere in the grid
        shapeless(plugin, ItemType.LAB_STATION, 1, "Crafting Table + Furnace + 3 Iron Ingots + 2 Glass Bottles",
                Material.CRAFTING_TABLE, Material.FURNACE, Material.IRON_INGOT, Material.IRON_INGOT, Material.IRON_INGOT,
                Material.GLASS_BOTTLE, Material.GLASS_BOTTLE);
        shapeless(plugin, ItemType.GROW_LAMP, 1, "Lantern + 2 Iron Ingots + Redstone + Glowstone Dust",
                Material.LANTERN, Material.IRON_INGOT, Material.IRON_INGOT, Material.REDSTONE, Material.GLOWSTONE_DUST);
        shapeless(plugin, ItemType.PLANTER_BOX, 2, "2 Dirt + Bone Meal + 3 any Planks",
                Material.DIRT, Material.DIRT, Material.BONE_MEAL, planks, planks, planks);
        shapeless(plugin, ItemType.DEALER, 1, "Chest + 2 Gold Ingots",
                Material.CHEST, Material.GOLD_INGOT, Material.GOLD_INGOT);
        shapeless(plugin, ItemType.BONG, 1, "Glass Bottle + 3 Glass",
                Material.GLASS_BOTTLE, Material.GLASS, Material.GLASS, Material.GLASS);
        shapeless(plugin, ItemType.ROLLING_PAPERS, 3, "Paper",
                Material.PAPER);
        shapeless(plugin, ItemType.BLUNT_WRAP, 2, "Paper + Cocoa Beans",
                Material.PAPER, Material.COCOA_BEANS);
        shapeless(plugin, ItemType.FERTILIZER, 4, "2 Bone Meal + Rotten Flesh",
                Material.BONE_MEAL, Material.BONE_MEAL, Material.ROTTEN_FLESH);
        shapeless(plugin, ItemType.LAB_SOLVENT, 3, "Glass Bottle + Sugar",
                Material.GLASS_BOTTLE, Material.SUGAR);
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
        java.util.Map<Character, String> names = new java.util.HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            char c = (Character) pairs[i];
            Object v = pairs[i + 1];
            if (v instanceof Material m) {
                r.setIngredient(c, m);
                names.put(c, m.name().toLowerCase(java.util.Locale.ROOT));
            } else {
                r.setIngredient(c, (RecipeChoice) v);
                names.put(c, "planks");
            }
        }
        String[] grid = new String[9];
        for (int row = 0; row < shape.length; row++) {
            for (int col = 0; col < shape[row].length(); col++) {
                grid[row * 3 + col] = names.get(shape[row].charAt(col));
            }
        }
        r.setCategory(CraftingBookCategory.MISC);
        r.setGroup("kushcraft");
        if (Bukkit.addRecipe(r)) {
            KEYS.add(key);
            INFO.add(new Info(type, amount, shape, legend, grid));
        }
    }

    /** in: Materials, or a RecipeChoice for "any planks". */
    private static void shapeless(KushCraft plugin, ItemType type, int amount, String legend, Object... in) {
        NamespacedKey key = new NamespacedKey(plugin, type.id());
        ShapelessRecipe r = new ShapelessRecipe(key, Items.create(type, amount));
        String[] grid = new String[9];
        for (int i = 0; i < in.length; i++) {
            if (in[i] instanceof Material m) {
                r.addIngredient(m);
                grid[i] = m.name().toLowerCase(java.util.Locale.ROOT);
            } else {
                r.addIngredient((RecipeChoice) in[i]);
                grid[i] = "planks";
            }
        }
        r.setCategory(CraftingBookCategory.MISC);
        r.setGroup("kushcraft");
        if (Bukkit.addRecipe(r)) {
            KEYS.add(key);
            INFO.add(new Info(type, amount, null, legend, grid));
        }
    }
}
