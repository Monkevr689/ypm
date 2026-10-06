package dev.kushcraft.strain;

import dev.kushcraft.effect.EffectType;
import dev.kushcraft.util.Text;

import java.util.List;
import java.util.UUID;

/** A cannabis strain: built in, from strains.yml, or bred in the Drug Lab. */
public final class Strain {

    private final String id;
    private final String name;
    private final StrainType type;
    private final Look look;
    private final Climate climate;
    private final int potency;
    private final List<EffectType> effects;
    private final List<String> wildBiomes;
    private final double wildWeight;
    private final String flavor;
    private final double price;
    private final boolean inShop;
    private final UUID creator;
    private final String creatorName;

    public Strain(String id, String name, StrainType type, Look look, Climate climate, int potency,
                  List<EffectType> effects, List<String> wildBiomes, double wildWeight, String flavor, double price,
                  boolean inShop, UUID creator, String creatorName) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.look = look;
        this.climate = climate == null ? type.defaultClimate() : climate;
        this.potency = Math.max(5, Math.min(35, potency));
        this.effects = List.copyOf(effects);
        this.wildBiomes = List.copyOf(wildBiomes);
        this.wildWeight = Math.max(0, wildWeight);
        this.flavor = flavor == null ? "" : flavor;
        this.price = price;
        this.inShop = inShop;
        this.creator = creator;
        this.creatorName = creatorName;
    }

    /** A copy with another name (id stays the same). */
    public Strain renamed(String newName) {
        return new Strain(id, newName, type, look, climate, potency, effects, wildBiomes, wildWeight, flavor, price,
                inShop, creator, creatorName);
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

    public Look look() {
        return look;
    }

    /** Bud colour. */
    public int color() {
        return look.bud();
    }

    public Exotic exotic() {
        return look.exotic();
    }

    /** The climate it grows best in. */
    public Climate climate() {
        return climate;
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

    /** How often it turns up when grass drops seeds in its biomes (1 = normal, 0.02 = very rare). */
    public double wildWeight() {
        return wildWeight;
    }

    /** "Mango", "Diesel"... (empty when unknown). */
    public String flavor() {
        return flavor;
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

    /** Sold as seeds in the Shop (built-in strains unless strains.yml says shop: false). */
    public boolean inShop() {
        return inShop && !isCustom();
    }

    /** Shop price of one seed: strains.yml price, or by rarity and potency. */
    public double seedPrice() {
        if (price > 0) {
            return price;
        }
        return Math.max(5, Math.round(rarity().seedPrice() * (0.6 + potency / 50.0) / 5.0) * 5.0);
    }

    /** Coloured, escaped strain name in MiniMessage (Mythic strains in their own colours). */
    public String colored() {
        if (look.exotic() != Exotic.NONE) {
            return look.exotic().wrap(Text.escape(name));
        }
        return "<color:" + Text.hex(brighten(look.bud())) + ">" + Text.escape(name) + "</color>";
    }

    public Rarity rarity() {
        return Rarity.of(potency, effects.size(), look.exotic());
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
