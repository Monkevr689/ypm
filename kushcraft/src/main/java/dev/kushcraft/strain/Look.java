package dev.kushcraft.strain;

/**
 * How a strain looks: bud, leaf and pistil (hair) colours, the bud shape, an
 * optional second bud colour in a pattern (two-tone buds) and an optional
 * Mythic / Exotic look. Seeds, buds and plants are tinted with these.
 */
public record Look(int bud, int leaf, int pistil, BudShape shape, Exotic exotic, int accent, BudPattern pattern) {

    public static final int DEFAULT_LEAF = 0x4E9E34;
    public static final int DEFAULT_PISTIL = 0xE8862E;

    public Look {
        bud &= 0xFFFFFF;
        leaf &= 0xFFFFFF;
        pistil &= 0xFFFFFF;
        accent &= 0xFFFFFF;
        shape = shape == null ? BudShape.CLASSIC : shape;
        exotic = exotic == null ? Exotic.NONE : exotic;
        pattern = pattern == null ? BudPattern.NONE : pattern;
    }

    /** One-colour buds. */
    public Look(int bud, int leaf, int pistil, BudShape shape, Exotic exotic) {
        this(bud, leaf, pistil, shape, exotic, bud, BudPattern.NONE);
    }

    /** Defaults for strains made before 3.0: leaves with a touch of the bud colour, orange hairs. */
    public static Look legacy(int bud, StrainType type, String id) {
        BudShape shape = BudShape.values()[Math.floorMod(id.hashCode(), BudShape.values().length)];
        return new Look(bud, mix(DEFAULT_LEAF, bud, 0.25), DEFAULT_PISTIL, shape, Exotic.NONE);
    }

    /** Dried buds: duller leaves and darker, browner hairs. */
    public int driedLeaf() {
        return mix(leaf, 0x9A8A4A, 0.45);
    }

    public int driedPistil() {
        return mix(pistil, 0x8A5A2A, 0.35);
    }

    public static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Multiplies brightness (keeps the hue). */
    public static int scale(int c, double f) {
        int r = (int) Math.min(255, Math.round(((c >> 16) & 255) * f));
        int g = (int) Math.min(255, Math.round(((c >> 8) & 255) * f));
        int b = (int) Math.min(255, Math.round((c & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    /** h, s, v in 0..1 -> RGB. */
    public static int hsv(double h, double s, double v) {
        h = ((h % 1) + 1) % 1 * 6;
        int i = (int) Math.floor(h);
        double f = h - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        double r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) Math.round(r * 255) << 16) | ((int) Math.round(g * 255) << 8) | (int) Math.round(b * 255);
    }
}
