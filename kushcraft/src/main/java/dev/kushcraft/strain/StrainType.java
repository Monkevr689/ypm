package dev.kushcraft.strain;

import java.util.Locale;

/** Sativa / Indica / Hybrid. Decides plant shape and which climate it loves. */
public enum StrainType {
    SATIVA("Sativa", "#D4F05A", Climate.WARM, "type_sativa", "sativa", "Tall plant, loves warm biomes"),
    INDICA("Indica", "#B07AF0", Climate.COLD, "type_indica", "indica", "Short bushy plant, loves cold biomes"),
    HYBRID("Hybrid", "#7AE0A0", Climate.MILD, "type_hybrid", "hybrid", "Balanced plant, loves mild biomes");

    private final String display;
    private final String color;
    private final Climate climate;
    private final String icon;
    private final String plantModel;
    private final String blurb;

    StrainType(String display, String color, Climate climate, String icon, String plantModel, String blurb) {
        this.display = display;
        this.color = color;
        this.climate = climate;
        this.icon = icon;
        this.plantModel = plantModel;
        this.blurb = blurb;
    }

    public String display() {
        return display;
    }

    public String colored() {
        return "<color:" + color + ">" + display + "</color>";
    }

    public Climate climate() {
        return climate;
    }

    public String icon() {
        return icon;
    }

    public String plantModel() {
        return plantModel;
    }

    public String blurb() {
        return blurb;
    }

    /** Growth multiplier for this type in a given climate. */
    public double climateMultiplier(Climate c) {
        if (c == climate) {
            return 1.4;
        }
        if (this == HYBRID || c == Climate.MILD) {
            return 1.0;
        }
        return 0.6; // sativa in the cold or indica in the heat
    }

    /** Quality bonus (-1, 0, +1) for this type in a given climate. */
    public int climateQuality(Climate c) {
        if (c == climate) {
            return 1;
        }
        if (this == HYBRID || c == Climate.MILD) {
            return 0;
        }
        return -1;
    }

    public StrainType next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static StrainType parse(String s) {
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return HYBRID;
        }
    }
}
