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

    /**
     * One stop of a trip: walk to stand, look at look, then do act (false = stop the trip).
     * jump: Runners and Suppliers can't walk there - they take the back way (vanish and appear).
     */
    record Step(Location stand, Location look, BooleanSupplier act, boolean jump) {
        Step(Location stand, Location look, BooleanSupplier act) {
            this(stand, look, act, false);
        }
    }

    /** What a worker is missing right now (Runners bring it, the Supplier buys it). */
    record Want(java.util.function.Predicate<ItemStack> match, String what, int amount) {
    }

    /** Most chests an old save (before 7.0.1, when you linked chests by hand) can keep for a worker. */
    public static final int MAX_LINKS = 8;

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
    /** Supplier: money spent on supplies since hired. */
    double spent;
    /** Cook: the LabRecipe they make, ROLL_JOINT / ROLL_BLUNT or AUTO (null = not picked yet). */
    String recipe;
    /** Chests linked by hand in old saves: they still count as the owner's (nothing new is linked). */
    final java.util.List<dev.kushcraft.util.BlockKey> links = new java.util.ArrayList<>();
    /** Supplier: never spends the owner's wallet below this. */
    double reserve = 1000;

    // live state
    transient UUID entityId;
    transient Location pos;
    final transient Deque<Step> steps = new ArrayDeque<>();
    transient Step current;
    transient int workTicks;
    transient int restTicks;
    transient String status = "Starting work...";
    transient Want want;
    /** Cook on AUTO: what they decided to make this time. */
    transient dev.kushcraft.lab.LabRecipe auto;
    final transient Deque<Location> path = new ArrayDeque<>();

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

    /** Supplier: what they spent on supplies since hired. */
    public double spent() {
        return spent;
    }

    /** Cook recipe ids for rolling instead of cooking, and for picking by themselves. */
    public static final String ROLL_JOINT = "ROLL_JOINT";
    public static final String ROLL_BLUNT = "ROLL_BLUNT";
    public static final String AUTO = "AUTO";

    /** Cook: the Drug Lab recipe they make (on AUTO: what they picked), or null. */
    public dev.kushcraft.lab.LabRecipe recipe() {
        if (AUTO.equals(recipe)) {
            return auto;
        }
        return recipe == null ? null : dev.kushcraft.lab.LabRecipe.parse(recipe);
    }

    /** Cook: they pick the best drug they have the ingredients for. */
    public boolean autoPick() {
        return AUTO.equals(recipe);
    }

    public double reserve() {
        return reserve;
    }

    /** What they're missing right now (null = nothing). */
    public String wants() {
        return want == null ? null : want.what();
    }

    /** Cook: JOINT or BLUNT when they roll instead of cooking, else null. */
    public dev.kushcraft.item.ItemType rolls() {
        if (ROLL_JOINT.equals(recipe)) {
            return dev.kushcraft.item.ItemType.JOINT;
        }
        return ROLL_BLUNT.equals(recipe) ? dev.kushcraft.item.ItemType.BLUNT : null;
    }

    /** Cook: what they make (a drug, or joints / blunts), or null. */
    public dev.kushcraft.item.ItemType product() {
        dev.kushcraft.lab.LabRecipe r = recipe();
        return r != null ? r.output() : rolls();
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
