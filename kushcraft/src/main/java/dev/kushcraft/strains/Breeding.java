package dev.kushcraft.strains;

import dev.kushcraft.effects.EffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Crossing two strains gives a random child: each parent effect may or may
 * not be passed on, a new effect can mutate in, potency drifts around the
 * parents' average and the type, climate, colours, accent pattern and bud
 * shape come from one parent or the other - or mutate into something new.
 * Sometimes the child is Mythic (an animated rainbow, galaxy, gold... look) -
 * much more often from Legendary parents;
 * crossing two Mythic strains can, rarely, give an Exotic one - the only way
 * to get them. Same parents, different results - breed again for a better roll.
 */
public final class Breeding {

    public static final int MAX_EFFECTS = 4;
    /** Chance an effect is passed on when only one parent has it / both have it. */
    static final double INHERIT_ONE = 0.55;
    static final double INHERIT_BOTH = 0.9;
    /** Chance of a brand new effect (and of a second one). */
    static final double MUTATION = 0.35;
    static final double SECOND_MUTATION = 0.08;
    /** Chance of a big potency jump. */
    static final double JACKPOT = 0.04;
    /** Chance of a new bud / leaf / hair colour, accent pattern, shape or climate. */
    static final double COLOR_MUTATION = 0.2;
    static final double LEAF_MUTATION = 0.12;
    static final double PISTIL_MUTATION = 0.15;
    static final double PATTERN_MUTATION = 0.14;
    static final double SHAPE_MUTATION = 0.1;
    static final double CLIMATE_MUTATION = 0.1;
    /** Chance of a Mythic look out of nowhere, and of keeping a Mythic parent's look. */
    public static final double MYTHIC = 0.065;
    public static final double MYTHIC_INHERIT = 0.2;
    /** Extra chance of a new Mythic look for every Legendary (and Epic) parent: two Legendary = about 1 in 4. */
    public static final double LEGENDARY_PARENT = 0.09;
    public static final double EPIC_PARENT = 0.025;
    /** Chance of an Exotic look when both parents are Mythic (or better), and extra per Exotic parent. */
    public static final double EXOTIC = 0.03;
    public static final double EXOTIC_PER_PARENT = 0.02;
    /** Crossing the two parents of a built-in Exotic strain: chance of getting it. */
    public static final double DISCOVERY = 0.06;

    /** Unusual leaf colours: purple, black, blue, gold, red, ghost white, lime, pink, orange, teal. */
    static final int[] LEAVES = {0x5A3A82, 0x24332A, 0x2E5A7A, 0xA8A03A, 0x8A3A3A, 0x9AB8A8, 0x6AD83A, 0xC85A9A,
            0xD8782A, 0x2A9A8A};
    /** Weird bud colours a mutation can land on (besides any bright colour). */
    static final int[] WEIRD = {0x16141E, 0xF4F4FF, 0xFF2AD8, 0x2AFFE8, 0xFF6A00, 0x7A2AFF, 0xC8FF2A, 0x2A3AFF,
            0xFF2A4A, 0xFFD82A, 0x6A3A1E, 0x5AE8A8, 0xE8A8FF, 0x2A8A8A, 0xB0B8C0, 0x3A0A2A};

    /**
     * discovered: a built-in Exotic strain this cross found (keep it to get its seeds), else null.
     */
    public record Result(StrainType type, Look look, Climate climate, int potency, List<EffectType> effects,
                         List<EffectType> mutations, boolean jackpot, String flavor, boolean newLook,
                         Strain discovered) {

        public Result(StrainType type, Look look, Climate climate, int potency, List<EffectType> effects,
                      List<EffectType> mutations, boolean jackpot, String flavor, boolean newLook) {
            this(type, look, climate, potency, effects, mutations, jackpot, flavor, newLook, null);
        }

        public Rarity rarity() {
            return discovered != null ? discovered.rarity() : Rarity.of(potency, effects.size(), look.exotic());
        }

        public int color() {
            return look.bud();
        }
    }

    private Breeding() {
    }

    private static final String[] SUFFIX = {"Dream", "Haze", "Kush", "Diesel", "Glue", "Cookies", "Cake", "Breath",
            "Fire", "Frost", "Punch", "Runtz", "Gelato", "Zkittlez", "Widow", "Express"};

    /** A free name for a cross: the first word of one parent and the last word of the other (or a classic ending). */
    public static String childName(Strain a, Strain b, java.util.function.Predicate<String> taken) {
        String first = a.name().split(" ")[0];
        String[] bw = b.name().split(" ");
        String n = first + " " + bw[bw.length - 1];
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        int tries = 0;
        while ((taken.test(n) || n.equalsIgnoreCase(a.name()) || n.equalsIgnoreCase(b.name())) && tries++ < 60) {
            n = first + " " + SUFFIX[r.nextInt(SUFFIX.length)] + (tries > 16 ? " " + (tries - 15) : "");
        }
        return n.length() > 24 ? n.substring(0, 24).trim() : n;
    }

    /** Effects either parent can pass on, with the chance of each. */
    public static Map<EffectType, Double> odds(Strain a, Strain b) {
        Map<EffectType, Double> out = new LinkedHashMap<>();
        for (Strain s : List.of(a, b)) {
            for (EffectType e : s.effects()) {
                if (e.selectable()) {
                    out.put(e, a.effects().contains(e) && b.effects().contains(e) ? INHERIT_BOTH : INHERIT_ONE);
                }
            }
        }
        return out;
    }

    /** Chance the child is Mythic. */
    public static double mythicChance(Strain a, Strain b) {
        boolean parent = a.exotic() != Exotic.NONE || b.exotic() != Exotic.NONE;
        return parent ? MYTHIC_INHERIT + newMythicChance(a, b) : newMythicChance(a, b);
    }

    /** Chance of a brand new Mythic look: higher with Legendary (and Epic) parents. */
    public static double newMythicChance(Strain a, Strain b) {
        return MYTHIC + parentBonus(a) + parentBonus(b);
    }

    private static double parentBonus(Strain s) {
        return switch (s.rarity()) {
            case LEGENDARY -> LEGENDARY_PARENT;
            case EPIC -> EPIC_PARENT;
            default -> 0;
        };
    }

    /** Chance the child is Exotic: only when both parents are Mythic or Exotic. */
    public static double exoticChance(Strain a, Strain b) {
        if (a.exotic() == Exotic.NONE || b.exotic() == Exotic.NONE) {
            return 0;
        }
        int exoticParents = (a.exotic().exoticTier() ? 1 : 0) + (b.exotic().exoticTier() ? 1 : 0);
        return EXOTIC + EXOTIC_PER_PARENT * exoticParents;
    }

    /** A built-in Exotic strain that crossing a and b can give (null if none). */
    public static Strain recipe(Strain a, Strain b, Collection<Strain> known) {
        if (known == null || a.exotic() == Exotic.NONE || b.exotic() == Exotic.NONE) {
            return null;
        }
        for (Strain s : known) {
            if (s.bredFrom(a, b)) {
                return s;
            }
        }
        return null;
    }

    public static Result cross(Strain a, Strain b, Random r) {
        return cross(a, b, r, null);
    }

    /** known: every strain (for the built-in Exotic strains two Mythic parents can give). */
    public static Result cross(Strain a, Strain b, Random r, Collection<Strain> known) {
        List<EffectType> effects = new ArrayList<>();
        Map<EffectType, Double> odds = odds(a, b);
        for (Map.Entry<EffectType, Double> e : odds.entrySet()) {
            if (r.nextDouble() < e.getValue()) {
                effects.add(e.getKey());
            }
        }
        if (effects.isEmpty() && !odds.isEmpty()) {
            List<EffectType> pool = new ArrayList<>(odds.keySet());
            effects.add(pool.get(r.nextInt(pool.size())));
        }
        List<EffectType> mutations = new ArrayList<>();
        if (r.nextDouble() < MUTATION) {
            mutate(effects, mutations, r);
            if (r.nextDouble() < SECOND_MUTATION) {
                mutate(effects, mutations, r);
            }
        }
        if (effects.isEmpty()) {
            mutate(effects, mutations, r);
        }
        if (effects.size() > MAX_EFFECTS) {
            // keep the mutations, drop random inherited ones
            List<EffectType> inherited = new ArrayList<>(effects);
            inherited.removeAll(mutations);
            Collections.shuffle(inherited, r);
            effects.clear();
            effects.addAll(mutations);
            for (EffectType e : inherited) {
                if (effects.size() < MAX_EFFECTS) {
                    effects.add(e);
                }
            }
        }

        boolean jackpot = r.nextDouble() < JACKPOT;
        double avg = (a.potency() + b.potency()) / 2.0;
        int potency = (int) Math.round(avg + 1 + r.nextGaussian() * 3 + (jackpot ? 6 : 0));
        potency = Math.max(5, Math.min(35, potency));

        // looks ------------------------------------------------------------
        Look la = a.look(), lb = b.look();
        boolean newLook = false;
        int bud;
        if (r.nextDouble() < COLOR_MUTATION) {
            bud = mutantColor(r);
            newLook = true;
        } else {
            bud = color(la.bud(), lb.bud(), r);
        }
        int leaf;
        if (r.nextDouble() < LEAF_MUTATION) {
            leaf = LEAVES[r.nextInt(LEAVES.length)];
            newLook = true;
        } else {
            leaf = Look.mix(r.nextBoolean() ? la.leaf() : lb.leaf(), r.nextBoolean() ? la.leaf() : lb.leaf(), 0.3);
        }
        int pistil;
        if (r.nextDouble() < PISTIL_MUTATION) {
            pistil = Look.hsv(r.nextDouble(), 0.4 + r.nextDouble() * 0.5, 0.9 + r.nextDouble() * 0.1);
            newLook = true;
        } else {
            pistil = r.nextBoolean() ? la.pistil() : lb.pistil();
        }
        BudPattern pattern;
        int accent;
        if (r.nextDouble() < PATTERN_MUTATION) {
            BudPattern[] all = BudPattern.values();
            pattern = all[1 + r.nextInt(all.length - 1)];
            accent = r.nextBoolean() ? vivid(r) : WEIRD[r.nextInt(WEIRD.length)];
            newLook = true;
        } else {
            Look from = r.nextBoolean() ? la : lb;
            Look other = from == la ? lb : la;
            pattern = from.pattern();
            accent = other.pattern() != BudPattern.NONE ? Look.mix(from.accent(), other.accent(), 0.25) : from.accent();
            if (pattern == BudPattern.NONE) {
                accent = bud;
            }
        }
        BudShape shape;
        if (r.nextDouble() < SHAPE_MUTATION) {
            shape = BudShape.values()[r.nextInt(BudShape.values().length)];
        } else {
            shape = r.nextBoolean() ? la.shape() : lb.shape();
        }
        Climate climate;
        if (r.nextDouble() < CLIMATE_MUTATION) {
            climate = Climate.values()[r.nextInt(Climate.values().length)];
        } else {
            climate = r.nextBoolean() ? a.climate() : b.climate();
        }
        StrainType type = type(a.type(), b.type(), r);
        String flavor = flavor(a.flavor(), b.flavor(), r);
        // two Mythic parents: a small chance of an Exotic child (or a built-in Exotic strain)
        double exoticChance = exoticChance(a, b);
        Strain found = recipe(a, b, known);
        if (found != null && r.nextDouble() < DISCOVERY) {
            Look fl = found.look();
            return new Result(found.type(), fl, found.climate(), found.potency(), found.effects(), List.of(), false,
                    found.flavor(), true, found);
        }
        Exotic exotic = Exotic.NONE;
        if (exoticChance > 0 && r.nextDouble() < exoticChance) {
            // an Exotic parent may pass its look on, else a new Exotic look
            List<Exotic> from = new ArrayList<>();
            for (Exotic e : List.of(la.exotic(), lb.exotic())) {
                if (e.exoticTier()) {
                    from.add(e);
                }
            }
            exotic = !from.isEmpty() && r.nextBoolean() ? from.get(r.nextInt(from.size())) : randomExotic(r);
        } else {
            double roll = r.nextDouble();
            if (roll < newMythicChance(a, b)) {
                exotic = randomMythic(r);
            } else if (roll < mythicChance(a, b)) {
                // a Mythic parent passes its look on (if both are Mythic, either one); an Exotic
                // look only passes on through two Mythic parents, so here it fades to a Mythic one
                List<Exotic> from = new ArrayList<>();
                for (Exotic e : List.of(la.exotic(), lb.exotic())) {
                    if (e != Exotic.NONE) {
                        from.add(e.exoticTier() ? randomMythic(r) : e);
                    }
                }
                exotic = from.get(r.nextInt(from.size()));
            }
        }
        Look look = new Look(bud, leaf, pistil, shape, exotic, accent, pattern);
        return new Result(type, look, climate, potency, List.copyOf(effects), List.copyOf(mutations), jackpot, flavor,
                newLook);
    }

    static Exotic randomMythic(Random r) {
        List<Exotic> all = Exotic.mythics();
        return all.get(r.nextInt(all.size()));
    }

    static Exotic randomExotic(Random r) {
        List<Exotic> all = Exotic.exotics();
        return all.get(r.nextInt(all.size()));
    }

    private static void mutate(List<EffectType> effects, List<EffectType> mutations, Random r) {
        List<EffectType> pool = new ArrayList<>(EffectType.selectableValues());
        pool.removeAll(effects);
        if (!pool.isEmpty()) {
            EffectType m = pool.get(r.nextInt(pool.size()));
            effects.add(m);
            mutations.add(m);
        }
    }

    static StrainType type(StrainType a, StrainType b, Random r) {
        double x = r.nextDouble();
        if (a == b) {
            if (a == StrainType.HYBRID) {
                return x < 0.7 ? StrainType.HYBRID : x < 0.85 ? StrainType.SATIVA : StrainType.INDICA;
            }
            return x < 0.85 ? a : StrainType.HYBRID;
        }
        return x < 0.5 ? StrainType.HYBRID : x < 0.75 ? a : b;
    }

    static int color(int x, int y, Random r) {
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int c = (((x >> shift) & 255) + ((y >> shift) & 255)) / 2 + r.nextInt(-28, 29);
            out |= Math.max(30, Math.min(255, c)) << shift;
        }
        return out;
    }

    /** A bright new bud colour. */
    static int vivid(Random r) {
        return Look.hsv(r.nextDouble(), 0.55 + r.nextDouble() * 0.4, 0.75 + r.nextDouble() * 0.25);
    }

    /** A new bud colour: any bright colour, or one of the weird ones (jet black, ghost white, acid lime...). */
    static int mutantColor(Random r) {
        return r.nextDouble() < 0.4 ? WEIRD[r.nextInt(WEIRD.length)] : vivid(r);
    }

    /** "Grape & Mint" from the parents' first flavour words. */
    static String flavor(String a, String b, Random r) {
        String fa = first(a), fb = first(b);
        if (fa.isEmpty() || fb.isEmpty() || fa.equalsIgnoreCase(fb)) {
            return fa.isEmpty() ? fb : fa;
        }
        return r.nextBoolean() ? fa + " & " + fb : fb + " & " + fa;
    }

    private static String first(String f) {
        if (f == null || f.isBlank()) {
            return "";
        }
        String s = f.split("[&,]")[0].trim();
        return s.length() > 12 ? s.substring(0, 12).trim() : s;
    }
}
