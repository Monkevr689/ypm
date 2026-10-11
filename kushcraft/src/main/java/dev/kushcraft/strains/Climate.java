package dev.kushcraft.strains;

import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Locale;

/**
 * Where a plant grows: from the biome's temperature and humidity and the
 * height. Every strain has a home climate - it grows fast there, fine in
 * most others and badly in the opposite one (a Grow Lamp fixes that).
 */
public enum Climate {
    TROPICAL("Tropical", "#3ED67A", "jungles"),
    DESERT("Desert", "#F0B43A", "deserts, savannas, badlands"),
    TEMPERATE("Temperate", "#9ADC4A", "plains, forests"),
    WETLAND("Wetland", "#3AC8B8", "swamps, rivers, beaches"),
    COLD("Cold", "#9AD8FF", "snow, taiga"),
    MOUNTAIN("Mountain", "#C0A8F0", "hills, meadows, above y 100");

    /** How well a strain likes the climate it's planted in. */
    public enum Fit {
        IDEAL(1.5, 1, 1), OK(1.0, 0, 0), HARSH(0.5, -1, -1);

        private final double growth;
        private final int quality;
        private final int buds;

        Fit(double growth, int quality, int buds) {
            this.growth = growth;
            this.quality = quality;
            this.buds = buds;
        }

        public double growth() {
            return growth;
        }

        public int quality() {
            return quality;
        }

        /** Extra (or fewer) buds at harvest. */
        public int buds() {
            return buds;
        }
    }

    private final String display;
    private final String color;
    private final String where;

    Climate(String display, String color, String where) {
        this.display = display;
        this.color = color;
        this.where = where;
    }

    public String display() {
        return display;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String color() {
        return color;
    }

    public String colored() {
        return "<color:" + color + ">" + display + "</color>";
    }

    /** Where you find it, for the guide. */
    public String where() {
        return where;
    }

    /** Opposite climates: a strain from one grows badly in the other. */
    public boolean harsh(Climate other) {
        return pair(other, TROPICAL, COLD) || pair(other, TROPICAL, MOUNTAIN) || pair(other, DESERT, COLD)
                || pair(other, DESERT, WETLAND) || pair(other, DESERT, MOUNTAIN);
    }

    private boolean pair(Climate other, Climate a, Climate b) {
        return (this == a && other == b) || (this == b && other == a);
    }

    /** How a strain from this (home) climate does when planted in {@code here}. */
    public Fit fit(Climate here) {
        if (here == this) {
            return Fit.IDEAL;
        }
        return harsh(here) ? Fit.HARSH : Fit.OK;
    }

    public static Climate of(Block block) {
        World.Environment env = block.getWorld().getEnvironment();
        if (env == World.Environment.NETHER) {
            return DESERT;
        }
        if (env == World.Environment.THE_END) {
            return COLD;
        }
        return of(block.getBiome().getKey().getKey(), block.getTemperature(), block.getHumidity(), block.getY());
    }

    /** biome: key without namespace, temperature/humidity as the game reports them. */
    public static Climate of(String biome, double temperature, double humidity, int y) {
        String b = biome.toLowerCase(Locale.ROOT);
        if (temperature > 1.0 || b.contains("desert") || b.contains("badlands") || b.contains("savanna")) {
            return DESERT;
        }
        if (y >= 100 || b.contains("meadow") || b.contains("cherry") || b.contains("windswept") || b.contains("peaks")) {
            return MOUNTAIN;
        }
        if (temperature <= 0.3 || b.contains("snow") || b.contains("frozen") || b.contains("ice")) {
            return COLD;
        }
        if (b.contains("jungle") || (temperature >= 0.85 && humidity >= 0.7)) {
            return TROPICAL;
        }
        if (humidity >= 0.85 || b.contains("swamp") || b.contains("river") || b.contains("beach") || b.contains("lush")) {
            return WETLAND;
        }
        return TEMPERATE;
    }

    public static Climate parse(String s, Climate def) {
        if (s == null) {
            return def;
        }
        String k = s.trim().toUpperCase(Locale.ROOT);
        // 2.x names
        switch (k) {
            case "WARM" -> {
                return TROPICAL;
            }
            case "MILD" -> {
                return TEMPERATE;
            }
            default -> {
            }
        }
        try {
            return valueOf(k);
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
