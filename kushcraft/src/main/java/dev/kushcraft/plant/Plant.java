package dev.kushcraft.plant;

import dev.kushcraft.util.BlockKey;

import java.util.UUID;

/** A growing plant. Its look is made of display entities that are respawned on chunk load. */
public final class Plant {

    public enum Kind { CANNABIS, MUSHROOM }

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

    /** Cannabis: 0..4, mushrooms: 0..3. */
    public int stage() {
        if (kind == Kind.MUSHROOM) {
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
        if (kind == Kind.MUSHROOM) {
            return switch (stage()) {
                case 0 -> "Mycelium";
                case 1 -> "Pinning";
                case 2 -> "Fruiting";
                default -> "Ready to pick";
            };
        }
        return switch (stage()) {
            case 0 -> "Seedling";
            case 1 -> "Young plant";
            case 2 -> "Vegetative";
            case 3 -> "Flowering";
            default -> "Ready to harvest";
        };
    }
}
