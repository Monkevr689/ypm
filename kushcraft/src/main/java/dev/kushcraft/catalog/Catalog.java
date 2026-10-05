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
        GROW("Seeds & Harvest", "seed_pack"),
        GEAR("Supplies & Blocks", "machine_lab_station");

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
        // weed
        add(ItemType.BUD_DRIED, Category.WEED, "Strain effects (in a Bong)", "Drug Lab > Dry: fresh buds.");
        add(ItemType.JOINT, Category.WEED, "Strain effects, 3 hits", "Drug Lab > Roll: 1 dried bud + papers.");
        add(ItemType.BLUNT, Category.WEED, "Strain effects, 5 strong hits", "Drug Lab > Roll: 2 dried buds + wrap.");
        add(ItemType.HASH, Category.WEED, "Strong strain effects (Bong)", "Drug Lab > Cook: 4 dried buds + ice.");
        add(ItemType.MOON_ROCK, Category.WEED, "Very strong strain effects (Bong)",
                "Drug Lab > Cook: bud + hash + honey bottle.");
        add(ItemType.SPACE_BROWNIE, Category.WEED, "Long strain effects + Munchies",
                "Drug Lab > Cook: 2 buds + cocoa + 2 wheat + sugar.");
        add(ItemType.GUMMIES, Category.WEED, "Very long strain effects", "Drug Lab > Cook: hash + 2 sugar + slime ball.");
        // psychedelics
        drug(ItemType.MAGIC_MUSHROOM, Category.PSYCH, "Harvest grown magic mushrooms.");
        drug(ItemType.SHROOM_TEA, Category.PSYCH, "Drug Lab > Cook: 2 magic mushrooms + bottle.");
        drug(ItemType.LUCID_TAB, Category.PSYCH, "Drug Lab > Cook: solvent + 2 mushrooms + paper.");
        drug(ItemType.PEYOTE_BUTTON, Category.PSYCH, "Harvest a flowering peyote cactus.");
        drug(ItemType.MESCALINE, Category.PSYCH, "Drug Lab > Cook: 4 peyote buttons + solvent.");
        drug(ItemType.DMT, Category.PSYCH, "Drug Lab > Cook: solvent + catalyst + 3 glow berries.");
        // uppers
        drug(ItemType.COCAINE, Category.UPPERS, "Drug Lab > Cook: 8 coca leaves + solvent + sugar.");
        drug(ItemType.CRACK, Category.UPPERS, "Drug Lab > Cook: 2 cocaine + bone meal.");
        drug(ItemType.BLUE_CRYSTAL, Category.UPPERS, "Drug Lab > Cook: solvent + catalyst + 4 lapis + 2 sugar.");
        drug(ItemType.ECSTASY, Category.UPPERS, "Drug Lab > Cook: solvent + catalyst + 2 pink dye + sugar.");
        drug(ItemType.PIXIE_DUST, Category.UPPERS, "Drug Lab > Cook: solvent + 4 glowstone dust + 2 sugar.");
        drug(ItemType.ANGEL_DUST, Category.UPPERS, "Drug Lab > Cook: solvent + catalyst + 2 gunpowder + blaze powder.");
        // downers
        drug(ItemType.OPIUM, Category.DOWNERS, "Drug Lab > Cook: 3 poppy pods + glass bottle.");
        drug(ItemType.HEROIN, Category.DOWNERS, "Drug Lab > Cook: 6 poppy pods + solvent + catalyst.");
        drug(ItemType.LEAN, Category.DOWNERS, "Drug Lab > Cook: bottle + 2 sugar + purple dye + 2 sweet berries.");
        drug(ItemType.KETAMINE, Category.DOWNERS, "Drug Lab > Cook: solvent + 2 nether wart + 2 sugar.");
        // seeds & harvest
        add(ItemType.SEED_PACK, Category.GROW, null,
                "Break grass/ferns (biome decides the strain),", "buy at the Shop or breed your own.");
        add(ItemType.COCA_SEEDS, Category.GROW, null, "Break grass in jungles/savannas or buy them.");
        add(ItemType.POPPY_SEEDS, Category.GROW, null, "Break red poppy flowers or buy them.");
        add(ItemType.PEYOTE_SEEDS, Category.GROW, null, "Break dead bushes in deserts or buy them.");
        add(ItemType.MUSHROOM_SPORES, Category.GROW, null, "Break small mushrooms or buy them.");
        add(ItemType.BUD_FRESH, Category.GROW, null, "Harvest a fully grown cannabis plant.");
        add(ItemType.COCA_LEAVES, Category.GROW, null, "Harvest a fully grown coca bush.");
        add(ItemType.POPPY_POD, Category.GROW, null, "Harvest fully grown opium poppies.");
        // supplies & blocks
        add(ItemType.ROLLING_PAPERS, Category.GEAR, null, "Craft: 3 paper + sugar cane (gives 6).");
        add(ItemType.BLUNT_WRAP, Category.GEAR, null, "Craft: paper + cocoa beans + dried kelp.");
        add(ItemType.BONG, Category.GEAR, null, "Craft: glass, glass bottle, iron nugget.");
        add(ItemType.LAB_SOLVENT, Category.GEAR, null, "Craft: bottle + sugar + redstone + gunpowder.");
        add(ItemType.CATALYST, Category.GEAR, null, "Craft: amethyst + glowstone dust + redstone.");
        add(ItemType.FERTILIZER, Category.GEAR, null, "Craft: 2 bone meal + rotten flesh.");
        add(ItemType.GROWER_GUIDE, Category.GEAR, null, "Craft: book + wheat seeds. Opens the menu.");
        add(ItemType.LAB_STATION, Category.GEAR, null, "Crafting table (see the recipe).");
        add(ItemType.PLANTER_BOX, Category.GEAR, null, "Crafting table (see the recipe).");
        add(ItemType.GROW_LAMP, Category.GEAR, null, "Crafting table (see the recipe).");
        add(ItemType.DEALER, Category.GEAR, null, "Crafting table (see the recipe).");
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
