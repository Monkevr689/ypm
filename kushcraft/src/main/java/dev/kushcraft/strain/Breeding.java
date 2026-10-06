package dev.kushcraft.strain;

import dev.kushcraft.effect.EffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Crossing two strains gives a random child: each parent effect may or may
 * not be passed on, a new effect can mutate in, potency drifts around the
 * parents' average and the type, climate, colours and bud shape come from
 * one parent or the other - or mutate into something new. Very rarely the
 * child is Mythic (an animated rainbow, galaxy, gold... look). Same parents,
 * different results - breed again for a better roll.
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
    /** Chance of a new bud / leaf / hair colour, shape or climate. */
    static final double COLOR_MUTATION = 0.2;
    static final double LEAF_MUTATION = 0.12;
    static final double PISTIL_MUTATION = 0.15;
    static final double SHAPE_MUTATION = 0.1;
    static final double CLIMATE_MUTATION = 0.1;
    /** Chance of a Mythic look out of nowhere, and of keeping a Mythic parent's look. */
    public static final double MYTHIC = 0.015;
    public static final double MYTHIC_INHERIT = 0.2;

    /** Unusual leaf colours: purple, black, blue, gold, red, ghost white, lime. */
    static final int[] LEAVES = {0x5A3A82, 0x24332A, 0x2E5A7A, 0xA8A03A, 0x8A3A3A, 0x9AB8A8, 0x6AD83A};

    public record Result(StrainType type, Look look, Climate climate, int potency, List<EffectType> effects,
                         List<EffectType> mutations, boolean jackpot, String flavor, boolean newLook) {

        public Rarity rarity() {
            return Rarity.of(potency, effects.size(), look.exotic());
        }

        public int color() {
            return look.bud();
        }
    }

    private Breeding() {
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
        return parent ? MYTHIC_INHERIT + MYTHIC : MYTHIC;
    }

    public static Result cross(Strain a, Strain b, Random r) {
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
            bud = vivid(r);
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
        Exotic exotic = Exotic.NONE;
        double roll = r.nextDouble();
        if (roll < MYTHIC) {
            exotic = randomExotic(r);
        } else if (roll < mythicChance(a, b)) {
            // a Mythic parent passes its look on (if both are Mythic, either one)
            List<Exotic> from = new ArrayList<>();
            for (Exotic e : List.of(la.exotic(), lb.exotic())) {
                if (e != Exotic.NONE) {
                    from.add(e);
                }
            }
            exotic = from.get(r.nextInt(from.size()));
        }
        Look look = new Look(bud, leaf, pistil, shape, exotic);
        return new Result(type(a.type(), b.type(), r), look, climate, potency, List.copyOf(effects),
                List.copyOf(mutations), jackpot, flavor(a.flavor(), b.flavor(), r), newLook);
    }

    static Exotic randomExotic(Random r) {
        Exotic[] all = Exotic.values();
        return all[1 + r.nextInt(all.length - 1)];
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
