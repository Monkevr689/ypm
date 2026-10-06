package dev.kushcraft.worker;

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

    /** Satchel slots. */
    public static final int SATCHEL = 27;

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
    final Inventory satchel = Bukkit.createInventory(null, SATCHEL);
    String name;
    int level = 1;
    boolean paused;
    int jobs;
    double wages;

    // live state
    transient UUID entityId;
    transient Location pos;
    final transient Deque<Step> steps = new ArrayDeque<>();
    transient Step current;
    transient int workTicks;
    transient int restTicks;
    transient String status = "Starting work...";

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
