package dev.kushcraft.effects;

import dev.kushcraft.strains.Strain;

import java.util.EnumMap;
import java.util.Map;

/** What a single hit / bite / sip does to you. */
public final class Dose {

    private final Map<EffectType, Integer> effects = new EnumMap<>(EffectType.class);
    private double high;
    private int delaySeconds;
    private String kickIn;

    public Dose add(EffectType type, int seconds) {
        if (seconds > 0) {
            effects.merge(type, seconds, Integer::sum);
        }
        return this;
    }

    public Dose high(double high) {
        this.high = high;
        return this;
    }

    public Dose delay(int seconds, String kickInMessage) {
        this.delaySeconds = seconds;
        this.kickIn = kickInMessage;
        return this;
    }

    public Map<EffectType, Integer> effects() {
        return effects;
    }

    public double high() {
        return high;
    }

    public int delaySeconds() {
        return delaySeconds;
    }

    public String kickIn() {
        return kickIn;
    }

    /** Quality 1..5 stars => 0.85 .. 1.45 */
    public static double qualityFactor(int quality) {
        return 0.7 + 0.15 * Math.max(1, Math.min(5, quality));
    }

    /** A dose of a strain's own effects. baseSeconds/baseHigh are for a 20% THC, 3 star product. */
    public static Dose strain(Strain strain, int quality, int baseSeconds, double baseHigh) {
        double f = strain.potencyFactor() * qualityFactor(quality) / qualityFactor(3);
        Dose d = new Dose();
        for (EffectType e : strain.effects()) {
            d.add(e, (int) Math.round(baseSeconds * f));
        }
        return d.high(baseHigh * f);
    }
}
