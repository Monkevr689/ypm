package dev.kushcraft.award;

import dev.kushcraft.KushCraft;
import org.bukkit.OfflinePlayer;

import java.util.List;

/**
 * Getting started: the first seven things a new player does, in order. A
 * step is done when its award is unlocked (each award pays a reward), so
 * there is nothing extra to save. Shown on the guide button of every
 * menu page and in the Getting Started menu.
 */
public enum Starter {
    PLANT("Plant a seed", "seed_pack", List.of(Award.FIRST_SEED),
            "Buy seeds in the Shop (or break grass),", "then right-click farmland or grass."),
    HARVEST("Harvest it", "bud_fresh", List.of(Award.FIRST_HARVEST),
            "Wait until it's fully grown, then", "right-click the plant."),
    LAB("Get a Drug Lab", "machine_lab_station", List.of(Award.BUILD_LAB),
            "Shop > Gear & Workers, or craft one.", "Place it like a block."),
    DRY("Dry your buds", "bud_dried", List.of(Award.FIRST_DRY),
            "Right-click the lab, open Dry and", "click your fresh buds. 30 seconds!"),
    MAKE("Roll or cook", "joint", List.of(Award.FIRST_ROLL, Award.FIRST_COOK),
            "Drug Lab > Roll for joints, or Cook:", "dried buds > kief > hash, and more."),
    SELL("Sell your product", "cash", List.of(Award.FIRST_SALE),
            "Shop > Sell all, or click product", "in your inventory while it's open."),
    FORAGE("Pick a wild plant", "award_forager", List.of(Award.FORAGER),
            "Wild plants grow out in the world,", "away from farms. Go exploring!");

    private final String title;
    private final String icon;
    private final List<Award> awards;
    private final String line1;
    private final String line2;

    Starter(String title, String icon, List<Award> awards, String line1, String line2) {
        this.title = title;
        this.icon = icon;
        this.awards = awards;
        this.line1 = line1;
        this.line2 = line2;
    }

    public String title() {
        return title;
    }

    public String icon() {
        return icon;
    }

    /** Two short lines: how to do it. */
    public List<String> how() {
        return List.of(line1, line2);
    }

    /** Money for doing it (the award's reward). */
    public double reward() {
        return awards.get(0).reward();
    }

    public boolean done(OfflinePlayer p) {
        Awards a = KushCraft.get().awards();
        for (Award aw : awards) {
            if (a.has(p, aw)) {
                return true;
            }
        }
        return false;
    }

    /** True when unlocking this award just finished the step (and no other award had). */
    public boolean justDoneBy(OfflinePlayer p, Award a) {
        if (!awards.contains(a)) {
            return false;
        }
        Awards all = KushCraft.get().awards();
        for (Award other : awards) {
            if (other != a && all.has(p, other)) {
                return false;
            }
        }
        return true;
    }

    /** The first step not done yet, or null when every step is done. */
    public static Starter next(OfflinePlayer p) {
        for (Starter s : values()) {
            if (!s.done(p)) {
                return s;
            }
        }
        return null;
    }

    public static int doneCount(OfflinePlayer p) {
        int n = 0;
        for (Starter s : values()) {
            if (s.done(p)) {
                n++;
            }
        }
        return n;
    }
}
