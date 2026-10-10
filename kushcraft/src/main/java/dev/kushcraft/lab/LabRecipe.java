package dev.kushcraft.lab;

import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.List;

/**
 * Drug Lab recipes. They follow the real steps loosely (coca paste before
 * cocaine, morphine before heroin...) but with game ingredients and a
 * made-up Lab Solvent, never real chemistry. Many drugs take two or three
 * steps. The first strain-source ingredient decides the strain of the
 * product. Weed is just buds, joints and blunts (rolled) and vape pens.
 * Same order as tools/recipe_images.py COOK and tools/gui.py COOK_GROUPS.
 */
public enum LabRecipe {
    // weed: a vape pen is bud oil in a cartridge
    VAPE_PEN(ItemType.VAPE_PEN, 1, 40,
            Ingredient.strain(ItemType.BUD_DRIED, 4), Ingredient.of(ItemType.LAB_SOLVENT, 1),
            Ingredient.of(Material.IRON_NUGGET, 2), Ingredient.of(Material.GLASS_PANE, 1)),
    // psychedelics
    SHROOM_TEA(ItemType.SHROOM_TEA, 2, 20,
            Ingredient.of(ItemType.MAGIC_MUSHROOM, 3), Ingredient.water(1)),
    SHROOM_CHOCOLATE(ItemType.SHROOM_CHOCOLATE, 3, 35,
            Ingredient.of(ItemType.MAGIC_MUSHROOM, 2), Ingredient.of(Material.COCOA_BEANS, 2),
            Ingredient.of(Material.SUGAR, 1)),
    ERGOT(ItemType.ERGOT, 2, 15,
            Ingredient.of(Material.WHEAT, 4)),
    ERGOT_EXTRACT(ItemType.ERGOT_EXTRACT, 2, 45,
            Ingredient.of(ItemType.ERGOT, 3), Ingredient.of(ItemType.LAB_SOLVENT, 1)),
    LUCID_TAB(ItemType.LUCID_TAB, 6, 30,
            Ingredient.of(ItemType.ERGOT_EXTRACT, 1), Ingredient.of(Material.PAPER, 2)),
    MESCALINE(ItemType.MESCALINE, 3, 40,
            Ingredient.of(ItemType.PEYOTE_BUTTON, 4), Ingredient.of(ItemType.LAB_SOLVENT, 1)),
    DMT(ItemType.DMT, 3, 50,
            Ingredient.of(Material.GLOW_BERRIES, 3), Ingredient.of(ItemType.LAB_SOLVENT, 1)),
    AYAHUASCA(ItemType.AYAHUASCA, 2, 50,
            Ingredient.of(ItemType.DMT, 1), Ingredient.of(Material.VINE, 2), Ingredient.water(1)),
    // uppers
    COCA_PASTE(ItemType.COCA_PASTE, 2, 40,
            Ingredient.of(ItemType.COCA_LEAVES, 6), Ingredient.of(ItemType.LAB_SOLVENT, 1)),
    COCAINE(ItemType.COCAINE, 3, 50,
            Ingredient.of(ItemType.COCA_PASTE, 2), Ingredient.of(ItemType.LAB_SOLVENT, 1)),
    CRACK(ItemType.CRACK, 2, 30,
            Ingredient.of(ItemType.COCAINE, 1), Ingredient.water(1), Ingredient.of(Material.BONE_MEAL, 1)),
    BLUE_CRYSTAL(ItemType.BLUE_CRYSTAL, 4, 60,
            Ingredient.of(Material.LAPIS_LAZULI, 3), Ingredient.of(ItemType.LAB_SOLVENT, 2), Ingredient.of(Material.REDSTONE, 1)),
    ECSTASY(ItemType.ECSTASY, 4, 40,
            Ingredient.of(Material.PINK_DYE, 2), Ingredient.of(Material.SUGAR, 2), Ingredient.of(ItemType.LAB_SOLVENT, 1)),
    PIXIE_DUST(ItemType.PIXIE_DUST, 4, 40,
            Ingredient.of(Material.GLOWSTONE_DUST, 2), Ingredient.of(Material.SUGAR, 2)),
    ANGEL_DUST(ItemType.ANGEL_DUST, 3, 50,
            Ingredient.of(Material.GUNPOWDER, 3), Ingredient.of(ItemType.LAB_SOLVENT, 1)),
    SPEED(ItemType.SPEED, 4, 45,
            Ingredient.of(Material.REDSTONE, 2), Ingredient.of(Material.SUGAR, 2), Ingredient.of(ItemType.LAB_SOLVENT, 1)),
    // downers
    OPIUM(ItemType.OPIUM, 2, 30,
            Ingredient.of(ItemType.POPPY_POD, 3)),
    // morphine base: 4 poppy seeds, also at the crafting table (dev.kushcraft.recipes.Recipes)
    MORPHINE(ItemType.MORPHINE, 1, 20,
            Ingredient.of(ItemType.POPPY_SEEDS, 4)),
    HEROIN(ItemType.HEROIN, 2, 60,
            Ingredient.of(ItemType.MORPHINE, 2), Ingredient.of(ItemType.LAB_SOLVENT, 1)),
    OXY(ItemType.OXY, 3, 45,
            Ingredient.of(ItemType.MORPHINE, 1), Ingredient.of(Material.SUGAR, 1), Ingredient.of(ItemType.LAB_SOLVENT, 1)),
    COUGH_SYRUP(ItemType.COUGH_SYRUP, 2, 30,
            Ingredient.of(ItemType.OPIUM, 1), Ingredient.of(Material.HONEY_BOTTLE, 1)),
    LEAN(ItemType.LEAN, 2, 30,
            Ingredient.of(ItemType.COUGH_SYRUP, 1), Ingredient.of(Material.SUGAR, 1), Ingredient.of(Material.PURPLE_DYE, 1)),
    KETAMINE(ItemType.KETAMINE, 4, 45,
            Ingredient.of(Material.NETHER_WART, 3), Ingredient.of(ItemType.LAB_SOLVENT, 1)),
    XANNY_BARS(ItemType.XANNY_BARS, 6, 40,
            Ingredient.of(Material.WHITE_DYE, 2), Ingredient.of(Material.SUGAR, 1), Ingredient.of(ItemType.LAB_SOLVENT, 1)),
    MOONSHINE(ItemType.MOONSHINE, 3, 50,
            Ingredient.of(Material.WHEAT, 4), Ingredient.of(Material.SUGAR, 2), Ingredient.water(1)),
    LAUGHING_GAS(ItemType.LAUGHING_GAS, 4, 30,
            Ingredient.of(Material.SLIME_BALL, 1), Ingredient.of(Material.IRON_NUGGET, 2), Ingredient.of(ItemType.LAB_SOLVENT, 1));

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

    /** True when some recipe uses this item. */
    public static boolean anyNeeds(ItemStack it) {
        for (LabRecipe r : values()) {
            for (Ingredient ing : r.ingredients) {
                if (ing.matches(it)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The recipe that makes this item (null if none). */
    public static LabRecipe making(ItemType t) {
        for (LabRecipe r : values()) {
            if (r.output == t) {
                return r;
            }
        }
        return null;
    }

    /** Either a KushCraft item or a plain vanilla material (POTION = a water bottle). */
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

        /** A plain water bottle. */
        static Ingredient water(int amount) {
            return new Ingredient(null, Material.POTION, amount, false);
        }

        public boolean matches(ItemStack it) {
            if (it == null) {
                return false;
            }
            if (custom != null) {
                return Items.type(it) == custom;
            }
            if (it.getType() != vanilla || Items.isCustom(it)) {
                return false;
            }
            if (vanilla == Material.POTION) {
                return it.getItemMeta() instanceof PotionMeta pm && pm.getBasePotionType() == PotionType.WATER
                        && !pm.hasCustomEffects();
            }
            return true;
        }

        /** What is left after using one (the bucket of a milk bucket, the bottle of a honey bottle...). */
        public Material remainder() {
            if (vanilla == null) {
                return null;
            }
            return switch (vanilla) {
                case MILK_BUCKET -> Material.BUCKET;
                case POTION, HONEY_BOTTLE -> Material.GLASS_BOTTLE;
                default -> null;
            };
        }

        public String name() {
            if (custom != null) {
                return custom.display();
            }
            return vanilla == Material.POTION ? "Water Bottle" : Text.titleCase(vanilla.name());
        }

        /** Where a new player finds this (shown under the recipe). */
        public String where() {
            if (custom != null) {
                if (custom == ItemType.MORPHINE) {
                    return "craft: 4 poppy seeds";
                }
                LabRecipe r = making(custom);
                if (r != null) {
                    return "cook it here first";
                }
                return switch (custom) {
                    case BUD_DRIED -> "dry fresh buds (Dry tab)";
                    case LAB_SOLVENT -> "Shop, or craft: bottle + sugar";
                    case MAGIC_MUSHROOM -> "grow mushroom spores";
                    case COCA_LEAVES -> "grow coca (warm biomes)";
                    case POPPY_POD -> "grow poppies";
                    case POPPY_SEEDS -> "grow poppies (2-3 a harvest)";
                    case PEYOTE_BUTTON -> "grow peyote on sand";
                    default -> "Shop";
                };
            }
            return switch (vanilla) {
                case POTION -> "bottle + water source";
                case MILK_BUCKET -> "milk a cow";
                case GLOW_BERRIES -> "lush caves, or Trade";
                case SLIME_BALL -> "slimes, or Trade";
                case NETHER_WART -> "Nether fortresses, or Trade";
                case LAPIS_LAZULI, REDSTONE -> "mining, or Trade";
                case VINE -> "jungles and swamps, or Trade";
                case WHITE_DYE -> "bone meal or lilies, or Trade";
                case SUGAR -> "sugar cane, or Trade";
                case GLOWSTONE_DUST -> "the Nether, or Trade";
                case GUNPOWDER -> "creepers, or Trade";
                case HONEY_BOTTLE -> "bee nests, or Trade";
                case PINK_DYE, PURPLE_DYE -> "flowers/dyes, or Trade";
                case COCOA_BEANS -> "jungles, or Trade";
                case SWEET_BERRIES -> "taiga bushes, or Trade";
                case IRON_NUGGET -> "smelt / craft iron";
                case GLASS_PANE -> "craft from glass";
                default -> "farm it, or Trade";
            };
        }
    }
}
