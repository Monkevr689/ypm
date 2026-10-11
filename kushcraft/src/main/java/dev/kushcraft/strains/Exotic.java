package dev.kushcraft.strains;

import org.bukkit.Color;
import org.bukkit.Particle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The very rare animated looks (custom_model_data strings[1]) and plants
 * that sparkle. Mythic looks are bred by luck or found wild once in a blue
 * moon; Exotic looks are rarer still and only come from crossing two
 * Mythic (or Exotic) strains. Same order as tools/buds.py EXOTICS.
 */
public enum Exotic {
    NONE("", "", null, false, false),
    // --- Mythic -------------------------------------------------------------
    RAINBOW("Rainbow", "<rainbow>", Particle.DUST, false, false),
    GALAXY("Galaxy", "<gradient:#8A6AFF:#F06AF0:#6AD8FF>", Particle.END_ROD, true, false),
    GOLDEN("Golden", "<gradient:#FFF08A:#E0A030:#FFF08A>", Particle.WAX_ON, false, false),
    CRYSTAL("Crystal", "<gradient:#FFFFFF:#8AE4FF:#FFFFFF>", Particle.SNOWFLAKE, false, false),
    NEON("Neon", "<gradient:#6AFF8A:#3AE8FF>", Particle.GLOW, true, false),
    INFERNO("Inferno", "<gradient:#FFE43A:#FF5A1A:#D81A1A>", Particle.SMALL_FLAME, true, false),
    AURORA("Aurora", "<gradient:#3AFFA8:#3AC8FF:#A85AFF>", Particle.END_ROD, true, false),
    TOXIC("Toxic", "<gradient:#C8FF3A:#5AE81A:#C8FF3A>", Particle.ITEM_SLIME, true, false),
    SAKURA("Sakura", "<gradient:#FFD0E8:#FF7AB8:#FFD0E8>", Particle.CHERRY_LEAVES, false, false),
    PLASMA("Plasma", "<gradient:#C85AFF:#5A8AFF:#FFFFFF>", Particle.ELECTRIC_SPARK, true, false),
    BLOOD_MOON("Blood Moon", "<gradient:#FF3A3A:#7A0A1A:#FF3A3A>", Particle.CRIMSON_SPORE, false, false),
    OCEAN("Ocean", "<gradient:#3AE8FF:#1A5AD8:#3AE8FF>", Particle.BUBBLE_POP, false, false),
    CANDY("Candy", "<gradient:#FF9AD8:#FFFFFF:#9AFFD8>", Particle.DUST, false, false),
    // --- Exotic (only from two Mythic parents) --------------------------------
    VOID("Void", "<gradient:#B85AFF:#2A0A3A:#FFFFFF>", Particle.REVERSE_PORTAL, true, true),
    PRISM("Prism", "<gradient:#FF5A5A:#FFE85A:#5AFF8A:#5AC8FF:#C85AFF>", Particle.DUST, true, true),
    CELESTIAL("Celestial", "<gradient:#FFE88A:#5A7AFF:#FFE88A>", Particle.END_ROD, true, true),
    PHOENIX("Phoenix", "<gradient:#FFFFFF:#FFD03A:#FF5A1A:#FF2A8A>", Particle.FLAME, true, true),
    QUANTUM("Quantum", "<gradient:#3AFFFF:#FF3AE8:#3AFFFF>", Particle.ELECTRIC_SPARK, true, true),
    ECLIPSE("Eclipse", "<gradient:#FFF0B0:#1A1A1A:#FFF0B0>", Particle.WHITE_ASH, true, true);

    private final String display;
    private final String open;
    private final Particle particle;
    private final boolean glows;
    private final boolean exoticTier;

    Exotic(String display, String open, Particle particle, boolean glows, boolean exoticTier) {
        this.display = display;
        this.open = open;
        this.particle = particle;
        this.glows = glows;
        this.exoticTier = exoticTier;
    }

    public String display() {
        return display;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** True for the Exotic looks (rarer than Mythic: only from two Mythic parents). */
    public boolean exoticTier() {
        return exoticTier;
    }

    /** The rarity a strain with this look has (null for NONE). */
    public Rarity rarity() {
        return this == NONE ? null : exoticTier ? Rarity.EXOTIC : Rarity.MYTHIC;
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

    /** Dust colour of looks that sparkle in changing colours (rainbow, prism, candy). */
    public Color dust(long tick) {
        return switch (this) {
            case CANDY -> Color.fromRGB(new int[]{0xFF9AD8, 0xFFFFFF, 0x9AFFD8}[(int) ((tick / 6) % 3)]);
            case PRISM -> Color.fromRGB(Look.hsv((tick % 30) / 30.0, 0.65, 1.0));
            default -> rainbow(tick);
        };
    }

    /** Dust colour for rainbow sparkles. */
    public static Color rainbow(long tick) {
        return Color.fromRGB(Look.hsv((tick % 60) / 60.0, 0.8, 1.0));
    }

    /** Every Mythic look (not NONE, not Exotic). */
    public static List<Exotic> mythics() {
        List<Exotic> out = new ArrayList<>();
        for (Exotic e : values()) {
            if (e != NONE && !e.exoticTier) {
                out.add(e);
            }
        }
        return out;
    }

    /** Every Exotic look. */
    public static List<Exotic> exotics() {
        List<Exotic> out = new ArrayList<>();
        for (Exotic e : values()) {
            if (e.exoticTier) {
                out.add(e);
            }
        }
        return out;
    }

    public static Exotic parse(String s) {
        if (s == null || s.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
