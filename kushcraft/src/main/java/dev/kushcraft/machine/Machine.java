package dev.kushcraft.machine;

import dev.kushcraft.util.BlockKey;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** A placed machine. Lab and drying rack keep a little state. */
public final class Machine {

    private final BlockKey key;
    private final MachineType type;
    private final float yaw;
    private final UUID owner;

    // lab station
    String job;
    long jobEnd;
    long jobStart;
    ItemStack output;
    boolean notified;

    // drying rack
    String rackStrain;
    int rackQuality;
    int rackAmount;
    long rackDone;

    transient UUID displayId;
    transient String shownModel;

    public Machine(BlockKey key, MachineType type, float yaw, UUID owner) {
        this.key = key;
        this.type = type;
        this.yaw = yaw;
        this.owner = owner;
    }

    public BlockKey key() {
        return key;
    }

    public MachineType type() {
        return type;
    }

    public float yaw() {
        return yaw;
    }

    public UUID owner() {
        return owner;
    }

    public UUID displayId() {
        return displayId;
    }

    // ---- lab ----
    public String job() {
        return job;
    }

    public long jobEnd() {
        return jobEnd;
    }

    public long jobStart() {
        return jobStart;
    }

    public ItemStack output() {
        return output;
    }

    public boolean busy() {
        return job != null;
    }

    public boolean jobDone() {
        return job != null && System.currentTimeMillis() >= jobEnd;
    }

    public double jobProgress() {
        if (job == null) {
            return 0;
        }
        long total = Math.max(1, jobEnd - jobStart);
        return Math.max(0, Math.min(1, (System.currentTimeMillis() - jobStart) / (double) total));
    }

    public void startJob(String recipe, long durationMs, ItemStack result) {
        this.job = recipe;
        this.jobStart = System.currentTimeMillis();
        this.jobEnd = jobStart + durationMs;
        this.output = result;
        this.notified = false;
    }

    public void clearJob() {
        this.job = null;
        this.output = null;
        this.jobEnd = 0;
        this.jobStart = 0;
        this.notified = false;
    }

    // ---- drying rack ----
    public String rackStrain() {
        return rackStrain;
    }

    public int rackQuality() {
        return rackQuality;
    }

    public int rackAmount() {
        return rackAmount;
    }

    public long rackDone() {
        return rackDone;
    }

    public boolean rackDry() {
        return rackAmount > 0 && System.currentTimeMillis() >= rackDone;
    }

    public void fillRack(String strain, int quality, int amount, long doneAt) {
        this.rackStrain = strain;
        this.rackQuality = quality;
        this.rackAmount = amount;
        this.rackDone = doneAt;
    }

    public void emptyRack() {
        this.rackStrain = null;
        this.rackAmount = 0;
        this.rackQuality = 0;
        this.rackDone = 0;
    }
}
