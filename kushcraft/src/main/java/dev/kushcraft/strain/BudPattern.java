package dev.kushcraft.strain;

import java.util.Locale;

/**
 * A second bud colour (the accent) painted over the buds in a pattern:
 * two-tone strains. custom_model_data strings[2] picks the pattern and
 * colours[3] is the accent (tools/buds.py PATTERNS, same order).
 */
public enum BudPattern {
    NONE(""),
    TIPS("Frosted tips"),
    STRIPES("Tiger stripes"),
    SPOTS("Leopard spots"),
    MARBLE("Marbled"),
    SPECKLES("Speckled"),
    HALO("Glowing edges"),
    SPLIT("Two-faced");

    private final String display;

    BudPattern(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public String id() {
        return this == NONE ? "" : name().toLowerCase(Locale.ROOT);
    }

    public static BudPattern parse(String s) {
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
