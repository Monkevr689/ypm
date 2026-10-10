package dev.kushcraft.strains;

/**
 * How special a strain is: from its potency and number of effects, Mythic
 * for the rare animated looks and Exotic for the rarest looks (only bred
 * from two Mythic parents). Rarer strains sell for more and their seeds
 * cost more.
 */
public enum Rarity {
    COMMON("Common", "<gray>", 1.0, 15),
    UNCOMMON("Uncommon", "<green>", 1.1, 30),
    RARE("Rare", "<aqua>", 1.25, 55),
    EPIC("Epic", "<light_purple>", 1.45, 100),
    LEGENDARY("Legendary", "<gold>", 1.7, 180),
    MYTHIC("Mythic", "<gradient:#FF6AE8:#8A6AFF:#6AE8FF>", 4.0, 600),
    EXOTIC("Exotic", "<gradient:#FFF08A:#FF5AD8:#5AFFFF:#B85AFF>", 7.0, 1500);

    private final String display;
    private final String open;
    private final double priceFactor;
    private final double seedPrice;

    Rarity(String display, String open, double priceFactor, double seedPrice) {
        this.display = display;
        this.open = open;
        this.priceFactor = priceFactor;
        this.seedPrice = seedPrice;
    }

    public String display() {
        return display;
    }

    public String colored() {
        String tag = open.substring(1, open.indexOf(open.contains(":") ? ':' : '>'));
        return open + display + "</" + tag + ">";
    }

    public double priceFactor() {
        return priceFactor;
    }

    /** Shop price of a seed of this rarity at 20% THC (strains.yml can set its own). */
    public double seedPrice() {
        return seedPrice;
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

    public static Rarity of(int potency, int effects, Exotic exotic) {
        return exotic != null && exotic != Exotic.NONE ? exotic.rarity() : of(potency, effects);
    }

    /** Mythic or Exotic: the animated looks. */
    public boolean animated() {
        return this == MYTHIC || this == EXOTIC;
    }
}
