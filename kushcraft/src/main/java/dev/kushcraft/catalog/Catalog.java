package dev.kushcraft.catalog;

import dev.kushcraft.effect.Dose;
import dev.kushcraft.effect.EffectType;
import dev.kushcraft.item.ItemType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The product & drug catalog: what every item is, how you get it and what it
 * does. Used by the /kush catalog menu and when a drug is used.
 */
public final class Catalog {

    public enum Category {
        PLANTS("Seeds & Harvest", "seed_pack"),
        WEED("Weed", "bud_dried"),
        HARD("Hard Drugs", "cocaine"),
        SUPPLIES("Supplies", "lab_solvent"),
        BLOCKS("Blocks", "machine_lab_station");

        private final String display;
        private final String icon;

        Category(String display, String icon) {
            this.display = display;
            this.icon = icon;
        }

        public String display() {
            return display;
        }

        public String icon() {
            return icon;
        }
    }

    public record Entry(ItemType type, Category category, List<String> howTo, String effects) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private static void add(ItemType t, Category c, String effects, String... howTo) {
        ENTRIES.add(new Entry(t, c, List.of(howTo), effects));
    }

    static {
        // seeds & harvest
        add(ItemType.SEED_PACK, Category.PLANTS, null,
                "Break grass/ferns (biome decides the strain),", "buy at the Market or mix in the Drug Lab.");
        add(ItemType.COCA_SEEDS, Category.PLANTS, null, "Break grass in jungles/savannas or buy them.");
        add(ItemType.POPPY_SEEDS, Category.PLANTS, null, "Break red poppy flowers or buy them.");
        add(ItemType.MUSHROOM_SPORES, Category.PLANTS, null, "Break small mushrooms or buy them.");
        add(ItemType.BUD_FRESH, Category.PLANTS, null, "Harvest a fully grown cannabis plant.");
        add(ItemType.COCA_LEAVES, Category.PLANTS, null, "Harvest a fully grown coca bush.");
        add(ItemType.POPPY_POD, Category.PLANTS, null, "Harvest fully grown opium poppies.");
        add(ItemType.MAGIC_MUSHROOM, Category.PLANTS, "Trippy + Giggles", "Harvest grown magic mushrooms.");
        // weed
        add(ItemType.BUD_DRIED, Category.WEED, "Strain effects (in a Bong)", "Drug Lab > Dry: fresh buds.");
        add(ItemType.JOINT, Category.WEED, "Strain effects, 3 hits", "Drug Lab > Roll: 1 dried bud + papers.");
        add(ItemType.BLUNT, Category.WEED, "Strain effects, 5 strong hits", "Drug Lab > Roll: 2 dried buds + wrap.");
        add(ItemType.HASH, Category.WEED, "Strong strain effects (Bong)", "Drug Lab > Cook: 4 dried buds + ice.");
        add(ItemType.MOON_ROCK, Category.WEED, "Very strong strain effects (Bong)",
                "Drug Lab > Cook: bud + hash + honey bottle.");
        add(ItemType.SPACE_BROWNIE, Category.WEED, "Long strain effects + Munchies",
                "Drug Lab > Cook: 2 buds + cocoa + 2 wheat + sugar.");
        add(ItemType.SHROOM_TEA, Category.WEED, "Trippy + Euphoria", "Drug Lab > Cook: 2 magic mushrooms + bottle.");
        // hard drugs
        add(ItemType.COCAINE, Category.HARD, effects(ItemType.COCAINE),
                "Drug Lab > Cook: 8 coca leaves + solvent + sugar.");
        add(ItemType.HEROIN, Category.HARD, effects(ItemType.HEROIN),
                "Drug Lab > Cook: 6 poppy pods + solvent + catalyst.");
        add(ItemType.LUCID_TAB, Category.HARD, effects(ItemType.LUCID_TAB),
                "Drug Lab > Cook: solvent + 2 magic mushrooms + paper.");
        add(ItemType.BLUE_CRYSTAL, Category.HARD, effects(ItemType.BLUE_CRYSTAL),
                "Drug Lab > Cook: solvent + catalyst + 4 lapis + 2 sugar.");
        add(ItemType.PIXIE_DUST, Category.HARD, effects(ItemType.PIXIE_DUST),
                "Drug Lab > Cook: solvent + 4 glowstone dust + 2 sugar.");
        // supplies
        add(ItemType.ROLLING_PAPERS, Category.SUPPLIES, null, "Craft: 3 paper + sugar cane (gives 6).");
        add(ItemType.BLUNT_WRAP, Category.SUPPLIES, null, "Craft: paper + cocoa beans + dried kelp.");
        add(ItemType.BONG, Category.SUPPLIES, null, "Craft: glass, glass bottle, iron nugget.");
        add(ItemType.LAB_SOLVENT, Category.SUPPLIES, null, "Craft: bottle + sugar + redstone + gunpowder.");
        add(ItemType.CATALYST, Category.SUPPLIES, null, "Craft: amethyst + glowstone dust + redstone.");
        add(ItemType.FERTILIZER, Category.SUPPLIES, null, "Craft: 2 bone meal + rotten flesh.");
        add(ItemType.GROWER_GUIDE, Category.SUPPLIES, null, "Craft: book + wheat seeds. Opens this menu.");
        // blocks
        add(ItemType.LAB_STATION, Category.BLOCKS, null, "Craft: bottle, brewing stand, bottle /",
                "iron, cauldron, iron / iron, _, iron.");
        add(ItemType.PLANTER_BOX, Category.BLOCKS, null, "Craft: planks, bone meal, planks /",
                "planks, dirt, planks / 3 planks (gives 2).");
        add(ItemType.GROW_LAMP, Category.BLOCKS, null, "Craft: 3 iron / glowstone dust,",
                "redstone lamp, glowstone dust / _, iron, _.");
        add(ItemType.DEALER, Category.BLOCKS, null, "Craft: 3 emerald / planks, chest, planks /",
                "3 planks.");
    }

    private Catalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static List<Entry> entries(Category c) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ENTRIES) {
            if (c == null || e.category() == c) {
                out.add(e);
            }
        }
        return out;
    }

    public static Entry of(ItemType t) {
        for (Entry e : ENTRIES) {
            if (e.type() == t) {
                return e;
            }
        }
        return null;
    }

    /** Fixed doses for drugs that are not tied to a cannabis strain. */
    public static Dose dose(ItemType t) {
        return switch (t) {
            case MAGIC_MUSHROOM -> new Dose().add(EffectType.TRIPPY, 90).add(EffectType.GIGGLES, 90).high(16);
            case SHROOM_TEA -> new Dose().add(EffectType.TRIPPY, 180).add(EffectType.EUPHORIA, 180).high(26)
                    .delay(5, "<light_purple>The tea starts to work...");
            case LUCID_TAB -> new Dose().add(EffectType.TRIPPY, 300).add(EffectType.CREATIVE, 300)
                    .add(EffectType.FOCUS, 200).high(30).delay(10, "<light_purple>The colours start to breathe...");
            case BLUE_CRYSTAL -> new Dose().add(EffectType.HYPER, 150).add(EffectType.FOCUS, 90).high(30);
            case COCAINE -> new Dose().add(EffectType.ENERGY, 150).add(EffectType.FOCUS, 150)
                    .add(EffectType.PARANOIA, 60).high(22);
            case HEROIN -> new Dose().add(EffectType.EUPHORIA, 240).add(EffectType.COUCH_LOCK, 240)
                    .add(EffectType.PAIN_RELIEF, 240).add(EffectType.SLEEPY, 120).high(42);
            case PIXIE_DUST -> new Dose().add(EffectType.GLOW, 120).add(EffectType.FLOATY, 120)
                    .add(EffectType.EUPHORIA, 120).high(24);
            default -> null;
        };
    }

    /** "Energy Rush 2:30, Focus 2:30" for a fixed drug. */
    public static String effects(ItemType t) {
        Dose d = dose(t);
        if (d == null) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<EffectType, Integer> e : d.effects().entrySet()) {
            if (!b.isEmpty()) {
                b.append(", ");
            }
            b.append(e.getKey().display());
        }
        if (t == ItemType.BLUE_CRYSTAL) {
            b.append(" (then a Crash)");
        }
        return b.toString();
    }
}
