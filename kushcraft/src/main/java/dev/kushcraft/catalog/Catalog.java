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
        GROW("Seeds & Ingredients", "seed_pack");

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
        // weed (one row in the Drugs tab, max 9 per category): buds, joints, blunts, vape pens
        add(ItemType.BUD_FRESH, Category.WEED, null, "Harvest a grown cannabis plant.");
        add(ItemType.BUD_DRIED, Category.WEED, "Strain effects (in a Bong)", "Dry fresh buds at a Drug Lab.");
        add(ItemType.JOINT, Category.WEED, "Strain effects, 3 hits", "Roll at a Drug Lab.");
        add(ItemType.BLUNT, Category.WEED, "Strain effects, 5 strong hits", "Roll at a Drug Lab.");
        add(ItemType.VAPE_PEN, Category.WEED, "Strain effects, 10 puffs", "Cook at a Drug Lab.");
        // psychedelics
        drug(ItemType.MAGIC_MUSHROOM, Category.PSYCH, "Grow magic mushrooms.");
        drug(ItemType.SHROOM_TEA, Category.PSYCH, "Cook at a Drug Lab.");
        drug(ItemType.SHROOM_CHOCOLATE, Category.PSYCH, "Cook at a Drug Lab.");
        drug(ItemType.LUCID_TAB, Category.PSYCH, "Wheat > ergot > extract > tabs.");
        drug(ItemType.PEYOTE_BUTTON, Category.PSYCH, "Grow a peyote cactus.");
        drug(ItemType.MESCALINE, Category.PSYCH, "Cook at a Drug Lab.");
        drug(ItemType.DMT, Category.PSYCH, "Cook at a Drug Lab.");
        drug(ItemType.AYAHUASCA, Category.PSYCH, "Glow berries > DMT > ayahuasca.");
        // uppers
        drug(ItemType.COCAINE, Category.UPPERS, "Coca leaves > coca paste > cocaine.");
        drug(ItemType.CRACK, Category.UPPERS, "Cook at a Drug Lab.");
        drug(ItemType.BLUE_CRYSTAL, Category.UPPERS, "Cook at a Drug Lab.");
        drug(ItemType.SPEED, Category.UPPERS, "Cook at a Drug Lab.");
        drug(ItemType.ECSTASY, Category.UPPERS, "Cook at a Drug Lab.");
        drug(ItemType.PIXIE_DUST, Category.UPPERS, "Cook at a Drug Lab.");
        drug(ItemType.ANGEL_DUST, Category.UPPERS, "Cook at a Drug Lab.");
        // downers
        drug(ItemType.OPIUM, Category.DOWNERS, "Cook poppy pods at a Drug Lab.");
        drug(ItemType.HEROIN, Category.DOWNERS, "Poppy seeds > morphine base > heroin.");
        drug(ItemType.OXY, Category.DOWNERS, "Poppy seeds > morphine base > oxy.");
        drug(ItemType.LEAN, Category.DOWNERS, "Opium > cough syrup > lean.");
        drug(ItemType.XANNY_BARS, Category.DOWNERS, "Cook at a Drug Lab.");
        drug(ItemType.KETAMINE, Category.DOWNERS, "Cook at a Drug Lab.");
        drug(ItemType.MOONSHINE, Category.DOWNERS, "Cook at a Drug Lab.");
        drug(ItemType.LAUGHING_GAS, Category.DOWNERS, "Cook at a Drug Lab.");
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
        add(ItemType.POPPY_SEEDS, Category.GROW, null, "Break red poppies, or harvest poppies.",
                "4 seeds craft into Morphine Base.");
        add(ItemType.PEYOTE_SEEDS, Category.GROW, null, "Break dead bushes in deserts.");
        add(ItemType.COCA_LEAVES, Category.GROW, null, "Harvest a grown coca bush.");
        add(ItemType.POPPY_POD, Category.GROW, null, "Harvest grown poppies.");
        add(ItemType.ERGOT, Category.GROW, null, "Cook wheat at a Drug Lab, or find it harvesting wheat.");
        // in-between steps, all cooked at a Drug Lab
        add(ItemType.ERGOT_EXTRACT, Category.GROW, null, "Cook ergot at a Drug Lab.");
        add(ItemType.COCA_PASTE, Category.GROW, null, "Cook coca leaves at a Drug Lab.");
        add(ItemType.MORPHINE, Category.GROW, null, "Crafting table: 4 poppy seeds.", "Or let a Cook make it.");
        add(ItemType.COUGH_SYRUP, Category.GROW, null, "Cook opium + honey at a Drug Lab.");
        add(ItemType.GROWER_GUIDE, Category.GROW, null, "Type /kush.");
        // old weed products (not made any more; they still work and sell)
        for (ItemType t : ItemType.values()) {
            if (t.legacy()) {
                add(t, Category.GROW, null, "Not made any more - sell old ones in the Shop.");
            }
        }
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

    /** The one-of-a-kind effect each drug has (null for everything else). */
    public static EffectType signature(ItemType t) {
        return switch (t) {
            case JOINT -> EffectType.SMOKE_RINGS;
            case BLUNT -> EffectType.HOTBOX;
            case VAPE_PEN -> EffectType.CLOUD_CHASER;
            case MAGIC_MUSHROOM -> EffectType.FAIRY_RING;
            case SHROOM_TEA -> EffectType.SUNNY;
            case SHROOM_CHOCOLATE -> EffectType.SWEET_TOOTH;
            case LUCID_TAB -> EffectType.KALEIDOSCOPE;
            case PEYOTE_BUTTON -> EffectType.SPIRIT_FOX;
            case MESCALINE -> EffectType.CACTUS_SKIN;
            case DMT -> EffectType.MACHINE_ELVES;
            case AYAHUASCA -> EffectType.VINE_SIGHT;
            case COCAINE -> EffectType.NOSE_CANDY;
            case CRACK -> EffectType.TWEAKING;
            case BLUE_CRYSTAL -> EffectType.CHEMIST;
            case SPEED -> EffectType.QUICK_STEP;
            case ECSTASY -> EffectType.RAVE;
            case PIXIE_DUST -> EffectType.FAIRY_WINGS;
            case ANGEL_DUST -> EffectType.ANGEL_WINGS;
            case OPIUM -> EffectType.POPPY_TRAIL;
            case HEROIN -> EffectType.NUMB;
            case OXY -> EffectType.BOUNCE;
            case LEAN -> EffectType.SLOW_MO;
            case KETAMINE -> EffectType.MOON_GRAVITY;
            case XANNY_BARS -> EffectType.CHILL_PILL;
            case MOONSHINE -> EffectType.BEER_GOGGLES;
            case LAUGHING_GAS -> EffectType.BALLOON;
            default -> null;
        };
    }

    /** How long a drug's signature lasts (seconds; weed: per hit). */
    public static int signatureSeconds(ItemType t) {
        return switch (t) {
            case JOINT -> 40;
            case BLUNT -> 60;
            case VAPE_PEN -> 30;
            case LAUGHING_GAS -> 25;
            case MAGIC_MUSHROOM, PEYOTE_BUTTON, DMT -> 90;
            case CRACK -> 60;
            case LUCID_TAB, MESCALINE, AYAHUASCA -> 180;
            default -> 150;
        };
    }

    /** Fixed doses for drugs that are not tied to a cannabis strain (with their signature). */
    public static Dose dose(ItemType t) {
        Dose d = baseDose(t);
        EffectType sig = signature(t);
        if (d != null && sig != null) {
            d.add(sig, signatureSeconds(t));
        }
        return d;
    }

    private static Dose baseDose(ItemType t) {
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
            case SHROOM_CHOCOLATE -> new Dose().add(EffectType.TRIPPY, 150).add(EffectType.GIGGLES, 150)
                    .add(EffectType.EUPHORIA, 120).high(22).delay(20, "<light_purple>The chocolate melts the walls...");
            case AYAHUASCA -> new Dose().add(EffectType.VISIONS, 360).add(EffectType.TRIPPY, 300)
                    .add(EffectType.CREATIVE, 120).add(EffectType.DIZZY, 40).high(36)
                    .delay(20, "<light_purple>The vine pulls you under...");
            case SPEED -> new Dose().add(EffectType.ENERGY, 240).add(EffectType.FOCUS, 120).add(EffectType.PARANOIA, 60)
                    .high(24);
            case OXY -> new Dose().add(EffectType.PAIN_RELIEF, 240).add(EffectType.EUPHORIA, 180)
                    .add(EffectType.COUCH_LOCK, 120).add(EffectType.SLEEPY, 60).high(30);
            case XANNY_BARS -> new Dose().add(EffectType.ZEN, 240).add(EffectType.SLEEPY, 180)
                    .add(EffectType.COUCH_LOCK, 90).high(22);
            case MOONSHINE -> new Dose().add(EffectType.SMOOTH_TALKER, 180).add(EffectType.EUPHORIA, 120)
                    .add(EffectType.GIGGLES, 60).add(EffectType.DIZZY, 90).high(20);
            case LAUGHING_GAS -> new Dose().add(EffectType.GIGGLES, 40).add(EffectType.FLOATY, 30)
                    .add(EffectType.DIZZY, 20).high(10);
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
            if (e.getKey().signature()) {
                continue;
            }
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
