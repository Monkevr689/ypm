package dev.kushcraft.worker;

import dev.kushcraft.KushCraft;
import dev.kushcraft.Keys;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.machine.Machine;
import dev.kushcraft.machine.MachineType;
import dev.kushcraft.plant.Plant;
import dev.kushcraft.plant.PlantManager;
import dev.kushcraft.strain.Strain;
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
import dev.kushcraft.lab.LabRecipe;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
 * Hired workers. A Farmhand harvests the owner's ripe plants around them and
 * plants them again; a Dryer hangs fresh buds on the owner's Drug Lab racks
 * and collects them when they're dry; a Cook cooks (or rolls) the drug you
 * pick - or picks the best one by themselves; a Runner sells the finished
 * product and carries things between workers; a Supplier buys ingredients
 * with the owner's money. They walk over, work, walk back home and get paid
 * a small wage for every job (the Runner takes a cut).
 *
 * No chests: work goes from hand to hand. One player's workers within
 * chain-radius of each other are a crew - a Farmhand's harvest goes to the
 * Runner, the Runner takes it to the Dryer, the Dryer's buds go to a Cook or
 * back to the Runner, who sells them on the spot. What a worker is missing,
 * a Runner brings from anywhere in the crew (they take the back way) and the
 * Supplier buys with the owner's money. Nobody works through walls (see
 * WalkArea) and nobody takes what another worker needs for their own job.
 * Workers plan their next job where they stand and only walk home when
 * there's nothing to do; a stuck worker tells the owner.
 */
public final class Workers implements Listener {

    private static final String[] NAMES = {"Bud", "Sage", "Blaze", "Ziggy", "Dusty", "Basil", "Clover", "Moss",
            "Indie", "Skye", "Rowan", "Jojo", "Pip", "Sunny", "Rico", "Lupe", "Benny", "Nico", "Kiki", "Juniper",
            "Mojo", "Hazel", "Biscuit", "Noodle"};

    private final KushCraft plugin;
    private final File file;
    private final Map<UUID, Worker> workers = new LinkedHashMap<>();
    private final Map<UUID, Worker> byEntity = new HashMap<>();
    private final Map<String, Set<UUID>> byChunk = new HashMap<>();
    private final Map<UUID, WalkArea> areas = new HashMap<>();
    /** How long a worker's walk area is trusted (building near them refreshes it right away). */
    private static final long AREA_MILLIS = 60_000L;
    /** Crews, worked out once a second (they're asked for a lot). */
    private final Map<UUID, List<Worker>> crewCache = new HashMap<>();
    private long crewAt = -1;
    /** Farmhands' empty soil and Cooks' water, kept for a few seconds (both read a lot of blocks). */
    private final Map<UUID, SoilList> soil = new HashMap<>();
    private final Map<UUID, WaterSpot> water = new HashMap<>();
    private boolean dirty;
    private long ticks;
    /** False when the server can't spawn mannequins (they stay invisible but still work). */
    private boolean mannequins = true;

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
        return (int) pick(plugin.getConfig().getDoubleList("workers.radius"), w.level(), 6);
    }

    /** Seconds of rest between jobs. */
    public int restSeconds(Worker w) {
        return (int) Math.max(1, pick(plugin.getConfig().getDoubleList("workers.rest-seconds"), w.level(), 6));
    }

    /** Walking speed in blocks per tick. */
    private double speed(Worker w) {
        return 0.16 + 0.04 * (w.level() - 1);
    }

    /** Wage for one job (Runners take a cut of each sale instead; a Supplier per delivery). */
    public double wage(WorkerType t) {
        return Math.max(0, plugin.getConfig().getDouble("workers." + t.id() + ".wage", t == WorkerType.RUNNER ? 0
                : t == WorkerType.SUPPLIER ? 6 : 3));
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

    // ------------------------------------------------------------------
    // storage
    // ------------------------------------------------------------------

    public void load() {
        for (Worker w : workers.values()) {
            despawn(w);
        }
        workers.clear();
        list = new Worker[0];
        byEntity.clear();
        byChunk.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("workers");
        if (sec == null) {
            return;
        }
        for (String k : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(k);
            WorkerType type = s == null ? null : WorkerType.parse(s.getString("type", ""));
            if (type == null) {
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
                w.keep = s.getInt("keep", 2);
                w.spent = s.getDouble("spent");
                w.sell = s.getBoolean("sell");
                readItems(s.getConfigurationSection("satchel"), w.satchel);
                readItems(s.getConfigurationSection("seeds"), w.seeds);
                if (type == WorkerType.FARMHAND) {
                    bagSeeds(w); // from 7.1 and older: their seeds were in the satchel
                }
                add(w);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("workers.yml: skipped a broken worker " + k);
            }
        }
        plugin.getLogger().info("Loaded " + workers.size() + " workers.");
    }

    private static void readItems(ConfigurationSection items, Inventory into) {
        if (items == null) {
            return;
        }
        for (String slot : items.getKeys(false)) {
            ItemStack it = decode(items.getString(slot));
            try {
                int i = Integer.parseInt(slot);
                if (it != null && i >= 0 && i < into.getSize()) {
                    into.setItem(i, it);
                }
            } catch (NumberFormatException ignored) {
                // not a slot
            }
        }
    }

    private static void writeItems(ConfigurationSection s, String path, Inventory from) {
        ItemStack[] items = from.getStorageContents();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && !items[i].getType().isAir()) {
                s.set(path + "." + i, encode(items[i]));
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        ConfigurationSection sec = y.createSection("workers");
        for (Worker w : workers.values()) {
            ConfigurationSection s = sec.createSection(w.id().toString());
            s.set("type", w.type().name());
            s.set("owner", w.owner().toString());
            s.set("name", w.name);
            Location h = w.home();
            s.set("world", w.worldName());
            if (h != null) {
                s.set("x", h.getX());
                s.set("y", h.getY());
                s.set("z", h.getZ());
                s.set("yaw", (double) h.getYaw());
            }
            s.set("level", w.level);
            if (w.paused) {
                s.set("paused", true);
            }
            s.set("jobs", w.jobs);
            s.set("wages", Math.round(w.wages * 100) / 100.0);
            if (w.recipe != null) {
                s.set("recipe", w.recipe);
            }
            if (w.sell) {
                s.set("sell", true);
            }
            if (w.type() == WorkerType.SUPPLIER) {
                s.set("spent", Math.round(w.spent * 100) / 100.0);
            }
            if (w.mixes()) {
                s.set("keep", w.keep);
            }
            writeItems(s, "satchel", w.satchel);
            writeItems(s, "seeds", w.seeds);
        }
        try {
            y.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save workers.yml", e);
        }
    }

    private static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
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

    public void markDirty() {
        dirty = true;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                save();
            }
        }, 20L * 60, 20L * 60);
        for (World w : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk c : w.getLoadedChunks()) {
                chunkLoaded(w, c.getX(), c.getZ());
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::keepLoaded, 40L, 100L);
    }

    public void shutdown() {
        for (Worker w : workers.values()) {
            despawn(w);
        }
        releaseChunks();
        save();
    }

    // ------------------------------------------------------------------
    // working while nobody is around
    // ------------------------------------------------------------------

    /** Chunks this plugin keeps loaded for the workers, per world (chunk x << 32 | chunk z). */
    private final Map<String, Set<Long>> held = new HashMap<>();

    /** Workers keep working when their owner is offline or far away: the chunks around them stay loaded. */
    public boolean workOffline() {
        return plugin.getConfig().getBoolean("workers.work-offline", true);
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /** Keeps the chunks within every worker's reach loaded (a few new ones per run, at most work-offline-max-chunks). */
    private void keepLoaded() {
        Map<String, Set<Long>> want = new HashMap<>();
        int max = Math.max(0, plugin.getConfig().getInt("workers.work-offline-max-chunks", 100));
        int total = 0;
        if (workOffline() && enabled()) {
            for (Worker w : workers.values()) {
                Location h = w.home();
                if (h == null) {
                    continue;
                }
                int r = radius(w) + 1;
                Set<Long> mine = want.computeIfAbsent(w.worldName(), k -> new HashSet<>());
                Set<Long> already = held.getOrDefault(w.worldName(), Set.of());
                for (int cx = (h.getBlockX() - r) >> 4; cx <= (h.getBlockX() + r) >> 4; cx++) {
                    for (int cz = (h.getBlockZ() - r) >> 4; cz <= (h.getBlockZ() + r) >> 4; cz++) {
                        long k = chunkKey(cx, cz);
                        if (!mine.contains(k) && (already.contains(k) || total < max)) {
                            mine.add(k);
                            total++;
                        }
                    }
                }
            }
        }
        for (Map.Entry<String, Set<Long>> en : held.entrySet()) {
            World world = Bukkit.getWorld(en.getKey());
            Set<Long> keep = want.getOrDefault(en.getKey(), Set.of());
            en.getValue().removeIf(k -> {
                if (keep.contains(k)) {
                    return false;
                }
                if (world != null) {
                    world.removePluginChunkTicket((int) (k >> 32), (int) (long) k, plugin);
                }
                return true;
            });
        }
        int added = 0;
        for (Map.Entry<String, Set<Long>> en : want.entrySet()) {
            World world = Bukkit.getWorld(en.getKey());
            if (world == null) {
                continue;
            }
            Set<Long> have = held.computeIfAbsent(en.getKey(), k -> new HashSet<>());
            for (long k : en.getValue()) {
                if (!have.contains(k)) {
                    if (added++ >= 12) {
                        return;
                    }
                    world.addPluginChunkTicket((int) (k >> 32), (int) k, plugin);
                    have.add(k);
                }
            }
        }
    }

    private void releaseChunks() {
        for (Map.Entry<String, Set<Long>> en : held.entrySet()) {
            World world = Bukkit.getWorld(en.getKey());
            if (world != null) {
                for (long k : en.getValue()) {
                    world.removePluginChunkTicket((int) (k >> 32), (int) k, plugin);
                }
            }
        }
        held.clear();
    }

    /** Works out the chunks to keep loaded right now (it also runs every 5 seconds). */
    public void syncChunks() {
        keepLoaded();
    }

    /** Chunks being kept loaded for the workers (for /kush selftest and the admin). */
    public int keptChunks() {
        return held.values().stream().mapToInt(Set::size).sum();
    }

    /** Every worker, for the tick loop (no new list every tick). */
    private Worker[] list = new Worker[0];

    private void add(Worker w) {
        crewCache.clear();
        workers.put(w.id(), w);
        list = workers.values().toArray(new Worker[0]);
        byChunk.computeIfAbsent(w.chunkId(), k -> new HashSet<>()).add(w.id());
    }

    private void forget(Worker w) {
        crewCache.clear();
        workers.remove(w.id());
        list = workers.values().toArray(new Worker[0]);
        Set<UUID> set = byChunk.get(w.chunkId());
        if (set != null) {
            set.remove(w.id());
            if (set.isEmpty()) {
                byChunk.remove(w.chunkId());
            }
        }
        dirty = true;
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
        int have = of(p.getUniqueId()).size();
        if (maxPerPlayer() > 0 && have >= maxPerPlayer() && !p.hasPermission("kushcraft.admin")) {
            p.sendActionBar(Text.mm("<red>You already have " + have + " workers (the most you can hire)."));
            return false;
        }
        Location home = spot.getLocation().add(0.5, 0, 0.5);
        home.setYaw(Math.round((p.getLocation().getYaw() + 180) / 90f) * 90f);
        Worker w = hireAt(home, type, p.getUniqueId(), Items.level(item));
        if (p.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
        p.swingMainHand();
        p.sendMessage(Text.msg(type.colored() + " " + Text.escape(w.name) + " <gray>started working for you. "
                + type.job() + " <dark_gray>(Right-click them for their satchel.)"));
        plugin.awards().hired(p, of(p.getUniqueId()).size());
        if (type == WorkerType.COOK) {
            plugin.awards().hiredCook(p);
        }
        if (type == WorkerType.SUPPLIER) {
            plugin.awards().hiredSupplier(p);
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
        if (types.containsAll(java.util.EnumSet.of(WorkerType.FARMHAND, WorkerType.DRYER, WorkerType.COOK,
                WorkerType.RUNNER))) {
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
        dirty = true;
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
        areas.remove(w.id());
        soil.remove(w.id());
        water.remove(w.id());
        List<ItemStack> back = new ArrayList<>();
        back.add(Items.machine(w.type().item(), w.level()));
        for (Inventory inv : List.of(w.satchel, w.seeds)) {
            for (ItemStack it : inv.getStorageContents()) {
                if (it != null && !it.getType().isAir()) {
                    back.add(it);
                }
            }
            inv.clear();
        }
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
        if (!plugin.economy().withdraw(p, cost)) {
            return "Training costs " + plugin.economy().format(cost) + ".";
        }
        w.level++;
        dirty = true;
        nameplate(w);
        return null;
    }

    public void rename(Worker w, String name) {
        String clean = name.replaceAll("[^A-Za-z0-9 _'-]", "").trim();
        if (clean.isEmpty()) {
            return;
        }
        w.name = clean.length() > 16 ? clean.substring(0, 16) : clean;
        dirty = true;
        nameplate(w);
    }

    public void pause(Worker w, boolean paused) {
        w.paused = paused;
        w.status = paused ? "Paused" : "Back to work";
        if (paused) {
            w.steps.clear();
        }
        dirty = true;
        nameplate(w);
    }

    /** Cook: what they make from now on. */
    public void setRecipe(Worker w, dev.kushcraft.lab.LabRecipe r) {
        setJob(w, r == null ? null : r.name());
    }

    /** Cook: a LabRecipe name, Worker.ROLL_JOINT / ROLL_BLUNT / AUTO, or null. */
    public void setJob(Worker w, String job) {
        w.recipe = job;
        w.auto = null;
        ItemType made = w.product();
        w.status = w.autoPick() ? "Looking for something to make" : w.mixes() ? "Ready to mix strains"
                : made == null ? "Pick a drug for them" : "Ready to make " + made.display();
        w.restTicks = 1;
        dirty = true;
        nameplate(w);
    }

    /** Puts items in a worker's satchel (a Farmhand's seeds in the backpack); whatever doesn't fit is returned. */
    public List<ItemStack> stash(Worker w, List<ItemStack> items) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack it : items) {
            if (it == null || it.getType().isAir()) {
                continue;
            }
            if (w.type() == WorkerType.FARMHAND && isSeed(it)) {
                for (ItemStack rest : w.seeds.addItem(it).values()) {
                    left.addAll(w.satchel.addItem(rest).values());
                }
            } else {
                left.addAll(w.satchel.addItem(it).values());
            }
        }
        dirty = true;
        return left;
    }

    /** A Farmhand's seeds in the satchel go into the backpack (as many as fit). */
    private void bagSeeds(Worker w) {
        ItemStack[] items = w.satchel.getStorageContents();
        for (int i = 0; i < items.length; i++) {
            ItemStack it = items[i];
            if (!isSeed(it)) {
                continue;
            }
            Map<Integer, ItemStack> left = w.seeds.addItem(it.clone());
            w.satchel.setItem(i, left.isEmpty() ? null : left.values().iterator().next());
            dirty = true;
        }
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
                e.setInvulnerable(true);
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
            m.setDescription(Text.mm(w.paused ? "<red>Paused" : "<gray>" + w.type().display()
                    + (w.autoPick() ? " <dark_gray>· <aqua>Auto" : w.mixes() ? " <dark_gray>· <light_purple>Mixing"
                    : made != null ? " <dark_gray>· <aqua>" + made.display() : "")
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
        for (UUID id : new ArrayList<>(ids)) {
            Worker w = workers.get(id);
            if (w != null) {
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
        for (UUID id : ids) {
            Worker w = workers.get(id);
            if (w != null) {
                despawn(w);
            }
        }
    }

    // ------------------------------------------------------------------
    // working
    // ------------------------------------------------------------------

    private void tick() {
        ticks++;
        for (Worker w : list) {
            if (w.pos == null) {
                continue; // not loaded
            }
            // every worker thinks once a second, but not all on the same tick (no lag spikes)
            boolean think = (ticks + phase(w)) % 20 == 0;
            if (think) {
                if (mannequins && w.entityId != null && entity(w) == null && w.isLoaded()) {
                    spawn(w); // the mannequin got unloaded on a walk: put them back home
                    continue;
                }
                w.watched = watched(w);
            }
            if (w.busy()) {
                move(w);
            } else if (think) {
                think(w);
            }
        }
    }

    private static int phase(Worker w) {
        return Math.floorMod(w.id().hashCode(), 20);
    }

    /** A player near enough to see them walk (else the mannequin only moves at the end of each walk: less lag). */
    private static boolean watched(Worker w) {
        Location at = w.pos;
        if (at == null || at.getWorld() == null) {
            return false;
        }
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) < 96 * 96) {
                return true;
            }
        }
        return false;
    }

    private boolean atHome(Worker w) {
        Location h = w.home();
        return h == null || w.pos == null || w.pos.distanceSquared(h) < 0.01;
    }

    /**
     * Once a second when idle: rest where they are, then look for the next job right there.
     * They only walk home when there's nothing to do (much less running back and forth).
     */
    private void think(Worker w) {
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
        }
        // Runners and Suppliers wait where they are for a while (the next job is often close by)
        if (!stroll(w) && (!courier(w) || now - w.idleSince > 45_000L)) {
            goHome(w);
        }
    }

    private void goHome(Worker w) {
        Location home = w.home();
        if (home != null && !atHome(w) && !w.busy()) {
            w.steps.add(new Worker.Step(home, null, null));
        }
    }

    /** A Farmhand with nothing to do (and nothing wrong) walks around among the plants near home now and then. */
    private boolean stroll(Worker w) {
        if (w.type() != WorkerType.FARMHAND || w.problemSince > 0 || w.pos == null
                || ThreadLocalRandom.current().nextInt(3) == 0) {
            return false;
        }
        WalkArea a = area(w);
        Location h = w.home();
        if (a == null || h == null) {
            return false;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 12; i++) {
            int x = h.getBlockX() + r.nextInt(-5, 6), z = h.getBlockZ() + r.nextInt(-5, 6);
            for (int dy = -2; dy <= 2; dy++) {
                int y = h.getBlockY() + dy;
                if (a.contains(x, y, z) && (x != w.pos.getBlockX() || z != w.pos.getBlockZ())) {
                    w.steps.add(new Worker.Step(new Location(h.getWorld(), x + 0.5, y, z + 0.5), null, () -> true));
                    return true;
                }
            }
        }
        return false;
    }

    /** Plans the next job (adds steps). Returns true when there was one. */
    boolean plan(Worker w) {
        if (!enabled()) {
            w.status = "Workers are turned off";
            return false;
        }
        if (area(w) == null) {
            w.status = "Asleep (nobody nearby)";
            return false;
        }
        w.want = null;
        boolean busy = planJob(w);
        checkProblem(w);
        return busy;
    }

    private boolean planJob(Worker w) {
        if (tight(w)) {
            tidy(w);
        }
        return switch (w.type()) {
            case FARMHAND -> planFarmhand(w);
            case DRYER -> planDryer(w);
            case COOK -> planCook(w);
            case RUNNER -> planRunner(w);
            case SUPPLIER -> planSupplier(w);
        };
    }

    /** Does the next job right away, without walking (for /kush selftest). */
    public boolean workNow(Worker w) {
        if (!plan(w)) {
            return false;
        }
        boolean ok = true;
        while (ok && !w.steps.isEmpty()) {
            Worker.Step s = w.steps.poll();
            ok = s.act() == null || s.act().getAsBoolean();
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
        if (wage > 0 && plugin.economy().withdraw(Bukkit.getOfflinePlayer(w.owner()), wage)) {
            w.wages += wage;
        }
        w.jobs++;
        dirty = true;
    }

    private boolean near(BlockKey k, Worker w, double r) {
        Location h = w.home();
        if (h == null || !k.world().equals(w.worldName())) {
            return false;
        }
        double dx = k.x() + 0.5 - h.getX(), dy = k.y() - h.getY(), dz = k.z() + 0.5 - h.getZ();
        return dx * dx + dz * dz <= r * r && Math.abs(dy) <= 4;
    }

    private double dist2(BlockKey k, Location from) {
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

    /** Where a worker is right now (their home when not loaded). */
    private static Location spot(Worker w) {
        return w.pos != null ? w.pos : w.home();
    }

    // ------------------------------------------------------------------
    // telling the owner when a worker is stuck
    // ------------------------------------------------------------------

    /** A red status is a problem after 15 seconds, a yellow one ("needs...") after 2 minutes. */
    private void checkProblem(Worker w) {
        String st = w.status == null ? "" : w.status;
        boolean red = st.startsWith("<red>");
        if (!red && !st.startsWith("<yellow>")) {
            w.problemSince = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (w.problemSince == 0) {
            w.problemSince = now;
        }
        if (now - w.problemSince < (red ? 15_000L : 120_000L) || now - w.notifiedAt < 600_000L) {
            return;
        }
        Player p = Bukkit.getPlayer(w.owner());
        if (p == null) {
            return;
        }
        w.notifiedAt = now;
        p.sendMessage(Text.msg("<gold>⚠ " + w.type().colored() + " " + Text.escape(w.name) + " <gray>needs you: "
                + st + where(w)));
        p.playSound(p.getLocation(), "minecraft:block.note_block.bell", SoundCategory.PLAYERS, 0.7f, 0.7f);
    }

    private static String where(Worker w) {
        Location h = w.home();
        return h == null ? "" : " <dark_gray>(" + h.getBlockX() + ", " + h.getBlockY() + ", " + h.getBlockZ() + ")";
    }

    /** Workers of this player that are stuck right now (for the menus). */
    public List<Worker> stuck(UUID owner) {
        List<Worker> out = new ArrayList<>();
        for (Worker w : of(owner)) {
            if (w.problem() != null && !w.paused) {
                out.add(w);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // where they can go: walk areas (no working through walls)
    // ------------------------------------------------------------------

    /** Runners and Suppliers cover the whole crew and get through walls the back way. */
    static boolean courier(Worker w) {
        return w.type() == WorkerType.RUNNER || w.type() == WorkerType.SUPPLIER;
    }

    /**
     * Everywhere a worker can walk to from home. Worked out again once a minute, or right away
     * when blocks around them change. Null when not loaded.
     */
    WalkArea area(Worker w) {
        Location h = w.home();
        if (h == null || !w.isLoaded()) {
            return null;
        }
        long now = System.currentTimeMillis();
        WalkArea a = areas.get(w.id());
        if (a == null || now - a.made > AREA_MILLIS || !a.world.equals(h.getWorld())) {
            boolean c = courier(w);
            a = WalkArea.build(h, c ? chainRadius() : radius(w) + 1, c ? 20000 : 8000,
                    now - ThreadLocalRandom.current().nextInt(5000));
            areas.put(w.id(), a);
        }
        return a;
    }

    /** Forget the cached walk areas and searches (the self test). */
    public void refreshAreas() {
        areas.clear();
        soil.clear();
        water.clear();
        crewCache.clear();
    }

    /** Blocks changed here: the workers around it work out where they can walk again. */
    private void blockChanged(Block b) {
        for (Worker w : workers.values()) {
            WalkArea a = areas.get(w.id());
            if (a != null && a.world.equals(b.getWorld()) && Math.abs(b.getX() - a.hx) <= a.radius + 2
                    && Math.abs(b.getZ() - a.hz) <= a.radius + 2) {
                areas.remove(w.id());
                soil.remove(w.id());
            }
        }
    }

    /** A spot next to target this worker can walk to (self: they may stand on it), or null. */
    private Location stand(Worker w, BlockKey target, boolean self) {
        WalkArea a = area(w);
        return a == null ? null : a.standFor(target, self, spot(w));
    }

    /** True when the worker can walk up to target. */
    public boolean reaches(Worker w, BlockKey target, boolean self) {
        return stand(w, target, self) != null;
    }

    /** A spot next to where another worker is (to hand things over), or null. */
    private Location standByWorker(Worker w, Worker o) {
        Location at = spot(o);
        return at == null ? null : stand(w, BlockKey.of(at), false);
    }

    // ------------------------------------------------------------------
    // the work chain: crews, handing things over (no chests: worker to worker)
    // ------------------------------------------------------------------

    /** How far apart one player's workers can be and still work together (blocks). */
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
            return java.util.Collections.unmodifiableList(out);
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
        return java.util.Collections.unmodifiableList(out);
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

    /** Room for more of this item in the stacks of it that aren't full yet. */
    private static int partialRoom(Inventory inv, ItemStack it) {
        int n = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (s != null && !s.getType().isAir() && s.isSimilar(it)) {
                n += Math.max(0, s.getMaxStackSize() - s.getAmount());
            }
        }
        return n;
    }

    /** How many more of this item fit in the inventory. */
    private static int roomIn(Inventory inv, ItemStack it) {
        int free = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (s == null || s.getType().isAir()) {
                free++;
            }
        }
        return free * it.getMaxStackSize() + partialRoom(inv, it);
    }

    /** Another worker's satchel to take things from. stand = where to go, jump = the back way. */
    private record Source(Worker worker, Location stand, BlockKey where, boolean jump) {
    }

    /** Things a worker needs for their own job (they never hand these on, except spare seeds). */
    public boolean uses(Worker w, ItemStack it) {
        ItemType t = Items.type(it);
        return switch (w.type()) {
            case FARMHAND -> t == ItemType.FERTILIZER || PlantManager.kindOf(t) != null;
            case DRYER -> t == ItemType.BUD_FRESH && Items.strain(it) != null;
            case COOK -> cookUses(w, it);
            case RUNNER, SUPPLIER -> false;
        };
    }

    private static boolean cookUses(Worker w, ItemStack it) {
        if (w.mixes()) {
            return Items.type(it) == ItemType.SEED_PACK && Items.strain(it) != null;
        }
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
            if (ing.matches(it)) {
                return true;
            }
        }
        // empty bottles get filled with water for the next batch
        return it.getType() == Material.GLASS_BOTTLE && !Items.isCustom(it) && needsWater(r);
    }

    private static boolean needsWater(LabRecipe r) {
        return r != null && r.ingredients().stream().anyMatch(i -> i.vanilla() == Material.POTION);
    }

    /** What a worker makes: a Runner passes it on to whoever uses it, or sells it. */
    public boolean produces(Worker w, ItemStack it) {
        ItemType t = Items.type(it);
        if (t == null) {
            return false;
        }
        return switch (w.type()) {
            case FARMHAND -> t == ItemType.BUD_FRESH || t == ItemType.COCA_LEAVES || t == ItemType.POPPY_POD
                    || t == ItemType.MAGIC_MUSHROOM || t == ItemType.PEYOTE_BUTTON || t == ItemType.ERGOT;
            case DRYER -> t == ItemType.BUD_DRIED;
            case COOK -> w.mixes() ? t == ItemType.SEED_PACK
                    : w.autoPick() ? LabRecipe.making(t) != null : t == w.product();
            case RUNNER, SUPPLIER -> false;
        };
    }

    /** Empty buckets and bottles a Cook doesn't need. */
    private boolean junk(Worker w, ItemStack it) {
        return w.type() == WorkerType.COOK && !Items.isCustom(it) && !uses(w, it)
                && (it.getType() == Material.GLASS_BOTTLE || it.getType() == Material.BUCKET);
    }

    /** What a Runner sells: anything the dealer buys - never seeds (those get planted). */
    private boolean sellable(ItemStack it) {
        return it != null && !it.getType().isAir() && !isSeed(it) && plugin.shop().sellPrice(it) > 0;
    }

    private static boolean isSeed(ItemStack it) {
        return it != null && PlantManager.kindOf(Items.type(it)) != null;
    }

    private static String seedKey(ItemStack it) {
        Strain s = Items.strain(it);
        return Items.type(it).name() + ":" + (s == null ? "" : s.id());
    }

    /** Seeds a Farmhand keeps of each kind in the backpack; spare ones go to another Farmhand or a Cook mixing strains. */
    static final int SEED_KEEP = 32;
    /** A full backpack: common seeds past this many of a kind become fertilizer. */
    static final int COMPOST_KEEP = 64;

    /** A satchel running out of room: Runners empty it first, and sell what nobody can take in time. */
    static boolean tight(Worker w) {
        return w.freeSlots() < 6;
    }

    /** Seeds a worker keeps of each kind: a Farmhand 32 (in their backpack), a Cook mixing strains 2. */
    static int seedKeep(Worker w) {
        if (w.type() == WorkerType.FARMHAND) {
            return SEED_KEEP;
        }
        return w.type() == WorkerType.COOK && w.mixes() ? 2 : 0;
    }

    /** True when one of the owner's Runners works with this worker. */
    private boolean hasRunner(Worker w) {
        for (Worker o : crew(w)) {
            if (o.type() == WorkerType.RUNNER) {
                return true;
            }
        }
        return false;
    }

    /** A Dryer set to sell, with a Runner in the crew to take the buds. */
    private boolean sellsToRunner(Worker w) {
        return w.type() == WorkerType.DRYER && w.sell && hasRunner(w);
    }

    /** How many of each matching stack a worker can hand on, in the satchel and in the seed backpack (index = slot). */
    private record Spare(int[] satchel, int[] bag) {
        int total() {
            int n = 0;
            for (int c : satchel) {
                n += c;
            }
            for (int c : bag) {
                n += c;
            }
            return n;
        }
    }

    /**
     * What a worker can hand on: everything they don't use themselves, and seeds past
     * {@link #seedKeep} of a kind (counting the satchel and the backpack).
     */
    private Spare spare(Worker o, Predicate<ItemStack> want) {
        ItemStack[] items = o.satchel.getStorageContents();
        ItemStack[] bag = o.seeds.getStorageContents();
        int keep = seedKeep(o);
        Map<String, Integer> allowance = new HashMap<>();
        if (keep > 0) {
            Map<String, Integer> seeds = new HashMap<>();
            for (ItemStack[] inv : List.of(items, bag)) {
                for (ItemStack it : inv) {
                    if (isSeed(it) && uses(o, it)) {
                        seeds.merge(seedKey(it), it.getAmount(), Integer::sum);
                    }
                }
            }
            seeds.forEach((k, n) -> allowance.put(k, Math.max(0, n - keep)));
        }
        boolean reserved = sellsToRunner(o);
        return new Spare(spareOf(o, items, want, keep, allowance, reserved), spareOf(o, bag, want, keep, allowance, reserved));
    }

    private int[] spareOf(Worker o, ItemStack[] items, Predicate<ItemStack> want, int keep, Map<String, Integer> allowance,
                          boolean reserved) {
        int[] out = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            ItemStack it = items[i];
            if (it == null || it.getType().isAir() || !want.test(it)) {
                continue;
            }
            if (reserved && produces(o, it)) {
                continue; // a Dryer set to sell keeps its dried buds for the Runners
            }
            if (!uses(o, it)) {
                out[i] = it.getAmount();
            } else if (keep > 0 && isSeed(it)) {
                String k = seedKey(it);
                int can = Math.min(it.getAmount(), allowance.getOrDefault(k, 0));
                allowance.put(k, allowance.getOrDefault(k, 0) - can);
                out[i] = can;
            }
        }
        return out;
    }

    private int spareCount(Worker o, Predicate<ItemStack> want) {
        return spare(o, want).total();
    }

    /** Moves matching items from one inventory to another; returns how many moved. */
    private int move(Inventory from, Inventory to, Predicate<ItemStack> match, int max) {
        int[] limit = new int[from.getStorageContents().length];
        java.util.Arrays.fill(limit, Integer.MAX_VALUE);
        return move(from, to, match, max, limit);
    }

    private int move(Inventory from, Inventory to, Predicate<ItemStack> match, int max, int[] limit) {
        int moved = 0;
        ItemStack[] items = from.getStorageContents();
        for (int i = 0; i < items.length && moved < max; i++) {
            ItemStack it = items[i];
            if (it == null || it.getType().isAir() || !match.test(it) || limit[i] <= 0) {
                continue;
            }
            int want = Math.min(Math.min(it.getAmount(), limit[i]), max - moved);
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
        dirty |= moved > 0;
        return moved;
    }

    /** Takes the spare matching items out of another worker's satchel (and spare seeds out of their backpack). */
    private int take(Source src, Inventory to, Predicate<ItemStack> want, int max) {
        Worker o = src.worker();
        Spare sp = spare(o, want);
        int n = move(o.satchel, to, want, max, sp.satchel());
        if (n < max) {
            n += move(o.seeds, to, want, max - n, sp.bag());
        }
        return n;
    }

    private boolean has(Source src, Predicate<ItemStack> want) {
        return spareCount(src.worker(), want) > 0;
    }

    // ---- how much a worker takes (nobody gets so much they can't work any more) ----

    /** Free satchel slots a worker keeps for what they make: deliveries stop there. */
    static int reserve(Worker o) {
        return o.type() == WorkerType.FARMHAND ? 8 : 6;
    }

    /** The most of one kind of thing a worker wants to hold. */
    private int cap(Worker o, ItemStack it) {
        ItemType t = Items.type(it);
        switch (o.type()) {
            case FARMHAND:
                return isSeed(it) ? SEED_KEEP : 64;
            case DRYER:
                return 192;
            case COOK:
                if (o.mixes()) {
                    return seedKeep(o);
                }
                if (o.rolls() != null) {
                    return t == ItemType.BUD_DRIED ? 128 : 64;
                }
                LabRecipe r = o.recipe();
                if (r != null) {
                    for (LabRecipe.Ingredient ing : r.ingredients()) {
                        if (ing.matches(it) || matcher(ing).test(it)) {
                            return Math.max(16, ing.amount() * dev.kushcraft.lab.Cooking.MAX_BATCHES * 2);
                        }
                    }
                }
                return 16; // empty bottles
            default:
                return 0;
        }
    }

    private static boolean sameKind(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.getType().isAir()) {
            return false;
        }
        ItemType ta = Items.type(a);
        if (ta == null) {
            return Items.type(b) == null && a.getType() == b.getType();
        }
        return ta == Items.type(b) && (!isSeed(a) || seedKey(a).equals(seedKey(b)));
    }

    /** How many of the same kind they hold (seeds: of that strain, in the backpack too). */
    private static int holding(Worker o, ItemStack it) {
        int n = InventoryUtil.count(o.satchel, x -> sameKind(x, it));
        return isSeed(it) ? n + InventoryUtil.count(o.seeds, x -> sameKind(x, it)) : n;
    }

    /** How many of this a worker takes right now: they use it, are short of it and keep room for their own work. */
    int acceptAmount(Worker o, ItemStack it) {
        if (it == null || courier(o) || !uses(o, it)) {
            return 0;
        }
        int short_ = cap(o, it) - holding(o, it);
        if (short_ <= 0) {
            return 0;
        }
        int space;
        if (o.type() == WorkerType.FARMHAND && isSeed(it)) {
            space = roomIn(o.seeds, it);
        } else {
            space = partialRoom(o.satchel, it) + Math.max(0, o.freeSlots() - reserve(o)) * it.getMaxStackSize();
        }
        return Math.min(short_, space);
    }

    /** Gives another worker everything they take from a satchel (no more than they have room for). */
    private int handOver(Inventory from, Worker to) {
        int moved = 0;
        ItemStack[] items = from.getStorageContents();
        for (int i = 0; i < items.length; i++) {
            ItemStack it = items[i];
            if (it == null || it.getType().isAir()) {
                continue;
            }
            int n = Math.min(it.getAmount(), acceptAmount(to, it));
            if (n <= 0) {
                continue;
            }
            ItemStack part = it.clone();
            part.setAmount(n);
            int kept = 0;
            for (ItemStack left : stash(to, List.of(part))) {
                kept += left.getAmount();
            }
            int done = n - kept;
            ItemStack rest = it.clone();
            rest.setAmount(it.getAmount() - done);
            from.setItem(i, rest.getAmount() <= 0 ? null : rest);
            moved += done;
        }
        dirty |= moved > 0;
        return moved;
    }

    /** The satchels of crew members a worker can walk up to, the nearest first. Nothing behind walls. */
    private List<Source> sources(Worker w) {
        List<Source> out = new ArrayList<>();
        for (Worker o : crew(w)) {
            if (courier(o)) {
                continue;
            }
            Location st = standByWorker(w, o);
            if (st != null) {
                out.add(new Source(o, st, BlockKey.of(spot(o)), false));
            }
        }
        Location from = spot(w);
        out.sort(java.util.Comparator.comparingDouble(s -> s.stand().distanceSquared(from)));
        return out;
    }

    /** Every worker in the crew a courier can take things from (they get anywhere, walls or not). */
    private List<Source> crewSources(Worker courier, Worker except) {
        List<Source> out = new ArrayList<>();
        for (Worker o : crew(courier)) {
            if (o != except && !courier(o)) {
                out.add(source(courier, o));
            }
        }
        return out;
    }

    private Source source(Worker courier, Worker o) {
        BlockKey at = BlockKey.of(spot(o));
        return new Source(o, standOrJump(courier, at), at, standByWorker(courier, o) == null);
    }

    /**
     * Plans a trip to fetch matching items from a crew member they can walk to. Returns false when
     * nobody they can reach has any - or when a Runner works with them: then they stay put and the
     * Runner brings it (see Worker#want), or the Supplier buys it.
     */
    private boolean fetch(Worker w, Predicate<ItemStack> want, String what, int max) {
        if (w.freeSlots() == 0 || hasRunner(w)) {
            return false;
        }
        for (Source src : sources(w)) {
            if (!has(src, want)) {
                continue;
            }
            String from = src.worker().name();
            w.status = "Fetching " + what + " from " + from;
            go(w, src.where(), src.stand(), false, () -> {
                int n = take(src, w.satchel, want, max);
                if (n > 0) {
                    w.status = "Got " + n + " " + what + " from " + from;
                    if (w.type() == WorkerType.FARMHAND) {
                        bagSeeds(w);
                    }
                }
                return n > 0;
            });
            return true;
        }
        return false;
    }

    /** True when a crew member they can walk to has matching items (without planning a trip). */
    private boolean available(Worker w, Predicate<ItemStack> want) {
        for (Source src : sources(w)) {
            if (has(src, want)) {
                return true;
            }
        }
        return false;
    }

    /** Does a job where they stand. */
    private void here(Worker w, BooleanSupplier act) {
        w.steps.add(new Worker.Step(w.pos != null ? w.pos.clone() : w.home(), null, act));
    }

    /** After the steps planned so far: act wherever they are by then (it may plan more steps). */
    private void then(Worker w, BooleanSupplier act) {
        w.steps.add(new Worker.Step(null, null, act));
    }

    /** Tells Runners (and the Supplier) what this worker is missing. */
    private void want(Worker w, Predicate<ItemStack> match, String what, int amount) {
        w.want = new Worker.Want(match, what, amount);
    }

    /** The status of a worker whose satchel is full: yellow while a Runner will empty it, red otherwise. */
    private String full(Worker w, String what) {
        return hasRunner(w) ? "<yellow>Satchel full: waiting for a Runner to take " + what + "."
                : "<red>Satchel full: hire a Runner to take " + what + " (or take it out).";
    }

    /** A tight satchel: a Farmhand composts spare seeds, a Cook throws out empty bottles nobody needs. */
    private void tidy(Worker w) {
        if (w.type() == WorkerType.FARMHAND) {
            bagSeeds(w);
            compost(w);
        } else if (w.type() == WorkerType.COOK) {
            int n = InventoryUtil.remove(w.satchel, it -> junk(w, it), Integer.MAX_VALUE);
            dirty |= n > 0;
        }
    }

    // ---- farmhand ----

    /** The owner's plants within r blocks of the worker's home (looked up by chunk, not one by one). */
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
        return k != null && it.getAmount() > 0 && (k != Plant.Kind.CANNABIS || Items.strain(it) != null);
    }

    /** Seeds from the backpack that grow on this soil: the kind they have the most of. Null when none do. */
    private ItemStack seedFor(Worker w, Block soilBlock) {
        ItemStack best = null;
        for (ItemStack it : w.seeds.getStorageContents()) {
            if (plantable(it) && plugin.plants().isSoil(soilBlock, PlantManager.kindOf(Items.type(it)))
                    && (best == null || it.getAmount() > best.getAmount())) {
                best = it;
            }
        }
        return best;
    }

    /** A backpack (mostly) full of seeds: they plant before anything else, in big rounds. */
    static boolean lotsOfSeeds(Worker w) {
        return w.seedSlots() >= Worker.SEED_BAG / 2 || w.seedCount() >= 192;
    }

    private boolean planFarmhand(Worker w) {
        bagSeeds(w);
        if (w.seeds.firstEmpty() < 0) {
            compost(w);
        }
        if (!canPay(w)) {
            return false;
        }
        int r = radius(w);
        Location from = spot(w);
        // 1. seeds go in the ground right away: every empty farmland / Planter they can walk to
        if (planPlanting(w, lotsOfSeeds(w) ? 10 + 2 * w.level() : 4 + 2 * w.level())) {
            return true;
        }
        List<Plant> mine = plantsNear(w, r);
        // 2. harvest ripe plants they can walk to, a few in one round
        List<Plant> ripe = new ArrayList<>();
        for (Plant p : mine) {
            if (p.mature() && p.key().isLoaded() && reaches(w, p.key(), true)) {
                ripe.add(p);
            }
        }
        if (!ripe.isEmpty() && w.freeSlots() >= 2) {
            // the harvest takes about a slot a plant (seeds go in the backpack): never stop half way
            int room = Math.max(1, w.freeSlots() - 1);
            List<Plant> round = route(ripe, from, Math.min(2 + w.level(), room));
            w.status = "Harvesting " + round.size() + " plant" + (round.size() > 1 ? "s" : "");
            for (Plant target : round) {
                String what = plantName(target);
                go(w, target.key(), true, () -> {
                    if (plugin.plants().at(target.key()) != target || !target.mature()) {
                        return true; // someone else picked it: go on with the next one
                    }
                    if (w.freeSlots() == 0) {
                        return false; // full after all: come back for it once a Runner emptied the satchel
                    }
                    if (!canPay(w)) {
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
        // 3. fertilize growing plants, a few in one round
        ItemStack fert = first(w, it -> Items.type(it) == ItemType.FERTILIZER);
        List<Plant> growing = new ArrayList<>();
        for (Plant p : mine) {
            if (!p.fertilized() && !p.mature() && p.key().isLoaded() && reaches(w, p.key(), true)) {
                growing.add(p);
            }
        }
        if (fert != null && !growing.isEmpty()) {
            List<Plant> round = route(growing, from, Math.min(fert.getAmount(), 2 + w.level()));
            w.status = "Fertilizing " + round.size() + " plant" + (round.size() > 1 ? "s" : "");
            for (Plant target : round) {
                go(w, target.key(), true, () -> {
                    ItemStack f = first(w, it -> Items.type(it) == ItemType.FERTILIZER);
                    if (f == null) {
                        return false;
                    }
                    if (plugin.plants().at(target.key()) == target && plugin.plants().fertilize(target)) {
                        f.setAmount(f.getAmount() - 1);
                        dirty = true;
                    }
                    return true;
                });
            }
            return true;
        }
        // 4. empty farmland but no seeds that grow there: another Farmhand's spare ones, a Runner or the Supplier
        boolean bare = !emptySoil(w, null, 1).isEmpty();
        if (bare) {
            Predicate<ItemStack> seeds = Workers::plantable;
            if (fetch(w, seeds, "seeds", SEED_KEEP)) {
                return true;
            }
            want(w, seeds, "seeds", 16);
        }
        if (fert == null && !growing.isEmpty()) {
            if (fetch(w, it -> Items.type(it) == ItemType.FERTILIZER, "fertilizer", 16)) {
                return true;
            }
            if (w.want == null) {
                want(w, it -> Items.type(it) == ItemType.FERTILIZER, "fertilizer", 8);
            }
        }
        if (!ripe.isEmpty()) {
            w.status = full(w, "the harvest");
        } else if (bare) {
            w.status = "Out of seeds for the empty farmland" + (crew(w).stream().anyMatch(o -> o.type() == WorkerType.SUPPLIER)
                    ? " <gray>(the Supplier buys some)" : " <gray>(give them seeds or hire a Supplier)");
        } else {
            w.status = "Waiting for your plants to ripen";
        }
        return false;
    }

    /**
     * Plants seeds from the backpack on empty farmland and Planters they can walk to (whatever grows
     * there: the kind they have most of), up to max in one round. False when there's nothing to plant.
     */
    private boolean planPlanting(Worker w, int max) {
        if (w.seedCount() == 0) {
            return false;
        }
        List<Block> round = new ArrayList<>();
        int seeds = w.seedCount();
        for (Block b : emptySoil(w, null, 64)) {
            if (round.size() >= Math.min(max, seeds)) {
                break;
            }
            if (seedFor(w, b) != null) {
                round.add(b);
            }
        }
        if (round.isEmpty()) {
            return false;
        }
        w.status = "Planting " + round.size() + " seed" + (round.size() > 1 ? "s" : "") + " <dark_gray>(" + seeds
                + " in the backpack)";
        for (Block soilBlock : order(round, spot(w))) {
            BlockKey spot = BlockKey.of(soilBlock).up();
            go(w, spot, true, () -> {
                Block s = spot.block();
                if (s == null || !s.getType().isAir() || plugin.plants().at(spot) != null) {
                    return true; // planted already: on to the next one
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
                String name = Text.plain(seed.effectiveName());
                seed.setAmount(seed.getAmount() - 1);
                dirty = true;
                plugin.plants().plantAt(spot, kind, strain, w.owner());
                Location c = spot.bottomCenter();
                c.getWorld().playSound(c, "minecraft:item.crop.plant", SoundCategory.BLOCKS, 1f, 1f);
                pay(w);
                w.status = "Planted " + name;
                return true;
            });
        }
        return true;
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

    /**
     * A full seed backpack: every common seed past 64 of a kind becomes fertilizer (4 seeds = 1) -
     * never Rare and better strains, nor seeds a Cook of the crew uses (morphine base, mixing).
     */
    public void compost(Worker w) {
        if (w.seeds.firstEmpty() >= 0 && !tight(w)) {
            return;
        }
        List<Worker> crew = crew(w);
        Map<String, Integer> kept = new HashMap<>();
        int spare = 0;
        for (Inventory inv : List.of(w.seeds, w.satchel)) {
            ItemStack[] items = inv.getStorageContents();
            for (int i = 0; i < items.length; i++) {
                ItemStack it = items[i];
                if (!isSeed(it)) {
                    continue;
                }
                Strain st = Items.strain(it);
                if ((st != null && st.rarity().ordinal() >= dev.kushcraft.strain.Rarity.RARE.ordinal())
                        || crew.stream().anyMatch(o -> o.type() != WorkerType.FARMHAND && uses(o, it))) {
                    continue; // rare strains' seeds are never composted
                }
                String key = seedKey(it);
                int have = kept.getOrDefault(key, 0);
                int keep = Math.max(0, Math.min(it.getAmount(), COMPOST_KEEP - have));
                kept.put(key, have + keep);
                spare += it.getAmount() - keep;
                if (keep <= 0) {
                    inv.setItem(i, null);
                } else if (keep < it.getAmount()) {
                    ItemStack less = it.clone();
                    less.setAmount(keep);
                    inv.setItem(i, less);
                }
            }
        }
        if (spare >= 4) {
            drop(w, stash(w, List.of(Items.create(ItemType.FERTILIZER, spare / 4))), BlockKey.of(spot(w)));
            w.status = "Turned " + spare + " spare seeds into fertilizer";
        }
        dirty |= spare > 0;
    }

    private String plantName(Plant p) {
        return p.kind() == Plant.Kind.CANNABIS ? Text.plain(Text.mm(plugin.strains().getOrDefault(p.strainId()).colored()))
                : p.kind().display();
    }

    private record SoilList(long made, List<Block> blocks) {
    }

    /**
     * Empty farmland or the owner's Planters they can walk to, where this kind (null: anything) grows,
     * closest to where they are first. The search is kept for a few seconds (it reads a lot of blocks).
     */
    private List<Block> emptySoil(Worker w, Plant.Kind kind, int max) {
        long now = System.currentTimeMillis();
        SoilList cached = soil.get(w.id());
        if (cached == null || now - cached.made() > 8_000L) {
            cached = new SoilList(now, findSoil(w));
            soil.put(w.id(), cached);
        }
        Location from = spot(w);
        List<Block> found = new ArrayList<>();
        for (Block b : cached.blocks()) {
            Block above = b.getRelative(BlockFace.UP);
            if (above.getType().isAir() && plugin.plants().at(BlockKey.of(above)) == null
                    && (kind == null || plugin.plants().isSoil(b, kind))) {
                found.add(b);
            }
        }
        found.sort(java.util.Comparator.comparingDouble(b -> b.getLocation().distanceSquared(from)));
        return found.size() > max ? new ArrayList<>(found.subList(0, max)) : found;
    }

    private List<Block> findSoil(Worker w) {
        Location h = w.home();
        int r = radius(w);
        World world = h.getWorld();
        int hx = h.getBlockX(), hy = h.getBlockY(), hz = h.getBlockZ();
        List<Block> found = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r || !world.isChunkLoaded((hx + dx) >> 4, (hz + dz) >> 4)) {
                    continue;
                }
                for (int dy = -3; dy <= 1; dy++) {
                    Block soilBlock = world.getBlockAt(hx + dx, hy + dy, hz + dz);
                    Material type = soilBlock.getType();
                    if (type != Material.FARMLAND && type != Material.BARRIER) {
                        continue;
                    }
                    Machine m = type == Material.BARRIER ? plugin.machines().at(soilBlock) : null;
                    boolean planter = m != null && m.type() == MachineType.PLANTER_BOX && w.owner().equals(m.owner());
                    if (!planter && type != Material.FARMLAND) {
                        continue;
                    }
                    Block above = soilBlock.getRelative(BlockFace.UP);
                    BlockKey key = BlockKey.of(above);
                    if (plugin.machines().at(key) != null || !reaches(w, key, true)) {
                        continue;
                    }
                    found.add(soilBlock);
                }
            }
        }
        return found;
    }

    // ---- dryer ----

    /** The owner's Drug Labs within r that they can walk up to. */
    private List<Machine> labsNear(Worker w, int r) {
        List<Machine> labs = new ArrayList<>();
        Location h = w.home();
        if (h == null) {
            return labs;
        }
        for (Machine m : plugin.machines().near(w.worldName(), h.getBlockX(), h.getBlockZ(), r + 1)) {
            if (m.type() == MachineType.LAB_STATION && w.owner().equals(m.owner()) && near(m.key(), w, r)
                    && m.key().isLoaded() && reaches(w, m.key(), false)) {
                labs.add(m);
            }
        }
        Location from = spot(w);
        labs.sort(java.util.Comparator.comparingDouble(m -> dist2(m.key(), from)));
        return labs;
    }

    private String noLab(Worker w, int r) {
        Location h = w.home();
        for (Machine m : h == null ? List.<Machine>of()
                : plugin.machines().near(w.worldName(), h.getBlockX(), h.getBlockZ(), r + 1)) {
            if (m.type() == MachineType.LAB_STATION && w.owner().equals(m.owner()) && near(m.key(), w, r)) {
                return "<red>They can't walk to your Drug Lab - walls in the way.";
            }
        }
        return "<red>No Drug Lab of yours within " + r + " blocks.";
    }

    private boolean planDryer(Worker w) {
        int r = radius(w);
        List<Machine> labs = labsNear(w, r);
        if (labs.isEmpty()) {
            w.status = noLab(w, r);
            return false;
        }
        // 1. take dry buds off the racks
        boolean noRoom = false;
        for (Machine lab : labs) {
            if (lab.racksDry() > 0) {
                if (w.freeSlots() < lab.racksDry()) {
                    noRoom = true;
                    continue;
                }
                if (!canPay(w)) {
                    return false;
                }
                w.status = "Collecting dry buds";
                go(w, lab.key(), false, () -> {
                    List<ItemStack> dried = new ArrayList<>();
                    for (Machine.Rack rack : lab.takeDry()) {
                        dried.add(Items.strainItem(ItemType.BUD_DRIED, plugin.strains().getOrDefault(rack.strain()),
                                rack.quality(), rack.amount()));
                    }
                    if (dried.isEmpty()) {
                        return false;
                    }
                    drop(w, stash(w, dried), lab.key());
                    plugin.machines().markDirty();
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
                go(w, lab.key(), false, () -> {
                    int hung = hangAll(w, lab);
                    if (hung <= 0) {
                        return false;
                    }
                    plugin.machines().markDirty();
                    pay(w);
                    w.status = "Hung " + hung + " buds to dry";
                    return true;
                });
                return true;
            }
        }
        if (noRoom) {
            w.status = full(w, "the dried buds");
            return false;
        }
        // 3. fetch fresh buds from a Farmhand they can walk to - or a Runner brings them
        Predicate<ItemStack> fresh = it -> Items.type(it) == ItemType.BUD_FRESH && Items.strain(it) != null;
        if (first(w, it -> Items.type(it) == ItemType.BUD_FRESH) == null) {
            // never so many that there's no room left for the dried ones
            int room = (w.freeSlots() - reserve(w)) * 64;
            if (room > 0 && fetch(w, fresh, "fresh buds", room)) {
                return true;
            }
            want(w, fresh, "fresh buds", 16);
        }
        w.status = first(w, it -> Items.type(it) == ItemType.BUD_FRESH) != null ? "Waiting for a free rack"
                : "Waiting for fresh buds";
        return false;
    }

    // ---- cook ----

    /**
     * A Cook on AUTO picks what to make: the most valuable recipe they have everything for (in their
     * satchel or in the satchels of workers they can walk to). Keeps the current pick while it works.
     */
    private LabRecipe pickAuto(Worker w) {
        if (w.auto != null && canGather(w, w.auto)) {
            return w.auto;
        }
        for (LabRecipe r : byValue()) {
            if (canGather(w, r)) {
                return r;
            }
        }
        return null;
    }

    /** Every recipe, the most valuable batch first. */
    private List<LabRecipe> byValue() {
        List<LabRecipe> all = new ArrayList<>(List.of(LabRecipe.values()));
        all.sort(java.util.Comparator.comparingDouble((LabRecipe r) -> -plugin.shop().basePrice(r.output()) * r.amount()));
        return all;
    }

    private static Predicate<ItemStack> matcher(LabRecipe.Ingredient ing) {
        return ing.strainSource() ? it -> Items.type(it) == ing.custom() && Items.strain(it) != null : ing::matches;
    }

    private boolean canGather(Worker w, LabRecipe r) {
        for (LabRecipe.Ingredient ing : r.ingredients()) {
            Predicate<ItemStack> m = matcher(ing);
            if (InventoryUtil.count(w.satchel, m) >= ing.amount()) {
                continue;
            }
            if (ing.vanilla() == Material.POTION && first(w, it -> it.getType() == Material.GLASS_BOTTLE
                    && !Items.isCustom(it)) != null && waterNear(w) != null) {
                continue;
            }
            if (!available(w, m)) {
                return false;
            }
        }
        return true;
    }

    private boolean planCook(Worker w) {
        if (w.mixes()) {
            return planMix(w);
        }
        ItemType roll = w.rolls();
        if (roll != null) {
            return planRoll(w, roll);
        }
        int r = radius(w);
        List<Machine> labs = labsNear(w, r);
        if (w.autoPick()) {
            w.auto = pickAuto(w);
        }
        LabRecipe recipe = w.recipe();
        if (recipe == null && !w.autoPick()) {
            w.status = "<yellow>Pick a drug for them to make (button at the top).";
            return false;
        }
        if (labs.isEmpty()) {
            w.status = noLab(w, r);
            return false;
        }
        // 1. collect finished batches of their recipe (on AUTO: any batch no other Cook of yours makes)
        List<Worker> crew = crew(w);
        for (Machine lab : labs) {
            boolean mine = lab.job() != null && (w.autoPick() ? LabRecipe.parse(lab.job()) != null
                    && crew.stream().noneMatch(o -> o.type() == WorkerType.COOK && !o.autoPick()
                    && lab.job().equals(o.recipe)) : recipe != null && recipe.name().equals(lab.job()));
            if (lab.busy() && lab.jobDone() && lab.output() != null && mine) {
                if (w.freeSlots() < 1) {
                    w.status = full(w, "the drugs");
                    return false;
                }
                w.status = "Collecting a batch";
                go(w, lab.key(), false, () -> {
                    if (!lab.busy() || !lab.jobDone() || lab.output() == null) {
                        return false;
                    }
                    ItemStack out = lab.output().clone();
                    lab.clearJob();
                    plugin.machines().markDirty();
                    drop(w, stash(w, List.of(out)), lab.key());
                    w.status = "Collected " + out.getAmount() + " " + Text.plain(out.effectiveName());
                    return true;
                });
                return true;
            }
        }
        if (recipe == null) {
            w.status = "<yellow>Nothing to make: no ingredients for any drug "
                    + (crew.stream().anyMatch(o -> o.type() == WorkerType.SUPPLIER) ? "yet (the Supplier is on it)."
                    : "- hire a Supplier or give them some.");
            return false;
        }
        // 2. start a batch at a free lab
        LabRecipe.Ingredient missing = dev.kushcraft.lab.Cooking.missing(w.satchel, recipe, null, 0);
        if (missing == null) {
            for (Machine lab : labs) {
                if (lab.busy()) {
                    continue;
                }
                if (!canPay(w)) {
                    return false;
                }
                LabRecipe make = recipe;
                w.status = "Cooking " + make.output().display();
                go(w, lab.key(), false, () -> {
                    var res = dev.kushcraft.lab.Cooking.start(w.satchel, lab, make, dev.kushcraft.lab.Cooking.MAX_BATCHES,
                            null, 0, left -> drop(w, stash(w, List.of(left)), lab.key()));
                    if (!res.ok()) {
                        return false;
                    }
                    Location c = lab.key().center();
                    if (c != null) {
                        c.getWorld().playSound(c, "minecraft:block.brewing_stand.brew", SoundCategory.BLOCKS, 0.8f, 1f);
                    }
                    pay(w);
                    w.status = "Cooking " + res.batches() + " batch" + (res.batches() > 1 ? "es" : "") + " of "
                            + make.output().display();
                    return true;
                });
                return true;
            }
            w.status = "Waiting for the lab to finish";
            return false;
        }
        // 3. water: fill empty bottles at water nearby
        if (missing.vanilla() == Material.POTION && first(w, it -> it.getType() == Material.GLASS_BOTTLE && !Items.isCustom(it)) != null) {
            Block water = waterNear(w);
            if (water != null) {
                w.status = "Filling bottles with water";
                go(w, BlockKey.of(water), false, () -> fillBottles(w) > 0);
                return true;
            }
        }
        // 4. fetch what's missing from workers they can walk to
        Predicate<ItemStack> need = matcher(missing);
        if (fetch(w, need, missing.name(), missing.amount() * dev.kushcraft.lab.Cooking.MAX_BATCHES * 2)) {
            return true;
        }
        // 5. nobody nearby has it: Runners bring it, the Supplier buys it
        want(w, need, missing.name(), missing.amount() * dev.kushcraft.lab.Cooking.MAX_BATCHES);
        w.status = "<yellow>Needs " + missing.amount() + " " + missing.name() + " <gray>(a Runner or a Supplier brings it)";
        return false;
    }

    /** Cook rolling joints or blunts: no lab needed, they roll where they stand. */
    private boolean planRoll(Worker w, ItemType product) {
        ItemType wrap = product == ItemType.JOINT ? ItemType.ROLLING_PAPERS : ItemType.BLUNT_WRAP;
        int budsEach = product == ItemType.JOINT ? 1 : 2;
        dev.kushcraft.util.StrainStock.Group g = dev.kushcraft.util.StrainStock.pick(w.satchel, ItemType.BUD_DRIED,
                budsEach, null, 0);
        int wraps = InventoryUtil.count(w.satchel, it -> Items.type(it) == wrap);
        if (g != null && wraps > 0) {
            if (w.freeSlots() == 0 && first(w, it -> Items.type(it) == product) != null) {
                w.status = full(w, "the " + product.display().toLowerCase(java.util.Locale.ROOT) + "s");
                return false;
            }
            if (!canPay(w)) {
                return false;
            }
            w.status = "Rolling " + product.display().toLowerCase(java.util.Locale.ROOT) + "s";
            here(w, () -> {
                var group = dev.kushcraft.util.StrainStock.pick(w.satchel, ItemType.BUD_DRIED, budsEach, null, 0);
                int have = InventoryUtil.count(w.satchel, it -> Items.type(it) == wrap);
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
        if (g == null && fetch(w, buds, "dried buds", 64)) {
            return true;
        }
        if (wraps == 0 && fetch(w, it -> Items.type(it) == wrap, wrap.display(), 64)) {
            return true;
        }
        if (g == null) {
            want(w, buds, "dried buds", 16);
        } else {
            want(w, it -> Items.type(it) == wrap, wrap.display(), 16);
        }
        w.status = "<yellow>Needs " + (g == null ? budsEach + " Dried Bud" : wrap.display())
                + " <gray>(a Runner or a Supplier brings it)";
        return false;
    }

    // ---- cook: mixing strains ----

    private double mixCost() {
        return Math.max(0, plugin.getConfig().getDouble("strain-maker.cost", 1500));
    }

    private int maxStrains() {
        return plugin.getConfig().getInt("strain-maker.max-per-player", 25);
    }

    private int seedsGiven() {
        return Math.max(1, plugin.getConfig().getInt("strain-maker.seeds-given", 3));
    }

    /** The two best different strains a Cook has seeds of (rarest, then strongest), or null. */
    public Strain[] mixPair(Worker w) {
        Map<String, Strain> have = new LinkedHashMap<>();
        for (ItemStack it : w.satchel.getStorageContents()) {
            Strain s = Items.type(it) == ItemType.SEED_PACK ? Items.strain(it) : null;
            if (s != null) {
                have.putIfAbsent(s.id(), s);
            }
        }
        if (have.size() < 2) {
            return null;
        }
        List<Strain> best = new ArrayList<>(have.values());
        best.sort(java.util.Comparator.comparingInt((Strain s) -> -s.rarity().ordinal())
                .thenComparingInt(s -> -s.potency()));
        return new Strain[]{best.get(0), best.get(1)};
    }

    /** Cook mixing strains: crosses the two best strains they have seeds of at a Drug Lab, with your money. */
    private boolean planMix(Worker w) {
        int r = radius(w);
        List<Machine> labs = labsNear(w, r);
        if (labs.isEmpty()) {
            w.status = noLab(w, r);
            return false;
        }
        if (plugin.strains().countCreatedBy(w.owner()) >= maxStrains()) {
            w.status = "<red>You bred the most strains you can (" + maxStrains() + ").";
            return false;
        }
        Strain[] pair = mixPair(w);
        if (pair == null) {
            Predicate<ItemStack> seeds = it -> Items.type(it) == ItemType.SEED_PACK && Items.strain(it) != null;
            if (fetch(w, seeds, "seeds to mix", 8)) {
                return true;
            }
            want(w, seeds, "seeds of two strains", 4);
            w.status = "<yellow>Needs seeds of two strains <gray>(a Farmhand's spare ones, a Runner or a Supplier)";
            return false;
        }
        if (plugin.economy().balance(Bukkit.getOfflinePlayer(w.owner())) < mixCost() + wage(w.type())) {
            w.status = "<red>Not paid! Mixing costs " + plugin.economy().format(mixCost()) + ".";
            return false;
        }
        Machine lab = labs.get(0);
        w.status = "Mixing " + pair[0].name() + " x " + pair[1].name();
        go(w, lab.key(), false, () -> mixAt(w, lab));
        return true;
    }

    private static final java.util.Random RANDOM = new java.util.Random();

    /** One cross at the lab: pays, uses a seed of each parent, keeps the child when it's rare enough. */
    boolean mixAt(Worker w, Machine lab) {
        Strain[] pair = mixPair(w);
        if (pair == null) {
            return false;
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(w.owner());
        if (!plugin.economy().withdraw(owner, mixCost())) {
            w.status = "<red>Not paid! Mixing costs " + plugin.economy().format(mixCost()) + ".";
            return false;
        }
        pay(w);
        w.spent += mixCost();
        for (Strain s : pair) {
            InventoryUtil.remove(w.satchel, it -> Items.type(it) == ItemType.SEED_PACK && Items.strain(it) != null
                    && Items.strain(it).id().equals(s.id()), 1);
        }
        dev.kushcraft.strain.Breeding.Result res = dev.kushcraft.strain.Breeding.cross(pair[0], pair[1], RANDOM,
                plugin.strains().all());
        Location c = lab.key().center();
        if (c != null) {
            c.getWorld().playSound(c, "minecraft:block.brewing_stand.brew", SoundCategory.BLOCKS, 0.8f, 1.4f);
            c.getWorld().spawnParticle(Particle.WITCH, c.clone().add(0, 0.8, 0), 12, 0.3, 0.3, 0.3, 0.02);
        }
        Strain child = res.discovered();
        if (child == null && res.rarity().ordinal() < w.keepRarity().ordinal()) {
            w.status = "Mixed a " + res.rarity().display() + " strain: not good enough, threw it away";
            return true;
        }
        Player online = owner.getPlayer();
        if (child == null) {
            if (plugin.strains().countCreatedBy(w.owner()) >= maxStrains()) {
                w.status = "<red>You bred the most strains you can (" + maxStrains() + ").";
                return false;
            }
            String name = dev.kushcraft.strain.Breeding.childName(pair[0], pair[1], plugin.strains()::nameTaken);
            child = plugin.strains().create(name, res, w.owner(), owner.getName() == null ? "?" : owner.getName());
        }
        drop(w, stash(w, List.of(Items.strainItem(ItemType.SEED_PACK, child, 3, seedsGiven()))), lab.key());
        w.status = "Bred " + child.rarity().display() + " " + child.name() + "!";
        if (online != null) {
            plugin.awards().bred(online, child.rarity());
            online.sendMessage(Text.msg(w.type().colored() + " " + Text.escape(w.name) + " <gray>bred a "
                    + child.rarity().colored() + " <gray>strain: " + child.colored() + " <gray>(" + child.potency()
                    + "% THC)"));
        }
        if (child.rarity().ordinal() >= dev.kushcraft.strain.Rarity.MYTHIC.ordinal()) {
            Bukkit.broadcast(Text.msg("<white>" + Text.escape(String.valueOf(owner.getName())) + "<gray>'s Cook bred a "
                    + child.rarity().colored() + " <gray>strain: " + child.colored() + "<gray>!"));
        }
        return true;
    }

    /** A water source block they can walk up to near home (for filling bottles), or null. Kept for a while. */
    private Block waterNear(Worker w) {
        long now = System.currentTimeMillis();
        WaterSpot cached = water.get(w.id());
        if (cached != null && now - cached.made() < 30_000L
                && (cached.block() == null || cached.block().getType() == Material.WATER)) {
            return cached.block();
        }
        Block found = findWater(w, Math.min(radius(w), 10));
        water.put(w.id(), new WaterSpot(now, found));
        return found;
    }

    private record WaterSpot(long made, Block block) {
    }

    private Block findWater(Worker w, int r) {
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
                        if (d < bestD && reaches(w, BlockKey.of(b), false)) {
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

    private static List<ItemStack> waterBottles(int n) {
        ItemStack water = new ItemStack(Material.POTION, 1);
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

    // ---- runner ----

    /** Share of every sale the Runner keeps (0.1 = 10%). */
    public double runnerCut() {
        return Math.max(0, Math.min(0.9, plugin.getConfig().getDouble("workers.runner.cut", 0.1)));
    }

    /** Where a courier stands to reach target: a spot they can walk to, else right by it (the back way). */
    private Location standOrJump(Worker w, BlockKey target) {
        Location st = stand(w, target, false);
        return st != null ? st : jumpSpot(target);
    }

    /** A free spot right next to target (ignoring walls), or the target itself. */
    private Location jumpSpot(BlockKey target) {
        World world = target.bukkitWorld();
        if (world == null) {
            return null;
        }
        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
            for (int dy = 0; dy >= -1; dy--) {
                Block feet = world.getBlockAt(target.x() + o[0], target.y() + dy, target.z() + o[1]);
                if (WalkArea.walkable(feet)) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return target.bottomCenter();
    }

    /**
     * The Runner keeps the chain moving, worker to worker (no chests): they empty the other workers'
     * satchels, take a Farmhand's fresh buds to the Dryer, the Dryer's buds to a Cook who uses them,
     * spare seeds to whoever plants or mixes them - and sell everything else the moment they have it
     * (never seeds). Nobody gets more than they have room for, so nobody gets stuck with a full satchel.
     */
    private boolean planRunner(Worker w) {
        List<Worker> crew = crew(w);
        // 1. what they carry: hand it to whoever takes it, sell the rest right here
        if (w.carried() > 0 && planUnload(w, crew)) {
            return true;
        }
        // 2. bring a worker what they're missing (from anywhere in the crew, the back way)
        if (w.freeSlots() > 2 && planDelivery(w, crew)) {
            return true;
        }
        // 3. empty a satchel: what someone takes goes on, what sells is sold on the spot
        if (w.freeSlots() > 2 && planPickup(w, crew)) {
            return true;
        }
        if (w.freeSlots() <= 2) {
            w.status = "<yellow>Satchel full of things nobody needs or buys: take them out of their satchel.";
        } else {
            w.status = crew.isEmpty() ? "<yellow>Hire workers near them: they carry and sell what they make."
                    : "Waiting for something to carry";
        }
        return false;
    }

    /** True when handing this to o helps: they use it, are short of it and have room (see acceptAmount). */
    private boolean takesFrom(Worker o, ItemStack it) {
        return acceptAmount(o, it) > 0;
    }

    /** The crew member nearest to from who takes this (see takesFrom), not counting except. */
    private Worker taker(List<Worker> crew, ItemStack it, Worker except, Location from) {
        Worker best = null;
        double bestD = Double.MAX_VALUE;
        for (Worker o : crew) {
            if (o == except || courier(o)) {
                continue;
            }
            Location at = spot(o);
            double d = at == null || from == null || !at.getWorld().equals(from.getWorld()) ? Double.MAX_VALUE / 2
                    : at.distanceSquared(from);
            if (d < bestD && takesFrom(o, it)) {
                bestD = d;
                best = o;
            }
        }
        return best;
    }

    /** What a Runner carries: sold right where they are unless a crew member takes it; the rest to whoever does. */
    private boolean planUnload(Worker w, List<Worker> crew) {
        Location from = spot(w);
        Predicate<ItemStack> sell = it -> sellable(it) && taker(crew, it, null, from) == null;
        if (first(w, sell) != null) {
            w.status = "Selling";
            here(w, () -> sellAll(w, sell) > 0);
            return true;
        }
        for (ItemStack it : w.satchel.getStorageContents()) {
            if (it == null || it.getType().isAir()) {
                continue;
            }
            Worker target = taker(crew, it, null, from);
            if (target != null) {
                handTo(w, target, Text.plain(it.effectiveName()), null);
                return true;
            }
        }
        // seeds nobody is short of: into a Farmhand's backpack anyway (it holds thousands)
        ItemStack seed = first(w, Workers::isSeed);
        if (seed != null) {
            for (Worker o : crew) {
                if (o.type() == WorkerType.FARMHAND && roomIn(o.seeds, seed) > 0) {
                    BlockKey at = BlockKey.of(spot(o));
                    w.status = "Bringing seeds to " + o.name();
                    go(w, at, standOrJump(w, at), standByWorker(w, o) == null,
                            () -> move(w.satchel, o.seeds, Workers::isSeed, Integer.MAX_VALUE) > 0);
                    return true;
                }
            }
        }
        return false;
    }

    /** Walks to target and gives them what they take from the satchel. from: who it came from (for the award). */
    private void handTo(Worker w, Worker target, String what, Worker from) {
        BlockKey at = BlockKey.of(spot(target));
        w.status = "Bringing " + what + " to " + target.name();
        go(w, at, standOrJump(w, at), standByWorker(w, target) == null, () -> {
            int n = handOver(w.satchel, target);
            if (n > 0) {
                target.want = null;
                target.restTicks = 1;
                w.jobs++;
                dirty = true;
                w.status = "Gave " + n + " " + what + " to " + target.name();
                if (target.type() == WorkerType.DRYER) {
                    Player p = Bukkit.getPlayer(w.owner());
                    if (p != null) {
                        plugin.awards().logistics(p);
                    }
                }
            }
            return n > 0;
        });
    }

    /** One delivery: pick up what a crew member is missing, take it to them. */
    private boolean planDelivery(Worker w, List<Worker> crew) {
        for (Worker o : crew) {
            Worker.Want want = o.want;
            if (want == null || courier(o) || (o.freeSlots() == 0 && o.type() != WorkerType.FARMHAND)) {
                continue;
            }
            for (Source src : crewSources(w, o)) {
                if (!has(src, want.match())) {
                    continue;
                }
                int max = Math.max(1, want.amount());
                w.status = "Getting " + want.what() + " for " + o.name();
                go(w, src.where(), src.stand(), src.jump(), () -> take(src, w.satchel, want.match(), max) > 0);
                handTo(w, o, want.what(), src.worker());
                o.want = null; // someone is on it
                return true;
            }
        }
        return false;
    }

    /**
     * One pickup, from the crew member with the most to hand on (a filling satchel first, then whoever
     * a worker is waiting on): everything they don't need that someone takes or the dealer buys. They
     * wait for a few at a time (less running around). What nobody can take in time is sold on the spot.
     */
    private boolean planPickup(Worker w, List<Worker> crew) {
        Worker best = null;
        Predicate<ItemStack> bestGoes = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        Location from = spot(w);
        for (Worker o : crew) {
            if (courier(o)) {
                continue;
            }
            Location oAt = spot(o);
            boolean selling = sellsToRunner(o);
            boolean filling = tight(o) || o.freeSlots() <= reserve(o);
            // what goes: someone takes it, or it sells and nobody else uses it (or their satchel is filling up)
            Predicate<ItemStack> goes = it -> taker(crew, it, o, oAt) != null
                    || (sellable(it) && (filling || crew.stream().noneMatch(u -> u != o && uses(u, it))));
            int n = spareCount(o, goes) + (selling ? InventoryUtil.count(o.satchel, x -> produces(o, x) && sellable(x)) : 0);
            if (n <= 0) {
                continue;
            }
            boolean waiting = false;
            // worth a trip: a few things, something worth a lot, or a Runner with nothing else to do for a while
            boolean worth = n >= 4 || (w.idleSince > 0 && System.currentTimeMillis() - w.idleSince > 20_000L)
                    || saleValue(o, goes) >= 60;
            if (!filling && !worth) {
                // a worker with none of it right now (a Dryer with nothing to hang) doesn't wait for more
                for (ItemStack it : o.satchel.getStorageContents()) {
                    if (it != null && !it.getType().isAir() && !uses(o, it)) {
                        Worker t = taker(crew, it, o, oAt);
                        if (t != null && holding(t, it) == 0) {
                            waiting = true;
                            break;
                        }
                    }
                }
                if (!waiting) {
                    continue;
                }
            }
            double dist = oAt == null || from == null || !oAt.getWorld().equals(from.getWorld()) ? 0
                    : Math.sqrt(oAt.distanceSquared(from));
            double score = n + (filling ? 1000 : 0) + (waiting ? 200 : 0) - dist;
            if (score > bestScore) {
                bestScore = score;
                best = o;
                bestGoes = goes;
            }
        }
        if (best == null) {
            return false;
        }
        Worker o = best;
        Predicate<ItemStack> goes = bestGoes;
        boolean selling = sellsToRunner(o);
        Source src = source(w, o);
        w.status = "Picking up from " + o.name();
        go(w, src.where(), src.stand(), src.jump(), () -> {
            int got = take(src, w.satchel, goes, Integer.MAX_VALUE);
            if (selling) {
                got += move(o.satchel, w.satchel, x -> produces(o, x) && sellable(x), Integer.MAX_VALUE);
            }
            if (got <= 0) {
                return false;
            }
            w.status = "Picked up " + got + " things from " + o.name();
            // sold right here: what sells and nobody takes (a Dryer set to sell: everything it dried)
            List<Worker> c = crew(w);
            Location at = spot(w);
            sellAll(w, it -> sellable(it) && ((selling && produces(o, it)) || taker(c, it, null, at) == null));
            return true;
        });
        // and straight on to whoever takes the rest
        then(w, () -> {
            if (w.carried() > 0) {
                planUnload(w, crew(w));
            }
            return true;
        });
        return true;
    }

    /** What the spare matching items of a worker would sell for (for the Runner: is it worth a trip?). */
    private double saleValue(Worker o, Predicate<ItemStack> which) {
        Spare sp = spare(o, which);
        ItemStack[] items = o.satchel.getStorageContents();
        double v = 0;
        for (int i = 0; i < items.length; i++) {
            if (sp.satchel()[i] > 0 && sellable(items[i])) {
                v += plugin.shop().sellPrice(items[i]) * sp.satchel()[i];
            }
        }
        return v;
    }

    /** Sells everything sellable in a Runner's satchel for the owner. Returns what the owner got. */
    public double sellAll(Worker w) {
        return sellAll(w, it -> true);
    }

    /** Sells the sellable items in a Runner's satchel that match. Returns what the owner got. */
    double sellAll(Worker w, Predicate<ItemStack> which) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(w.owner());
        Player online = owner.getPlayer();
        double total = 0;
        int count = 0;
        Map<ItemType, Integer> sold = new java.util.EnumMap<>(ItemType.class);
        ItemStack[] items = w.satchel.getStorageContents();
        for (int i = 0; i < items.length; i++) {
            ItemStack it = items[i];
            if (!sellable(it) || !which.test(it)) {
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
        plugin.economy().deposit(owner, paid);
        sold.forEach((t, n) -> plugin.market().sold(t, n));
        if (online != null) {
            plugin.ranks().sold(online, paid);
            online.sendActionBar(Text.mm("<yellow>" + Text.escape(w.name()) + " <gray>sold " + count + " items: <gold>+"
                    + plugin.economy().format(paid)));
        } else {
            plugin.economy().addSales(owner, paid);
            plugin.cartels().sold(w.owner(), paid);
            plugin.ranks().refresh();
        }
        w.jobs++;
        w.wages += cut;
        dirty = true;
        Location c = spot(w);
        if (c != null) {
            c.getWorld().playSound(c, "minecraft:entity.villager.yes", SoundCategory.NEUTRAL, 0.8f, 1.1f);
            c.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, c.clone().add(0, 1.6, 0), 8, 0.3, 0.3, 0.3, 0);
        }
        w.status = "Sold " + count + " items for " + plugin.economy().format(paid);
        return Math.max(0.01, paid);
    }

    // ---- supplier ----

    /** Water bottles, which the Supplier gets for a small price each. */
    public double waterPrice() {
        return Math.max(0, plugin.getConfig().getDouble("workers.supplier.water-price", 2));
    }

    /**
     * One thing to buy: n of what, at price each, from Trade (material), the Shop's gear or seeds
     * (entry, bought in packs), or water.
     */
    public record Buy(String name, int amount, double each, Material material, ItemType gear, boolean water,
                      dev.kushcraft.shop.Shop.BuyEntry entry) {
        public double cost() {
            return Math.round(each * amount * 100) / 100.0;
        }

        List<ItemStack> items(dev.kushcraft.shop.Shop shop) {
            if (water) {
                return waterBottles(amount);
            }
            List<ItemStack> out = new ArrayList<>();
            if (entry != null) {
                for (int left = amount; left > 0; left -= Math.max(1, entry.amount())) {
                    out.add(shop.create(entry));
                }
                return out;
            }
            ItemStack one = gear != null ? Items.create(gear) : new ItemStack(material);
            for (int left = amount; left > 0; left -= one.getMaxStackSize()) {
                ItemStack s = one.clone();
                s.setAmount(Math.min(left, one.getMaxStackSize()));
                out.add(s);
            }
            return out;
        }
    }

    /** What the Supplier can buy for this ingredient (null: it isn't sold anywhere). */
    private Buy buyable(LabRecipe.Ingredient ing, int n) {
        if (ing.strainSource() || n <= 0) {
            return null;
        }
        if (ing.vanilla() == Material.POTION) {
            return new Buy("Water Bottle", n, waterPrice(), null, null, true, null);
        }
        if (ing.vanilla() != null) {
            var offer = plugin.exchange().enabled() ? plugin.exchange().offer(ing.vanilla()) : null;
            return offer == null ? null : new Buy(ing.name(), n, plugin.exchange().buyPrice(offer), ing.vanilla(), null,
                    false, null);
        }
        return gear(ing.custom(), n);
    }

    /** A KushCraft supply from the Shop's gear (solvent, papers, wraps, fertilizer), or null. */
    private Buy gear(ItemType t, int n) {
        for (var e : plugin.shop().gear()) {
            if (e.type() == t && e.amount() > 0) {
                int packs = (n + e.amount() - 1) / e.amount();
                return new Buy(t.display(), packs * e.amount(), e.price() / e.amount(), null, t, false, null);
            }
        }
        return null;
    }

    /** Packs of seeds from the Shop. */
    private Buy seedPack(dev.kushcraft.shop.Shop.BuyEntry e, int packs) {
        int n = Math.max(1, e.amount());
        String name = e.strain() != null ? plugin.strains().getOrDefault(e.strain()).name() + " seeds" : e.type().display();
        return new Buy(name, n * Math.max(1, packs), e.price() / n, null, null, false, e);
    }

    /**
     * The recipe a Cook on AUTO should be stocked for: the most valuable one where everything is
     * either at hand (their satchel or workers they can walk to) or can be bought.
     */
    private LabRecipe autoTarget(Worker o) {
        for (LabRecipe r : byValue()) {
            boolean ok = true;
            for (LabRecipe.Ingredient ing : r.ingredients()) {
                Predicate<ItemStack> m = matcher(ing);
                if (InventoryUtil.count(o.satchel, m) < ing.amount() && !available(o, m)
                        && buyable(ing, ing.amount()) == null) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return r;
            }
        }
        return null;
    }

    /** Everything a worker is short of that can be bought (a few batches' worth). No budget: you pay. */
    public List<Buy> needs(Worker o) {
        List<Buy> out = new ArrayList<>();
        if (o.type() == WorkerType.COOK) {
            if (o.mixes()) {
                if (mixPair(o) == null) {
                    Set<String> have = new HashSet<>();
                    for (ItemStack it : o.satchel.getStorageContents()) {
                        if (Items.type(it) == ItemType.SEED_PACK && Items.strain(it) != null) {
                            have.add(Items.strain(it).id());
                        }
                    }
                    // the two rarest strains the Shop sells that they don't have yet
                    List<dev.kushcraft.shop.Shop.BuyEntry> seeds = new ArrayList<>();
                    for (var e : plugin.shop().seeds()) {
                        if (e.type() == ItemType.SEED_PACK && e.strain() != null && !have.contains(e.strain())) {
                            seeds.add(e);
                        }
                    }
                    seeds.sort(java.util.Comparator.comparingDouble(e -> -e.price()));
                    for (int i = 0; i < seeds.size() && have.size() + i < 2; i++) {
                        out.add(seedPack(seeds.get(i), 1));
                    }
                }
                return out;
            }
            ItemType roll = o.rolls();
            if (roll != null) {
                ItemType wrap = roll == ItemType.JOINT ? ItemType.ROLLING_PAPERS : ItemType.BLUNT_WRAP;
                int have = InventoryUtil.count(o.satchel, it -> Items.type(it) == wrap);
                if (have < 8) {
                    Buy b = gear(wrap, 32 - have);
                    if (b != null) {
                        out.add(b);
                    }
                }
                return out;
            }
            LabRecipe r = o.recipe();
            if (r == null && o.autoPick()) {
                r = autoTarget(o);
            }
            if (r == null) {
                return out;
            }
            int batches = dev.kushcraft.lab.Cooking.MAX_BATCHES;
            for (LabRecipe.Ingredient ing : r.ingredients()) {
                int have = InventoryUtil.count(o.satchel, ing::matches);
                if (have < ing.amount() * 2) {
                    Buy b = buyable(ing, ing.amount() * batches - have);
                    if (b != null) {
                        out.add(b);
                    }
                }
            }
        } else if (o.type() == WorkerType.FARMHAND) {
            // fertilizer while plants are growing
            int have = InventoryUtil.count(o.satchel, it -> Items.type(it) == ItemType.FERTILIZER);
            if (have < 4 && plantsNear(o, radius(o)).stream().anyMatch(p -> !p.mature() && !p.fertilized())) {
                Buy b = gear(ItemType.FERTILIZER, 16 - have);
                if (b != null) {
                    out.add(b);
                }
            }
            // seeds for the empty farmland they have nothing to plant on (up to 16 at a time)
            List<Block> empty = emptySoil(o, null, 64);
            if (!empty.isEmpty()) {
                int seeds = 0;
                for (ItemStack it : o.seeds.getStorageContents()) {
                    if (plantable(it) && empty.stream().anyMatch(b -> plugin.plants().isSoil(b,
                            PlantManager.kindOf(Items.type(it))))) {
                        seeds += it.getAmount();
                    }
                }
                int short_ = empty.size() - seeds;
                var e = short_ > 0 ? seedsFor(o) : null;
                if (e != null) {
                    out.add(seedPack(e, (Math.min(16, short_) + Math.max(1, e.amount()) - 1) / Math.max(1, e.amount())));
                }
            }
        }
        return out;
    }

    /** Seeds for a Farmhand with empty soil: of the strain (or plant) grown most around them, else the cheapest. */
    private dev.kushcraft.shop.Shop.BuyEntry seedsFor(Worker o) {
        Map<String, Integer> grown = new HashMap<>();
        for (Plant p : plantsNear(o, radius(o))) {
            grown.merge(p.kind() == Plant.Kind.CANNABIS ? "S:" + p.strainId() : "K:" + p.kind().name(), 1, Integer::sum);
        }
        Map<Plant.Kind, Boolean> grows = new java.util.EnumMap<>(Plant.Kind.class);
        dev.kushcraft.shop.Shop.BuyEntry best = null;
        int bestN = -1;
        for (var e : plugin.shop().seeds()) {
            Plant.Kind k = PlantManager.kindOf(e.type());
            if (k == null || !grows.computeIfAbsent(k, kk -> !emptySoil(o, kk, 1).isEmpty())) {
                continue; // nowhere to plant it
            }
            // what's grown around them most, then weed, then the cheapest
            int n = grown.getOrDefault(k == Plant.Kind.CANNABIS ? "S:" + e.strain() : "K:" + k.name(), 0) * 2
                    + (k == Plant.Kind.CANNABIS ? 1 : 0);
            if (n > bestN || (n == bestN && best != null && e.price() < best.price())) {
                best = e;
                bestN = n;
            }
        }
        return best;
    }

    private boolean planSupplier(Worker w) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(w.owner());
        String broke = null;
        // whoever is waiting for something first
        List<Worker> crew = new ArrayList<>(crew(w));
        crew.sort(java.util.Comparator.comparingInt(o -> o.want == null ? 1 : 0));
        for (Worker o : crew) {
            if (courier(o)) {
                continue;
            }
            List<Buy> buys = needs(o);
            if (buys.isEmpty()) {
                continue;
            }
            boolean onlySeeds = o.type() == WorkerType.FARMHAND && buys.stream().allMatch(b -> b.entry() != null);
            if (o.freeSlots() == 0 && !(onlySeeds && o.seeds.firstEmpty() >= 0)) {
                continue; // nothing fits: a Runner empties their satchel first
            }
            double cost = wage(w.type()) + buys.get(0).cost();
            if (plugin.economy().balance(owner) < cost) {
                broke = "<red>Not enough money to buy " + buys.get(0).name() + " for " + o.name() + " ("
                        + plugin.economy().format(cost) + ").";
                continue;
            }
            BlockKey to = BlockKey.of(spot(o));
            w.status = "Buying " + buys.get(0).name() + (buys.size() > 1 ? " and more" : "") + " for " + o.name();
            go(w, to, standOrJump(w, to), standByWorker(w, o) == null, () -> deliverPurchase(w, o));
            return true;
        }
        if (broke != null) {
            w.status = broke;
            return false;
        }
        w.status = crew(w).isEmpty() ? "<yellow>Hire Cooks or Farmhands near them to supply."
                : "Everyone has what they need";
        return false;
    }

    /** At the worker: buy what they need right now (prices may have moved) and hand it over. */
    private boolean deliverPurchase(Worker w, Worker o) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(w.owner());
        List<Buy> buys = needs(o);
        double spent = 0;
        int items = 0;
        for (Buy b : buys) {
            if (plugin.economy().balance(owner) < b.cost() + wage(w.type()) || !plugin.economy().withdraw(owner, b.cost())) {
                continue;
            }
            if (b.material() != null) {
                plugin.exchange().bought(b.material(), b.amount());
            }
            List<ItemStack> left = stash(o, b.items(plugin.shop()));
            if (!left.isEmpty()) {
                List<ItemStack> rest = new ArrayList<>();
                for (ItemStack it : left) {
                    rest.addAll(w.satchel.addItem(it).values());
                }
                drop(o, rest, BlockKey.of(spot(o)));
            }
            spent += b.cost();
            items += b.amount();
        }
        if (items == 0) {
            return false;
        }
        pay(w);
        o.want = null;
        o.restTicks = 1;
        soil.remove(o.id());
        w.spent += spent;
        w.status = "Bought " + items + " items for " + o.name() + " (" + plugin.economy().format(spent) + ")";
        Player online = owner.getPlayer();
        if (online != null && spent >= 50) {
            online.sendActionBar(Text.mm("<yellow>" + Text.escape(w.name()) + " <gray>bought supplies for "
                    + Text.escape(o.name()) + ": <gold>-" + plugin.economy().format(spent)));
        }
        Location c = spot(w);
        if (c != null) {
            c.getWorld().playSound(c, "minecraft:item.bundle.drop_contents", SoundCategory.NEUTRAL, 0.8f, 1f);
        }
        return true;
    }

    /** Everything your workers made (not what they need) goes to your inventory. Returns how many items. */
    public int collectAll(Player p) {
        int n = 0;
        for (Worker w : of(p.getUniqueId())) {
            ItemStack[] items = w.satchel.getStorageContents();
            for (int i = 0; i < items.length; i++) {
                ItemStack it = items[i];
                if (it == null || it.getType().isAir() || uses(w, it)
                        || !(produces(w, it) || w.type() == WorkerType.RUNNER)) {
                    continue;
                }
                Map<Integer, ItemStack> left = p.getInventory().addItem(it.clone());
                int kept = left.isEmpty() ? 0 : left.values().iterator().next().getAmount();
                n += it.getAmount() - kept;
                if (kept > 0) {
                    ItemStack rest = it.clone();
                    rest.setAmount(kept);
                    w.satchel.setItem(i, rest);
                    dirty = true;
                    return n; // inventory full
                }
                w.satchel.setItem(i, null);
                dirty = true;
            }
        }
        return n;
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
        dirty |= hung > 0;
        return hung;
    }

    /** Whatever didn't fit in the satchel goes to a Runner of the crew, else falls on the ground. */
    private void drop(Worker w, List<ItemStack> left, BlockKey at) {
        if (left.isEmpty()) {
            return;
        }
        List<ItemStack> rest = new ArrayList<>(left);
        for (Worker o : crew(w)) {
            if (o.type() != WorkerType.RUNNER || rest.isEmpty()) {
                continue;
            }
            List<ItemStack> still = new ArrayList<>();
            for (ItemStack it : rest) {
                still.addAll(o.satchel.addItem(it).values());
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
        w.status = "<yellow>Satchel full! Some items fell on the ground.";
    }

    // ---- walking ----

    /** Adds a trip to a block they can walk up to (self: they may stand on it, like crops). */
    private void go(Worker w, BlockKey target, boolean self, BooleanSupplier act) {
        Location stand = stand(w, target, self);
        go(w, target, stand != null ? stand : (w.pos != null ? w.pos.clone() : w.home()), false, act);
    }

    /** Adds a trip: walk (or, jump = true, take the back way) to stand, look at target, then act. */
    private void go(Worker w, BlockKey target, Location stand, boolean jump, BooleanSupplier act) {
        w.steps.add(new Worker.Step(stand, target.center(), act, jump));
    }

    /** One tick of walking / working. */
    private void move(Worker w) {
        Entity e = entity(w);
        if (w.current == null) {
            w.current = w.steps.poll();
            if (w.current == null) {
                Location home = w.home();
                if (home == null) {
                    return;
                }
                WalkArea a = area(w);
                boolean lost = a == null || !a.contains(w.pos.getBlockX(), w.pos.getBlockY(), w.pos.getBlockZ());
                w.current = new Worker.Step(home, null, null, lost && w.pos.distanceSquared(home) > 4);
            }
            w.workTicks = 0;
            w.path.clear();
            Worker.Step s = w.current;
            if (s.stand() != null && s.jump()) {
                // the back way: vanish here, pop up there
                poof(w.pos);
                w.pos = s.stand().clone();
                if (e != null) {
                    e.teleport(w.pos);
                }
                poof(w.pos);
            } else if (s.stand() != null) {
                WalkArea a = area(w);
                List<Location> way = a == null ? List.of() : a.path(w.pos, s.stand());
                if (way.isEmpty() && w.pos.getWorld().equals(s.stand().getWorld())
                        && w.pos.distanceSquared(s.stand()) > 4) {
                    // no way on foot from here (after taking the back way): the back way again
                    poof(w.pos);
                    w.pos = s.stand().clone();
                    if (e != null) {
                        e.teleport(w.pos);
                    }
                    poof(w.pos);
                } else {
                    w.path.addAll(way);
                }
            }
        }
        Worker.Step s = w.current;
        if (s.stand() == null && s.look() == null && s.act() != null) {
            // planning on the way (see then): no walking, no work to show
            boolean ok = act(s);
            w.current = null;
            if (!ok) {
                w.steps.clear();
            }
            return;
        }
        Location to = w.path.isEmpty() ? s.stand() : w.path.peek();
        double dist = to == null || !to.getWorld().equals(w.pos.getWorld()) ? 0 : w.pos.distance(to);
        if (dist > 0.02) {
            Vector dir = to.toVector().subtract(w.pos.toVector());
            double step = speed(w);
            if (dist <= step) {
                w.pos = to.clone();
                if (!w.path.isEmpty()) {
                    w.path.poll();
                }
            } else {
                w.pos.add(dir.clone().normalize().multiply(step));
            }
            if (Math.abs(dir.getX()) + Math.abs(dir.getZ()) > 0.001) {
                float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
                w.pos.setYaw(yaw);
            }
            w.pos.setPitch(0);
            // nobody near: the mannequin jumps along every half second instead of every tick
            if (e != null && (w.watched || ticks % 10 == 0)) {
                e.teleport(w.pos);
                if (e instanceof LivingEntity le) {
                    le.setBodyYaw(w.pos.getYaw());
                }
            }
            return;
        }
        if (!w.path.isEmpty()) {
            w.path.poll();
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
        if (w.workTicks == 0 && e instanceof LivingEntity le) {
            if (!w.watched) {
                e.teleport(w.pos); // arrived (it only jumped along on the way)
            }
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
        if (w.workTicks >= 14) {
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

    private static void poof(Location l) {
        if (l != null && l.getWorld() != null) {
            l.getWorld().spawnParticle(Particle.POOF, l.clone().add(0, 1, 0), 8, 0.25, 0.4, 0.25, 0.02);
        }
    }

    // ------------------------------------------------------------------
    // showing where they work, settings
    // ------------------------------------------------------------------

    /** For 10 seconds: a ring around their area and green sparks over the workers they work with. */
    public void show(Player p, Worker w) {
        new org.bukkit.scheduler.BukkitRunnable() {
            int n;

            @Override
            public void run() {
                if (!p.isOnline() || get(w.id()) == null || n++ >= 20) {
                    cancel();
                    return;
                }
                for (Worker o : crew(w)) {
                    Location at = spot(o);
                    if (at != null && at.getWorld().equals(p.getWorld())) {
                        p.spawnParticle(Particle.DUST, at.clone().add(0, 2.3, 0), 6, 0.2, 0.15, 0.2, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(0x5AE85A), 1.4f));
                    }
                }
                Location h = w.home();
                if (h != null && h.getWorld().equals(p.getWorld())) {
                    int r = courier(w) ? chainRadius() : radius(w);
                    for (int i = 0; i < 48; i++) {
                        double a = i * Math.PI * 2 / 48;
                        p.spawnParticle(Particle.DUST, h.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r), 1, 0, 0, 0,
                                0, new Particle.DustOptions(org.bukkit.Color.fromRGB(0x5AD8F0), 1.2f));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    /** Dryer: hand everything they dry to the Runners to sell. */
    public void setSell(Worker w, boolean on) {
        w.sell = on;
        dirty = true;
    }

    /** Cook mixing strains: the lowest rarity they keep (cycles Uncommon+ ... Mythic+). */
    public void cycleKeep(Worker w, boolean down) {
        int min = dev.kushcraft.strain.Rarity.UNCOMMON.ordinal(), max = dev.kushcraft.strain.Rarity.MYTHIC.ordinal();
        int k = w.keep + (down ? -1 : 1);
        w.keep = k > max ? min : k < min ? max : k;
        dirty = true;
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
            new dev.kushcraft.gui.WorkerMenu(p, w).open();
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

    /** Building around workers: they work out where they can walk again (walls, doors, stairs). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        blockChanged(e.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(org.bukkit.event.block.BlockBreakEvent e) {
        blockChanged(e.getBlock());
    }

    /** When an owner joins: one line for each of their workers that is stuck. */
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            List<Worker> stuck = stuck(p.getUniqueId());
            if (stuck.isEmpty()) {
                return;
            }
            p.sendMessage(Text.msg("<gold>⚠ " + stuck.size() + " of your workers need" + (stuck.size() == 1 ? "s" : "")
                    + " you <dark_gray>(/kush workers)"));
            for (Worker w : stuck.subList(0, Math.min(5, stuck.size()))) {
                p.sendMessage(Text.mm("  " + w.type().colored() + " " + Text.escape(w.name) + "<gray>: " + w.status
                        + where(w)));
                w.notifiedAt = System.currentTimeMillis();
            }
        }, 100L);
    }
}
