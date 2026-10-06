package dev.kushcraft.strain;

import org.bukkit.Color;
import org.bukkit.Particle;

import java.util.Locale;

/**
 * The very rare Mythic looks: animated rainbow, galaxy, gold, crystal, neon
 * or fire buds (custom_model_data strings[1]), and plants that sparkle.
 * Bred by luck, or found wild once in a blue moon.
 */
public enum Exotic {
    NONE("", "", null, false),
    RAINBOW("Rainbow", "<rainbow>", Particle.DUST, false),
    GALAXY("Galaxy", "<gradient:#8A6AFF:#F06AF0:#6AD8FF>", Particle.END_ROD, true),
    GOLDEN("Golden", "<gradient:#FFF08A:#E0A030:#FFF08A>", Particle.WAX_ON, false),
    CRYSTAL("Crystal", "<gradient:#FFFFFF:#8AE4FF:#FFFFFF>", Particle.SNOWFLAKE, false),
    NEON("Neon", "<gradient:#6AFF8A:#3AE8FF>", Particle.GLOW, true),
    INFERNO("Inferno", "<gradient:#FFE43A:#FF5A1A:#D81A1A>", Particle.SMALL_FLAME, true);

    private final String display;
    private final String open;
    private final Particle particle;
    private final boolean glows;

    Exotic(String display, String open, Particle particle, boolean glows) {
        this.display = display;
        this.open = open;
        this.particle = particle;
        this.glows = glows;
    }

    public String display() {
        return display;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Text in this look's colours (MiniMessage, text must already be escaped). */
    public String wrap(String text) {
        if (this == NONE) {
            return text;
        }
        String tag = open.substring(1, open.indexOf(open.contains(":") ? ':' : '>'));
        return open + text + "</" + tag + ">";
    }

    /** Sparkles around a growing plant (null = none). */
    public Particle particle() {
        return particle;
    }

    /** The plant is drawn fully lit, so it glows at night. */
    public boolean glows() {
        return glows;
    }

    /** Dust colour for rainbow sparkles. */
    public static Color rainbow(long tick) {
        return Color.fromRGB(Look.hsv((tick % 60) / 60.0, 0.8, 1.0));
    }

    public static Exotic parse(String s) {
        if (s == null || s.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
