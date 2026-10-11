package dev.kushcraft.effects;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom KushCraft effects. Selectable ones can be bred into strains. None
 * of them help in a fight (no strength, resistance or invisibility): they
 * are about farming, mining, moving around and having a weird time.
 */
public enum EffectType {
    GIGGLES("Giggles", "#FFD83A", "effect_giggles", "Laughing fits and extra luck.", true),
    MUNCHIES("Munchies", "#F0943A", "effect_munchies", "Hungry fast, but food fills you way more.", true),
    COUCH_LOCK("Couch Lock", "#E85A5A", "effect_couch_lock", "Slow as a sloth, but you never get hungry.", true),
    ENERGY("Energy Rush", "#FFE23A", "effect_energy", "Run faster and dig faster.", true),
    EUPHORIA("Euphoria", "#FF7AC0", "effect_euphoria", "Slowly heals you. Good vibes only.", true),
    CREATIVE("Creative Flow", "#F8E84A", "effect_creative", "Mine like crazy and earn bonus XP.", true),
    FLOATY("Floaty", "#C8DCFF", "effect_floaty", "Jump higher and fall like a feather.", true),
    PARANOIA("Paranoia", "#E84A4A", "effect_paranoia", "...was that a creeper behind you?", true),
    SLEEPY("Sleepy", "#F8E87A", "effect_sleepy", "Drowsy and slow, but you heal while resting.", true),
    FOCUS("Focus", "#FF6A5A", "effect_focus", "See in the dark and find better loot.", true),
    PAIN_RELIEF("Pain Relief", "#FFFFFF", "effect_pain_relief", "No fall damage, and you heal slowly.", true),
    TRIPPY("Trippy", "#D84AF0", "effect_trippy", "The world melts into colours.", true),
    LUCKY("Lucky", "#5AE87A", "effect_lucky", "Luck, and ores sometimes drop double.", true),
    NIGHT_OWL("Night Owl", "#7A9AF0", "effect_night_owl", "See in the dark like an owl.", true),
    AQUATIC("Aquatic", "#4AC8F0", "effect_aquatic", "Breathe and swim fast underwater.", true),
    FIREPROOF("Fireproof", "#F07A2A", "effect_fireproof", "Fire and lava can't hurt you.", true),
    GHOST("Ghost", "#C8D0D8", "effect_ghost", "Monsters don't notice you.", true),
    LOVED_UP("Loved Up", "#FF5AA8", "effect_loved_up", "Hearts everywhere; you heal people near you.", true),
    VISIONS("Visions", "#9A6AF0", "effect_visions", "Spirits swirl around you. Night sight.", true),
    RAGE("Rage", "#D83A2A", "effect_rage", "Smash through stone like a machine. Hungry.", true),
    DIZZY("Dizzy", "#B8C84A", "effect_dizzy", "The world won't stop spinning.", true),
    GREEN_THUMB("Green Thumb", "#6AD84A", "effect_green_thumb", "Plants near you grow 25% faster.", true),
    SMOOTH_TALKER("Smooth Talker", "#F0C83A", "effect_smooth_talker", "The Shop pays you 10% more.", true),
    FROSTY("Frosty", "#9AE0FF", "effect_frosty", "Water freezes under your feet.", true),
    MAGNETIC("Magnetic", "#C84AE8", "effect_magnetic", "Items nearby fly to you.", true),
    SIXTH_SENSE("Sixth Sense", "#E8E0FF", "effect_sixth_sense", "Mobs nearby glow through walls.", true),
    ZEN("Zen", "#8AE8C8", "effect_zen", "Stand still to heal fast.", true),
    DISSOCIATED("Dissociated", "#8AA8B8", "effect_dissociated", "You float outside your body.", false),
    SYRUPY("Syrupy", "#A050D8", "effect_syrupy", "Everything moves in slow motion.", false),
    BAD_TRIP("Bad Trip", "#5A2A3A", "effect_bad_trip", "Dark, scary and hungry. Ride it out.", false),
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
