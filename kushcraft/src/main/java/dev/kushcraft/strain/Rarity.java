package dev.kushcraft.strain;

/** How special a strain is: from its potency and number of effects. Rarer strains sell for more. */
public enum Rarity {
    COMMON("Common", "gray", 1.0),
    UNCOMMON("Uncommon", "green", 1.05),
    RARE("Rare", "aqua", 1.15),
    EPIC("Epic", "light_purple", 1.3),
    LEGENDARY("Legendary", "gold", 1.5);

    private final String display;
    private final String color;
    private final double priceFactor;

    Rarity(String display, String color, double priceFactor) {
        this.display = display;
        this.color = color;
        this.priceFactor = priceFactor;
    }

    public String display() {
        return display;
    }

    public String colored() {
        return "<" + color + ">" + display + "</" + color + ">";
    }

    public double priceFactor() {
        return priceFactor;
    }

    /** potency 5-35, 1-4 effects. */
    public static Rarity of(int potency, int effects) {
        int score = potency + 4 * effects;
        if (score >= 46) {
            return LEGENDARY;
        }
        if (score >= 40) {
            return EPIC;
        }
        if (score >= 34) {
            return RARE;
        }
        if (score >= 28) {
            return UNCOMMON;
        }
        return COMMON;
    }
}
