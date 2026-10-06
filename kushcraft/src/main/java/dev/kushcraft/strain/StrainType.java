package dev.kushcraft.strain;

import java.util.Locale;

/** Sativa / Indica / Hybrid. Decides the plant's shape (and the default climate of old strains). */
public enum StrainType {
    SATIVA("Sativa", "#D4F05A", Climate.TROPICAL, "type_sativa", "sativa", "Tall plant, thin leaves"),
    INDICA("Indica", "#B07AF0", Climate.COLD, "type_indica", "indica", "Short bushy plant, broad leaves"),
    HYBRID("Hybrid", "#7AE0A0", Climate.TEMPERATE, "type_hybrid", "hybrid", "Medium plant");

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

    /** Climate for strains that don't name one (made before 3.0). */
    public Climate defaultClimate() {
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
