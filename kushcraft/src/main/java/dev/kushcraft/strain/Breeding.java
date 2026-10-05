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
 * parents' average and the type and colour mix. Same parents, different
 * results - breed again for a better roll.
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

    public record Result(StrainType type, int color, int potency, List<EffectType> effects,
                         List<EffectType> mutations, boolean jackpot) {

        public Rarity rarity() {
            return Rarity.of(potency, effects.size());
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

        return new Result(type(a.type(), b.type(), r), color(a.color(), b.color(), r), potency,
                List.copyOf(effects), List.copyOf(mutations), jackpot);
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
}
