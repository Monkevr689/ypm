package dev.kushcraft.strain;

import java.util.Locale;

/** What the buds of a strain look like (picked by the resource pack from custom_model_data strings[0]). */
public enum BudShape {
    CLASSIC("Classic"),
    FOXTAIL("Foxtail"),
    POPCORN("Popcorn"),
    SPEAR("Spear");

    private final String display;

    BudShape(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static BudShape parse(String s, BudShape def) {
        if (s == null) {
            return def;
        }
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
