package dev.kushcraft.machine;

import dev.kushcraft.util.BlockKey;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** A placed machine. Lab and drying rack keep a little state. */
public final class Machine {

    /** Drying racks in every Drug Lab. */
    public static final int RACKS = 5;

    /** Fresh buds of one strain and quality hanging to dry. */
    public record Rack(String strain, int quality, int amount, long start, long done) {

        public boolean dry() {
            return System.currentTimeMillis() >= done;
        }

        public double progress() {
            long total = Math.max(1, done - start);
            return Math.max(0, Math.min(1, (System.currentTimeMillis() - start) / (double) total));
        }

        public int secondsLeft() {
            return (int) Math.ceil(Math.max(0, done - System.currentTimeMillis()) / 1000.0);
        }
    }

    private final BlockKey key;
    private final MachineType type;
    private final float yaw;
    private final UUID owner;

    // lab station
    int level = 1;
    String job;
    long jobEnd;
    long jobStart;
    ItemStack output;
    boolean notified;

    // drying racks (Drug Lab > Dry has RACKS of them, the old Drying Rack block uses the first)
    final Rack[] racks = new Rack[RACKS];

    transient UUID displayId;
    /** Dry racks the last tick saw (to chime once when buds are done). */
    transient int dryShown;
    transient String shownModel;

    public Machine(BlockKey key, MachineType type, float yaw, UUID owner) {
        this.key = key;
        this.type = type;
        this.yaw = yaw;
        this.owner = owner;
    }

    /** Drug Lab upgrade level (1-5): faster cooking and bonus output. */
    public int level() {
        return level;
    }

    public void level(int level) {
        this.level = Math.max(1, level);
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

    // ---- drying racks ----
    public Rack rack(int i) {
        return racks[i];
    }

    public void rack(int i, Rack r) {
        racks[i] = r;
    }

    public void emptyRack(int i) {
        racks[i] = null;
    }

    /** Racks with buds on them. */
    public int racksInUse() {
        int n = 0;
        for (Rack r : racks) {
            if (r != null) {
                n++;
            }
        }
        return n;
    }

    /** Racks that are done. */
    public int racksDry() {
        int n = 0;
        for (Rack r : racks) {
            if (r != null && r.dry()) {
                n++;
            }
        }
        return n;
    }

    // old single-rack API (Drying Rack block): the first rack
    public String rackStrain() {
        return racks[0] == null ? null : racks[0].strain();
    }

    public int rackQuality() {
        return racks[0] == null ? 0 : racks[0].quality();
    }

    public int rackAmount() {
        return racks[0] == null ? 0 : racks[0].amount();
    }

    public long rackDone() {
        return racks[0] == null ? 0 : racks[0].done();
    }

    public boolean rackDry() {
        return racks[0] != null && racks[0].dry();
    }

    public void fillRack(String strain, int quality, int amount, long doneAt) {
        long start = racks[0] != null && racks[0].strain().equals(strain) ? racks[0].start() : System.currentTimeMillis();
        racks[0] = new Rack(strain, quality, amount, Math.min(start, doneAt), doneAt);
    }

    public void emptyRack() {
        racks[0] = null;
    }
}
