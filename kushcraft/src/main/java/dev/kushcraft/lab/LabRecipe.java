package dev.kushcraft.lab;

import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Lab Station recipes. The first strain-source ingredient decides the strain of the product. */
public enum LabRecipe {
    HASH(ItemType.HASH, 2, 30,
            Ingredient.strain(ItemType.BUD_DRIED, 4), Ingredient.of(Material.ICE, 1)),
    MOON_ROCK(ItemType.MOON_ROCK, 1, 45,
            Ingredient.strain(ItemType.BUD_DRIED, 1), Ingredient.of(ItemType.HASH, 1), Ingredient.of(Material.HONEY_BOTTLE, 1)),
    SPACE_BROWNIE(ItemType.SPACE_BROWNIE, 3, 40,
            Ingredient.strain(ItemType.BUD_DRIED, 2), Ingredient.of(Material.COCOA_BEANS, 1),
            Ingredient.of(Material.WHEAT, 2), Ingredient.of(Material.SUGAR, 1)),
    SHROOM_TEA(ItemType.SHROOM_TEA, 1, 20,
            Ingredient.of(ItemType.MAGIC_MUSHROOM, 2), Ingredient.of(Material.GLASS_BOTTLE, 1)),
    LUCID_TAB(ItemType.LUCID_TAB, 6, 60,
            Ingredient.of(ItemType.LAB_SOLVENT, 1), Ingredient.of(ItemType.MAGIC_MUSHROOM, 2), Ingredient.of(Material.PAPER, 1)),
    BLUE_CRYSTAL(ItemType.BLUE_CRYSTAL, 4, 90,
            Ingredient.of(ItemType.LAB_SOLVENT, 1), Ingredient.of(ItemType.CATALYST, 1),
            Ingredient.of(Material.LAPIS_LAZULI, 4), Ingredient.of(Material.SUGAR, 2)),
    PIXIE_DUST(ItemType.PIXIE_DUST, 4, 60,
            Ingredient.of(ItemType.LAB_SOLVENT, 1), Ingredient.of(Material.GLOWSTONE_DUST, 4), Ingredient.of(Material.SUGAR, 2));

    private final ItemType output;
    private final int amount;
    private final int seconds;
    private final List<Ingredient> ingredients;

    LabRecipe(ItemType output, int amount, int seconds, Ingredient... ingredients) {
        this.output = output;
        this.amount = amount;
        this.seconds = seconds;
        this.ingredients = List.of(ingredients);
    }

    public ItemType output() {
        return output;
    }

    public int amount() {
        return amount;
    }

    public int seconds() {
        return seconds;
    }

    public List<Ingredient> ingredients() {
        return ingredients;
    }

    public boolean strainBased() {
        for (Ingredient i : ingredients) {
            if (i.strainSource()) {
                return true;
            }
        }
        return false;
    }

    public static LabRecipe parse(String s) {
        try {
            return valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** Either a KushCraft item or a plain vanilla material. */
    public record Ingredient(ItemType custom, Material vanilla, int amount, boolean strainSource) {

        static Ingredient of(ItemType t, int amount) {
            return new Ingredient(t, null, amount, false);
        }

        static Ingredient strain(ItemType t, int amount) {
            return new Ingredient(t, null, amount, true);
        }

        static Ingredient of(Material m, int amount) {
            return new Ingredient(null, m, amount, false);
        }

        public boolean matches(ItemStack it) {
            if (custom != null) {
                return Items.type(it) == custom;
            }
            return it.getType() == vanilla && !Items.isCustom(it);
        }

        public String name() {
            return custom != null ? custom.display() : Text.titleCase(vanilla.name());
        }
    }
}
