package dev.kushcraft.workers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * A hired worker: where they live, who they work for, their level and the
 * satchel they carry. In the world they are a mannequin that is spawned
 * when the chunk loads (never saved with the chunk).
 */
public final class Worker {

    /** Satchel slots (as big as a double chest; their menu shows it in two pages). */
    public static final int SATCHEL = 54;

    /** One stop of a trip: walk to stand, look at look, then do act (false = stop the trip). */
    record Step(Location stand, Location look, BooleanSupplier act) {
    }

    private final UUID id;
    private final WorkerType type;
    private final UUID owner;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    final Inventory satchel = Bukkit.createInventory(new Satchel(this), SATCHEL);
    String name;
    int level = 1;
    boolean paused;
    int jobs;
    double wages;
    /** Cook: the LabRecipe they make, or ROLL_JOINT / ROLL_BLUNT (null = not picked yet). */
    String recipe;
    /** When they were hired (the oldest workers keep working when a rank has fewer slots). */
    long hired;
    /** Since when nobody has been near them (their chunk unloaded); 0 = loaded. Work while away is caught up. */
    long awaySince;
    /** Knocked out by a raider until then (no work). */
    long knockedUntil;

    /** Changed since the last database write. */
    transient boolean dirty = true;
    /** Health while raids are on (back to full when they come round). */
    transient double health = -1;
    /** Last time they did anything (a safety net for the database write). */
    transient long lastWork;
    /** Time alone (millis) still to be caught up, and when their chunk loaded. */
    transient long pendingAway;
    transient long loadedAt;

    // live state
    transient UUID entityId;
    transient Location pos;
    final transient Deque<Step> steps = new ArrayDeque<>();
    transient Step current;
    transient int workTicks;
    transient int restTicks;
    transient String status = "Starting work...";
    /** What they're missing right now (a Runner brings it from a chest; null = nothing). */
    transient Want want;
    /** A player can see them (the mannequin moves every tick; else only now and then: less lag). */
    transient boolean watched = true;
    /** Since when they've had nothing to do (0 = busy): they walk home after a while. */
    transient long idleSince;
    /** When they last bought something themselves (no buying sprees). */
    transient long boughtAt;

    /** The satchel's holder: lets any satchel change find its worker (to save it). */
    public static final class Satchel implements org.bukkit.inventory.InventoryHolder {
        private final Worker worker;

        Satchel(Worker worker) {
            this.worker = worker;
        }

        public Worker worker() {
            return worker;
        }

        @Override
        public Inventory getInventory() {
            return worker.satchel;
        }
    }

    /** Something a worker is missing: what matches, a name for it and how many they want. */
    record Want(java.util.function.Predicate<ItemStack> match, String what, int amount) {
    }

    Worker(UUID id, WorkerType type, UUID owner, Location home, String name) {
        this.id = id;
        this.type = type;
        this.owner = owner;
        this.world = home.getWorld().getName();
        this.x = home.getX();
        this.y = home.getY();
        this.z = home.getZ();
        this.yaw = home.getYaw();
        this.name = name;
    }

    Worker(UUID id, WorkerType type, UUID owner, String world, double x, double y, double z, float yaw, String name) {
        this.id = id;
        this.type = type;
        this.owner = owner;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.name = name;
    }

    public UUID id() {
        return id;
    }

    public WorkerType type() {
        return type;
    }

    public UUID owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public int level() {
        return level;
    }

    /** Hired at (epoch millis). */
    public long hired() {
        return hired;
    }

    /** True while knocked out by a raider. */
    public boolean knockedOut() {
        return knockedUntil > System.currentTimeMillis();
    }

    public long knockedUntil() {
        return knockedUntil;
    }

    public boolean paused() {
        return paused;
    }

    /** Jobs done since hired. */
    public int jobs() {
        return jobs;
    }

    /** Wages paid since hired. */
    public double wages() {
        return wages;
    }

    /** Cook recipe ids for rolling instead of cooking. */
    public static final String ROLL_JOINT = "ROLL_JOINT";
    public static final String ROLL_BLUNT = "ROLL_BLUNT";

    /** Cook: the Drug Lab recipe they make, or null (nothing picked, or they roll). */
    public dev.kushcraft.lab.LabRecipe recipe() {
        return recipe == null ? null : dev.kushcraft.lab.LabRecipe.parse(recipe);
    }

    /** Cook: JOINT or BLUNT when they roll instead of cooking, else null. */
    public dev.kushcraft.items.ItemType rolls() {
        if (ROLL_JOINT.equals(recipe)) {
            return dev.kushcraft.items.ItemType.JOINT;
        }
        return ROLL_BLUNT.equals(recipe) ? dev.kushcraft.items.ItemType.BLUNT : null;
    }

    /** Cook: what they make (a drug, or joints / blunts), or null. */
    public dev.kushcraft.items.ItemType product() {
        dev.kushcraft.lab.LabRecipe r = recipe();
        return r != null ? r.output() : rolls();
    }

    /** What they're missing right now ("seeds", "Lab Solvent"...), or null. */
    public String needs() {
        return want == null ? null : want.what();
    }

    /** What they're doing right now (shown in their menu). */
    public String status() {
        return status;
    }

    /** The mannequin while their chunk is loaded (else null). */
    public UUID entityId() {
        return entityId;
    }

    public Inventory satchel() {
        return satchel;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    double z() {
        return z;
    }

    float yaw() {
        return yaw;
    }

    public String worldName() {
        return world;
    }

    public World world() {
        return Bukkit.getWorld(world);
    }

    /** Where they stand when they have nothing to do. */
    public Location home() {
        World w = world();
        return w == null ? null : new Location(w, x, y, z, yaw, 0);
    }

    public boolean isLoaded() {
        World w = world();
        return w != null && w.isChunkLoaded(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
    }

    public String chunkId() {
        return world + ":" + (((int) Math.floor(x)) >> 4) + ":" + (((int) Math.floor(z)) >> 4);
    }

    /** True while walking somewhere or working. */
    public boolean busy() {
        return current != null || !steps.isEmpty();
    }

    /** Free satchel slots. */
    public int freeSlots() {
        int n = 0;
        for (ItemStack it : satchel.getStorageContents()) {
            if (it == null || it.getType().isAir()) {
                n++;
            }
        }
        return n;
    }

    /** Items in the satchel. */
    public int carried() {
        int n = 0;
        for (ItemStack it : satchel.getStorageContents()) {
            if (it != null && !it.getType().isAir()) {
                n += it.getAmount();
            }
        }
        return n;
    }
}
