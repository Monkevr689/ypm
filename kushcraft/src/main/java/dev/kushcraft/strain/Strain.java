package dev.kushcraft.strain;

import dev.kushcraft.effect.EffectType;
import dev.kushcraft.util.Text;

import java.util.List;
import java.util.UUID;

/** A cannabis strain: built in, from strains.yml, or bred in the Strain Maker. */
public final class Strain {

    private final String id;
    private final String name;
    private final StrainType type;
    private final int color;
    private final int potency;
    private final List<EffectType> effects;
    private final List<String> wildBiomes;
    private final UUID creator;
    private final String creatorName;

    public Strain(String id, String name, StrainType type, int color, int potency, List<EffectType> effects,
                  List<String> wildBiomes, UUID creator, String creatorName) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.color = color & 0xFFFFFF;
        this.potency = Math.max(5, Math.min(35, potency));
        this.effects = List.copyOf(effects);
        this.wildBiomes = List.copyOf(wildBiomes);
        this.creator = creator;
        this.creatorName = creatorName;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public StrainType type() {
        return type;
    }

    public int color() {
        return color;
    }

    public int potency() {
        return potency;
    }

    public List<EffectType> effects() {
        return effects;
    }

    public List<String> wildBiomes() {
        return wildBiomes;
    }

    public UUID creator() {
        return creator;
    }

    public String creatorName() {
        return creatorName;
    }

    public boolean isCustom() {
        return creator != null;
    }

    /** Coloured, escaped strain name in MiniMessage. */
    public String colored() {
        return "<color:" + Text.hex(brighten(color)) + ">" + Text.escape(name) + "</color>";
    }

    public Rarity rarity() {
        return Rarity.of(potency, effects.size());
    }

    /** Potency factor used for effect length and prices (0.25 .. 1.75). */
    public double potencyFactor() {
        return potency / 20.0;
    }

    /** Darker colours are unreadable in chat, lift them a bit. */
    private static int brighten(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int max = Math.max(r, Math.max(g, b));
        if (max >= 140) {
            return rgb;
        }
        double f = 140.0 / Math.max(1, max);
        r = Math.min(255, (int) (r * f));
        g = Math.min(255, (int) (g * f));
        b = Math.min(255, (int) (b * f));
        return (r << 16) | (g << 8) | b;
    }
}
