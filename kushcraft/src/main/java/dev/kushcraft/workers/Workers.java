package dev.kushcraft.workers;

import dev.kushcraft.KushCraft;
import dev.kushcraft.Keys;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.lab.Cooking;
import dev.kushcraft.lab.LabRecipe;
import dev.kushcraft.machines.Machine;
import dev.kushcraft.machines.MachineType;
import dev.kushcraft.plants.Plant;
import dev.kushcraft.plants.PlantManager;
import dev.kushcraft.economy.Shop;
import dev.kushcraft.strains.Strain;
import dev.kushcraft.util.BlockKey;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Protection;
import dev.kushcraft.util.Text;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.entity.LookAnchor;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Hired workers. A Farmhand harvests the owner's ripe plants around them,
 * plants them again and plants seeds on empty farmland; a Dryer hangs fresh
 * buds on the owner's Drug Lab racks and collects them when they're dry; a
 * Cook cooks (or rolls) the drug you pick; a Runner sells everything the
 * crew makes and carries things to and from chests. They walk over, work and
 * get paid a small wage for every job (the Runner takes a cut of each sale).
 *
 * The work chain: one player's workers within chain-radius of each other are
 * a crew. Each one takes what they need from the others' satchels (only what
 * the other one doesn't need) and from any of the owner's chests around them
 * - several in one trip - and puts what they make in a chest. A Runner sells
 * whatever sells the moment they get it, brings workers what they're missing
 * from chests further away and puts what nobody needs right now in a chest.
 * With auto-buy on (the owner's choice, in /kush > Shop > Gear &amp; Workers)
 * a worker who can't find seeds, fertilizer or an ingredient buys it.
 *
 * No lag: every worker thinks on its own tick of the second, plants, labs and
 * chests are looked up by chunk (and kept for a while), item checks never copy
 * item meta, and a mannequin only moves every tick while a player can see it.
 */
public final class Workers implements Listener, dev.kushcraft.storage.Persistence.Source {

    private static final String[] NAMES = {"Bud", "Sage", "Blaze", "Ziggy", "Dusty", "Basil", "Clover", "Moss",
            "Indie", "Skye", "Rowan", "Jojo", "Pip", "Sunny", "Rico", "Lupe", "Benny", "Nico", "Kiki", "Juniper",
            "Mojo", "Hazel", "Biscuit", "Noodle"};

    private final KushCraft plugin;
    private final File file;
    private final Map<UUID, Worker> workers = new LinkedHashMap<>();
    private final Map<UUID, Worker> byEntity = new HashMap<>();
    private final Map<String, Set<UUID>> byChunk = new HashMap<>();
    /** Every worker, for the tick loop (no new list every tick). */
    private Worker[] list = new Worker[0];
    /** Crews and chests, kept for a little while (they're asked for a lot). */
    private final Map<UUID, List<Worker>> crewCache = new HashMap<>();
    private long crewAt = -1;
    private final Map<UUID, ChestList> chestCache = new HashMap<>();
    private long ticks;
    /** False when the server can't spawn mannequins (they stay invisible but still work). */
    private boolean mannequins = true;
    /** Time spent on workers (for /kush selftest live and the admin): total, ticks, the slowest tick. */
    private long busyNanos;
    private long busyTicks;
    private long slowestNanos;

    public Workers(KushCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "workers.yml");
    }

    // ------------------------------------------------------------------
    // config
    // ------------------------------------------------------------------

    public boolean enabled() {
        return plugin.getConfig().getBoolean("workers.enabled", true);
    }

    /** Most workers one player can hire; 0 = no limit. */
    public int maxPerPlayer() {
        return Math.max(0, plugin.getConfig().getInt("workers.max-per-player", 0));
    }

    private static double pick(List<Double> list, int level, double def) {
        if (list.isEmpty()) {
            return def;
        }
        return list.get(Math.max(0, Math.min(list.size() - 1, level - 1)));
    }

    /** How far from home they work (blocks). */
    public int radius(Worker w) {
        return (int) pick(plugin.getConfig().getDoubleList("workers.radius"), w.level(), 8);
    }

    /** How far from home a worker uses chests: their work radius (a Runner: the whole crew). */
    public int chestRadius(Worker w) {
        return w.type() == WorkerType.RUNNER ? Math.max(radius(w), chainRadius()) : radius(w);
    }

    /** Seconds of rest between jobs. */
    public int restSeconds(Worker w) {
        return (int) Math.max(1, pick(plugin.getConfig().getDoubleList("workers.rest-seconds"), w.level(), 4));
    }

    /** Walking speed in blocks per tick. */
    private double speed(Worker w) {
        return 0.16 + 0.04 * (w.level() - 1);
    }

    /** Wage for one job (Runners take a cut of each sale instead). */
    public double wage(WorkerType t) {
        return Math.max(0, plugin.getConfig().getDouble("workers." + t.id() + ".wage", t == WorkerType.RUNNER ? 0 : 3));
    }

    /** Price to hire one in the Shop (for the menus). */
    public double hirePrice(WorkerType t) {
        for (var e : plugin.shop().hires()) {
            if (e.type() == t.item()) {
                return e.price();
            }
        }
        return 0;
    }

    public List<Double> upgradeCosts() {
        return plugin.getConfig().getDoubleList("workers.upgrade-costs");
    }

    public int maxLevel() {
        return upgradeCosts().size() + 1;
    }

    /** Water bottles a worker buys cost this much each. */
    public double waterPrice() {
        return Math.max(0, plugin.getConfig().getDouble("workers.water-price", 2));
    }

    // ------------------------------------------------------------------
    // auto-buy: workers buy their own seeds, fertilizer and ingredients
    // (off by default since 9.0: supplying your workers is part of the grind)
    // ------------------------------------------------------------------

    /** True when this player's workers buy what they can't find (server allows it and they didn't switch it off). */
    public boolean autoBuy(UUID owner) {
        if (!autoBuyAllowed()) {
            return false;
        }
        var r = plugin.players().get(owner);
        return r == null || !r.has(dev.kushcraft.storage.PlayerRecord.AUTO_BUY_OFF);
    }

    /** True when the server lets workers buy at all (workers.auto-buy). */
    public boolean autoBuyAllowed() {
        return plugin.getConfig().getBoolean("workers.auto-buy", false);
    }

    public void setAutoBuy(UUID owner, boolean on) {
        plugin.economy().account(owner).set(dev.kushcraft.storage.PlayerRecord.AUTO_BUY_OFF, !on);
    }

    // ------------------------------------------------------------------
    // storage: one database row per worker (satchel included)
    // ------------------------------------------------------------------

    /** Reads every worker from the database; the first time, imports workers.yml (8.0 and older). */
    public void load() {
        for (Worker w : workers.values()) {
            despawn(w);
        }
        workers.clear();
        list = new Worker[0];
        byEntity.clear();
        byChunk.clear();
        deleted.clear();
        catchUps.clear();
        overLimitKnown = false;
        List<Worker> rows = plugin.db().call(c -> {
            List<Worker> out = new ArrayList<>();
            try (java.sql.Statement st = c.createStatement();
                 java.sql.ResultSet rs = st.executeQuery("SELECT * FROM workers ORDER BY hired")) {
                while (rs.next()) {
                    WorkerType type = WorkerType.parse(rs.getString("type"));
                    if (type == null) {
                        continue;
                    }
                    try {
                        Worker w = new Worker(UUID.fromString(rs.getString("id")), type,
                                UUID.fromString(rs.getString("owner")), rs.getString("world"), rs.getDouble("x"),
                                rs.getDouble("y"), rs.getDouble("z"), (float) rs.getDouble("yaw"), rs.getString("name"));
                        w.level = Math.max(1, rs.getInt("level"));
                        w.paused = rs.getInt("paused") != 0;
                        w.jobs = rs.getInt("jobs");
                        w.wages = rs.getLong("wages") / 100.0;
                        w.recipe = rs.getString("recipe");
                        w.hired = rs.getLong("hired");
                        w.awaySince = rs.getLong("away_since");
                        if (w.awaySince > 0) {
                            w.awaySince += plugin.downtime(); // the server being off isn't time alone
                        }
                        w.knockedUntil = rs.getLong("knocked_until");
                        w.satchel.setStorageContents(dev.kushcraft.storage.ItemCodec.decode(rs.getBytes("satchel"),
                                Worker.SATCHEL));
                        w.dirty = false;
                        out.add(w);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Skipped a broken worker row " + rs.getString("id"));
                    }
                }
            }
            return out;
        });
        for (Worker w : rows) {
            add(w);
        }
        if (rows.isEmpty() && file.exists() && !plugin.legacyImported()) {
            importYaml();
        }
        plugin.getLogger().info("Loaded " + workers.size() + " workers.");
    }

    /** 8.0 and older kept workers in workers.yml: read it once (the file is moved to legacy-yaml/ after). */
    private void importYaml() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        plugin.legacyFile(file);
        for (String id : y.getStringList("settings.auto-buy-off")) {
            try {
                setAutoBuy(UUID.fromString(id), false);
            } catch (IllegalArgumentException ignored) {
                // not a player id
            }
        }
        ConfigurationSection sec = y.getConfigurationSection("workers");
        if (sec == null) {
            return;
        }
        List<UUID> refunds = new ArrayList<>();
        long now = System.currentTimeMillis();
        int n = 0;
        for (String k : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(k);
            String typeName = s == null ? "" : s.getString("type", "");
            WorkerType type = WorkerType.parse(typeName);
            if (type == null) {
                // a Supplier from 7.x: they're gone - their hire price goes back to the owner
                if (s != null && "SUPPLIER".equalsIgnoreCase(typeName)) {
                    try {
                        refunds.add(UUID.fromString(s.getString("owner", "")));
                    } catch (IllegalArgumentException ignored) {
                        // no owner
                    }
                }
                continue;
            }
            try {
                Worker w = new Worker(UUID.fromString(k), type, UUID.fromString(s.getString("owner", "")),
                        s.getString("world", "world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                        (float) s.getDouble("yaw"), s.getString("name", NAMES[0]));
                w.level = Math.max(1, s.getInt("level", 1));
                w.paused = s.getBoolean("paused");
                w.jobs = s.getInt("jobs");
                w.wages = s.getDouble("wages");
                w.recipe = s.getString("recipe");
                w.hired = now + n++; // keeps their order
                if (w.recipe != null && LabRecipe.parse(w.recipe) == null && !Worker.ROLL_JOINT.equals(w.recipe)
                        && !Worker.ROLL_BLUNT.equals(w.recipe)) {
                    w.recipe = null; // a 7.x job (Auto, mixing strains): pick a drug again
                }
                List<ItemStack> extra = new ArrayList<>();
                readItems(s.getConfigurationSection("satchel"), w.satchel, extra);
                // 7.2 kept a Farmhand's seeds in a backpack: they go in the (bigger) satchel now
                readItems(s.getConfigurationSection("seeds"), null, extra);
                for (ItemStack it : extra) {
                    w.satchel.addItem(it);
                }
                w.dirty = true;
                add(w);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("workers.yml: skipped a broken worker " + k);
            }
        }
        if (!refunds.isEmpty()) {
            // paid a moment later, once the economy is ready
            double each = Math.max(0, plugin.getConfig().getDouble("workers.supplier-refund", 13000));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (UUID o : refunds) {
                    plugin.economy().deposit(o, each, dev.kushcraft.economy.Tx.REFUND, null, "7.x Supplier refund");
                }
                plugin.getLogger().info("Suppliers are gone: refunded " + refunds.size() + " of them to their owners ("
                        + plugin.economy().format(each) + " each).");
            }, 10L);
        }
        plugin.getLogger().info("Imported " + workers.size() + " workers from workers.yml.");
    }

    /** Reads saved items into the inventory (by slot); with into == null, or slots it doesn't have, into extra. */
    private static void readItems(ConfigurationSection items, Inventory into, List<ItemStack> extra) {
        if (items == null) {
            return;
        }
        for (String slot : items.getKeys(false)) {
            ItemStack it = decode(items.getString(slot));
            if (it == null) {
                continue;
            }
            int i;
            try {
                i = Integer.parseInt(slot);
            } catch (NumberFormatException e) {
                continue;
            }
            if (into != null && i >= 0 && i < into.getSize()) {
                into.setItem(i, it);
            } else {
                extra.add(it);
            }
        }
    }

    private static ItemStack decode(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
        } catch (Exception e) {
            return null;
        }
    }

    /** Worker ids removed since the last database write. */
    private final Set<UUID> deleted = new HashSet<>();

    /**
     * The worker changed and must be saved. important = money or a player's items were part of it
     * (always the case for the next flush anyway: every changed worker goes out with it).
     */
    public void touch(Worker w, boolean important) {
        w.dirty = true;
        w.lastWork = System.currentTimeMillis();
    }

    /** A satchel changed somewhere (moves between satchels and chests): save its worker. */
    static void touched(Inventory inv) {
        if (inv != null && inv.getHolder(false) instanceof Worker.Satchel s) {
            s.worker().dirty = true;
        }
    }

    /** Every worker gets saved again (after a config change). */
    public void markDirty() {
        for (Worker w : workers.values()) {
            w.dirty = true;
        }
    }

    private record Row(String id, String owner, String type, String world, double x, double y, double z, float yaw,
                       String name, int level, boolean paused, int jobs, long wages, String recipe, long hired,
                       long awaySince, long knockedUntil, ItemStack[] satchel) {
    }

    @Override
    public void collect(dev.kushcraft.storage.Persistence.Batch b, boolean full) {
        long recent = System.currentTimeMillis() - 60_000L;
        List<Worker> changed = new ArrayList<>();
        for (Worker w : list) {
            // the safety net on full saves: anyone who worked lately is written even if nobody marked them
            if (w.dirty || (full && w.lastWork > recent)) {
                changed.add(w);
            }
        }
        if (changed.isEmpty() && deleted.isEmpty()) {
            return;
        }
        List<Row> rows = new ArrayList<>(changed.size());
        for (Worker w : changed) {
            w.dirty = false;
            Location h = w.home();
            rows.add(new Row(w.id().toString(), w.owner().toString(), w.type().name(), w.worldName(),
                    h == null ? w.x() : h.getX(), h == null ? w.y() : h.getY(), h == null ? w.z() : h.getZ(),
                    h == null ? w.yaw() : h.getYaw(), w.name, w.level, w.paused, w.jobs, Math.round(w.wages * 100),
                    w.recipe, w.hired, w.awaySince, w.knockedUntil,
                    dev.kushcraft.storage.ItemCodec.copy(w.satchel.getStorageContents())));
        }
        List<String> gone = new ArrayList<>();
        deleted.forEach(id -> gone.add(id.toString()));
        Set<UUID> goneIds = new HashSet<>(deleted);
        deleted.clear();
        b.write(c -> {
            if (!rows.isEmpty()) {
                try (java.sql.PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO workers(id, owner, type, world, x, y, z, yaw, name, level, paused, jobs, wages, recipe,
                          hired, away_since, knocked_until, satchel) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        ON CONFLICT(id) DO UPDATE SET owner=excluded.owner, type=excluded.type, world=excluded.world,
                          x=excluded.x, y=excluded.y, z=excluded.z, yaw=excluded.yaw, name=excluded.name,
                          level=excluded.level, paused=excluded.paused, jobs=excluded.jobs, wages=excluded.wages,
                          recipe=excluded.recipe, hired=excluded.hired, away_since=excluded.away_since,
                          knocked_until=excluded.knocked_until, satchel=excluded.satchel""")) {
                    for (Row r : rows) {
                        ps.setString(1, r.id());
                        ps.setString(2, r.owner());
                        ps.setString(3, r.type());
                        ps.setString(4, r.world());
                        ps.setDouble(5, r.x());
                        ps.setDouble(6, r.y());
                        ps.setDouble(7, r.z());
                        ps.setDouble(8, r.yaw());
                        ps.setString(9, r.name());
                        ps.setInt(10, r.level());
                        ps.setInt(11, r.paused() ? 1 : 0);
                        ps.setInt(12, r.jobs());
                        ps.setLong(13, r.wages());
                        ps.setString(14, r.recipe());
                        ps.setLong(15, r.hired());
                        ps.setLong(16, r.awaySince());
                        ps.setLong(17, r.knockedUntil());
                        ps.setBytes(18, dev.kushcraft.storage.ItemCodec.encode(r.satchel()));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            if (!gone.isEmpty()) {
                try (java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM workers WHERE id=?")) {
                    for (String id : gone) {
                        ps.setString(1, id);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        });
        b.onFailure(() -> {
            changed.forEach(w -> w.dirty = true);
            for (UUID id : goneIds) {
                if (!workers.containsKey(id)) {
                    deleted.add(id);
                }
            }
        });
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        for (World w : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk c : w.getLoadedChunks()) {
                chunkLoaded(w, c.getX(), c.getZ());
            }
        }
    }

    /** Takes every mannequin out of the world (shutdown, reset). The data stays. */
    public void shutdown() {
        for (Worker w : workers.values()) {
            despawn(w);
        }
    }

    private void add(Worker w) {
        overLimitKnown = false;
        workers.put(w.id(), w);
        list = workers.values().toArray(new Worker[0]);
        crewCache.clear();
        byChunk.computeIfAbsent(w.chunkId(), k -> new HashSet<>()).add(w.id());
    }

    private void forget(Worker w) {
        overLimitKnown = false;
        workers.remove(w.id());
        list = workers.values().toArray(new Worker[0]);
        crewCache.clear();
        chestCache.remove(w.id());
        Set<UUID> set = byChunk.get(w.chunkId());
        if (set != null) {
            set.remove(w.id());
            if (set.isEmpty()) {
                byChunk.remove(w.chunkId());
            }
        }
        deleted.add(w.id());
    }

    // ------------------------------------------------------------------
    // lookups
    // ------------------------------------------------------------------

    public Collection<Worker> all() {
        return workers.values();
    }

    public Worker get(UUID id) {
        return workers.get(id);
    }

    public List<Worker> of(UUID owner) {
        List<Worker> out = new ArrayList<>();
        for (Worker w : workers.values()) {
            if (w.owner().equals(owner)) {
                out.add(w);
            }
        }
        return out;
    }

    public Worker fromEntity(Entity e) {
        Worker w = byEntity.get(e.getUniqueId());
        if (w != null) {
            return w;
        }
        String id = e.getPersistentDataContainer().get(Keys.WORKER, PersistentDataType.STRING);
        try {
            return id == null ? null : workers.get(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Average and slowest time a tick spent on workers (ms), since the last reset. */
    public double[] timing() {
        return new double[]{busyTicks == 0 ? 0 : busyNanos / 1e6 / busyTicks, slowestNanos / 1e6};
    }

    public void resetTiming() {
        busyNanos = 0;
        busyTicks = 0;
        slowestNanos = 0;
    }

    // ------------------------------------------------------------------
    // limits: rank slots, workers per chunk
    // ------------------------------------------------------------------

    /** How many workers this player may have placed: their rank's slots (and workers.max-per-player if set). */
    public int limit(UUID owner) {
        int slots = plugin.ranks().workerSlots(owner);
        return maxPerPlayer() > 0 ? Math.min(slots, maxPerPlayer()) : slots;
    }

    public int maxPerChunk() {
        return Math.max(1, plugin.getConfig().getInt("workers.max-per-chunk", 6));
    }

    /** Workers past their owner's limit (oldest hires keep working), worked out when it can change. */
    private final Set<UUID> overLimit = new HashSet<>();
    private boolean overLimitKnown;

    public boolean overLimit(Worker w) {
        if (!overLimitKnown) {
            overLimit.clear();
            Map<UUID, List<Worker>> byOwner = new HashMap<>();
            for (Worker o : workers.values()) {
                byOwner.computeIfAbsent(o.owner(), k -> new ArrayList<>()).add(o);
            }
            byOwner.forEach((owner, list) -> {
                int limit = limit(owner);
                Player p = Bukkit.getPlayer(owner);
                if (p != null && p.hasPermission("kushcraft.workers.unlimited")) {
                    return;
                }
                list.sort(java.util.Comparator.comparingLong(Worker::hired));
                for (int i = limit; i < list.size(); i++) {
                    overLimit.add(list.get(i).id());
                }
            });
            overLimitKnown = true;
        }
        return overLimit.contains(w.id());
    }

    /** A rank changed (rank-up, admin, reset, config reload): the limits are worked out again. */
    public void slotsChanged(UUID owner) {
        overLimitKnown = false;
    }

    /** True when one of the owner's Farmhands looks after this plant (their catch-up grows it). */
    public boolean tended(Plant p) {
        for (Worker w : workers.values()) {
            if (w.type() == WorkerType.FARMHAND && w.owner().equals(p.owner()) && near(p.key(), w, radius(w))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // raids (pvp/WorkerRaids decides who may hit; this keeps the worker's side)
    // ------------------------------------------------------------------

    /** Takes health off a worker; returns what's left. */
    public double hurt(Worker w, double damage, double max) {
        if (w.health < 0 || w.health > max) {
            w.health = max;
        }
        w.health -= Math.max(0, damage);
        return w.health;
    }

    /** Takes everything out of a worker's satchel (a knock-out drops it). */
    public List<ItemStack> empty(Worker w) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack it : w.satchel.getStorageContents()) {
            if (it != null && !it.getType().isAir()) {
                out.add(it.clone());
            }
        }
        w.satchel.clear();
        touch(w, true);
        return out;
    }

    /** Knocked out until then: no work, lying down. */
    public void knockOut(Worker w, long until) {
        w.knockedUntil = until;
        w.health = -1;
        w.steps.clear();
        w.current = null;
        w.status = "Knocked out";
        touch(w, true);
        Entity e = entity(w);
        Location home = w.home();
        if (e != null && home != null) {
            w.pos = home.clone();
            e.teleport(home);
            try {
                e.setPose(org.bukkit.entity.Pose.SLEEPING, true);
            } catch (RuntimeException ignored) {
                // this entity can't lie down: they just stand there
            }
        }
        nameplate(w);
    }

    /** Back on their feet when the knock-out is over. */
    private void wakeUp(Worker w) {
        w.knockedUntil = 0;
        touch(w, true);
        Entity e = entity(w);
        if (e != null) {
            try {
                e.setPose(org.bukkit.entity.Pose.STANDING, false);
            } catch (RuntimeException ignored) {
                // never lay down
            }
        }
        w.status = "Back on their feet";
        nameplate(w);
    }

    // ------------------------------------------------------------------
    // work while nobody is around: caught up when their area loads again
    // ------------------------------------------------------------------

    /** One crew's catch-up, done a few steps per tick. */
    private final class CatchUp {
        final UUID owner;
        final List<Worker> crew;
        final List<Plant> plants = new ArrayList<>();
        final Map<Plant, Double> growth = new HashMap<>();
        final List<Machine> labs = new ArrayList<>();
        final double awaySeconds;
        int steps;
        int done;
        int jobs;
        final double before;

        CatchUp(UUID owner, List<Worker> crew, double awaySeconds, double effectiveSeconds) {
            this.owner = owner;
            this.crew = crew;
            this.awaySeconds = awaySeconds;
            this.steps = (int) Math.ceil(effectiveSeconds / STEP);
            this.before = plugin.economy().balance(owner);
            Set<Plant> seen = new HashSet<>();
            Set<Machine> labSeen = new HashSet<>();
            for (Worker w : crew) {
                if (w.type() == WorkerType.FARMHAND) {
                    for (Plant p : plantsNear(w, radius(w))) {
                        if (owner.equals(p.owner()) && !p.wild() && seen.add(p)) {
                            plants.add(p);
                            growth.put(p, plugin.plants().awayMultiplier(p));
                        }
                    }
                }
                if (w.type() == WorkerType.DRYER || w.type() == WorkerType.COOK) {
                    for (Machine m : labsNear(w, radius(w))) {
                        if (labSeen.add(m)) {
                            labs.add(m);
                        }
                    }
                }
            }
        }
    }

    /** Seconds of (effective) work time per catch-up step. */
    private static final double STEP = 60;
    private final java.util.ArrayDeque<CatchUp> catchUps = new java.util.ArrayDeque<>();

    public boolean awayEnabled() {
        return plugin.getConfig().getBoolean("workers.away.enabled", true);
    }

    private double awayRate() {
        return Math.max(0, plugin.getConfig().getDouble("workers.away.rate", 0.5));
    }

    private long awayMaxMillis() {
        return (long) (Math.max(0, plugin.getConfig().getDouble("workers.away.max-hours", 12)) * 3_600_000L);
    }

    /** True while this worker's crew is catching up (they don't do live work meanwhile). */
    public boolean catchingUp(Worker w) {
        for (CatchUp c : catchUps) {
            if (c.crew.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** Every tick: start catch-ups for crews whose area is back, then work on them within the budget. */
    private void catchUpTick() {
        long now = System.currentTimeMillis();
        if (ticks % 20 == 0) {
            for (Worker w : list) {
                if (w.pendingAway > 0 && w.pos != null && now - w.loadedAt > 2_000L) {
                    startCatchUp(w);
                }
            }
        }
        if (catchUps.isEmpty()) {
            return;
        }
        long budget = (long) (Math.max(0.5, plugin.getConfig().getDouble("workers.away.budget-ms", 3)) * 1_000_000L);
        long start = System.nanoTime();
        while (!catchUps.isEmpty() && System.nanoTime() - start < budget) {
            CatchUp c = catchUps.peek();
            if (c.done >= c.steps || c.crew.stream().anyMatch(w -> w.pos == null)) {
                catchUps.poll();
                finish(c);
                continue;
            }
            step(c);
        }
    }

    private void startCatchUp(Worker first) {
        List<Worker> crew = new ArrayList<>();
        crew.add(first);
        for (Worker o : crew(first)) {
            if (o.pos != null) {
                crew.add(o);
            }
        }
        long away = 0;
        for (Worker w : crew) {
            away = Math.max(away, w.pendingAway);
            w.pendingAway = 0;
        }
        crew.removeIf(w -> w.paused || w.knockedOut() || overLimit(w));
        if (crew.isEmpty() || !awayEnabled() || !enabled()) {
            return;
        }
        crew.sort(java.util.Comparator.comparingInt(w -> w.type().ordinal())); // farm, dry, cook, sell
        double effective = Math.min(away, awayMaxMillis()) / 1000.0 * awayRate();
        if (effective < STEP) {
            return;
        }
        catchUps.add(new CatchUp(first.owner(), crew, away / 1000.0, effective));
        for (Worker w : crew) {
            w.status = "Catching up on the time you were away...";
        }
    }

    /** One step: the plants grow and the labs cook for STEP seconds, then everyone works until there's nothing to do. */
    private void step(CatchUp c) {
        for (Plant p : c.plants) {
            if (plugin.plants().at(p.key()) == p) {
                plugin.plants().advance(p, STEP, c.growth.getOrDefault(p, 0.0));
            }
        }
        // plants a Farmhand replanted this step are new objects: follow them
        for (int i = 0; i < c.plants.size(); i++) {
            Plant p = c.plants.get(i);
            Plant now = plugin.plants().at(p.key());
            if (now != p && now != null && c.owner.equals(now.owner())) {
                c.plants.set(i, now);
                c.growth.put(now, c.growth.getOrDefault(p, plugin.plants().awayMultiplier(now)));
            }
        }
        for (Machine m : c.labs) {
            m.shift((long) (STEP * 1000));
        }
        for (Worker w : c.crew) {
            int max = (int) Math.ceil(STEP / (restSeconds(w) + 3.0));
            for (int i = 0; i < max; i++) {
                if (!workOnce(w)) {
                    break;
                }
                c.jobs++;
            }
        }
        c.done++;
    }

    private void finish(CatchUp c) {
        double earned = plugin.economy().balance(c.owner) - c.before;
        String hours = dev.kushcraft.util.Text.duration((long) (c.awaySeconds * 1000));
        for (Worker w : c.crew) {
            w.status = "Caught up";
            touch(w, true);
        }
        if (c.jobs == 0) {
            return;
        }
        plugin.economy().ledger().log(c.owner, dev.kushcraft.economy.Tx.WORKER_AWAY, 0, null, c.crew.size() + " workers",
                "caught up " + hours + " away (" + c.done + " of " + c.steps + " steps): " + c.jobs + " jobs, money "
                        + (earned >= 0 ? "+" : "") + plugin.economy().format(earned));
        Player p = Bukkit.getPlayer(c.owner);
        if (p != null) {
            p.sendMessage(Text.msg("<gray>While nobody was around (" + hours + "), your crew of " + c.crew.size()
                    + " did <white>" + c.jobs + "</white> jobs" + (Math.abs(earned) >= 0.01 ? " <gray>(money "
                    + (earned >= 0 ? "<green>+" : "<red>") + plugin.economy().format(earned) + "<gray>)" : "") + "."));
        }
    }

    /**
     * Self test: as if this worker's crew had been alone for awayMillis, caught up right now (no
     * budget). Returns how many jobs they did.
     */
    public int catchUpNow(Worker w, long awayMillis) {
        w.pendingAway = awayMillis;
        int before = catchUps.size();
        startCatchUp(w);
        if (catchUps.size() == before) {
            return 0;
        }
        CatchUp c = catchUps.pollLast();
        while (c.done < c.steps) {
            step(c);
        }
        finish(c);
        return c.jobs;
    }

    /** Stops catch-ups (shutdown, reset): what wasn't done yet is skipped. */
    public void stopping() {
        catchUps.clear();
    }

    // ------------------------------------------------------------------
    // hiring, upgrading, dismissing
    // ------------------------------------------------------------------

    /** Right-click a block with a worker contract: they move in on top of it. */
    public boolean hire(Player p, ItemStack item, Block clicked, BlockFace face) {
        WorkerType type = WorkerType.of(Items.type(item));
        if (type == null || clicked == null) {
            return false;
        }
        if (!enabled()) {
            p.sendActionBar(Text.mm("<red>Workers are turned off on this server."));
            return false;
        }
        if (face != BlockFace.UP) {
            p.sendActionBar(Text.mm("<yellow>Click the <white>top</white> of a block to put them there."));
            return false;
        }
        Block spot = clicked.getRelative(BlockFace.UP);
        if (!spot.isPassable() || !spot.getRelative(BlockFace.UP).isPassable()
                || plugin.plants().at(BlockKey.of(spot)) != null || plugin.machines().at(spot) != null) {
            p.sendActionBar(Text.mm("<red>There is no room to stand here."));
            return false;
        }
        if (!Protection.canBuild(p, spot)) {
            p.sendActionBar(Text.mm("<red>You can't hire anyone here."));
            return false;
        }
        if (!plugin.rates().allow(p.getUniqueId(), "hire", 2_000L)) {
            p.sendActionBar(Text.mm("<gray>One worker at a time."));
            return false;
        }
        // the cap is checked and the worker made in the same tick: nothing can slip in between
        int have = of(p.getUniqueId()).size();
        int limit = limit(p.getUniqueId());
        if (have >= limit && !p.hasPermission("kushcraft.workers.unlimited")) {
            p.sendActionBar(Text.mm("<red>Your rank (" + plugin.ranks().of(p.getUniqueId()).colored()
                    + "<red>) allows " + limit + " worker" + (limit == 1 ? "" : "s") + ". <gray>/rankup for more."));
            return false;
        }
        Set<UUID> here = byChunk.get(BlockKey.chunkId(spot.getWorld().getName(), spot.getX() >> 4, spot.getZ() >> 4));
        if (here != null && here.size() >= maxPerChunk()) {
            p.sendActionBar(Text.mm("<red>This chunk already has " + here.size() + " workers. <gray>Spread them out a little."));
            return false;
        }
        Location home = spot.getLocation().add(0.5, 0, 0.5);
        home.setYaw(Math.round((p.getLocation().getYaw() + 180) / 90f) * 90f);
        Worker w = hireAt(home, type, p.getUniqueId(), Items.level(item));
        if (p.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
            plugin.persistence().took(p); // the contract left their inventory for a worker in the database
        }
        plugin.economy().ledger().log(p.getUniqueId(), dev.kushcraft.economy.Tx.WORKER_HIRE, 0, null, w.id().toString(),
                type.display() + " " + w.name() + " (level " + w.level() + ") at " + home.getBlockX() + " " + home.getBlockY()
                        + " " + home.getBlockZ() + ", " + (have + 1) + "/" + limit + " slots");
        p.swingMainHand();
        p.sendMessage(Text.msg(type.colored() + " " + Text.escape(w.name) + " <gray>started working for you. "
                + type.job() + " <dark_gray>(Right-click them for their satchel.)"));
        plugin.awards().hired(p, of(p.getUniqueId()).size());
        if (type == WorkerType.COOK) {
            plugin.awards().hiredCook(p);
        }
        checkChain(p, w);
        return true;
    }

    /** The Assembly Line award: a Farmhand, Dryer, Cook and Runner in one crew. */
    private void checkChain(Player p, Worker w) {
        java.util.EnumSet<WorkerType> types = java.util.EnumSet.of(w.type());
        for (Worker o : crew(w)) {
            types.add(o.type());
        }
        if (types.size() == WorkerType.values().length) {
            plugin.awards().assemblyLine(p);
        }
    }

    /** Creates a worker without any checks (hiring and /kush selftest). */
    public Worker hireAt(Location home, WorkerType type, UUID owner, int level) {
        Worker w = new Worker(UUID.randomUUID(), type, owner, home,
                NAMES[ThreadLocalRandom.current().nextInt(NAMES.length)]);
        w.level = Math.max(1, Math.min(maxLevel(), level));
        w.restTicks = 2;
        add(w);
        w.hired = System.currentTimeMillis();
        spawn(w);
        World world = home.getWorld();
        world.playSound(home, "minecraft:entity.villager.celebrate", SoundCategory.NEUTRAL, 0.8f, 1.2f);
        world.spawnParticle(Particle.HAPPY_VILLAGER, home.clone().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0);
        return w;
    }

    /** Sends a worker home for good: their contract and satchel go to the player. */
    public void dismiss(Worker w, Player p) {
        despawn(w);
        forget(w);
        plugin.economy().ledger().log(w.owner(), dev.kushcraft.economy.Tx.WORKER_DISMISS, 0, null, w.id().toString(),
                w.type().display() + " " + w.name() + (p != null && !p.getUniqueId().equals(w.owner())
                ? " by " + p.getName() : ""));
        overLimit.clear();
        List<ItemStack> back = new ArrayList<>();
        back.add(Items.machine(w.type().item(), w.level()));
        for (ItemStack it : w.satchel.getStorageContents()) {
            if (it != null && !it.getType().isAir()) {
                back.add(it);
            }
        }
        w.satchel.clear();
        if (p != null) {
            InventoryUtil.give(p, back.toArray(new ItemStack[0]));
        } else {
            Location h = w.home();
            if (h != null && w.isLoaded()) {
                for (ItemStack it : back) {
                    h.getWorld().dropItemNaturally(h, it);
                }
            }
        }
    }

    /** Returns an error message, or null when it worked. */
    public String upgrade(Worker w, Player p) {
        List<Double> costs = upgradeCosts();
        if (w.level() - 1 >= costs.size()) {
            return "Already at the top level.";
        }
        double cost = costs.get(w.level() - 1);
        if (!plugin.economy().withdraw(p.getUniqueId(), cost, dev.kushcraft.economy.Tx.WORKER_TRAIN, w.id().toString(),
                w.type().display() + " " + w.name() + " to level " + (w.level() + 1))) {
            return "Training costs " + plugin.economy().format(cost) + ".";
        }
        w.level++;
        touch(w, true);
        chestCache.remove(w.id());
        nameplate(w);
        return null;
    }

    public void rename(Worker w, String name) {
        String clean = name.replaceAll("[^A-Za-z0-9 _'-]", "").trim();
        if (clean.isEmpty()) {
            return;
        }
        w.name = clean.length() > 16 ? clean.substring(0, 16) : clean;
        touch(w, true);
        nameplate(w);
    }

    public void pause(Worker w, boolean paused) {
        w.paused = paused;
        w.status = paused ? "Paused" : "Back to work";
        if (paused) {
            w.steps.clear();
        }
        touch(w, true);
        nameplate(w);
    }

    /** Pauses (or restarts) every worker of a player. Returns how many changed. */
    public int pauseAll(UUID owner, boolean paused) {
        int n = 0;
        for (Worker w : of(owner)) {
            if (w.paused != paused) {
                pause(w, paused);
                n++;
            }
        }
        return n;
    }

    /** Cook: what they make from now on. */
    public void setRecipe(Worker w, LabRecipe r) {
        setJob(w, r == null ? null : r.name());
    }

    /** Cook: a LabRecipe name, Worker.ROLL_JOINT / ROLL_BLUNT, or null. */
    public void setJob(Worker w, String job) {
        w.recipe = job;
        ItemType made = w.product();
        w.status = made == null ? "Pick a drug for them" : "Ready to make " + made.display();
        w.restTicks = 1;
        w.want = null;
        touch(w, true);
        nameplate(w);
    }

    /** Puts items in a worker's satchel; whatever doesn't fit is returned. */
    public List<ItemStack> stash(Worker w, List<ItemStack> items) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack it : items) {
            if (it != null && !it.getType().isAir()) {
                left.addAll(w.satchel.addItem(it).values());
            }
        }
        touch(w, true);
        return left;
    }

    /** Get going right away (after you gave them something). */
    public void hurry(Worker w) {
        w.restTicks = 1;
        w.idleSince = 0;
    }

    // ------------------------------------------------------------------
    // the mannequin
    // ------------------------------------------------------------------

    private void spawn(Worker w) {
        despawn(w);
        Location home = w.home();
        if (home == null || !w.isLoaded()) {
            return;
        }
        w.pos = home.clone();
        if (!mannequins) {
            return;
        }
        try {
            Mannequin m = home.getWorld().spawn(home, Mannequin.class, e -> {
                e.setPersistent(false);
                // not invulnerable: hits must reach the event so raids can see them (all other damage is cancelled)
                e.setInvulnerable(false);
                e.setSilent(true);
                e.setGravity(false);
                e.setImmovable(true);
                e.setCollidable(false);
                e.setRemoveWhenFarAway(false);
                e.getPersistentDataContainer().set(Keys.VISUAL, PersistentDataType.STRING, w.id().toString());
                e.getPersistentDataContainer().set(Keys.WORKER, PersistentDataType.STRING, w.id().toString());
                try {
                    e.setProfile(ResolvableProfile.resolvableProfile()
                            .skinPatch(p -> p.body(Key.key(Keys.PACK_NS, w.type().skin()))
                                    .model(PlayerTextures.SkinModel.CLASSIC))
                            .build());
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Worker skins are not supported here: " + ex);
                }
                e.getEquipment().setHelmet(hat(w.type()));
                e.getEquipment().setItemInMainHand(new ItemStack(w.type().tool()));
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    try {
                        e.getEquipment().setDropChance(slot, 0f);
                    } catch (RuntimeException ignored) {
                        // not every slot exists on every entity
                    }
                }
            });
            w.entityId = m.getUniqueId();
            byEntity.put(m.getUniqueId(), w);
            if (w.knockedOut()) {
                try {
                    m.setPose(org.bukkit.entity.Pose.SLEEPING, true);
                } catch (RuntimeException ignored) {
                    // can't lie down
                }
            }
            nameplate(w);
        } catch (RuntimeException ex) {
            mannequins = false;
            plugin.getLogger().warning("Could not spawn worker mannequins (" + ex + "); workers will work unseen.");
        }
    }

    private static ItemStack hat(WorkerType t) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(Keys.model(t.hat()));
        it.setItemMeta(meta);
        return it;
    }

    private void nameplate(Worker w) {
        Entity e = entity(w);
        if (e == null) {
            return;
        }
        e.customName(Text.mm(w.type().color() + Text.escape(w.name)));
        e.setCustomNameVisible(true);
        if (e instanceof Mannequin m) {
            ItemType made = w.product();
            m.setDescription(Text.mm(w.knockedOut() ? "<red>Knocked out" : w.paused ? "<red>Paused" : "<gray>" + w.type().display()
                    + (made != null ? " <dark_gray>· <aqua>" + made.display() : "")
                    + " <dark_gray>· <gold>Lv " + w.level));
        }
    }

    private Entity entity(Worker w) {
        if (w.entityId == null) {
            return null;
        }
        Entity e = Bukkit.getEntity(w.entityId);
        return e != null && e.isValid() ? e : null;
    }

    private void despawn(Worker w) {
        Entity e = w.entityId == null ? null : Bukkit.getEntity(w.entityId);
        if (e != null) {
            e.remove();
        }
        if (w.entityId != null) {
            byEntity.remove(w.entityId);
        }
        w.entityId = null;
        w.steps.clear();
        w.current = null;
        w.pos = null;
    }

    public void chunkLoaded(World world, int cx, int cz) {
        Set<UUID> ids = byChunk.get(BlockKey.chunkId(world.getName(), cx, cz));
        if (ids == null) {
            return;
        }
        crewCache.clear();
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(ids)) {
            Worker w = workers.get(id);
            if (w != null) {
                w.loadedAt = now;
                if (w.awaySince > 0) {
                    w.pendingAway += Math.max(0, now - w.awaySince);
                    w.awaySince = 0;
                    touch(w, false);
                }
                spawn(w);
            }
        }
    }

    public void chunkUnloaded(World world, int cx, int cz) {
        Set<UUID> ids = byChunk.get(BlockKey.chunkId(world.getName(), cx, cz));
        if (ids == null) {
            return;
        }
        crewCache.clear();
        long now = System.currentTimeMillis();
        for (UUID id : ids) {
            Worker w = workers.get(id);
            if (w != null) {
                despawn(w);
                if (w.awaySince == 0) {
                    w.awaySince = now;
                    touch(w, false);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // working
    // ------------------------------------------------------------------

    private int cursor;

    private void tick() {
        ticks++;
        catchUpTick(); // has its own budget (workers.away.budget-ms)
        long start = System.nanoTime();
        long budget = (long) (Math.max(0.5, plugin.getConfig().getDouble("workers.tick-budget-ms", 5)) * 1_000_000L);
        int n = list.length;
        int first = n == 0 ? 0 : cursor % n;
        for (int k = 0; k < n; k++) {
            // a time budget per tick: whoever doesn't fit goes first next tick (no lag, however many workers)
            if ((k & 7) == 7 && System.nanoTime() - start > budget) {
                cursor = (first + k) % n;
                break;
            }
            Worker w = list[(first + k) % n];
            if (w.pos == null) {
                continue; // not loaded
            }
            // every worker thinks once a second, but not all on the same tick (no lag spikes)
            boolean think = (ticks + phase(w)) % 20 == 0;
            if (think) {
                Entity e = mannequins && w.entityId != null ? entity(w) : null;
                if (mannequins && w.entityId != null && e == null && w.isLoaded()) {
                    spawn(w); // the mannequin got unloaded on a walk: put them back home
                    continue;
                }
                w.watched = e == null || !e.getTrackedBy().isEmpty();
            }
            if (w.busy()) {
                move(w);
            } else if (think) {
                think(w);
            }
        }
        long took = System.nanoTime() - start;
        busyNanos += took;
        busyTicks++;
        slowestNanos = Math.max(slowestNanos, took);
    }

    private static int phase(Worker w) {
        return Math.floorMod(w.id().hashCode(), 20);
    }

    private boolean atHome(Worker w) {
        Location h = w.home();
        return h == null || w.pos == null || w.pos.distanceSquared(h) < 0.01;
    }

    /**
     * Once a second when idle: rest, then look for the next job right where they are. They only
     * walk home after a while with nothing to do (no running back and forth between jobs).
     */
    private void think(Worker w) {
        if (w.knockedUntil > 0) {
            if (w.knockedOut()) {
                w.status = "Knocked out - back in " + Text.duration(w.knockedUntil - System.currentTimeMillis());
                return;
            }
            wakeUp(w);
        }
        if (catchingUp(w)) {
            return;
        }
        if (overLimit(w)) {
            w.status = "<red>Not working: your rank allows " + limit(w.owner()) + " workers (/rankup)";
            goHome(w);
            return;
        }
        if (w.paused) {
            w.status = "Paused";
            goHome(w);
            return;
        }
        if (--w.restTicks > 0) {
            return;
        }
        w.restTicks = restSeconds(w);
        if (plan(w)) {
            w.idleSince = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (w.idleSince == 0) {
            w.idleSince = now;
        } else if (now - w.idleSince > 30_000L) {
            goHome(w);
        }
    }

    private void goHome(Worker w) {
        Location home = w.home();
        if (home != null && !atHome(w) && !w.busy()) {
            w.steps.add(new Worker.Step(home, null, null));
        }
    }

    /** Plans the next job (adds steps). Returns true when there was one. */
    boolean plan(Worker w) {
        if (!enabled()) {
            w.status = "Workers are turned off";
            return false;
        }
        if (!w.isLoaded()) {
            w.status = "Asleep (nobody nearby)";
            return false;
        }
        w.want = null;
        // a satchel getting full: first put the finished work in a chest
        if (w.type() != WorkerType.RUNNER && w.freeSlots() < 10 && planDeposit(w, false)) {
            return true;
        }
        boolean busy = switch (w.type()) {
            case FARMHAND -> planFarmhand(w);
            case DRYER -> planDryer(w);
            case COOK -> planCook(w);
            case RUNNER -> planRunner(w);
        };
        if (busy) {
            return true;
        }
        // nothing else to do: tidy up
        if (w.type() == WorkerType.FARMHAND) {
            compost(w);
        }
        return w.type() != WorkerType.RUNNER && planDeposit(w, true);
    }

    /** One job right away, without walking, keeping the chest and soil caches (the away catch-up). */
    private boolean workOnce(Worker w) {
        if (!plan(w)) {
            return false;
        }
        boolean ok = true;
        while (ok && !w.steps.isEmpty()) {
            Worker.Step s = w.steps.poll();
            ok = s.act() == null || act(s);
        }
        w.steps.clear();
        w.current = null;
        touch(w, false);
        return ok;
    }

    /** Does the next job right away, without walking or waiting (for /kush selftest). */
    public boolean workNow(Worker w) {
        chestCache.remove(w.id());
        soilCache.remove(w.id());
        w.boughtAt = 0;
        if (!plan(w)) {
            return false;
        }
        boolean ok = true;
        while (ok && !w.steps.isEmpty()) {
            Worker.Step s = w.steps.poll();
            ok = s.act() == null || act(s);
        }
        w.steps.clear();
        return ok;
    }

    private boolean canPay(Worker w) {
        double wage = wage(w.type());
        OfflinePlayer o = Bukkit.getOfflinePlayer(w.owner());
        if (wage > 0 && plugin.economy().balance(o) < wage) {
            w.status = "<red>Not paid! You need " + plugin.economy().format(wage) + " for their wage.";
            return false;
        }
        return true;
    }

    private void pay(Worker w) {
        double wage = wage(w.type());
        if (wage > 0 && plugin.economy().frequent(w.owner(), -wage, dev.kushcraft.economy.Tx.WAGES, w.id().toString(),
                w.type().display() + " " + w.name())) {
            w.wages += wage;
        }
        w.jobs++;
        touch(w, false);
    }

    private boolean near(BlockKey k, Worker w, double r) {
        Location h = w.home();
        if (h == null || !k.world().equals(w.worldName())) {
            return false;
        }
        double dx = k.x() + 0.5 - h.getX(), dy = k.y() - h.getY(), dz = k.z() + 0.5 - h.getZ();
        return dx * dx + dz * dz <= r * r && Math.abs(dy) <= 6;
    }

    private static double dist2(BlockKey k, Location from) {
        double dx = k.x() + 0.5 - from.getX(), dy = k.y() - from.getY(), dz = k.z() + 0.5 - from.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static ItemStack first(Worker w, Predicate<ItemStack> match) {
        return first(w.satchel, match);
    }

    private static ItemStack first(Inventory inv, Predicate<ItemStack> match) {
        for (ItemStack it : inv.getStorageContents()) {
            if (it != null && !it.getType().isAir() && match.test(it)) {
                return it;
            }
        }
        return null;
    }

    private static int count(Inventory inv, Predicate<ItemStack> match) {
        int n = 0;
        for (ItemStack it : inv.getStorageContents()) {
            if (it != null && !it.getType().isAir() && match.test(it)) {
                n += it.getAmount();
            }
        }
        return n;
    }

    /** Where a worker is right now (home when not loaded). */
    private static Location spot(Worker w) {
        return w.pos != null ? w.pos : w.home();
    }

    // ------------------------------------------------------------------
    // the crew and the chests
    // ------------------------------------------------------------------

    /** How far apart one player's workers can be and still hand things to each other (blocks). */
    public int chainRadius() {
        return Math.max(4, plugin.getConfig().getInt("workers.chain-radius", 32));
    }

    /** The owner's other workers within chain-radius (worked out once a second). */
    public List<Worker> crew(Worker w) {
        long sec = ticks / 20;
        if (sec != crewAt) {
            crewCache.clear();
            crewAt = sec;
        }
        return crewCache.computeIfAbsent(w.id(), k -> findCrew(w));
    }

    private List<Worker> findCrew(Worker w) {
        List<Worker> out = new ArrayList<>();
        Location h = w.home();
        if (h == null) {
            return out;
        }
        double r2 = (double) chainRadius() * chainRadius();
        for (Worker o : workers.values()) {
            if (o == w || !o.owner().equals(w.owner()) || !o.worldName().equals(w.worldName()) || !o.isLoaded()) {
                continue;
            }
            Location oh = o.home();
            if (oh != null && oh.distanceSquared(h) <= r2) {
                out.add(o);
            }
        }
        return out;
    }

    private record ChestList(long made, List<Block> blocks) {
    }

    static boolean isChest(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL;
    }

    /** The chest's inventory right now (both halves of a double chest), or null when it's gone. */
    private static Inventory inventoryOf(Block b) {
        return b != null && isChest(b.getType()) && b.getState(false) instanceof Container c ? c.getInventory() : null;
    }

    /** A chest a worker may use: one their owner placed, or an old one nobody is marked on. */
    private static boolean usable(BlockState st, UUID owner) {
        if (!(st instanceof TileState t)) {
            return false;
        }
        String placer = t.getPersistentDataContainer().get(Keys.PLACER, PersistentDataType.STRING);
        return placer == null || placer.equals(owner.toString());
    }

    /**
     * Every chest and barrel of the owner around a worker (their work radius; a Runner's whole crew
     * area), nearest first. Found through the chunks' block entities (no block-by-block search) and
     * kept for 20 seconds - placing or breaking a chest nearby refreshes it right away.
     */
    public List<Block> chests(Worker w) {
        long now = System.currentTimeMillis();
        ChestList cached = chestCache.get(w.id());
        if (cached == null || now - cached.made() > 20_000L) {
            cached = new ChestList(now, findChests(w));
            chestCache.put(w.id(), cached);
        }
        List<Block> out = new ArrayList<>(cached.blocks().size());
        for (Block b : cached.blocks()) {
            if (isChest(b.getType())) {
                out.add(b);
            }
        }
        Location from = spot(w);
        if (from != null) {
            out.sort(java.util.Comparator.comparingDouble(b -> dist2(BlockKey.of(b), from)));
        }
        return out;
    }

    private List<Block> findChests(Worker w) {
        List<Block> out = new ArrayList<>();
        Location h = w.home();
        if (h == null || !w.isLoaded()) {
            return out;
        }
        World world = h.getWorld();
        int r = chestRadius(w);
        int hx = h.getBlockX(), hy = h.getBlockY(), hz = h.getBlockZ();
        Set<String> seen = new HashSet<>();
        for (int cx = (hx - r) >> 4; cx <= (hx + r) >> 4; cx++) {
            for (int cz = (hz - r) >> 4; cz <= (hz + r) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                for (BlockState st : world.getChunkAt(cx, cz).getTileEntities(b -> isChest(b.getType()), false)) {
                    int dx = st.getX() - hx, dz = st.getZ() - hz, dy = st.getY() - hy;
                    if (dx * dx + dz * dz > r * r || Math.abs(dy) > 6 || !usable(st, w.owner())
                            || !(st instanceof Container c)) {
                        continue;
                    }
                    // both halves of a double chest are one inventory
                    Location l = c.getInventory().getLocation();
                    String key = l == null ? st.getX() + "," + st.getY() + "," + st.getZ()
                            : l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
                    if (seen.add(key)) {
                        out.add(st.getBlock());
                    }
                }
            }
        }
        return out;
    }

    /** Chests changed near here: the workers around forget their chest lists. */
    public void chestsChanged(Block b) {
        for (Worker w : list) {
            Location h = w.home();
            if (h != null && h.getWorld().equals(b.getWorld())) {
                int r = chestRadius(w) + 2;
                if (Math.abs(h.getBlockX() - b.getX()) <= r && Math.abs(h.getBlockZ() - b.getZ()) <= r) {
                    chestCache.remove(w.id());
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // what workers use, make and sell
    // ------------------------------------------------------------------

    /** Things a worker needs for their own job (they never hand these on). */
    public boolean uses(Worker w, ItemStack it) {
        ItemType t = Items.type(it);
        return switch (w.type()) {
            case FARMHAND -> t == ItemType.FERTILIZER || PlantManager.kindOf(t) != null;
            case DRYER -> t == ItemType.BUD_FRESH;
            case COOK -> cookUses(w, it);
            case RUNNER -> false;
        };
    }

    private static boolean cookUses(Worker w, ItemStack it) {
        ItemType roll = w.rolls();
        if (roll != null) {
            ItemType t = Items.type(it);
            return t == ItemType.BUD_DRIED || t == (roll == ItemType.JOINT ? ItemType.ROLLING_PAPERS : ItemType.BLUNT_WRAP);
        }
        LabRecipe r = w.recipe();
        if (r == null) {
            return false;
        }
        for (LabRecipe.Ingredient ing : r.ingredients()) {
            if (matcher(ing).test(it)) {
                return true;
            }
        }
        // empty bottles get filled with water for the next batch
        return it.getType() == Material.GLASS_BOTTLE && !Items.isCustom(it) && needsWater(r);
    }

    private static boolean needsWater(LabRecipe r) {
        return r != null && r.ingredients().stream().anyMatch(i -> i.vanilla() == Material.POTION);
    }

    /** Matches an ingredient (a strain ingredient: any strain of that item). */
    private static Predicate<ItemStack> matcher(LabRecipe.Ingredient ing) {
        return ing.strainSource() ? it -> Items.type(it) == ing.custom() && Items.strain(it) != null : ing::matches;
    }

    /** What a worker makes: it goes in a chest, the next worker takes it or the Runner sells it. */
    public boolean produces(Worker w, ItemStack it) {
        ItemType t = Items.type(it);
        if (t == null) {
            return false;
        }
        return switch (w.type()) {
            case FARMHAND -> t == ItemType.BUD_FRESH || t == ItemType.COCA_LEAVES || t == ItemType.POPPY_POD
                    || t == ItemType.MAGIC_MUSHROOM || t == ItemType.PEYOTE_BUTTON || t == ItemType.ERGOT;
            case DRYER -> t == ItemType.BUD_DRIED;
            case COOK -> t == w.product() || LabRecipe.making(t) != null;
            case RUNNER -> false;
        };
    }

    /** Empty buckets and bottles a Cook doesn't need: they go in a chest. */
    private boolean junk(Worker w, ItemStack it) {
        return w.type() == WorkerType.COOK && !Items.isCustom(it) && !uses(w, it)
                && (it.getType() == Material.GLASS_BOTTLE || it.getType() == Material.BUCKET);
    }

    /** What the dealer buys (never seeds). */
    private boolean sellable(ItemStack it) {
        return it != null && !it.getType().isAir() && PlantManager.kindOf(Items.type(it)) == null
                && plugin.shop().sellPrice(it) > 0;
    }

    /** Something nobody in the crew (nor the worker themselves) uses. */
    private boolean nobodyUses(List<Worker> crew, Worker self, ItemStack it) {
        if (self != null && uses(self, it)) {
            return false;
        }
        for (Worker o : crew) {
            if (uses(o, it)) {
                return false;
            }
        }
        return true;
    }

    /** Moves matching items from one inventory to another, at most max; returns how many moved. */
    private int move(Inventory from, Inventory to, Predicate<ItemStack> match, int max) {
        int moved = 0;
        ItemStack[] items = from.getStorageContents();
        for (int i = 0; i < items.length && moved < max; i++) {
            ItemStack it = items[i];
            if (it == null || it.getType().isAir() || !match.test(it)) {
                continue;
            }
            int want = Math.min(it.getAmount(), max - moved);
            ItemStack part = it.clone();
            part.setAmount(want);
            Map<Integer, ItemStack> left = to.addItem(part);
            int kept = left.isEmpty() ? 0 : left.values().iterator().next().getAmount();
            int done = want - kept;
            moved += done;
            ItemStack rest = it.clone();
            rest.setAmount(it.getAmount() - done);
            from.setItem(i, rest.getAmount() <= 0 ? null : rest);
            if (kept > 0) {
                break; // the other side is full
            }
        }
        if (moved > 0) {
            touched(from);
            touched(to);
        }
        return moved;
    }

    // ------------------------------------------------------------------
    // fetching: several chests and workers in one trip
    // ------------------------------------------------------------------

    /** Somewhere to take things from: another worker's satchel (what they don't use), or a chest. */
    private record Source(Worker worker, Block chest) {
        Inventory inv() {
            return worker != null ? worker.satchel : inventoryOf(chest);
        }

        BlockKey where() {
            return worker != null ? BlockKey.of(spot(worker)) : BlockKey.of(chest);
        }

        String name() {
            return worker != null ? worker.name() : "a chest";
        }
    }

    /** One thing a worker is after: what matches, how many, and a name for the status line. */
    private record Need(Predicate<ItemStack> match, int amount, String name) {
    }

    /** Chests around a worker (nearest first), then the satchels of the crew. */
    private List<Source> sources(Worker w, boolean fromWorkers) {
        List<Source> out = new ArrayList<>();
        for (Block c : chests(w)) {
            out.add(new Source(null, c));
        }
        if (fromWorkers) {
            for (Worker o : crew(w)) {
                if (o.type() != WorkerType.RUNNER) {
                    out.add(new Source(o, null));
                }
            }
        }
        return out;
    }

    /** From a source: what matches (from a worker only what they don't use themselves). */
    private Predicate<ItemStack> from(Source src, Predicate<ItemStack> want) {
        return src.worker() == null ? want : it -> want.test(it) && !uses(src.worker(), it);
    }

    /**
     * Plans one trip that gathers what a worker needs from as many chests (and crew satchels) as it
     * takes, nearest first. Each stop takes only what's still missing. False when nobody has any.
     */
    private boolean gather(Worker w, List<Need> needs, boolean fromWorkers) {
        if (w.freeSlots() == 0 || needs.isEmpty()) {
            return false;
        }
        int[] left = new int[needs.size()];
        for (int i = 0; i < needs.size(); i++) {
            left[i] = needs.get(i).amount();
        }
        int stops = 0;
        String first = null;
        for (Source src : sources(w, fromWorkers)) {
            Inventory inv = src.inv();
            if (inv == null) {
                continue;
            }
            boolean useful = false;
            for (int i = 0; i < needs.size(); i++) {
                if (left[i] <= 0) {
                    continue;
                }
                int have = count(inv, from(src, needs.get(i).match()));
                if (have > 0) {
                    useful = true;
                    left[i] -= have;
                }
            }
            if (!useful) {
                continue;
            }
            stops++;
            if (first == null) {
                first = src.name();
            }
            // at the stop: take what's still missing (the counts may have changed since)
            go(w, src.where(), () -> {
                Inventory now = src.inv();
                if (now == null) {
                    return true; // the chest is gone: on to the next stop
                }
                int got = 0;
                for (Need n : needs) {
                    int missing = n.amount() - count(w.satchel, n.match());
                    if (missing > 0) {
                        got += move(now, w.satchel, from(src, n.match()), missing);
                    }
                }
                if (got > 0) {
                    w.status = "Got " + got + " items from " + src.name();
                }
                return true;
            });
            boolean all = true;
            for (int l : left) {
                all &= l <= 0;
            }
            if (all || stops >= 6) {
                break;
            }
        }
        if (stops == 0) {
            return false;
        }
        String what = needs.get(0).name() + (needs.size() > 1 ? " and more" : "");
        w.status = "Fetching " + what + " from " + first + (stops > 1 ? " and " + (stops - 1) + " more" : "");
        return true;
    }

    /** True when some chest or crew satchel has matching items (without planning a trip). */
    private boolean available(Worker w, Predicate<ItemStack> want, boolean fromWorkers) {
        for (Source src : sources(w, fromWorkers)) {
            Inventory inv = src.inv();
            if (inv != null && first(inv, from(src, want)) != null) {
                return true;
            }
        }
        return false;
    }

    /** How many of an ingredient a Cook can get: their satchel, your chests near them and the crew's satchels. */
    public int stock(Worker w, LabRecipe.Ingredient ing) {
        Predicate<ItemStack> m = matcher(ing);
        int n = count(w.satchel, m);
        if (!w.isLoaded()) {
            return n;
        }
        for (Source src : sources(w, true)) {
            Inventory inv = src.inv();
            if (inv != null) {
                n += count(inv, from(src, m));
            }
        }
        return n;
    }

    /** True when a worker would buy this ingredient themselves (auto-buy on, and it's sold somewhere). */
    public boolean wouldBuy(Worker w, LabRecipe.Ingredient ing) {
        return autoBuy(w.owner()) && buyIngredient(ing, ing.amount()) != null;
    }

    /** Tells the Runners what this worker is missing (they bring it from chests further away). */
    private void want(Worker w, Predicate<ItemStack> match, String what, int amount) {
        w.want = new Worker.Want(match, what, amount);
    }

    // ------------------------------------------------------------------
    // putting things in chests
    // ------------------------------------------------------------------

    /**
     * Puts what a worker made (and empty bottles) in the nearest chests with room. Unless the
     * satchel is getting full they wait for a few (less walking). force: also a handful.
     */
    private boolean planDeposit(Worker w, boolean force) {
        Predicate<ItemStack> out = it -> (produces(w, it) && !uses(w, it)) || junk(w, it);
        int n = count(w.satchel, out);
        if (n == 0 || (!force && n < 8 && w.freeSlots() >= 10)) {
            return false;
        }
        return putAway(w, out, "their work");
    }

    /** Walks matching things from the satchel into the nearest chests with room (several if needed). */
    private boolean putAway(Worker w, Predicate<ItemStack> what, String name) {
        int stops = 0;
        for (Block c : chests(w)) {
            Inventory inv = inventoryOf(c);
            if (inv == null || (inv.firstEmpty() < 0 && first(w, it -> what.test(it) && fits(inv, it)) == null)) {
                continue;
            }
            go(w, BlockKey.of(c), () -> {
                Inventory now = inventoryOf(c);
                if (now == null) {
                    return true;
                }
                int moved = move(w.satchel, now, what, Integer.MAX_VALUE);
                if (moved > 0) {
                    w.status = "Put " + moved + " items in a chest";
                }
                return true;
            });
            if (++stops >= 3) {
                break;
            }
        }
        if (stops == 0) {
            return false;
        }
        w.status = "Putting " + name + " in a chest";
        return true;
    }

    /** True when something of this stack still fits in the inventory. */
    private static boolean fits(Inventory inv, ItemStack it) {
        for (ItemStack s : inv.getStorageContents()) {
            if (s == null || s.getType().isAir() || (s.isSimilar(it) && s.getAmount() < s.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    /** Does a job where they stand. */
    private void here(Worker w, BooleanSupplier act) {
        Location at = spot(w);
        w.steps.add(new Worker.Step(at == null ? null : at.clone(), null, act));
    }

    // ------------------------------------------------------------------
    // buying: workers buy what they can't find (when their owner lets them)
    // ------------------------------------------------------------------

    /** One purchase: amount of name for cost, and the items it gives. */
    public record Buy(String name, int amount, double cost, java.util.function.Supplier<List<ItemStack>> items,
                      Material traded) {
    }

    /** n of a KushCraft supply from the Shop (fertilizer, solvent, papers, wraps, seeds...), or null. */
    private Buy buyItem(ItemType t, int n) {
        for (Shop.BuyEntry e : plugin.shop().buyEntries()) {
            if (e.type() == t && e.strain() == null && e.amount() > 0 && !t.strainBound()) {
                int packs = Math.max(1, (n + e.amount() - 1) / e.amount());
                return new Buy(t.display(), packs * e.amount(), packs * e.price(), () -> {
                    List<ItemStack> out = new ArrayList<>();
                    for (int i = 0; i < packs; i++) {
                        out.add(plugin.shop().create(e));
                    }
                    return out;
                }, null);
            }
        }
        return null;
    }

    /** n strain seeds of a Shop entry. */
    private Buy buySeeds(Shop.BuyEntry e, int n) {
        int packs = Math.max(1, (n + Math.max(1, e.amount()) - 1) / Math.max(1, e.amount()));
        String name = e.strain() != null ? plugin.strains().getOrDefault(e.strain()).name() + " seeds" : e.type().display();
        return new Buy(name, packs * Math.max(1, e.amount()), packs * e.price(), () -> {
            List<ItemStack> out = new ArrayList<>();
            for (int i = 0; i < packs; i++) {
                out.add(plugin.shop().create(e));
            }
            return out;
        }, null);
    }

    /** n of a vanilla item from Trade (water bottles at the water price), or null when Trade doesn't sell it. */
    private Buy buyVanilla(Material m, String name, int n) {
        if (m == Material.POTION) {
            return new Buy("Water Bottle", n, Math.round(waterPrice() * n * 100) / 100.0, () -> waterBottles(n), null);
        }
        var offer = plugin.exchange().enabled() ? plugin.exchange().offer(m) : null;
        if (offer == null) {
            return null;
        }
        double each = plugin.exchange().buyPrice(offer);
        return new Buy(name, n, Math.round(each * n * 100) / 100.0, () -> {
            List<ItemStack> out = new ArrayList<>();
            ItemStack one = new ItemStack(m);
            for (int left = n; left > 0; left -= one.getMaxStackSize()) {
                ItemStack s = one.clone();
                s.setAmount(Math.min(left, one.getMaxStackSize()));
                out.add(s);
            }
            return out;
        }, m);
    }

    /** What n of a recipe ingredient costs, or null when it can't be bought (buds, things you cook). */
    private Buy buyIngredient(LabRecipe.Ingredient ing, int n) {
        if (n <= 0 || ing.strainSource()) {
            return null;
        }
        if (ing.vanilla() != null) {
            return buyVanilla(ing.vanilla(), ing.name(), n);
        }
        return ing.custom() == null ? null : buyItem(ing.custom(), n);
    }

    /**
     * Buys it with the owner's money, right where they are (no walking to the Shop), when the owner
     * lets their workers buy. At most once every few seconds. True when they bought it.
     */
    private boolean buy(Worker w, Buy b) {
        if (b == null || !autoBuy(w.owner())) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - w.boughtAt < 3_000L) {
            return false;
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(w.owner());
        if (plugin.economy().balance(owner) < b.cost() || !plugin.economy().frequent(owner.getUniqueId(), -b.cost(),
                dev.kushcraft.economy.Tx.WORKER_BUY, w.id().toString(), b.name())) {
            w.status = "<red>Can't buy " + b.name() + ": you need " + plugin.economy().format(b.cost()) + ".";
            return false;
        }
        w.boughtAt = now;
        if (b.traded() != null) {
            plugin.exchange().bought(b.traded(), b.amount());
        }
        List<ItemStack> left = stash(w, b.items().get());
        if (!left.isEmpty()) {
            drop(w, left, BlockKey.of(spot(w)));
        }
        w.status = "Bought " + b.amount() + " " + b.name() + " for " + plugin.economy().format(b.cost());
        Player online = owner.getPlayer();
        if (online != null) {
            online.sendActionBar(Text.mm("<yellow>" + Text.escape(w.name()) + " <gray>bought " + b.amount() + " "
                    + b.name() + ": <gold>-" + plugin.economy().format(b.cost())));
        }
        Location c = spot(w);
        if (c != null) {
            c.getWorld().playSound(c, "minecraft:entity.villager.trade", SoundCategory.NEUTRAL, 0.7f, 1.1f);
        }
        touch(w, true);
        return true;
    }

    private static List<ItemStack> waterBottles(int n) {
        ItemStack water = new ItemStack(Material.POTION);
        water.editMeta(org.bukkit.inventory.meta.PotionMeta.class,
                m -> m.setBasePotionType(org.bukkit.potion.PotionType.WATER));
        List<ItemStack> stacks = new ArrayList<>();
        for (int left = n; left > 0; left -= water.getMaxStackSize()) {
            ItemStack s = water.clone();
            s.setAmount(Math.min(left, water.getMaxStackSize()));
            stacks.add(s);
        }
        return stacks;
    }

    /** Seeds for a Farmhand's empty soil: of the strain (or plant) grown most around them, else the cheapest weed. */
    private Shop.BuyEntry seedsFor(Worker w) {
        Map<String, Integer> grown = new HashMap<>();
        for (Plant p : plantsNear(w, radius(w))) {
            grown.merge(p.kind() == Plant.Kind.CANNABIS ? "S:" + p.strainId() : "K:" + p.kind().name(), 1, Integer::sum);
        }
        Map<Plant.Kind, Boolean> grows = new java.util.EnumMap<>(Plant.Kind.class);
        Shop.BuyEntry best = null;
        int bestN = -1;
        for (Shop.BuyEntry e : plugin.shop().seeds()) {
            Plant.Kind k = PlantManager.kindOf(e.type());
            if (k == null || !grows.computeIfAbsent(k, kk -> !emptySoil(w, kk, 1).isEmpty())) {
                continue; // nowhere to plant it
            }
            Strain s = e.strain() == null ? null : plugin.strains().get(e.strain());
            if (s != null && s.rarity().animated()) {
                continue; // Mythic seeds are your call: buy those yourself
            }
            int n = grown.getOrDefault(k == Plant.Kind.CANNABIS ? "S:" + e.strain() : "K:" + k.name(), 0) * 2
                    + (k == Plant.Kind.CANNABIS ? 1 : 0);
            if (n > bestN || (n == bestN && best != null && e.price() < best.price())) {
                best = e;
                bestN = n;
            }
        }
        return best;
    }

    /** What a worker would buy right now (for their menu): their missing seeds, fertilizer or ingredient. */
    public Buy nextBuy(Worker w) {
        if (!w.isLoaded()) {
            return null;
        }
        if (w.type() == WorkerType.FARMHAND) {
            if (first(w, Workers::plantable) == null && !emptySoil(w, null, 1).isEmpty()) {
                Shop.BuyEntry e = seedsFor(w);
                return e == null ? null : buySeeds(e, Math.min(16, emptySoil(w, null, 16).size()));
            }
            return null;
        }
        if (w.type() == WorkerType.COOK) {
            ItemType roll = w.rolls();
            if (roll != null) {
                ItemType wrap = roll == ItemType.JOINT ? ItemType.ROLLING_PAPERS : ItemType.BLUNT_WRAP;
                return count(w.satchel, it -> Items.type(it) == wrap) == 0 ? buyItem(wrap, 16) : null;
            }
            LabRecipe r = w.recipe();
            LabRecipe.Ingredient missing = r == null ? null : Cooking.missing(w.satchel, r, null, 0);
            return missing == null ? null : buyIngredient(missing, missing.amount() * Cooking.MAX_BATCHES
                    - count(w.satchel, matcher(missing)));
        }
        return null;
    }

    // ---- farmhand ----

    /** The owner's plants within r blocks of a worker's home (looked up by chunk, not one by one). */
    private List<Plant> plantsNear(Worker w, int r) {
        Location h = w.home();
        List<Plant> out = new ArrayList<>();
        if (h == null) {
            return out;
        }
        for (Plant p : plugin.plants().near(w.worldName(), h.getBlockX(), h.getBlockZ(), r + 1)) {
            if (w.owner().equals(p.owner()) && near(p.key(), w, r)) {
                out.add(p);
            }
        }
        return out;
    }

    private static boolean plantable(ItemStack it) {
        Plant.Kind k = it == null || it.getType().isAir() ? null : PlantManager.kindOf(Items.type(it));
        return k != null && (k != Plant.Kind.CANNABIS || Items.strain(it) != null);
    }

    private boolean planFarmhand(Worker w) {
        if (!canPay(w)) {
            return false;
        }
        int r = radius(w);
        Location from = spot(w);
        List<Plant> mine = plantsNear(w, r);
        // 1. harvest ripe plants, a few in one round (closest first, then the closest to that one)
        List<Plant> ripe = new ArrayList<>();
        for (Plant p : mine) {
            if (p.mature() && p.key().isLoaded()) {
                ripe.add(p);
            }
        }
        if (!ripe.isEmpty() && w.freeSlots() >= 3) {
            List<Plant> round = route(ripe, from, Math.min(2 + w.level(), w.freeSlots() - 2));
            w.status = "Harvesting " + round.size() + " plant" + (round.size() > 1 ? "s" : "");
            for (Plant target : round) {
                String what = plantName(target);
                go(w, target.key(), () -> {
                    if (plugin.plants().at(target.key()) != target || !target.mature()) {
                        return true; // someone else picked it: go on with the next one
                    }
                    if (w.freeSlots() == 0 || !canPay(w)) {
                        return false;
                    }
                    List<ItemStack> got = plugin.plants().pick(target, true);
                    drop(w, stash(w, got), target.key());
                    pay(w);
                    w.status = "Harvested " + what;
                    return true;
                });
            }
            return true;
        }
        // 2. plant seeds from the satchel on empty farmland / Planters, a round at a time
        List<Block> empty = emptySoil(w, null, 64);
        if (!empty.isEmpty() && first(w, Workers::plantable) != null) {
            List<Block> round = new ArrayList<>();
            int seeds = count(w.satchel, Workers::plantable);
            for (Block b : empty) {
                if (round.size() >= Math.min(seeds, 3 + 2 * w.level())) {
                    break;
                }
                if (seedFor(w, b) != null) {
                    round.add(b);
                }
            }
            if (!round.isEmpty()) {
                w.status = "Planting " + round.size() + " seed" + (round.size() > 1 ? "s" : "");
                for (Block soilBlock : order(round, from)) {
                    BlockKey spot = BlockKey.of(soilBlock).up();
                    go(w, spot, () -> {
                        Block s = spot.block();
                        if (s == null || !s.getType().isAir() || plugin.plants().at(spot) != null) {
                            return true;
                        }
                        ItemStack seed = seedFor(w, s.getRelative(BlockFace.DOWN));
                        if (seed == null) {
                            return true;
                        }
                        if (!canPay(w)) {
                            return false;
                        }
                        Plant.Kind kind = PlantManager.kindOf(Items.type(seed));
                        Strain strain = kind == Plant.Kind.CANNABIS ? Items.strain(seed) : null;
                        seed.setAmount(seed.getAmount() - 1);
                        touch(w, true);
                        plugin.plants().plantAt(spot, kind, strain, w.owner());
                        Location c = spot.bottomCenter();
                        c.getWorld().playSound(c, "minecraft:item.crop.plant", SoundCategory.BLOCKS, 1f, 1f);
                        pay(w);
                        return true;
                    });
                }
                return true;
            }
        }
        // 3. fertilize growing plants, a few in one round
        ItemStack fert = first(w, it -> Items.type(it) == ItemType.FERTILIZER);
        List<Plant> growing = new ArrayList<>();
        for (Plant p : mine) {
            if (!p.fertilized() && !p.mature() && p.key().isLoaded()) {
                growing.add(p);
            }
        }
        if (fert != null && !growing.isEmpty()) {
            List<Plant> round = route(growing, from, Math.min(fert.getAmount(), 2 + w.level()));
            w.status = "Fertilizing " + round.size() + " plant" + (round.size() > 1 ? "s" : "");
            for (Plant target : round) {
                go(w, target.key(), () -> {
                    ItemStack f = first(w, it -> Items.type(it) == ItemType.FERTILIZER);
                    if (f == null) {
                        return false;
                    }
                    if (plugin.plants().at(target.key()) == target && plugin.plants().fertilize(target)) {
                        f.setAmount(f.getAmount() - 1);
                        touch(w, true);
                    }
                    return true;
                });
            }
            return true;
        }
        // 4. empty farmland and no seeds: from a chest, else they buy some
        if (!empty.isEmpty() && first(w, Workers::plantable) == null) {
            if (gather(w, List.of(new Need(Workers::plantable, Math.min(64, empty.size()), "seeds")), false)) {
                return true;
            }
            Shop.BuyEntry e = seedsFor(w);
            if (e != null && buy(w, buySeeds(e, Math.min(16, empty.size())))) {
                return true;
            }
            want(w, Workers::plantable, "seeds", Math.min(16, empty.size()));
        }
        // 5. plants growing and no fertilizer: from a chest, else they buy some
        if (fert == null && !growing.isEmpty()) {
            Predicate<ItemStack> isFert = it -> Items.type(it) == ItemType.FERTILIZER;
            if (gather(w, List.of(new Need(isFert, 32, "fertilizer")), false)) {
                return true;
            }
            if (buy(w, buyItem(ItemType.FERTILIZER, 16))) {
                return true;
            }
            if (w.want == null) {
                want(w, isFert, "fertilizer", 16);
            }
        }
        if (!ripe.isEmpty()) {
            w.status = chests(w).isEmpty() ? "<red>Satchel full! Put a chest near them (or empty it)."
                    : "<yellow>Satchel full: no room left in the chests near them.";
        } else if (!empty.isEmpty() && first(w, Workers::plantable) == null) {
            if (!w.status.startsWith("<red>Can't buy")) {
                w.status = autoBuy(w.owner()) ? "<yellow>Out of seeds for the empty farmland"
                        : "<yellow>Out of seeds: put some in a chest near them (or switch on auto-buy).";
            }
        } else {
            w.status = "Waiting for your plants to ripen";
        }
        return false;
    }

    /** Seeds in the satchel that grow on this soil: the kind they have the most of. Null when none do. */
    private ItemStack seedFor(Worker w, Block soilBlock) {
        ItemStack best = null;
        for (ItemStack it : w.satchel.getStorageContents()) {
            if (plantable(it) && plugin.plants().isSoil(soilBlock, PlantManager.kindOf(Items.type(it)))
                    && (best == null || it.getAmount() > best.getAmount())) {
                best = it;
            }
        }
        return best;
    }

    /** Up to max plants: the closest one to start, then always the closest to the last one. */
    private List<Plant> route(List<Plant> plants, Location start, int max) {
        List<Plant> left = new ArrayList<>(plants);
        List<Plant> out = new ArrayList<>();
        Location at = start;
        while (!left.isEmpty() && out.size() < max) {
            Plant best = null;
            double bestD = Double.MAX_VALUE;
            for (Plant p : left) {
                double d = dist2(p.key(), at);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
            left.remove(best);
            out.add(best);
            Location c = best.key().center();
            at = c != null ? c : at;
        }
        return out;
    }

    /** Blocks in walking order: the closest one first, then always the closest to the last one. */
    private static List<Block> order(List<Block> blocks, Location start) {
        List<Block> left = new ArrayList<>(blocks);
        List<Block> out = new ArrayList<>();
        double x = start.getX(), z = start.getZ();
        while (!left.isEmpty()) {
            Block best = null;
            double bestD = Double.MAX_VALUE;
            for (Block b : left) {
                double dx = b.getX() + 0.5 - x, dz = b.getZ() + 0.5 - z;
                if (dx * dx + dz * dz < bestD) {
                    bestD = dx * dx + dz * dz;
                    best = b;
                }
            }
            left.remove(best);
            out.add(best);
            x = best.getX() + 0.5;
            z = best.getZ() + 0.5;
        }
        return out;
    }

    /**
     * Spare seeds become fertilizer: when the satchel gets full and there's no chest with room,
     * every common seed past 64 of a kind is composted (4 seeds = 1 fertilizer). Rare and better
     * strains never are.
     */
    public void compost(Worker w) {
        if (w.freeSlots() >= 6) {
            return;
        }
        for (Block c : chests(w)) {
            Inventory inv = inventoryOf(c);
            if (inv != null && inv.firstEmpty() >= 0) {
                return; // there's room in a chest: they put things there instead
            }
        }
        Map<String, Integer> kept = new HashMap<>();
        int spare = 0;
        ItemStack[] items = w.satchel.getStorageContents();
        for (int i = 0; i < items.length; i++) {
            ItemStack it = items[i];
            ItemType t = Items.type(it);
            if (PlantManager.kindOf(t) == null) {
                continue;
            }
            Strain s = Items.strain(it);
            if (s != null && s.rarity().ordinal() >= dev.kushcraft.strains.Rarity.RARE.ordinal()) {
                continue;
            }
            String key = t.name() + ":" + (s == null ? "" : s.id());
            int have = kept.getOrDefault(key, 0);
            int keep = Math.max(0, Math.min(it.getAmount(), 64 - have));
            kept.put(key, have + keep);
            spare += it.getAmount() - keep;
            if (keep <= 0) {
                w.satchel.setItem(i, null);
            } else if (keep < it.getAmount()) {
                ItemStack less = it.clone();
                less.setAmount(keep);
                w.satchel.setItem(i, less);
            }
        }
        if (spare >= 4) {
            drop(w, stash(w, List.of(Items.create(ItemType.FERTILIZER, spare / 4))), BlockKey.of(spot(w)));
            w.status = "Turned " + spare + " spare seeds into fertilizer";
        }
        if (spare > 0) {
            touch(w, false);
        }
    }

    private String plantName(Plant p) {
        return p.kind() == Plant.Kind.CANNABIS ? Text.plain(Text.mm(plugin.strains().getOrDefault(p.strainId()).colored()))
                : p.kind().display();
    }

    /** Empty farmland, or the owner's Planters, near home where this kind (null: anything) can grow, closest first. */
    private List<Block> emptySoil(Worker w, Plant.Kind kind, int max) {
        Location h = w.home();
        int r = radius(w);
        World world = h.getWorld();
        int hx = h.getBlockX(), hy = h.getBlockY(), hz = h.getBlockZ();
        List<Block> found = new ArrayList<>();
        SoilList cached = soilCache.get(w.id());
        long now = System.currentTimeMillis();
        if (cached == null || now - cached.made() > 10_000L) {
            List<Block> all = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r || !world.isChunkLoaded((hx + dx) >> 4, (hz + dz) >> 4)) {
                        continue;
                    }
                    for (int dy = -3; dy <= 1; dy++) {
                        Block soil = world.getBlockAt(hx + dx, hy + dy, hz + dz);
                        Material type = soil.getType();
                        if (type == Material.FARMLAND) {
                            all.add(soil);
                        } else if (type == Material.BARRIER) {
                            Machine m = plugin.machines().at(soil);
                            if (m != null && m.type() == MachineType.PLANTER_BOX && w.owner().equals(m.owner())) {
                                all.add(soil);
                            }
                        }
                    }
                }
            }
            cached = new SoilList(now, all);
            soilCache.put(w.id(), cached);
        }
        for (Block soil : cached.blocks()) {
            Block above = soil.getRelative(BlockFace.UP);
            BlockKey key = BlockKey.of(above);
            if (!above.getType().isAir() || plugin.plants().at(key) != null || plugin.machines().at(key) != null
                    || (kind != null && !plugin.plants().isSoil(soil, kind))) {
                continue;
            }
            found.add(soil);
        }
        Location from = spot(w);
        found.sort(java.util.Comparator.comparingDouble(b -> b.getLocation().distanceSquared(from)));
        return found.size() > max ? new ArrayList<>(found.subList(0, max)) : found;
    }

    /** Empty farmland a Farmhand could plant (for their menu). */
    public int emptyFarmland(Worker w) {
        return w.isLoaded() && w.home() != null ? emptySoil(w, null, Integer.MAX_VALUE).size() : 0;
    }

    private record SoilList(long made, List<Block> blocks) {
    }

    private final Map<UUID, SoilList> soilCache = new HashMap<>();

    // ---- dryer ----

    private List<Machine> labsNear(Worker w, int r) {
        List<Machine> labs = new ArrayList<>();
        Location h = w.home();
        if (h == null) {
            return labs;
        }
        for (Machine m : plugin.machines().near(w.worldName(), h.getBlockX(), h.getBlockZ(), r + 1)) {
            if (m.type() == MachineType.LAB_STATION && w.owner().equals(m.owner()) && near(m.key(), w, r)
                    && m.key().isLoaded()) {
                labs.add(m);
            }
        }
        Location from = spot(w);
        labs.sort(java.util.Comparator.comparingDouble(m -> dist2(m.key(), from)));
        return labs;
    }

    private boolean planDryer(Worker w) {
        int r = radius(w);
        List<Machine> labs = labsNear(w, r);
        if (labs.isEmpty()) {
            w.status = "<red>No Drug Lab of yours within " + r + " blocks.";
            return false;
        }
        // 1. take dry buds off the racks
        for (Machine lab : labs) {
            if (lab.racksDry() > 0) {
                if (w.freeSlots() < lab.racksDry()) {
                    if (planDeposit(w, true)) {
                        return true;
                    }
                    w.status = "<red>Satchel full! Put a chest near them (or empty it).";
                    return false;
                }
                if (!canPay(w)) {
                    return false;
                }
                w.status = "Collecting dry buds";
                go(w, lab.key(), () -> {
                    List<ItemStack> dried = new ArrayList<>();
                    for (Machine.Rack rack : lab.takeDry()) {
                        dried.add(Items.strainItem(ItemType.BUD_DRIED, plugin.strains().getOrDefault(rack.strain()),
                                rack.quality(), rack.amount()));
                    }
                    if (dried.isEmpty()) {
                        return false;
                    }
                    drop(w, stash(w, dried), lab.key());
                    pay(w);
                    w.status = "Collected dry buds";
                    return true;
                });
                return true;
            }
        }
        // 2. hang fresh buds from the satchel
        for (Machine lab : labs) {
            ItemStack fresh = first(w, it -> Items.type(it) == ItemType.BUD_FRESH && Items.strain(it) != null
                    && lab.canHang(Items.strain(it).id(), Items.quality(it)));
            if (fresh != null) {
                if (!canPay(w)) {
                    return false;
                }
                w.status = "Hanging buds to dry";
                go(w, lab.key(), () -> {
                    int hung = hangAll(w, lab);
                    if (hung <= 0) {
                        return false;
                    }
                    pay(w);
                    w.status = "Hung " + hung + " buds to dry";
                    return true;
                });
                return true;
            }
        }
        // 3. fresh buds from the Farmhands' satchels and the chests (as many as there's room for)
        if (first(w, it -> Items.type(it) == ItemType.BUD_FRESH) == null) {
            int room = Math.max(0, w.freeSlots() - 6) * 64;
            Predicate<ItemStack> fresh = it -> Items.type(it) == ItemType.BUD_FRESH && Items.strain(it) != null;
            if (room > 0 && gather(w, List.of(new Need(fresh, Math.min(room, 5 * Machine.RACK_CAPACITY), "fresh buds")),
                    true)) {
                return true;
            }
            want(w, fresh, "fresh buds", 64);
        }
        w.status = first(w, it -> Items.type(it) == ItemType.BUD_FRESH) != null ? "Waiting for a free rack"
                : "Waiting for fresh buds";
        return false;
    }

    // ---- cook ----

    private boolean planCook(Worker w) {
        ItemType roll = w.rolls();
        if (roll != null) {
            return planRoll(w, roll);
        }
        LabRecipe recipe = w.recipe();
        if (recipe == null) {
            w.status = "<yellow>Pick a drug for them to make (button at the top).";
            return false;
        }
        int r = radius(w);
        List<Machine> labs = labsNear(w, r);
        if (labs.isEmpty()) {
            w.status = "<red>No Drug Lab of yours within " + r + " blocks.";
            return false;
        }
        // 1. collect finished batches: their own, and any other batch left in a lab (else that lab
        //    stays busy forever - the old "my Cook stopped" bug) unless another Cook of yours makes it
        List<Worker> crew = crew(w);
        for (Machine lab : labs) {
            if (!lab.busy() || !lab.jobDone() || lab.output() == null) {
                continue;
            }
            String job = lab.job();
            boolean mine = recipe.name().equals(job);
            boolean someoneElse = !mine && crew.stream().anyMatch(o -> o.type() == WorkerType.COOK && !o.paused()
                    && job != null && job.equals(o.recipe));
            if (someoneElse) {
                continue;
            }
            if (w.freeSlots() < 1) {
                if (planDeposit(w, true)) {
                    return true;
                }
                w.status = "<red>Satchel full! Put a chest near them (or empty it).";
                return false;
            }
            w.status = "Collecting a batch";
            go(w, lab.key(), () -> {
                if (!lab.busy() || !lab.jobDone() || lab.output() == null) {
                    return false;
                }
                ItemStack out = lab.output().clone();
                lab.clearJob();
                drop(w, stash(w, List.of(out)), lab.key());
                w.status = "Collected " + out.getAmount() + " " + Text.plain(out.effectiveName());
                return true;
            });
            return true;
        }
        // 2. start a batch at a free lab
        LabRecipe.Ingredient missing = Cooking.missing(w.satchel, recipe, null, 0);
        if (missing == null) {
            for (Machine lab : labs) {
                if (lab.busy()) {
                    continue;
                }
                if (!canPay(w)) {
                    return false;
                }
                w.status = "Cooking " + recipe.output().display();
                go(w, lab.key(), () -> {
                    var res = Cooking.start(w.satchel, lab, recipe, Cooking.MAX_BATCHES, null, 0,
                            left -> drop(w, stash(w, List.of(left)), lab.key()));
                    if (!res.ok()) {
                        return false;
                    }
                    Location c = lab.key().center();
                    if (c != null) {
                        c.getWorld().playSound(c, "minecraft:block.brewing_stand.brew", SoundCategory.BLOCKS, 0.8f, 1f);
                    }
                    pay(w);
                    w.status = "Cooking " + res.batches() + " batch" + (res.batches() > 1 ? "es" : "") + " of "
                            + recipe.output().display();
                    return true;
                });
                return true;
            }
            w.status = "Waiting for the lab to finish";
            return false;
        }
        // 3. water: fill empty bottles at water nearby
        if (missing.vanilla() == Material.POTION && first(w, it -> it.getType() == Material.GLASS_BOTTLE && !Items.isCustom(it)) != null) {
            Block water = waterNear(w, Math.min(r, 10));
            if (water != null) {
                w.status = "Filling bottles with water";
                go(w, BlockKey.of(water), () -> fillBottles(w) > 0);
                return true;
            }
        }
        // 4. everything that's short, from the chests and the crew - several chests in one trip
        List<Need> needs = new ArrayList<>();
        for (LabRecipe.Ingredient ing : recipe.ingredients()) {
            Predicate<ItemStack> m = matcher(ing);
            int want = ing.amount() * Cooking.MAX_BATCHES;
            if (count(w.satchel, m) < want) {
                needs.add(new Need(m, want, ing.name()));
            }
        }
        if (gather(w, needs, true)) {
            return true;
        }
        if (missing.vanilla() == Material.POTION && gather(w, List.of(new Need(
                it -> it.getType() == Material.GLASS_BOTTLE && !Items.isCustom(it), 16, "bottles")), false)) {
            return true;
        }
        // 5. nobody has it: they buy it (when you let them)
        if (buy(w, buyIngredient(missing, missing.amount() * Cooking.MAX_BATCHES - count(w.satchel, matcher(missing))))) {
            return true;
        }
        want(w, matcher(missing), missing.name(), missing.amount() * Cooking.MAX_BATCHES);
        if (!w.status.startsWith("<red>Can't buy")) {
            w.status = "<yellow>Needs " + missing.amount() + " " + missing.name() + " <gray>(put some in a chest near them"
                    + (buyIngredient(missing, 1) != null && !autoBuy(w.owner()) ? ", or switch on auto-buy)" : ")");
        }
        return false;
    }

    /** Cook rolling joints or blunts: no lab needed, they roll where they stand. */
    private boolean planRoll(Worker w, ItemType product) {
        ItemType wrap = product == ItemType.JOINT ? ItemType.ROLLING_PAPERS : ItemType.BLUNT_WRAP;
        int budsEach = product == ItemType.JOINT ? 1 : 2;
        dev.kushcraft.util.StrainStock.Group g = dev.kushcraft.util.StrainStock.pick(w.satchel, ItemType.BUD_DRIED,
                budsEach, null, 0);
        int wraps = count(w.satchel, it -> Items.type(it) == wrap);
        if (g != null && wraps > 0) {
            if (w.freeSlots() == 0 && planDeposit(w, true)) {
                return true;
            }
            if (!canPay(w)) {
                return false;
            }
            w.status = "Rolling " + product.display().toLowerCase(java.util.Locale.ROOT) + "s";
            here(w, () -> {
                var group = dev.kushcraft.util.StrainStock.pick(w.satchel, ItemType.BUD_DRIED, budsEach, null, 0);
                int have = count(w.satchel, it -> Items.type(it) == wrap);
                int n = group == null ? 0 : Math.min(8 + 4 * w.level(), Math.min(group.count() / budsEach, have));
                if (n <= 0) {
                    return false;
                }
                dev.kushcraft.util.StrainStock.take(w.satchel, ItemType.BUD_DRIED, group, n * budsEach);
                InventoryUtil.remove(w.satchel, it -> Items.type(it) == wrap, n);
                drop(w, stash(w, List.of(Items.strainItem(product, group.strain(), group.quality(), n))),
                        BlockKey.of(spot(w)));
                Location c = spot(w);
                c.getWorld().playSound(c, "minecraft:item.book.page_turn", SoundCategory.NEUTRAL, 1f, 1.3f);
                pay(w);
                w.status = "Rolled " + n + " " + product.display().toLowerCase(java.util.Locale.ROOT) + (n > 1 ? "s" : "");
                return true;
            });
            return true;
        }
        Predicate<ItemStack> buds = it -> Items.type(it) == ItemType.BUD_DRIED && Items.strain(it) != null;
        List<Need> needs = new ArrayList<>();
        if (g == null) {
            needs.add(new Need(buds, 64, "dried buds"));
        }
        if (wraps == 0) {
            needs.add(new Need(it -> Items.type(it) == wrap, 32, wrap.display()));
        }
        if (gather(w, needs, true)) {
            return true;
        }
        if (wraps == 0 && buy(w, buyItem(wrap, 16))) {
            return true;
        }
        if (g == null) {
            want(w, buds, "dried buds", 64);
        } else {
            want(w, it -> Items.type(it) == wrap, wrap.display(), 32);
        }
        w.status = "<yellow>Needs " + (g == null ? budsEach + " Dried Bud" : wrap.display())
                + " <gray>(put some in a chest near them)";
        return false;
    }

    /** A water source block near home (for filling bottles), or null. */
    private Block waterNear(Worker w, int r) {
        Location h = w.home();
        World world = h.getWorld();
        Block best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = h.getBlockX() + dx, z = h.getBlockZ() + dz;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (int dy = -3; dy <= 1; dy++) {
                    Block b = world.getBlockAt(x, h.getBlockY() + dy, z);
                    if (b.getType() == Material.WATER && b.getBlockData() instanceof org.bukkit.block.data.Levelled l
                            && l.getLevel() == 0) {
                        double d = dx * dx + dz * dz + dy * dy;
                        if (d < bestD) {
                            bestD = d;
                            best = b;
                        }
                    }
                }
            }
        }
        return best;
    }

    private int fillBottles(Worker w) {
        int n = InventoryUtil.remove(w.satchel, it -> it.getType() == Material.GLASS_BOTTLE && !Items.isCustom(it),
                Integer.MAX_VALUE);
        if (n <= 0) {
            return 0;
        }
        drop(w, stash(w, waterBottles(n)), BlockKey.of(spot(w)));
        Location c = spot(w);
        c.getWorld().playSound(c, "minecraft:item.bottle.fill", SoundCategory.NEUTRAL, 1f, 1f);
        w.status = "Filled " + n + " bottles with water";
        return n;
    }

    // ---- runner ----

    /** Share of every sale the Runner keeps (0.1 = 10%). */
    public double runnerCut() {
        return Math.max(0, Math.min(0.9, plugin.getConfig().getDouble("workers.runner.cut", 0.1)));
    }

    /**
     * The Runner sells everything the crew makes the moment they get it, brings workers what they're
     * missing from any of your chests around (or from another worker), puts what doesn't sell and
     * nobody needs right now in a chest, and keeps the crew's satchels from filling up.
     */
    private boolean planRunner(Worker w) {
        List<Worker> crew = crew(w);
        // 1. auto-sell: whatever sells, right away (at a Dealer Stand of yours if one is near)
        if (first(w, this::sellable) != null) {
            planSell(w);
            return true;
        }
        // 2. bring a worker what they're missing
        if (w.freeSlots() > 2 && planDelivery(w, crew)) {
            return true;
        }
        // 3. what they carry that doesn't sell: to whoever uses it, else into a chest
        if (w.carried() > 0 && planStore(w, crew)) {
            return true;
        }
        // 4. pick up finished product (crew satchels, your chests) and sell it
        if (w.freeSlots() > 2 && planPickup(w, crew)) {
            return true;
        }
        if (w.freeSlots() <= 2) {
            w.status = "<yellow>Satchel full of things that don't sell: put a chest near them.";
        } else {
            w.status = crew.isEmpty() && chests(w).isEmpty()
                    ? "<yellow>Put product in a chest near them, or hire workers nearby."
                    : "Waiting for something to sell";
        }
        return false;
    }

    private void planSell(Worker w) {
        Machine stand = null;
        double best = Double.MAX_VALUE;
        Location h = w.home();
        int r = radius(w);
        for (Machine m : plugin.machines().near(w.worldName(), h.getBlockX(), h.getBlockZ(), r + 1)) {
            if (m.type() == MachineType.DEALER && w.owner().equals(m.owner()) && near(m.key(), w, r)
                    && m.key().isLoaded() && dist2(m.key(), spot(w)) < best) {
                best = dist2(m.key(), spot(w));
                stand = m;
            }
        }
        w.status = "Selling product";
        BooleanSupplier sell = () -> sellAll(w) > 0;
        if (stand != null) {
            go(w, stand.key(), sell);
        } else {
            here(w, sell);
        }
    }

    /** One delivery: what a crew member is missing, from a chest or another worker, to them. */
    private boolean planDelivery(Worker w, List<Worker> crew) {
        for (Worker o : crew) {
            Worker.Want want = o.want;
            if (want == null || o.type() == WorkerType.RUNNER || o.freeSlots() == 0) {
                continue;
            }
            for (Source src : sources(w, true)) {
                if (src.worker() == o || src.inv() == null || first(src.inv(), from(src, want.match())) == null) {
                    continue;
                }
                int max = Math.max(1, want.amount());
                w.status = "Getting " + want.what() + " for " + o.name() + " from " + src.name();
                go(w, src.where(), () -> {
                    Inventory inv = src.inv();
                    return inv != null && move(inv, w.satchel, from(src, want.match()), max) > 0;
                });
                go(w, BlockKey.of(spot(o)), () -> {
                    int n = move(w.satchel, o.satchel, want.match(), max);
                    if (n > 0) {
                        o.want = null;
                        o.restTicks = 1;
                        w.jobs++;
                        w.status = "Gave " + n + " " + want.what() + " to " + o.name();
                    }
                    return true;
                });
                o.want = null; // someone is on it
                return true;
            }
        }
        return false;
    }

    /** What a Runner carries that doesn't sell: to a crew member who uses it, else in a chest. */
    private boolean planStore(Worker w, List<Worker> crew) {
        for (ItemStack it : w.satchel.getStorageContents()) {
            if (it == null || it.getType().isAir()) {
                continue;
            }
            for (Worker o : crew) {
                if (o.type() != WorkerType.RUNNER && uses(o, it) && o.freeSlots() > 4) {
                    ItemStack sample = it.clone();
                    w.status = "Bringing " + Text.plain(it.effectiveName()) + " to " + o.name();
                    go(w, BlockKey.of(spot(o)), () -> move(w.satchel, o.satchel, x -> x.isSimilar(sample),
                            Integer.MAX_VALUE) > 0);
                    return true;
                }
            }
        }
        return putAway(w, it -> !sellable(it), "what doesn't sell");
    }

    /**
     * One pickup of finished product nobody in the crew uses: from a worker's satchel or one of your
     * chests, wherever there's the most. A few at a time (less running around) unless a satchel is
     * filling up or they've had nothing to do for a while. They sell it right there.
     */
    private boolean planPickup(Worker w, List<Worker> crew) {
        boolean bored = w.idleSince > 0 && System.currentTimeMillis() - w.idleSince > 20_000L;
        Source best = null;
        Predicate<ItemStack> bestMatch = null;
        double bestScore = 0;
        for (Source src : sources(w, true)) {
            Inventory inv = src.inv();
            if (inv == null) {
                continue;
            }
            Worker o = src.worker();
            Predicate<ItemStack> match = o != null
                    ? it -> produces(o, it) && sellable(it) && nobodyUses(crew, o, it)
                    : it -> sellable(it) && nobodyUses(crew, null, it);
            int n = count(inv, match);
            if (n <= 0) {
                continue;
            }
            boolean filling = o != null && o.freeSlots() < 10;
            if (n < 4 && !filling && !bored) {
                continue;
            }
            double score = n + (filling ? 1000 : 0) - Math.sqrt(dist2(src.where(), spot(w)));
            if (best == null || score > bestScore) {
                best = src;
                bestMatch = match;
                bestScore = score;
            }
        }
        if (best == null) {
            return false;
        }
        Source src = best;
        Predicate<ItemStack> match = bestMatch;
        w.status = "Picking up product from " + src.name();
        go(w, src.where(), () -> {
            Inventory inv = src.inv();
            if (inv == null || move(inv, w.satchel, match, Integer.MAX_VALUE) <= 0) {
                return false;
            }
            sellAll(w); // auto-sell: right away
            return true;
        });
        return true;
    }

    /** Sells everything sellable in a Runner's satchel for the owner. Returns what the owner got. */
    public double sellAll(Worker w) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(w.owner());
        Player online = owner.getPlayer();
        double total = 0;
        int count = 0;
        Map<ItemType, Integer> sold = new java.util.EnumMap<>(ItemType.class);
        ItemStack[] items = w.satchel.getStorageContents();
        for (int i = 0; i < items.length; i++) {
            ItemStack it = items[i];
            if (!sellable(it)) {
                continue;
            }
            ItemType t = Items.type(it);
            int before = sold.getOrDefault(t, 0);
            total += plugin.shop().sellPrice(it) * it.getAmount() * plugin.market().bulkFactor(t, before, it.getAmount());
            sold.put(t, before + it.getAmount());
            count += it.getAmount();
            w.satchel.setItem(i, null);
        }
        if (count == 0) {
            return 0;
        }
        double bonus = online != null ? plugin.shop().bonus(online) : 1 + plugin.cartels().sellBonus(w.owner());
        total *= bonus;
        double cut = Math.round(total * runnerCut() * 100) / 100.0;
        double paid = Math.round((total - cut) * 100) / 100.0;
        plugin.economy().frequent(owner.getUniqueId(), paid, dev.kushcraft.economy.Tx.RUNNER_SALE, w.id().toString(),
                w.name() + " sold product");
        touch(w, true);
        sold.forEach((t, n) -> plugin.market().sold(t, n));
        if (online != null) {
            plugin.titles().sold(online, paid);
            online.sendActionBar(Text.mm("<yellow>" + Text.escape(w.name()) + " <gray>sold " + count + " items: <gold>+"
                    + plugin.economy().format(paid)));
        } else {
            plugin.titles().soldOffline(w.owner(), paid);
        }
        w.jobs++;
        w.wages += cut;
        touch(w, true);
        Location c = spot(w);
        if (c != null) {
            c.getWorld().playSound(c, "minecraft:entity.villager.yes", SoundCategory.NEUTRAL, 0.8f, 1.1f);
            c.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, c.clone().add(0, 1.6, 0), 8, 0.3, 0.3, 0.3, 0);
        }
        w.status = "Sold " + count + " items for " + plugin.economy().format(paid);
        return Math.max(0.01, paid);
    }

    /** What the sellable things in a worker's satchel would fetch right now. */
    public double carriedValue(Worker w) {
        double v = 0;
        for (ItemStack it : w.satchel.getStorageContents()) {
            if (sellable(it)) {
                v += plugin.shop().sellPrice(it) * it.getAmount();
            }
        }
        return v;
    }

    /**
     * The easy way to keep a crew going: everything in the player's inventory that one of their
     * workers uses (seeds and fertilizer, fresh buds, a Cook's ingredients) goes into that
     * worker's satchel. Runners get nothing (they'd sell it for a cut). Returns how many items.
     */
    public int supply(Player p) {
        List<Worker> mine = of(p.getUniqueId());
        mine.removeIf(w -> w.type() == WorkerType.RUNNER);
        mine.sort(java.util.Comparator.comparingInt(w -> w.type().ordinal()));
        ItemStack[] inv = p.getInventory().getStorageContents();
        int moved = 0;
        for (int i = 0; i < inv.length; i++) {
            ItemStack it = inv[i];
            if (it == null || it.getType().isAir() || Items.type(it) == ItemType.GROWER_GUIDE) {
                continue;
            }
            for (Worker w : mine) {
                if (it.getAmount() <= 0 || !uses(w, it)) {
                    continue;
                }
                int before = it.getAmount();
                Map<Integer, ItemStack> left = w.satchel.addItem(it.clone());
                int now = left.isEmpty() ? 0 : left.values().iterator().next().getAmount();
                if (now < before) {
                    moved += before - now;
                    it.setAmount(now);
                    touch(w, true);
                    hurry(w);
                }
            }
            inv[i] = it.getAmount() <= 0 ? null : it;
        }
        if (moved > 0) {
            p.getInventory().setStorageContents(inv);
            plugin.persistence().took(p);
        }
        return moved;
    }

    /** Everything your workers made (not what they need) goes to your inventory. Returns how many items. */
    public int collectAll(Player p) {
        plugin.persistence().gave(p);
        int n = 0;
        for (Worker w : of(p.getUniqueId())) {
            ItemStack[] items = w.satchel.getStorageContents();
            for (int i = 0; i < items.length; i++) {
                ItemStack it = items[i];
                if (it == null || it.getType().isAir() || uses(w, it) || !(produces(w, it) || w.type() == WorkerType.RUNNER)) {
                    continue;
                }
                Map<Integer, ItemStack> left = p.getInventory().addItem(it.clone());
                int kept = left.isEmpty() ? 0 : left.values().iterator().next().getAmount();
                n += it.getAmount() - kept;
                if (kept > 0) {
                    ItemStack rest = it.clone();
                    rest.setAmount(kept);
                    w.satchel.setItem(i, rest);
                    touch(w, true);
                    return n; // inventory full
                }
                w.satchel.setItem(i, null);
                touch(w, true);
            }
        }
        return n;
    }

    /** Hangs every fresh bud in the satchel that fits on this lab's racks. */
    int hangAll(Worker w, Machine lab) {
        long now = System.currentTimeMillis();
        long done = now + plugin.machines().dryingSeconds() * 1000L;
        int hung = 0;
        for (ItemStack it : w.satchel.getStorageContents()) {
            if (Items.type(it) != ItemType.BUD_FRESH || Items.strain(it) == null) {
                continue;
            }
            int n = lab.hang(Items.strain(it).id(), Items.quality(it), it.getAmount(), now, done);
            it.setAmount(it.getAmount() - n);
            hung += n;
        }
        if (hung > 0) {
            touch(w, false);
        }
        return hung;
    }

    /** Whatever didn't fit in the satchel goes in a chest near them, else falls on the ground. */
    private void drop(Worker w, List<ItemStack> left, BlockKey at) {
        if (left.isEmpty()) {
            return;
        }
        List<ItemStack> rest = new ArrayList<>(left);
        for (Block c : chests(w)) {
            Inventory inv = inventoryOf(c);
            if (inv == null || rest.isEmpty()) {
                continue;
            }
            List<ItemStack> still = new ArrayList<>();
            for (ItemStack it : rest) {
                still.addAll(inv.addItem(it).values());
            }
            rest = still;
        }
        Location l = at.center();
        if (rest.isEmpty() || l == null) {
            return;
        }
        for (ItemStack it : rest) {
            l.getWorld().dropItemNaturally(l, it);
        }
        w.status = "<red>Satchel full! Some items fell on the ground.";
    }

    // ---- walking ----

    /** Adds a trip to a block: walk next to it, then work on it. */
    private void go(Worker w, BlockKey target, BooleanSupplier act) {
        Location look = target.center();
        Location stand = standNear(w, target);
        w.steps.add(new Worker.Step(stand, look, act));
    }

    /** A free spot next to the target the worker can walk to in a straight line (else: where they are). */
    private Location standNear(Worker w, BlockKey target) {
        World world = target.bukkitWorld();
        Location from = w.pos != null ? w.pos : w.home();
        if (world == null || from == null) {
            return from;
        }
        Location best = null;
        double bestD = Double.MAX_VALUE;
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] o : around) {
            for (int dy = 0; dy >= -1; dy--) {
                Block feet = world.getBlockAt(target.x() + o[0], target.y() + dy, target.z() + o[1]);
                if (!standable(feet)) {
                    continue;
                }
                Location l = feet.getLocation().add(0.5, 0, 0.5);
                double d = l.distanceSquared(from);
                if (d < bestD && clear(from, l)) {
                    bestD = d;
                    best = l;
                }
            }
        }
        return best != null ? best : from.clone();
    }

    private boolean standable(Block feet) {
        if (!feet.isPassable() || feet.isLiquid() || !feet.getRelative(BlockFace.UP).isPassable()
                || plugin.plants().at(BlockKey.of(feet)) != null || plugin.machines().at(feet) != null) {
            return false;
        }
        Block below = feet.getRelative(BlockFace.DOWN);
        return !below.isPassable() || below.isLiquid();
    }

    /** Nothing solid at feet or head height on the straight line between a and b. */
    private boolean clear(Location a, Location b) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len > 48) {
            return false;
        }
        int n = (int) Math.ceil(len / 0.5);
        for (int i = 1; i < n; i++) {
            Location p = a.clone().add(d.clone().multiply(i / (double) n));
            Block feet = p.getBlock();
            if (!feet.isPassable() || !feet.getRelative(BlockFace.UP).isPassable()) {
                return false;
            }
        }
        return true;
    }

    /** Turns from one yaw towards another by at most max degrees (no snapping round). */
    private static float turn(float from, float to, float max) {
        float d = to - from;
        while (d > 180) {
            d -= 360;
        }
        while (d < -180) {
            d += 360;
        }
        return from + Math.max(-max, Math.min(max, d));
    }

    /**
     * One tick of walking / working. The mannequin moves every tick while a player can see it
     * (the client smooths it out) and only now and then when nobody can (no wasted work).
     */
    private void move(Worker w) {
        Entity e = entity(w);
        if (w.current == null) {
            w.current = w.steps.poll();
            if (w.current == null) {
                return;
            }
            w.workTicks = 0;
        }
        Worker.Step s = w.current;
        Location to = s.stand();
        double dist = to == null || !to.getWorld().equals(w.pos.getWorld()) ? 0 : w.pos.distance(to);
        if (dist > 0.02) {
            Vector dir = to.toVector().subtract(w.pos.toVector());
            double step = speed(w);
            boolean arrived = dist <= step || dist > 64;
            if (arrived) {
                w.pos = to.clone();
            } else {
                w.pos.add(dir.clone().normalize().multiply(step));
            }
            if (Math.abs(dir.getX()) + Math.abs(dir.getZ()) > 0.001) {
                float target = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
                w.pos.setYaw(arrived ? target : turn(w.pos.getYaw(), target, 35));
            }
            w.pos.setPitch(0);
            if (e != null && (w.watched || arrived || ticks % 10 == 0)) {
                e.teleport(w.pos);
                if (e instanceof LivingEntity le) {
                    le.setBodyYaw(w.pos.getYaw());
                }
            }
            return;
        }
        if (s.act() == null) {
            // back home: face the way they were put down
            w.current = null;
            Location home = w.home();
            w.pos = home.clone();
            if (e != null) {
                e.teleport(home);
                if (e instanceof LivingEntity le) {
                    le.setBodyYaw(home.getYaw());
                }
            }
            return;
        }
        if (w.workTicks == 0 && e instanceof LivingEntity le && w.watched) {
            if (s.look() != null) {
                le.lookAt(s.look().getX(), s.look().getY(), s.look().getZ(), LookAnchor.EYES);
            }
            le.swingMainHand();
        }
        w.workTicks++;
        if (w.workTicks == 8 && w.watched && e instanceof LivingEntity le) {
            le.swingMainHand();
            if (s.look() != null) {
                s.look().getWorld().spawnParticle(Particle.HAPPY_VILLAGER, s.look(), 3, 0.25, 0.25, 0.25, 0);
            }
        }
        if (w.workTicks >= (w.watched ? 14 : 4)) {
            boolean ok = act(s);
            w.current = null;
            if (!ok) {
                w.steps.clear();
            }
        }
    }

    private boolean act(Worker.Step s) {
        try {
            return s.act().getAsBoolean();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Worker job failed", ex);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // events
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        Worker w = fromEntity(e.getRightClicked());
        if (w == null) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = e.getPlayer();
        if (w.owner().equals(p.getUniqueId()) || p.hasPermission("kushcraft.admin")) {
            new dev.kushcraft.menus.WorkerMenu(p, w).open();
        } else {
            p.sendActionBar(Text.mm(w.type().colored() + " " + Text.escape(w.name) + " <gray>works for <white>"
                    + Text.escape(String.valueOf(Bukkit.getOfflinePlayer(w.owner()).getName()))));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractAt(PlayerInteractAtEntityEvent e) {
        if (fromEntity(e.getRightClicked()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (fromEntity(e.getEntity()) != null) {
            e.setCancelled(true);
        }
    }

    /** Chests and barrels remember who placed them: workers only use their owner's (and old unmarked ones). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (!isChest(b.getType())) {
            return;
        }
        if (b.getState(false) instanceof TileState t) {
            t.getPersistentDataContainer().set(Keys.PLACER, PersistentDataType.STRING,
                    e.getPlayer().getUniqueId().toString());
        }
        chestsChanged(b);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (isChest(e.getBlock().getType())) {
            chestsChanged(e.getBlock());
        }
    }
}
