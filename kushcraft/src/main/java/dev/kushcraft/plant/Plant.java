package dev.kushcraft.plant;

import dev.kushcraft.util.BlockKey;

import java.util.UUID;

/** A growing plant. Its look is made of display entities that are respawned on chunk load. */
public final class Plant {

    public enum Kind {
        CANNABIS, MUSHROOM, COCA, POPPY;

        /** Cannabis has 5 stages (0-4), everything else 4 (0-3). */
        public int lastStage() {
            return this == CANNABIS ? 4 : 3;
        }

        public String display() {
            return switch (this) {
                case CANNABIS -> "Cannabis";
                case MUSHROOM -> "Magic Mushrooms";
                case COCA -> "Coca Bush";
                case POPPY -> "Opium Poppy";
            };
        }
    }

    private final BlockKey key;
    private final Kind kind;
    private final String strainId;
    private double growth;
    private boolean fertilized;
    private final UUID owner;

    transient UUID displayId;
    transient UUID hitboxId;
    transient int shownStage = -1;
    /** Last reason the plant could not grow (shown when right-clicked). */
    transient String status = "";

    public Plant(BlockKey key, Kind kind, String strainId, double growth, boolean fertilized, UUID owner) {
        this.key = key;
        this.kind = kind;
        this.strainId = strainId;
        this.growth = growth;
        this.fertilized = fertilized;
        this.owner = owner;
    }

    public BlockKey key() {
        return key;
    }

    public Kind kind() {
        return kind;
    }

    public String strainId() {
        return strainId;
    }

    public double growth() {
        return growth;
    }

    public void growth(double g) {
        this.growth = Math.max(0, Math.min(100, g));
    }

    public boolean fertilized() {
        return fertilized;
    }

    public void fertilized(boolean f) {
        this.fertilized = f;
    }

    public UUID owner() {
        return owner;
    }

    public UUID displayId() {
        return displayId;
    }

    public UUID hitboxId() {
        return hitboxId;
    }

    public boolean mature() {
        return growth >= 100;
    }

    /** Cannabis: 0..4, everything else: 0..3. */
    public int stage() {
        if (kind != Kind.CANNABIS) {
            if (growth >= 100) {
                return 3;
            }
            return growth >= 60 ? 2 : growth >= 25 ? 1 : 0;
        }
        if (growth >= 100) {
            return 4;
        }
        if (growth >= 70) {
            return 3;
        }
        if (growth >= 40) {
            return 2;
        }
        return growth >= 15 ? 1 : 0;
    }

    public String stageName() {
        int st = stage();
        return switch (kind) {
            case MUSHROOM -> st == 0 ? "Mycelium" : st == 1 ? "Pinning" : st == 2 ? "Fruiting" : "Ready to pick";
            case COCA -> st == 0 ? "Sprout" : st == 1 ? "Young bush" : st == 2 ? "Leafy bush" : "Ready to pick";
            case POPPY -> st == 0 ? "Sprout" : st == 1 ? "Budding" : st == 2 ? "Flowering" : "Pods ready";
            case CANNABIS -> st == 0 ? "Seedling" : st == 1 ? "Young plant" : st == 2 ? "Vegetative"
                    : st == 3 ? "Flowering" : "Ready to harvest";
        };
    }
}
