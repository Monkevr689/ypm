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
        WEED("Weed", "bud_dried"),
        PSYCH("Psychedelics", "lucid_tab"),
        UPPERS("Uppers", "cocaine"),
        DOWNERS("Downers", "heroin"),
        GEAR("Gear", "bong"),
        GROW("Seeds & Harvest", "seed_pack");

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

    private static void drug(ItemType t, Category c, String howTo) {
        add(t, c, effects(t), howTo);
    }

    static {
        // weed (one row in the Drugs tab, max 9 per category)
        add(ItemType.BUD_DRIED, Category.WEED, "Strain effects", "Dry fresh buds at a Drug Lab.");
        add(ItemType.JOINT, Category.WEED, "Strain effects, 3 hits", "Roll at a Drug Lab.");
        add(ItemType.BLUNT, Category.WEED, "Strain effects, 5 strong hits", "Roll at a Drug Lab.");
        add(ItemType.HASH, Category.WEED, "Strong strain effects", "Cook at a Drug Lab.");
        add(ItemType.MOON_ROCK, Category.WEED, "Very strong strain effects", "Cook at a Drug Lab.");
        add(ItemType.SPACE_BROWNIE, Category.WEED, "Long strain effects + Munchies", "Cook at a Drug Lab.");
        add(ItemType.GUMMIES, Category.WEED, "Very long strain effects", "Cook at a Drug Lab.");
        add(ItemType.WAX, Category.WEED, "Huge strain effects", "Cook at a Drug Lab.");
        add(ItemType.VAPE_PEN, Category.WEED, "Strain effects, 10 puffs", "Cook at a Drug Lab.");
        // psychedelics
        drug(ItemType.MAGIC_MUSHROOM, Category.PSYCH, "Grow magic mushrooms.");
        drug(ItemType.SHROOM_TEA, Category.PSYCH, "Cook at a Drug Lab.");
        drug(ItemType.LUCID_TAB, Category.PSYCH, "Cook ergot + paper at a Drug Lab.");
        drug(ItemType.PEYOTE_BUTTON, Category.PSYCH, "Grow a peyote cactus.");
        drug(ItemType.MESCALINE, Category.PSYCH, "Cook at a Drug Lab.");
        drug(ItemType.DMT, Category.PSYCH, "Cook at a Drug Lab.");
        // uppers
        drug(ItemType.COCAINE, Category.UPPERS, "Cook at a Drug Lab.");
        drug(ItemType.CRACK, Category.UPPERS, "Cook at a Drug Lab.");
        drug(ItemType.BLUE_CRYSTAL, Category.UPPERS, "Cook at a Drug Lab.");
        drug(ItemType.ECSTASY, Category.UPPERS, "Cook at a Drug Lab.");
        drug(ItemType.PIXIE_DUST, Category.UPPERS, "Cook at a Drug Lab.");
        drug(ItemType.ANGEL_DUST, Category.UPPERS, "Cook at a Drug Lab.");
        // downers
        drug(ItemType.OPIUM, Category.DOWNERS, "Cook at a Drug Lab.");
        drug(ItemType.HEROIN, Category.DOWNERS, "Cook at a Drug Lab.");
        drug(ItemType.LEAN, Category.DOWNERS, "Cook at a Drug Lab.");
        drug(ItemType.KETAMINE, Category.DOWNERS, "Cook at a Drug Lab.");
        // supplies & blocks
        add(ItemType.LAB_STATION, Category.GEAR, null, "Crafting table.");
        add(ItemType.GROW_LAMP, Category.GEAR, null, "Crafting table.");
        add(ItemType.PLANTER_BOX, Category.GEAR, null, "Crafting table.");
        add(ItemType.DEALER, Category.GEAR, null, "Crafting table.");
        add(ItemType.BONG, Category.GEAR, null, "Crafting table.");
        add(ItemType.ROLLING_PAPERS, Category.GEAR, null, "Crafting table.");
        add(ItemType.BLUNT_WRAP, Category.GEAR, null, "Crafting table.");
        add(ItemType.LAB_SOLVENT, Category.GEAR, null, "Crafting table.");
        add(ItemType.FERTILIZER, Category.GEAR, null, "Crafting table.");
        // seeds & harvest (Shop / admin list)
        add(ItemType.SEED_PACK, Category.GROW, null, "Break grass (the biome picks the strain).");
        add(ItemType.MUSHROOM_SPORES, Category.GROW, null, "Break small mushrooms.");
        add(ItemType.COCA_SEEDS, Category.GROW, null, "Break grass in jungles and savannas.");
        add(ItemType.POPPY_SEEDS, Category.GROW, null, "Break red poppies.");
        add(ItemType.PEYOTE_SEEDS, Category.GROW, null, "Break dead bushes in deserts.");
        add(ItemType.BUD_FRESH, Category.GROW, null, "Harvest a grown cannabis plant.");
        add(ItemType.COCA_LEAVES, Category.GROW, null, "Harvest a grown coca bush.");
        add(ItemType.POPPY_POD, Category.GROW, null, "Harvest grown poppies.");
        add(ItemType.ERGOT, Category.GROW, null, "Cook wheat at a Drug Lab, or find it harvesting wheat.");
        add(ItemType.GROWER_GUIDE, Category.GROW, null, "Type /kush.");
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
            case PEYOTE_BUTTON -> new Dose().add(EffectType.VISIONS, 90).add(EffectType.TRIPPY, 60)
                    .add(EffectType.DIZZY, 30).high(16);
            case MESCALINE -> new Dose().add(EffectType.VISIONS, 300).add(EffectType.TRIPPY, 240)
                    .add(EffectType.EUPHORIA, 120).high(30).delay(15, "<light_purple>The desert spirits arrive...");
            case DMT -> new Dose().add(EffectType.VISIONS, 90).add(EffectType.TRIPPY, 90).add(EffectType.CREATIVE, 60)
                    .high(34);
            case BLUE_CRYSTAL -> new Dose().add(EffectType.HYPER, 150).add(EffectType.FOCUS, 90).high(30);
            case COCAINE -> new Dose().add(EffectType.ENERGY, 150).add(EffectType.FOCUS, 150)
                    .add(EffectType.PARANOIA, 60).high(22);
            case CRACK -> new Dose().add(EffectType.HYPER, 60).add(EffectType.ENERGY, 90).add(EffectType.PARANOIA, 150)
                    .high(32);
            case ECSTASY -> new Dose().add(EffectType.LOVED_UP, 240).add(EffectType.ENERGY, 180).add(EffectType.GLOW, 60)
                    .high(28).delay(10, "<light_purple>The pill kicks in - you love everyone!");
            case PIXIE_DUST -> new Dose().add(EffectType.GLOW, 120).add(EffectType.FLOATY, 120)
                    .add(EffectType.EUPHORIA, 120).high(24);
            case ANGEL_DUST -> new Dose().add(EffectType.RAGE, 180).add(EffectType.PAIN_RELIEF, 180)
                    .add(EffectType.PARANOIA, 90).add(EffectType.BAD_TRIP, 40).high(36);
            case OPIUM -> new Dose().add(EffectType.COUCH_LOCK, 180).add(EffectType.PAIN_RELIEF, 180)
                    .add(EffectType.SLEEPY, 120).high(24);
            case HEROIN -> new Dose().add(EffectType.EUPHORIA, 240).add(EffectType.COUCH_LOCK, 240)
                    .add(EffectType.PAIN_RELIEF, 240).add(EffectType.SLEEPY, 120).high(42);
            case LEAN -> new Dose().add(EffectType.SYRUPY, 240).add(EffectType.SLEEPY, 120).add(EffectType.EUPHORIA, 120)
                    .high(18);
            case KETAMINE -> new Dose().add(EffectType.DISSOCIATED, 150).add(EffectType.FLOATY, 120)
                    .add(EffectType.DIZZY, 60).high(30);
            default -> null;
        };
    }

    /** Psychedelics can turn into a bad trip when you're already very high. */
    public static boolean psychedelic(ItemType t) {
        Entry e = of(t);
        return e != null && e.category() == Category.PSYCH;
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
