package dev.kushcraft.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Custom KushCraft effects. Selectable ones can be bred into strains. */
public enum EffectType {
    GIGGLES("Giggles", "#FFD83A", "effect_giggles", "Laughing fits and extra luck.", true),
    MUNCHIES("Munchies", "#F0943A", "effect_munchies", "Hungry fast, but food fills you way more.", true),
    COUCH_LOCK("Couch Lock", "#E85A5A", "effect_couch_lock", "Slow as a sloth, tough as a rock.", true),
    ENERGY("Energy Rush", "#FFE23A", "effect_energy", "Run faster and dig faster.", true),
    EUPHORIA("Euphoria", "#FF7AC0", "effect_euphoria", "Slowly heals you. Good vibes only.", true),
    CREATIVE("Creative Flow", "#F8E84A", "effect_creative", "Mine like crazy and earn bonus XP.", true),
    FLOATY("Floaty", "#C8DCFF", "effect_floaty", "Jump higher and fall like a feather.", true),
    PARANOIA("Paranoia", "#E84A4A", "effect_paranoia", "...was that a creeper behind you?", true),
    SLEEPY("Sleepy", "#F8E87A", "effect_sleepy", "Drowsy and slow, but you heal while resting.", true),
    FOCUS("Focus", "#FF6A5A", "effect_focus", "See in the dark and hit harder.", true),
    PAIN_RELIEF("Pain Relief", "#FFFFFF", "effect_pain_relief", "Extra hearts and damage resistance.", true),
    TRIPPY("Trippy", "#D84AF0", "effect_trippy", "The world melts into colours.", true),
    HYPER("Hyper", "#FF4A3A", "effect_hyper", "Insane speed... the crash will hurt.", false),
    GLOW("Glow", "#FFF27A", "effect_glow", "You sparkle, glow and float.", false),
    CRASH("Crash", "#9A9AAA", "effect_crash", "Weak, slow and starving.", false),
    GREEN_OUT("Greened Out", "#8AD84A", "effect_green_out", "Way too much. Sit down for a minute.", false);

    private final String display;
    private final String color;
    private final String icon;
    private final String description;
    private final boolean selectable;

    EffectType(String display, String color, String icon, String description, boolean selectable) {
        this.display = display;
        this.color = color;
        this.icon = icon;
        this.description = description;
        this.selectable = selectable;
    }

    public String display() {
        return display;
    }

    public String color() {
        return color;
    }

    /** MiniMessage coloured name. */
    public String colored() {
        return "<color:" + color + ">" + display + "</color>";
    }

    public String icon() {
        return icon;
    }

    public String description() {
        return description;
    }

    public boolean selectable() {
        return selectable;
    }

    public static EffectType parse(String s) {
        if (s == null) {
            return null;
        }
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static List<EffectType> selectableValues() {
        List<EffectType> out = new ArrayList<>();
        for (EffectType t : values()) {
            if (t.selectable) {
                out.add(t);
            }
        }
        return out;
    }
}
