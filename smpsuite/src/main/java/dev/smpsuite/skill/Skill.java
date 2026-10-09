package dev.smpsuite.skill;

import org.bukkit.Material;

import java.util.Locale;

/** The skills. Vitality levels from a share of all the others. */
public enum Skill {
    MINING("Mining", "#9FB4C8", Material.IRON_PICKAXE, "haste-pulse", "Haste Pulse"),
    FARMING("Farming", "#8FD14F", Material.WHEAT, "bountiful-harvest", "Bountiful Harvest"),
    FISHING("Fishing", "#4FB8E8", Material.FISHING_ROD, "lucky-cast", "Lucky Cast"),
    FORAGING("Foraging", "#C8963E", Material.OAK_LOG, "tree-feller", "Tree Feller"),
    EXCAVATION("Excavation", "#D8B878", Material.IRON_SHOVEL, "treasure-sense", "Treasure Sense"),
    COMBAT("Combat", "#E85A4F", Material.IRON_SWORD, null, null),
    VITALITY("Vitality", "#F06AA8", Material.GOLDEN_APPLE, null, null);

    private final String display;
    private final String color;
    private final Material icon;
    private final String ability;
    private final String abilityName;

    Skill(String display, String color, Material icon, String ability, String abilityName) {
        this.display = display;
        this.color = color;
        this.icon = icon;
        this.ability = ability;
        this.abilityName = abilityName;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String display() {
        return display;
    }

    /** MiniMessage colour tag, e.g. "&lt;#9FB4C8&gt;". */
    public String color() {
        return "<" + color + ">";
    }

    public String colored() {
        return color() + display + "</" + color + ">";
    }

    public Material icon() {
        return icon;
    }

    /** Config id of this skill's ability, or null. */
    public String ability() {
        return ability;
    }

    public String abilityName() {
        return abilityName;
    }

    /** Skills with jobs pay and an XP table (everything but Vitality). */
    public boolean direct() {
        return this != VITALITY;
    }

    public static Skill parse(String s) {
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
