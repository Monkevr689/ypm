package dev.kushcraft.lab;

import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Drug Lab recipes, grouped by the dealer rank that unlocks them. The first
 * strain-source ingredient decides the strain of the product.
 */
public enum LabRecipe {
    // rank 1 - Street Seller
    HASH(ItemType.HASH, 2, 30, 1,
            Ingredient.strain(ItemType.BUD_DRIED, 4), Ingredient.of(Material.ICE, 1)),
    SPACE_BROWNIE(ItemType.SPACE_BROWNIE, 3, 40, 1,
            Ingredient.strain(ItemType.BUD_DRIED, 2), Ingredient.of(Material.COCOA_BEANS, 1),
            Ingredient.of(Material.WHEAT, 2), Ingredient.of(Material.SUGAR, 1)),
    GUMMIES(ItemType.GUMMIES, 4, 40, 1,
            Ingredient.strain(ItemType.HASH, 1), Ingredient.of(Material.SUGAR, 2), Ingredient.of(Material.SLIME_BALL, 1)),
    SHROOM_TEA(ItemType.SHROOM_TEA, 1, 20, 1,
            Ingredient.of(ItemType.MAGIC_MUSHROOM, 2), Ingredient.of(Material.GLASS_BOTTLE, 1)),
    OPIUM(ItemType.OPIUM, 2, 40, 1,
            Ingredient.of(ItemType.POPPY_POD, 3), Ingredient.of(Material.GLASS_BOTTLE, 1)),
    LEAN(ItemType.LEAN, 2, 30, 1,
            Ingredient.of(Material.GLASS_BOTTLE, 1), Ingredient.of(Material.SUGAR, 2), Ingredient.of(Material.PURPLE_DYE, 1),
            Ingredient.of(Material.SWEET_BERRIES, 2)),
    // rank 2 - Corner Dealer
    MOON_ROCK(ItemType.MOON_ROCK, 1, 45, 2,
            Ingredient.strain(ItemType.BUD_DRIED, 1), Ingredient.of(ItemType.HASH, 1), Ingredient.of(Material.HONEY_BOTTLE, 1)),
    LUCID_TAB(ItemType.LUCID_TAB, 6, 60, 2,
            Ingredient.of(ItemType.LAB_SOLVENT, 1), Ingredient.of(ItemType.MAGIC_MUSHROOM, 2), Ingredient.of(Material.PAPER, 1)),
    ECSTASY(ItemType.ECSTASY, 4, 60, 2,
            Ingredient.of(ItemType.LAB_SOLVENT, 1), Ingredient.of(ItemType.CATALYST, 1), Ingredient.of(Material.PINK_DYE, 2),
            Ingredient.of(Material.SUGAR, 1)),
    PIXIE_DUST(ItemType.PIXIE_DUST, 4, 60, 2,
            Ingredient.of(ItemType.LAB_SOLVENT, 1), Ingredient.of(Material.GLOWSTONE_DUST, 4), Ingredient.of(Material.SUGAR, 2)),
    // rank 3 - Hustler
    COCAINE(ItemType.COCAINE, 4, 60, 3,
            Ingredient.of(ItemType.COCA_LEAVES, 8), Ingredient.of(ItemType.LAB_SOLVENT, 1), Ingredient.of(Material.SUGAR, 1)),
    CRACK(ItemType.CRACK, 3, 40, 3,
            Ingredient.of(ItemType.COCAINE, 2), Ingredient.of(Material.BONE_MEAL, 1)),
    KETAMINE(ItemType.KETAMINE, 4, 60, 3,
            Ingredient.of(ItemType.LAB_SOLVENT, 1), Ingredient.of(Material.NETHER_WART, 2), Ingredient.of(Material.SUGAR, 2)),
    MESCALINE(ItemType.MESCALINE, 3, 60, 3,
            Ingredient.of(ItemType.PEYOTE_BUTTON, 4), Ingredient.of(ItemType.LAB_SOLVENT, 1)),
    // rank 4 - Supplier
    BLUE_CRYSTAL(ItemType.BLUE_CRYSTAL, 4, 90, 4,
            Ingredient.of(ItemType.LAB_SOLVENT, 1), Ingredient.of(ItemType.CATALYST, 1),
            Ingredient.of(Material.LAPIS_LAZULI, 4), Ingredient.of(Material.SUGAR, 2)),
    HEROIN(ItemType.HEROIN, 3, 75, 4,
            Ingredient.of(ItemType.POPPY_POD, 6), Ingredient.of(ItemType.LAB_SOLVENT, 1), Ingredient.of(ItemType.CATALYST, 1)),
    DMT(ItemType.DMT, 3, 75, 4,
            Ingredient.of(ItemType.LAB_SOLVENT, 1), Ingredient.of(ItemType.CATALYST, 1), Ingredient.of(Material.GLOW_BERRIES, 3)),
    // rank 5 - The Plug
    ANGEL_DUST(ItemType.ANGEL_DUST, 3, 90, 5,
            Ingredient.of(ItemType.LAB_SOLVENT, 1), Ingredient.of(ItemType.CATALYST, 1), Ingredient.of(Material.GUNPOWDER, 2),
            Ingredient.of(Material.BLAZE_POWDER, 1));

    private final ItemType output;
    private final int amount;
    private final int seconds;
    private final int rank;
    private final List<Ingredient> ingredients;

    LabRecipe(ItemType output, int amount, int seconds, int rank, Ingredient... ingredients) {
        this.output = output;
        this.amount = amount;
        this.seconds = seconds;
        this.rank = rank;
        this.ingredients = List.of(ingredients);
    }

    /** Dealer rank (1 = everyone) needed to cook this. */
    public int rank() {
        return rank;
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
