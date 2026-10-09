package dev.smpsuite.gem;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * The eight Bliss gems. Each has passives (while it's in your off hand with
 * energy) and two abilities: F = primary, Shift + F = secondary. They add
 * playstyle, not raw power: no extra hearts, no Strength effect, no
 * Resistance, and any bonus damage goes through the shared cap.
 */
public enum GemType {
    ASTRA("Astra", "#B57CFF", Material.AMETHYST_SHARD,
            List.of("Soul Absorption: hostile kills heal half a heart", "Phasing: 10% of projectiles pass through you"),
            "Dimensional Drift", "Blink up to 8 blocks the way you look",
            "Astral Daggers", "Throw 3 daggers (4 damage each to mobs)"),
    FIRE("Fire", "#FF7A2E", Material.BLAZE_POWDER,
            List.of("Fire Resistance", "Ores you mine come out smelted"),
            "Fireball", "Shoot a fireball that sets mobs alight",
            "Cozy Campfire", "Regeneration for you and your team for 10s"),
    FLUX("Flux", "#3AE8E0", Material.PRISMARINE_CRYSTALS,
            List.of("Lightning can't hurt you", "Conduit Power in water"),
            "Flux Beam", "A beam that zaps and slows the first thing it hits",
            "Static Burst", "Knock everything around you back"),
    LIFE("Life", "#FF6AB0", Material.GLISTERING_MELON_SLICE,
            List.of("Crops near you grow faster", "Food fills you up more"),
            "Vitality Vortex", "Regeneration II for you and your team",
            "Circle of Life", "Bone meal every crop and sapling around you"),
    PUFF("Puff", "#F4F4FF", Material.FEATHER,
            List.of("No fall damage"),
            "Dash", "Launch yourself forward",
            "Breezy Bash", "Fling mobs around you into the air"),
    SPEED("Speed", "#FFE04A", Material.SUGAR,
            List.of("Speed I", "Dolphin's Grace while swimming"),
            "Terminal Velocity", "Speed III and Haste II for 15s",
            "Slipstream", "Speed II for you and your team for 20s"),
    STRENGTH("Strength", "#E83A3A", Material.REDSTONE,
            List.of("+1 melee damage against mobs (shares the damage cap)", "20% knockback resistance"),
            "Frailer", "Weaken everything around you",
            "Bloodlust", "For 12s your hits on mobs heal you"),
    WEALTH("Wealth", "#3AD86A", Material.EMERALD,
            List.of("Luck and Hero of the Village", "+10% jobs pay"),
            "Rich Rush", "For 30s ores drop double",
            "Pockets", "Open 9 extra storage slots");

    private final String display;
    private final String color;
    private final Material base;
    private final List<String> passives;
    private final String primary;
    private final String primaryInfo;
    private final String secondary;
    private final String secondaryInfo;

    GemType(String display, String color, Material base, List<String> passives, String primary, String primaryInfo,
            String secondary, String secondaryInfo) {
        this.display = display;
        this.color = color;
        this.base = base;
        this.passives = passives;
        this.primary = primary;
        this.primaryInfo = primaryInfo;
        this.secondary = secondary;
        this.secondaryInfo = secondaryInfo;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String display() {
        return display;
    }

    public String hex() {
        return color;
    }

    public String colored() {
        return "<" + color + ">" + display + "</" + color + ">";
    }

    /** The vanilla item a gem is made of (what it looks like without the resource pack). */
    public Material base() {
        return base;
    }

    /** custom_model_data string the resource pack switches on. */
    public String model() {
        return "smp_gem_" + id();
    }

    public List<String> passives() {
        return passives;
    }

    public String primary() {
        return primary;
    }

    public String primaryInfo() {
        return primaryInfo;
    }

    public String secondary() {
        return secondary;
    }

    public String secondaryInfo() {
        return secondaryInfo;
    }

    public static GemType parse(String s) {
        if (s == null) {
            return null;
        }
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
