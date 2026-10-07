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
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
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
 * Nothing through walls: a worker only uses what they can walk up to (see
 * WalkArea) - the owner's chests (nearest one first, nothing to link) and
 * the satchels of other workers they can reach. One player's workers within
 * chain-radius of each other are a crew: what a worker is missing, a Runner
 * brings from anywhere in the crew (they take the back way, chests behind
 * walls included), a Runner also clears the finished work out of the
 * satchels, and a Supplier buys. Nobody takes what another worker needs for
 * their own job.
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
    /** The chests a worker uses, worked out at most every {@link #CHEST_MILLIS}. */
    private final Map<UUID, ChestList> chestLists = new HashMap<>();
    private static final long CHEST_MILLIS = 1_500L;
    /** How long a worker's walk area is trusted before it is worked out again. */
    private static final long AREA_MILLIS = 10_000L;
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
                w.reserve = s.getDouble("reserve", plugin.getConfig().getDouble("workers.supplier.reserve", 1000));
                w.spent = s.getDouble("spent");
                for (String l : s.getStringList("links")) {
                    BlockKey lk = BlockKey.parse(l);
                    if (lk != null && w.links.size() < Worker.MAX_LINKS) {
                        w.links.add(lk);
                    }
                }
                ConfigurationSection items = s.getConfigurationSection("satchel");
                if (items != null) {
                    for (String slot : items.getKeys(false)) {
                        ItemStack it = decode(items.getString(slot));
                        int i = Integer.parseInt(slot);
                        if (it != null && i >= 0 && i < Worker.SATCHEL) {
                            w.satchel.setItem(i, it);
                        }
                    }
                }
                add(w);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("workers.yml: skipped a broken worker " + k);
            }
        }
        plugin.getLogger().info("Loaded " + workers.size() + " workers.");
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
            if (w.type() == WorkerType.SUPPLIER) {
                s.set("reserve", w.reserve);
                s.set("spent", Math.round(w.spent * 100) / 100.0);
            }
            if (!w.links.isEmpty()) {
                List<String> links = new ArrayList<>();
                w.links.forEach(k -> links.add(k.serialize()));
                s.set("links", links);
            }
            ItemStack[] items = w.satchel.getStorageContents();
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null && !items[i].getType().isAir()) {
                    s.set("satchel." + i, encode(items[i]));
                }
            }
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
    }

    public void shutdown() {
        for (Worker w : workers.values()) {
            despawn(w);
        }
        save();
    }

    private void add(Worker w) {
        workers.put(w.id(), w);
        byChunk.computeIfAbsent(w.chunkId(), k -> new HashSet<>()).add(w.id());
    }

    private void forget(Worker w) {
        workers.remove(w.id());
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
        w.reserve = plugin.getConfig().getDouble("workers.supplier.reserve", 1000);
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
        chestLists.remove(w.id());
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
        w.status = w.autoPick() ? "Looking for something to make" : made == null ? "Pick a drug for them"
                : "Ready to make " + made.display();
        w.restTicks = 1;
        dirty = true;
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
        dirty = true;
        return left;
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
                    + (w.autoPick() ? " <dark_gray>· <aqua>Auto" : made != null ? " <dark_gray>· <aqua>" + made.display() : "")
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
        boolean think = ticks % 20 == 0;
        for (Worker w : new ArrayList<>(workers.values())) {
            if (w.pos == null) {
                continue; // not loaded
            }
            if (think && mannequins && w.entityId != null && entity(w) == null && w.isLoaded()) {
                spawn(w); // the mannequin got unloaded on a walk: put them back home
                continue;
            }
            if (w.busy() || (w.current == null && !atHome(w))) {
                move(w);
            } else if (think) {
                think(w);
            }
        }
    }

    private boolean atHome(Worker w) {
        Location h = w.home();
        return h == null || w.pos == null || w.pos.distanceSquared(h) < 0.01;
    }

    /** Once a second when idle: rest, then look for the next job. */
    private void think(Worker w) {
        if (w.paused) {
            w.status = "Paused";
            return;
        }
        if (--w.restTicks > 0) {
            return;
        }
        w.restTicks = restSeconds(w);
        plan(w);
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
        // a satchel getting full: first put the finished stuff in a chest
        if (!courier(w) && w.freeSlots() < 6 && planDeposit(w)) {
            return true;
        }
        boolean busy = switch (w.type()) {
            case FARMHAND -> planFarmhand(w);
            case DRYER -> planDryer(w);
            case COOK -> planCook(w);
            case RUNNER -> planRunner(w);
            case SUPPLIER -> planSupplier(w);
        };
        if (busy) {
            return true;
        }
        // nothing else to do: tidy up
        if (w.type() == WorkerType.FARMHAND) {
            compost(w);
        }
        return !courier(w) && planDeposit(w);
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

    // ------------------------------------------------------------------
    // where they can go: walk areas (no working through walls)
    // ------------------------------------------------------------------

    /** Runners and Suppliers cover the whole crew and get through walls the back way. */
    static boolean courier(Worker w) {
        return w.type() == WorkerType.RUNNER || w.type() == WorkerType.SUPPLIER;
    }

    /** Everywhere a worker can walk to from home (cached for a few seconds). Null when not loaded. */
    WalkArea area(Worker w) {
        Location h = w.home();
        if (h == null || !w.isLoaded()) {
            return null;
        }
        long now = System.currentTimeMillis();
        WalkArea a = areas.get(w.id());
        if (a == null || now - a.made > AREA_MILLIS || !a.world.equals(h.getWorld())) {
            boolean c = courier(w);
            a = WalkArea.build(h, c ? chainRadius() : radius(w) + 1, c ? 30000 : 12000,
                    now - ThreadLocalRandom.current().nextInt(2000));
            areas.put(w.id(), a);
        }
        return a;
    }

    /** Forget the cached walk areas (blocks changed a lot, the self test). */
    public void refreshAreas() {
        areas.clear();
        chestLists.clear();
    }

    /** A spot next to target this worker can walk to (self: they may stand on it), or null. */
    private Location stand(Worker w, BlockKey target, boolean self) {
        WalkArea a = area(w);
        return a == null ? null : a.standFor(target, self, w.pos != null ? w.pos : w.home());
    }

    /** True when the worker can walk up to target. */
    public boolean reaches(Worker w, BlockKey target, boolean self) {
        return stand(w, target, self) != null;
    }

    /** A spot next to another worker's home (to hand things over), or null. */
    private Location standByWorker(Worker w, Worker o) {
        Location oh = o.home();
        return oh == null ? null : stand(w, BlockKey.of(oh), false);
    }

    // ------------------------------------------------------------------
    // the work chain: crews, chests, fetching and handing over
    // ------------------------------------------------------------------

    /** How far apart one player's workers can be and still work together (blocks). */
    public int chainRadius() {
        return Math.max(4, plugin.getConfig().getInt("workers.chain-radius", 32));
    }

    /** The owner's other workers within chain-radius. */
    public List<Worker> crew(Worker w) {
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

    /** A chest or barrel a worker uses. */
    public record Chest(Block block) {
        /** The chest's inventory right now (both halves of a double chest), or an empty one when it's gone. */
        public Inventory inv() {
            Inventory inv = inventoryOf(block);
            return inv != null ? inv : Bukkit.createInventory(null, 9);
        }

        public BlockKey key() {
            return BlockKey.of(block);
        }
    }

    static boolean isChest(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL;
    }

    private static Inventory inventoryOf(Block b) {
        return isChest(b.getType()) && b.getState(false) instanceof Container c ? c.getInventory() : null;
    }

    /** Who placed a chest (null for chests from before 7.0 or placed by something else). */
    public static UUID placer(Block b) {
        if (!isChest(b.getType()) || !(b.getState(false) instanceof TileState t)) {
            return null;
        }
        String s = t.getPersistentDataContainer().get(Keys.PLACER, PersistentDataType.STRING);
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Both halves of a double chest are one inventory: a key for it. */
    private static String invKey(Block b) {
        Inventory inv = inventoryOf(b);
        Location l = inv == null ? null : inv.getLocation();
        return l == null ? b.toString() : l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    /** Right next to their home (within 2 blocks). */
    private boolean byHome(Worker w, BlockKey k) {
        Location h = w.home();
        return h != null && k.world().equals(w.worldName()) && Math.abs(k.x() - h.getBlockX()) <= 2
                && Math.abs(k.z() - h.getBlockZ()) <= 2 && Math.abs(k.y() - h.getBlockY()) <= 1;
    }

    /** Blocks around a worker's spot where a Runner picks finished product out of chests. */
    private static final int SPOT = 4;

    private boolean nearSpot(Worker w, BlockKey k) {
        Location h = w.home();
        return h != null && k.world().equals(w.worldName()) && Math.abs(k.x() - h.getBlockX()) <= SPOT
                && Math.abs(k.z() - h.getBlockZ()) <= SPOT && Math.abs(k.y() - h.getBlockY()) <= 3;
    }

    /**
     * One of the owner's chests: placed by them, or an old untagged one that is right by one of
     * their workers (or that an old save had linked to one).
     */
    private boolean ownersChest(Worker w, Block b, List<Worker> crew) {
        UUID placer = placer(b);
        if (placer != null) {
            return placer.equals(w.owner());
        }
        BlockKey k = BlockKey.of(b);
        if (byHome(w, k) || w.links.contains(k)) {
            return true;
        }
        for (Worker o : crew) {
            if (byHome(o, k) || o.links.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private record ChestList(long made, WalkArea area, List<Chest> chests) {
    }

    /**
     * The chests a worker uses - nothing to link: every chest or barrel of the owner's they can
     * walk up to, the nearest one first (to where they stand). A Runner or Supplier uses every
     * chest of the owner's within the crew's reach, walls or not (they take the back way).
     */
    public List<Chest> chests(Worker w) {
        WalkArea a = area(w);
        if (a == null) {
            return new ArrayList<>();
        }
        long now = System.currentTimeMillis();
        ChestList cl = chestLists.get(w.id());
        if (cl != null && cl.area() == a && now - cl.made() < CHEST_MILLIS) {
            return cl.chests();
        }
        List<Worker> crew = crew(w);
        Location from = w.pos != null ? w.pos : w.home();
        List<Block> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (BlockKey k : courier(w) ? scanChests(w) : a.chests()) {
            Block b = k.isLoaded() ? k.block() : null;
            if (b != null && inventoryOf(b) != null && ownersChest(w, b, crew) && seen.add(invKey(b))) {
                found.add(b);
            }
        }
        found.sort(java.util.Comparator.comparingDouble(b -> dist2(BlockKey.of(b), from)));
        List<Chest> out = new ArrayList<>();
        found.forEach(b -> out.add(new Chest(b)));
        chestLists.put(w.id(), new ChestList(now, a, out));
        return out;
    }

    /** Every chest or barrel within chain-radius of a courier's home, found on the map. */
    private List<BlockKey> scanChests(Worker w) {
        List<BlockKey> out = new ArrayList<>();
        Location h = w.home();
        World world = h.getWorld();
        int r = chainRadius();
        for (int cx = (h.getBlockX() - r) >> 4; cx <= (h.getBlockX() + r) >> 4; cx++) {
            for (int cz = (h.getBlockZ() - r) >> 4; cz <= (h.getBlockZ() + r) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                for (org.bukkit.block.BlockState st : world.getChunkAt(cx, cz).getTileEntities(
                        b -> isChest(b.getType()), false)) {
                    double dx = st.getX() + 0.5 - h.getX(), dz = st.getZ() + 0.5 - h.getZ();
                    if (dx * dx + dz * dz <= (double) r * r && Math.abs(st.getY() - h.getY()) <= 24) {
                        out.add(new BlockKey(world.getName(), st.getX(), st.getY(), st.getZ()));
                    }
                }
            }
        }
        return out;
    }

    /** The chests a Runner collects finished product from: the ones close to any worker's spot. */
    private List<Chest> workChests(Worker w) {
        List<Worker> group = new ArrayList<>(crew(w));
        group.add(w);
        List<Chest> out = new ArrayList<>();
        for (Chest c : chests(w)) {
            for (Worker o : group) {
                if (nearSpot(o, c.key())) {
                    out.add(c);
                    break;
                }
            }
        }
        return out;
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

    /** The first time a worker uses a chest of theirs, the owner earns an award. */
    private void usedChest(Worker w) {
        Player p = Bukkit.getPlayer(w.owner());
        if (p != null) {
            plugin.awards().usedChest(p);
        }
    }

    /** Somewhere to take things from: another worker's satchel, or a chest. stand = where to go. */
    private record Source(Worker worker, Chest chest, Location stand, BlockKey where, boolean jump) {
        Inventory inv() {
            return worker != null ? worker.satchel : chest.inv();
        }

        String name() {
            return worker != null ? worker.name() : "a chest";
        }
    }

    /** Things a worker needs for their own job (they never hand these on, except a Farmhand's spare seeds). */
    public boolean uses(Worker w, ItemStack it) {
        ItemType t = Items.type(it);
        return switch (w.type()) {
            case FARMHAND -> t == ItemType.FERTILIZER || PlantManager.kindOf(t) != null;
            case DRYER -> t == ItemType.BUD_FRESH;
            case COOK -> cookUses(w, it);
            case RUNNER, SUPPLIER -> false;
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

    /** What a worker makes: the next worker in the chain (or the Runner) takes it. */
    public boolean produces(Worker w, ItemStack it) {
        ItemType t = Items.type(it);
        if (t == null) {
            return false;
        }
        return switch (w.type()) {
            case FARMHAND -> t == ItemType.BUD_FRESH || t == ItemType.COCA_LEAVES || t == ItemType.POPPY_POD
                    || t == ItemType.MAGIC_MUSHROOM || t == ItemType.PEYOTE_BUTTON || t == ItemType.ERGOT;
            case DRYER -> t == ItemType.BUD_DRIED;
            case COOK -> w.autoPick() ? LabRecipe.making(t) != null : t == w.product();
            case RUNNER, SUPPLIER -> false;
        };
    }

    /** Empty buckets and bottles a Cook doesn't need: they go in the chest. */
    private boolean junk(Worker w, ItemStack it) {
        return w.type() == WorkerType.COOK && !Items.isCustom(it) && !uses(w, it)
                && (it.getType() == Material.GLASS_BOTTLE || it.getType() == Material.BUCKET);
    }

    private boolean sellable(ItemStack it) {
        return it != null && !it.getType().isAir() && plugin.shop().sellPrice(it) > 0;
    }

    private static boolean isSeed(ItemStack it) {
        return PlantManager.kindOf(Items.type(it)) != null;
    }

    private static String seedKey(ItemStack it) {
        Strain s = Items.strain(it);
        return Items.type(it).name() + ":" + (s == null ? "" : s.id());
    }

    /** Seeds a Farmhand keeps of each kind for planting; the rest they hand on (or compost). */
    static final int SEED_KEEP = 16;

    /**
     * How many of each matching stack a worker hands on: everything they don't use themselves,
     * and a Farmhand's seeds past {@link #SEED_KEEP} of a kind. index = satchel slot.
     */
    private int[] spare(Worker o, Predicate<ItemStack> want) {
        ItemStack[] items = o.satchel.getStorageContents();
        int[] out = new int[items.length];
        Map<String, Integer> seeds = new HashMap<>();
        for (ItemStack it : items) {
            if (it != null && o.type() == WorkerType.FARMHAND && isSeed(it)) {
                seeds.merge(seedKey(it), it.getAmount(), Integer::sum);
            }
        }
        Map<String, Integer> allowance = new HashMap<>();
        seeds.forEach((k, n) -> allowance.put(k, Math.max(0, n - SEED_KEEP)));
        for (int i = 0; i < items.length; i++) {
            ItemStack it = items[i];
            if (it == null || it.getType().isAir() || !want.test(it)) {
                continue;
            }
            if (!uses(o, it)) {
                out[i] = it.getAmount();
            } else if (o.type() == WorkerType.FARMHAND && isSeed(it)) {
                String k = seedKey(it);
                int can = Math.min(it.getAmount(), allowance.getOrDefault(k, 0));
                allowance.put(k, allowance.getOrDefault(k, 0) - can);
                out[i] = can;
            }
        }
        return out;
    }

    private int spareCount(Worker o, Predicate<ItemStack> want) {
        int n = 0;
        for (int c : spare(o, want)) {
            n += c;
        }
        return n;
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

    /** Takes matching items from a source: all of a chest's, only the spare ones of a worker's satchel. */
    private int take(Source src, Inventory to, Predicate<ItemStack> want, int max) {
        if (src.worker() == null) {
            return move(src.inv(), to, want, max);
        }
        return move(src.worker().satchel, to, want, max, spare(src.worker(), want));
    }

    private boolean has(Source src, Predicate<ItemStack> want) {
        return src.worker() == null ? first(src.inv(), want) != null : spareCount(src.worker(), want) > 0;
    }

    /**
     * Where a worker can fetch from on foot: their chests, then the satchels of crew members they can
     * walk up to (fromWorkers). Nothing behind walls - a Runner brings that.
     */
    private List<Source> sources(Worker w, boolean fromWorkers) {
        List<Source> out = new ArrayList<>();
        for (Chest c : chests(w)) {
            Location st = stand(w, c.key(), false);
            if (st != null) {
                out.add(new Source(null, c, st, c.key(), false));
            }
        }
        if (fromWorkers) {
            for (Worker o : crew(w)) {
                Location st = standByWorker(w, o);
                if (st != null) {
                    out.add(new Source(o, null, st, BlockKey.of(o.home()), false));
                }
            }
        }
        return out;
    }

    /** Every place in the crew with things in it (for Runners: they get anywhere). */
    private List<Source> crewSources(Worker courier, Worker except) {
        List<Source> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Worker> everyone = new ArrayList<>(crew(courier));
        everyone.add(0, courier);
        for (Worker o : everyone) {
            if (o != courier && o != except && !courier(o)) {
                out.add(new Source(o, null, standOrJump(courier, BlockKey.of(o.home())), BlockKey.of(o.home()),
                        standByWorker(courier, o) == null));
            }
            for (Chest c : chests(o)) {
                if (seen.add(invKey(c.block()))) {
                    Location st = stand(courier, c.key(), false);
                    out.add(new Source(null, c, st != null ? st : jumpSpot(c.key()), c.key(), st == null));
                }
            }
        }
        return out;
    }

    /**
     * Plans a trip to fetch matching items. Returns false when nobody they can walk to has any
     * (then a Runner may bring it: see {@link Worker#want}).
     */
    private boolean fetch(Worker w, Predicate<ItemStack> want, boolean fromWorkers, String what) {
        if (w.freeSlots() == 0) {
            return false;
        }
        for (Source src : sources(w, fromWorkers)) {
            if (!has(src, want)) {
                continue;
            }
            w.status = "Fetching " + what + " from " + src.name();
            go(w, src.where(), src.stand(), false, () -> {
                int n = take(src, w.satchel, want, Integer.MAX_VALUE);
                if (n > 0) {
                    w.status = "Got " + n + " " + what + " from " + src.name();
                    if (src.chest() != null) {
                        usedChest(w);
                    }
                }
                return n > 0;
            });
            return true;
        }
        return false;
    }

    /** True when some source they can walk to has matching items (without planning a trip). */
    private boolean available(Worker w, Predicate<ItemStack> want, boolean fromWorkers) {
        for (Source src : sources(w, fromWorkers)) {
            if (has(src, want)) {
                return true;
            }
        }
        return false;
    }

    /**
     * What a worker puts away, per satchel slot: what they made, empty bottles they don't need and
     * a Farmhand's seeds past {@link #SEED_KEEP} of a kind.
     */
    private int[] depositable(Worker w) {
        ItemStack[] items = w.satchel.getStorageContents();
        int[] seeds = w.type() == WorkerType.FARMHAND ? spare(w, Workers::isSeed) : new int[items.length];
        int[] out = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            ItemStack it = items[i];
            if (it != null && !it.getType().isAir()) {
                out[i] = (produces(w, it) && !uses(w, it)) || junk(w, it) ? it.getAmount() : seeds[i];
            }
        }
        return out;
    }

    /** True when the chest has room for at least one of the satchel's items with a positive limit. */
    private static boolean roomFor(Inventory chest, Inventory satchel, int[] limit) {
        ItemStack[] items = satchel.getStorageContents();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && !items[i].getType().isAir() && limit[i] > 0 && fits(chest, items[i])) {
                return true;
            }
        }
        return false;
    }

    /** Puts what a worker made (and spare seeds, empty bottles) in the nearest chest with room that they can walk to. */
    private boolean planDeposit(Worker w) {
        int[] limit = depositable(w);
        if (java.util.Arrays.stream(limit).sum() == 0) {
            return false;
        }
        for (Chest c : chests(w)) {
            if (!roomFor(c.inv(), w.satchel, limit)) {
                continue;
            }
            Location st = stand(w, c.key(), false);
            if (st == null) {
                continue;
            }
            w.status = "Putting their work in the nearest chest";
            go(w, c.key(), st, false, () -> {
                if (inventoryOf(c.block()) == null) {
                    return false; // the chest is gone
                }
                int n = move(w.satchel, c.inv(), it -> true, Integer.MAX_VALUE, depositable(w));
                if (n > 0) {
                    w.status = "Put " + n + " items in the chest";
                    usedChest(w);
                }
                return n > 0;
            });
            return true;
        }
        return false;
    }

    /** Does a job where they stand. */
    private void here(Worker w, BooleanSupplier act) {
        w.steps.add(new Worker.Step(w.home(), null, act));
    }

    /** Tells Runners (and the Supplier) what this worker is missing. */
    private void want(Worker w, Predicate<ItemStack> match, String what, int amount) {
        w.want = new Worker.Want(match, what, amount);
    }

    // ---- farmhand ----

    private boolean planFarmhand(Worker w) {
        int r = radius(w);
        Location h = w.home();
        // 1. harvest ripe plants they can walk to, a few in one round
        List<Plant> ripe = new ArrayList<>();
        for (Plant p : plugin.plants().all()) {
            if (p.mature() && w.owner().equals(p.owner()) && near(p.key(), w, r) && p.key().isLoaded()
                    && reaches(w, p.key(), true)) {
                ripe.add(p);
            }
        }
        if (!ripe.isEmpty()) {
            if (w.freeSlots() < 2) {
                w.status = "<red>Satchel full: needs a chest with room or a Runner.";
                return false;
            }
            if (!canPay(w)) {
                return false;
            }
            List<Plant> round = route(ripe, h, 2 + w.level());
            w.status = "Harvesting " + round.size() + " plant" + (round.size() > 1 ? "s" : "");
            for (Plant target : round) {
                String what = plantName(target);
                go(w, target.key(), true, () -> {
                    if (plugin.plants().at(target.key()) != target || !target.mature()) {
                        return true; // someone else picked it: go on with the next one
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
        // 2. fertilize growing plants, a few in one round
        ItemStack fert = first(w, it -> Items.type(it) == ItemType.FERTILIZER);
        List<Plant> growing = new ArrayList<>();
        for (Plant p : plugin.plants().all()) {
            if (!p.fertilized() && !p.mature() && w.owner().equals(p.owner()) && near(p.key(), w, r)
                    && p.key().isLoaded() && reaches(w, p.key(), true)) {
                growing.add(p);
            }
        }
        if (fert != null && !growing.isEmpty()) {
            List<Plant> round = route(growing, h, Math.min(fert.getAmount(), 2 + w.level()));
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
        // 3. plant seeds from the satchel on empty farmland / planters, a few in one round
        Predicate<ItemStack> isSeed = it -> {
            Plant.Kind k = PlantManager.kindOf(Items.type(it));
            return k != null && (k != Plant.Kind.CANNABIS || Items.strain(it) != null);
        };
        ItemStack seed = first(w, isSeed);
        if (seed != null) {
            Plant.Kind kind = PlantManager.kindOf(Items.type(seed));
            List<Block> soils = emptySoil(w, kind, r, Math.min(seed.getAmount(), 2 + w.level()));
            if (!soils.isEmpty()) {
                if (!canPay(w)) {
                    return false;
                }
                ItemStack chosen = seed;
                w.status = "Planting " + Text.plain(chosen.effectiveName());
                for (Block soil : soils) {
                    BlockKey spot = BlockKey.of(soil).up();
                    go(w, spot, true, () -> {
                        Block s = spot.block();
                        if (chosen.getAmount() <= 0 || !canPay(w)) {
                            return false;
                        }
                        if (s == null || !s.getType().isAir() || plugin.plants().at(spot) != null
                                || !plugin.plants().isSoil(s.getRelative(BlockFace.DOWN), kind)) {
                            return true;
                        }
                        Strain strain = kind == Plant.Kind.CANNABIS ? Items.strain(chosen) : null;
                        chosen.setAmount(chosen.getAmount() - 1);
                        plugin.plants().plantAt(spot, kind, strain, w.owner());
                        Location c = spot.bottomCenter();
                        c.getWorld().playSound(c, "minecraft:item.crop.plant", SoundCategory.BLOCKS, 1f, 1f);
                        pay(w);
                        return true;
                    });
                }
                return true;
            }
        } else if (hasEmptySoil(w, r)) {
            // 4. out of seeds but there's room to plant: seeds from a chest (or a Runner brings some)
            if (available(w, isSeed, false) && fetch(w, isSeed, false, "seeds")) {
                return true;
            }
            want(w, isSeed, "seeds", 8);
        }
        if (fert == null && !growing.isEmpty()) {
            if (fetch(w, it -> Items.type(it) == ItemType.FERTILIZER, false, "fertilizer")) {
                return true;
            }
            if (w.want == null) {
                want(w, it -> Items.type(it) == ItemType.FERTILIZER, "fertilizer", 8);
            }
        }
        w.status = "Waiting for your plants to ripen";
        return false;
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
     * Spare seeds become fertilizer: when the satchel gets full and no chest has room, every seed
     * past 16 of a kind is composted (4 seeds = 1 fertilizer) - unless another worker of the crew
     * uses them (a Cook making morphine base from poppy seeds).
     */
    public void compost(Worker w) {
        if (w.freeSlots() >= 6 || chests(w).stream().anyMatch(c -> c.inv().firstEmpty() >= 0)) {
            return;
        }
        List<Worker> crew = crew(w);
        Map<String, Integer> kept = new HashMap<>();
        int spare = 0;
        ItemStack[] items = w.satchel.getStorageContents();
        for (int i = 0; i < items.length; i++) {
            ItemStack it = items[i];
            ItemType t = Items.type(it);
            if (PlantManager.kindOf(t) == null || crew.stream().anyMatch(o -> o.type() != WorkerType.FARMHAND
                    && uses(o, it))) {
                continue;
            }
            String key = seedKey(it);
            int have = kept.getOrDefault(key, 0);
            int keep = Math.max(0, Math.min(it.getAmount(), SEED_KEEP - have));
            kept.put(key, have + keep);
            spare += it.getAmount() - keep;
            if (keep <= 0) {
                w.satchel.setItem(i, null);
            } else {
                it.setAmount(keep);
                w.satchel.setItem(i, it);
            }
        }
        if (spare >= 4) {
            drop(w, stash(w, List.of(Items.create(ItemType.FERTILIZER, spare / 4))), BlockKey.of(w.home()));
            w.status = "Turned " + spare + " spare seeds into fertilizer";
        }
        dirty |= spare > 0;
    }

    private String plantName(Plant p) {
        return p.kind() == Plant.Kind.CANNABIS ? Text.plain(Text.mm(plugin.strains().getOrDefault(p.strainId()).colored()))
                : p.kind().display();
    }

    private boolean hasEmptySoil(Worker w, int r) {
        return !emptySoil(w, null, r, 1).isEmpty();
    }

    /** Empty farmland or the owner's Planters they can walk to, where this kind (null: anything) grows, closest first. */
    private List<Block> emptySoil(Worker w, Plant.Kind kind, int r, int max) {
        Location h = w.home();
        World world = h.getWorld();
        int hx = h.getBlockX(), hy = h.getBlockY(), hz = h.getBlockZ();
        List<Block> found = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r || !world.isChunkLoaded((hx + dx) >> 4, (hz + dz) >> 4)) {
                    continue;
                }
                for (int dy = -3; dy <= 1; dy++) {
                    Block soil = world.getBlockAt(hx + dx, hy + dy, hz + dz);
                    Material type = soil.getType();
                    if (type != Material.FARMLAND && type != Material.BARRIER) {
                        continue;
                    }
                    Machine m = type == Material.BARRIER ? plugin.machines().at(soil) : null;
                    boolean planter = m != null && m.type() == MachineType.PLANTER_BOX && w.owner().equals(m.owner());
                    if (!planter && type != Material.FARMLAND) {
                        continue;
                    }
                    Block above = soil.getRelative(BlockFace.UP);
                    BlockKey key = BlockKey.of(above);
                    if (!above.getType().isAir() || plugin.plants().at(key) != null || plugin.machines().at(key) != null
                            || (kind != null && !plugin.plants().isSoil(soil, kind)) || !reaches(w, key, true)) {
                        continue;
                    }
                    found.add(soil);
                }
            }
        }
        found.sort(java.util.Comparator.comparingDouble(b -> b.getLocation().distanceSquared(h)));
        return found.size() > max ? new ArrayList<>(found.subList(0, max)) : found;
    }

    // ---- dryer ----

    /** The owner's Drug Labs within r that they can walk up to. */
    private List<Machine> labsNear(Worker w, int r) {
        List<Machine> labs = new ArrayList<>();
        for (Machine m : plugin.machines().all()) {
            if (m.type() == MachineType.LAB_STATION && w.owner().equals(m.owner()) && near(m.key(), w, r)
                    && m.key().isLoaded() && reaches(w, m.key(), false)) {
                labs.add(m);
            }
        }
        return labs;
    }

    private String noLab(Worker w, int r) {
        for (Machine m : plugin.machines().all()) {
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
        for (Machine lab : labs) {
            if (lab.racksDry() > 0) {
                if (w.freeSlots() < lab.racksDry()) {
                    w.status = "<red>Satchel full: needs a chest with room or a Runner.";
                    return false;
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
        // 3. fetch fresh buds from the crew (Farmhands, chests) - or a Runner brings them
        Predicate<ItemStack> fresh = it -> Items.type(it) == ItemType.BUD_FRESH && Items.strain(it) != null;
        if (first(w, it -> Items.type(it) == ItemType.BUD_FRESH) == null) {
            if (fetch(w, fresh, true, "fresh buds")) {
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
     * satchel, or in chests and satchels they can walk to). Keeps the current pick while it works.
     */
    private LabRecipe pickAuto(Worker w) {
        if (w.auto != null && canGather(w, w.auto)) {
            return w.auto;
        }
        List<LabRecipe> all = new ArrayList<>(List.of(LabRecipe.values()));
        all.sort(java.util.Comparator.comparingDouble((LabRecipe r) -> -plugin.shop().basePrice(r.output()) * r.amount()));
        for (LabRecipe r : all) {
            if (canGather(w, r)) {
                return r;
            }
        }
        return null;
    }

    private boolean canGather(Worker w, LabRecipe r) {
        for (LabRecipe.Ingredient ing : r.ingredients()) {
            Predicate<ItemStack> m = ing.strainSource() ? it -> Items.type(it) == ing.custom() && Items.strain(it) != null
                    : ing::matches;
            if (dev.kushcraft.util.InventoryUtil.count(w.satchel, m) >= ing.amount()) {
                continue;
            }
            if (ing.vanilla() == Material.POTION && (first(w, it -> it.getType() == Material.GLASS_BOTTLE
                    && !Items.isCustom(it)) != null || available(w, it -> it.getType() == Material.GLASS_BOTTLE
                    && !Items.isCustom(it), false)) && waterNear(w, Math.min(radius(w), 10)) != null) {
                continue;
            }
            if (!available(w, m, true)) {
                return false;
            }
        }
        return true;
    }

    private boolean planCook(Worker w) {
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
                    w.status = "<red>Satchel full: needs a chest with room or a Runner.";
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
            w.status = "<yellow>Nothing to make: no ingredients for any drug nearby.";
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
            Block water = waterNear(w, Math.min(r, 10));
            if (water != null) {
                w.status = "Filling bottles with water";
                go(w, BlockKey.of(water), false, () -> fillBottles(w) > 0);
                return true;
            }
        }
        // 4. fetch what's missing from chests around them and workers they can walk to
        Predicate<ItemStack> need = missing.strainSource() ? it -> Items.type(it) == missing.custom() && Items.strain(it) != null
                : missing::matches;
        if (fetch(w, need, true, missing.name())) {
            return true;
        }
        if (missing.vanilla() == Material.POTION
                && fetch(w, it -> it.getType() == Material.GLASS_BOTTLE && !Items.isCustom(it), false, "bottles")) {
            return true;
        }
        // 5. nobody nearby has it: Runners bring it, the Supplier buys it
        want(w, need, missing.name(), missing.amount() * dev.kushcraft.lab.Cooking.MAX_BATCHES);
        w.status = "<yellow>Needs " + missing.amount() + " " + missing.name() + " <gray>(chests around them, a Runner"
                + " or a Supplier)";
        return false;
    }

    /** Cook rolling joints or blunts: no lab needed, they roll where they stand. */
    private boolean planRoll(Worker w, ItemType product) {
        ItemType wrap = product == ItemType.JOINT ? ItemType.ROLLING_PAPERS : ItemType.BLUNT_WRAP;
        int budsEach = product == ItemType.JOINT ? 1 : 2;
        dev.kushcraft.util.StrainStock.Group g = dev.kushcraft.util.StrainStock.pick(w.satchel, ItemType.BUD_DRIED,
                budsEach, null, 0);
        int wraps = dev.kushcraft.util.InventoryUtil.count(w.satchel, it -> Items.type(it) == wrap);
        if (g != null && wraps > 0) {
            if (!canPay(w)) {
                return false;
            }
            w.status = "Rolling " + product.display().toLowerCase(java.util.Locale.ROOT) + "s";
            here(w, () -> {
                var group = dev.kushcraft.util.StrainStock.pick(w.satchel, ItemType.BUD_DRIED, budsEach, null, 0);
                int have = dev.kushcraft.util.InventoryUtil.count(w.satchel, it -> Items.type(it) == wrap);
                int n = group == null ? 0 : Math.min(8 + 4 * w.level(), Math.min(group.count() / budsEach, have));
                if (n <= 0) {
                    return false;
                }
                dev.kushcraft.util.StrainStock.take(w.satchel, ItemType.BUD_DRIED, group, n * budsEach);
                dev.kushcraft.util.InventoryUtil.remove(w.satchel, it -> Items.type(it) == wrap, n);
                drop(w, stash(w, List.of(Items.strainItem(product, group.strain(), group.quality(), n))),
                        BlockKey.of(w.home()));
                Location c = w.home();
                c.getWorld().playSound(c, "minecraft:item.book.page_turn", SoundCategory.NEUTRAL, 1f, 1.3f);
                pay(w);
                w.status = "Rolled " + n + " " + product.display().toLowerCase(java.util.Locale.ROOT) + (n > 1 ? "s" : "");
                return true;
            });
            return true;
        }
        Predicate<ItemStack> buds = it -> Items.type(it) == ItemType.BUD_DRIED && Items.strain(it) != null;
        if (g == null && fetch(w, buds, true, "dried buds")) {
            return true;
        }
        if (wraps == 0 && fetch(w, it -> Items.type(it) == wrap, false, wrap.display())) {
            return true;
        }
        if (g == null) {
            want(w, buds, "dried buds", 16);
        } else {
            want(w, it -> Items.type(it) == wrap, wrap.display(), 16);
        }
        w.status = "<yellow>Needs " + (g == null ? budsEach + " Dried Bud" : wrap.display())
                + " <gray>(chests around them, a Runner or a Supplier)";
        return false;
    }

    /** A water source block they can walk up to near home (for filling bottles), or null. */
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
        int n = dev.kushcraft.util.InventoryUtil.remove(w.satchel,
                it -> it.getType() == Material.GLASS_BOTTLE && !Items.isCustom(it), Integer.MAX_VALUE);
        if (n <= 0) {
            return 0;
        }
        drop(w, stash(w, waterBottles(n)), BlockKey.of(w.home()));
        Location c = w.home();
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

    /** A Runner clears a worker's satchel of finished work once this many spare items pile up (or it gets full). */
    private static final int UNLOAD_AT = 8;

    private boolean planRunner(Worker w) {
        // 1. bring workers what they're missing: from anywhere in the crew, through walls the back way
        if (w.freeSlots() > 2 && planDelivery(w)) {
            return true;
        }
        // 2. put away what they carry: to a worker who uses it, else in the nearest chest
        if (planUnload(w)) {
            return true;
        }
        // 3. sell what they carry (at a Dealer Stand of yours nearby, else where they stand)
        if (first(w, this::sellable) != null) {
            Machine stand = null;
            double best = Double.MAX_VALUE;
            for (Machine m : plugin.machines().all()) {
                if (m.type() == MachineType.DEALER && w.owner().equals(m.owner()) && near(m.key(), w, radius(w))
                        && m.key().isLoaded() && dist2(m.key(), w.home()) < best) {
                    best = dist2(m.key(), w.home());
                    stand = m;
                }
            }
            w.status = "Selling product";
            BooleanSupplier sell = () -> sellAll(w) > 0;
            if (stand != null) {
                go(w, stand.key(), standOrJump(w, stand.key()), stand(w, stand.key(), false) == null, sell);
            } else {
                here(w, sell);
            }
            return true;
        }
        List<Worker> crew = crew(w);
        if (w.freeSlots() > 1) {
            // 4. collect finished product nobody in the crew needs: from the chests by your workers, then satchels
            Predicate<ItemStack> spare = it -> sellable(it) && crew.stream().noneMatch(o -> uses(o, it));
            for (Chest c : workChests(w)) {
                if (first(c.inv(), spare) != null) {
                    return takeForSale(w, new Source(null, c, standOrJump(w, c.key()), c.key(),
                            stand(w, c.key(), false) == null), spare);
                }
            }
            for (Worker o : crew) {
                Predicate<ItemStack> theirs = it -> spare.test(it) && produces(o, it);
                if (!courier(o) && first(o.satchel, theirs) != null) {
                    BlockKey at = BlockKey.of(o.home());
                    return takeForSale(w, new Source(o, null, standOrJump(w, at), at, standByWorker(w, o) == null), theirs);
                }
            }
            // 5. empty the finished work out of satchels that are filling up, so nobody has to stop
            for (Worker o : crew) {
                if (courier(o)) {
                    continue;
                }
                Predicate<ItemStack> done = it -> produces(o, it) || (o.type() == WorkerType.FARMHAND && isSeed(it));
                int n = spareCount(o, done);
                if (n >= UNLOAD_AT || (n > 0 && o.freeSlots() <= 12)) {
                    BlockKey at = BlockKey.of(o.home());
                    Source src = new Source(o, null, standOrJump(w, at), at, standByWorker(w, o) == null);
                    w.status = "Emptying " + o.name() + "'s satchel";
                    go(w, src.where(), src.stand(), src.jump(), () -> take(src, w.satchel, done, Integer.MAX_VALUE) > 0);
                    return true;
                }
            }
        }
        w.status = crew.isEmpty() ? "<yellow>Hire workers near them, or put product in a chest by one."
                : "Waiting for product to sell";
        return false;
    }

    /** True when handing this to o helps: they use it, it fits and (a Farmhand's seeds) they're short of it. */
    private boolean takesFrom(Worker o, ItemStack it) {
        if (courier(o) || !uses(o, it) || !fits(o.satchel, it)) {
            return false;
        }
        if (o.type() == WorkerType.FARMHAND && isSeed(it)) {
            int have = dev.kushcraft.util.InventoryUtil.count(o.satchel, x -> isSeed(x) && seedKey(x).equals(seedKey(it)));
            return have < SEED_KEEP;
        }
        return true;
    }

    /** What a Runner carries goes to the worker who uses it (the nearest), else into the nearest chest (unless it sells). */
    private boolean planUnload(Worker w) {
        List<Worker> crew = crew(w);
        Location from = w.pos != null ? w.pos : w.home();
        for (ItemStack it : w.satchel.getStorageContents()) {
            if (it == null || it.getType().isAir()) {
                continue;
            }
            Worker to = null;
            double best = Double.MAX_VALUE;
            for (Worker o : crew) {
                double d = o.home() == null ? Double.MAX_VALUE : dist2(BlockKey.of(o.home()), from);
                if (d < best && takesFrom(o, it)) {
                    best = d;
                    to = o;
                }
            }
            if (to != null) {
                Worker target = to;
                BlockKey at = BlockKey.of(target.home());
                w.status = "Bringing " + Text.plain(it.effectiveName()) + " to " + target.name();
                go(w, at, standOrJump(w, at), standByWorker(w, target) == null, () -> {
                    int n = move(w.satchel, target.satchel, x -> uses(target, x), Integer.MAX_VALUE);
                    if (n > 0) {
                        target.want = null;
                        target.restTicks = 1;
                        w.jobs++;
                        w.status = "Brought " + n + " items to " + target.name();
                    }
                    return n > 0;
                });
                return true;
            }
        }
        // unsellable things - and what a full worker can't take yet (a Dryer's fresh buds) - wait in a chest
        Predicate<ItemStack> park = it -> !sellable(it) || crew.stream().anyMatch(o -> uses(o, it));
        if (first(w, park) == null) {
            return false;
        }
        for (Chest c : chests(w)) {
            boolean room = false;
            for (ItemStack it : w.satchel.getStorageContents()) {
                if (it != null && !it.getType().isAir() && park.test(it) && fits(c.inv(), it)) {
                    room = true;
                    break;
                }
            }
            if (!room) {
                continue;
            }
            w.status = "Putting things nobody needs in a chest";
            go(w, c.key(), standOrJump(w, c.key()), stand(w, c.key(), false) == null, () -> {
                if (inventoryOf(c.block()) == null) {
                    return false;
                }
                int n = move(w.satchel, c.inv(), park, Integer.MAX_VALUE);
                if (n > 0) {
                    usedChest(w);
                }
                return n > 0;
            });
            return true;
        }
        return false;
    }

    /** One delivery: pick up what a crew member is missing, take it to them. */
    private boolean planDelivery(Worker w) {
        for (Worker o : crew(w)) {
            Worker.Want want = o.want;
            if (want == null || courier(o) || o.freeSlots() == 0) {
                continue;
            }
            for (Source src : crewSources(w, o)) {
                if (src.worker() == o || !has(src, want.match())) {
                    continue;
                }
                int max = Math.max(1, want.amount());
                w.status = "Bringing " + want.what() + " to " + o.name();
                go(w, src.where(), src.stand(), src.jump(), () -> take(src, w.satchel, want.match(), max) > 0);
                BlockKey to = BlockKey.of(o.home());
                go(w, to, standOrJump(w, to), standByWorker(w, o) == null, () -> {
                    int n = move(w.satchel, o.satchel, want.match(), Integer.MAX_VALUE);
                    if (n > 0) {
                        o.want = null;
                        w.jobs++;
                        w.status = "Brought " + n + " " + want.what() + " to " + o.name();
                        o.restTicks = 1;
                    }
                    return n > 0;
                });
                o.want = null; // someone is on it
                return true;
            }
        }
        return false;
    }

    private boolean takeForSale(Worker w, Source src, Predicate<ItemStack> match) {
        w.status = "Picking up product from " + src.name();
        go(w, src.where(), src.stand(), src.jump(), () -> take(src, w.satchel, match, Integer.MAX_VALUE) > 0);
        return true;
    }

    /** Sells everything sellable in a Runner's satchel for the owner. Returns what the owner got. */
    double sellAll(Worker w) {
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
        Location c = w.pos != null ? w.pos : w.home();
        if (c != null) {
            c.getWorld().playSound(c, "minecraft:entity.villager.yes", SoundCategory.NEUTRAL, 0.8f, 1.1f);
            c.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, c.clone().add(0, 1.6, 0), 8, 0.3, 0.3, 0.3, 0);
        }
        w.status = "Sold " + count + " items for " + plugin.economy().format(paid);
        return paid;
    }

    // ---- supplier ----

    /** Reserve choices in the Supplier's menu. */
    public static final double[] RESERVES = {0, 500, 1000, 2500, 5000, 10000, 25000, 50000};

    /** Water bottles, which the Supplier gets for a small price each. */
    public double waterPrice() {
        return Math.max(0, plugin.getConfig().getDouble("workers.supplier.water-price", 2));
    }

    /** One thing to buy: n of what, at price each, from Trade (material) or the Shop (gear). */
    public record Buy(String name, int amount, double each, Material material, ItemType gear, boolean water) {
        public double cost() {
            return Math.round(each * amount * 100) / 100.0;
        }

        List<ItemStack> items() {
            if (water) {
                return waterBottles(amount);
            }
            List<ItemStack> out = new ArrayList<>();
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
            return new Buy("Water Bottle", n, waterPrice(), null, null, true);
        }
        if (ing.vanilla() != null) {
            var offer = plugin.exchange().enabled() ? plugin.exchange().offer(ing.vanilla()) : null;
            return offer == null ? null : new Buy(ing.name(), n, plugin.exchange().buyPrice(offer), ing.vanilla(), null,
                    false);
        }
        return gear(ing.custom(), n);
    }

    /** A KushCraft supply from the Shop's gear (solvent, papers, wraps, fertilizer), or null. */
    private Buy gear(ItemType t, int n) {
        for (var e : plugin.shop().gear()) {
            if (e.type() == t && e.amount() > 0) {
                int packs = (n + e.amount() - 1) / e.amount();
                return new Buy(t.display(), packs * e.amount(), e.price() / e.amount(), null, t, false);
            }
        }
        return null;
    }

    /** What a worker is running low on that can be bought (a few batches' worth). */
    public List<Buy> needs(Worker o) {
        List<Buy> out = new ArrayList<>();
        if (o.type() == WorkerType.COOK) {
            ItemType roll = o.rolls();
            if (roll != null) {
                ItemType wrap = roll == ItemType.JOINT ? ItemType.ROLLING_PAPERS : ItemType.BLUNT_WRAP;
                int have = dev.kushcraft.util.InventoryUtil.count(o.satchel, it -> Items.type(it) == wrap);
                if (have < 8) {
                    Buy b = gear(wrap, 32 - have);
                    if (b != null) {
                        out.add(b);
                    }
                }
                return out;
            }
            LabRecipe r = o.recipe();
            if (r == null) {
                return out;
            }
            int batches = dev.kushcraft.lab.Cooking.MAX_BATCHES;
            for (LabRecipe.Ingredient ing : r.ingredients()) {
                int have = dev.kushcraft.util.InventoryUtil.count(o.satchel, ing::matches);
                if (have < ing.amount() * 2) {
                    Buy b = buyable(ing, ing.amount() * batches - have);
                    if (b != null) {
                        out.add(b);
                    }
                }
            }
        } else if (o.type() == WorkerType.FARMHAND) {
            int have = dev.kushcraft.util.InventoryUtil.count(o.satchel, it -> Items.type(it) == ItemType.FERTILIZER);
            if (have < 4) {
                Buy b = gear(ItemType.FERTILIZER, 16 - have);
                if (b != null) {
                    out.add(b);
                }
            }
        }
        return out;
    }

    private boolean planSupplier(Worker w) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(w.owner());
        String broke = null;
        for (Worker o : crew(w)) {
            if (courier(o) || o.freeSlots() < 2) {
                continue;
            }
            List<Buy> buys = needs(o);
            if (buys.isEmpty()) {
                continue;
            }
            double cost = wage(w.type());
            for (Buy b : buys) {
                cost += b.cost();
            }
            if (plugin.economy().balance(owner) - cost < w.reserve) {
                broke = "<red>Not buying: you'd go under your " + plugin.economy().format(w.reserve) + " reserve.";
                continue;
            }
            BlockKey to = BlockKey.of(o.home());
            w.status = "Buying " + buys.get(0).name() + (buys.size() > 1 ? " and more" : "") + " for " + o.name();
            go(w, to, standOrJump(w, to), standByWorker(w, o) == null, () -> deliverPurchase(w, o));
            return true;
        }
        if (broke != null) {
            w.status = broke;
            return false;
        }
        w.status = crew(w).isEmpty() ? "<yellow>Hire Cooks or Farmhands near them to supply."
                : "Everyone is stocked up";
        return false;
    }

    /** At the worker: buy what they need right now (prices may have moved) and hand it over. */
    private boolean deliverPurchase(Worker w, Worker o) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(w.owner());
        List<Buy> buys = needs(o);
        double spent = 0;
        int items = 0;
        for (Buy b : buys) {
            if (plugin.economy().balance(owner) - b.cost() - wage(w.type()) < w.reserve
                    || !plugin.economy().withdraw(owner, b.cost())) {
                continue;
            }
            if (b.material() != null) {
                plugin.exchange().bought(b.material(), b.amount());
            }
            List<ItemStack> left = stash(o, b.items());
            if (!left.isEmpty()) {
                List<ItemStack> rest = new ArrayList<>();
                for (ItemStack it : left) {
                    rest.addAll(w.satchel.addItem(it).values());
                }
                drop(o, rest, BlockKey.of(o.home()));
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
        w.spent += spent;
        w.status = "Bought " + items + " items for " + o.name() + " (" + plugin.economy().format(spent) + ")";
        Player online = owner.getPlayer();
        if (online != null && spent >= 50) {
            online.sendActionBar(Text.mm("<yellow>" + Text.escape(w.name()) + " <gray>bought supplies for "
                    + Text.escape(o.name()) + ": <gold>-" + plugin.economy().format(spent)));
        }
        Location c = w.pos != null ? w.pos : w.home();
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

    /** Whatever didn't fit in the satchel goes in the nearest chests they use, else falls on the ground. */
    private void drop(Worker w, List<ItemStack> left, BlockKey at) {
        if (left.isEmpty()) {
            return;
        }
        List<ItemStack> rest = new ArrayList<>(left);
        for (Chest c : chests(w)) {
            List<ItemStack> still = new ArrayList<>();
            for (ItemStack it : rest) {
                still.addAll(c.inv().addItem(it).values());
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
                if (a != null) {
                    w.path.addAll(a.path(w.pos, s.stand()));
                }
            }
        }
        Worker.Step s = w.current;
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
            if (e != null) {
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
            if (s.look() != null) {
                le.lookAt(s.look().getX(), s.look().getY(), s.look().getZ(), LookAnchor.EYES);
            }
            le.swingMainHand();
        }
        w.workTicks++;
        if (w.workTicks == 8 && e instanceof LivingEntity le) {
            le.swingMainHand();
            if (s.look() != null) {
                s.look().getWorld().spawnParticle(Particle.HAPPY_VILLAGER, s.look(), 3, 0.25, 0.25, 0.25, 0);
            }
        }
        if (w.workTicks >= 14) {
            boolean ok;
            try {
                ok = s.act().getAsBoolean();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Worker job failed", ex);
                ok = false;
            }
            w.current = null;
            if (!ok) {
                w.steps.clear();
            }
        }
    }

    private static void poof(Location l) {
        if (l != null && l.getWorld() != null) {
            l.getWorld().spawnParticle(Particle.POOF, l.clone().add(0, 1, 0), 8, 0.25, 0.4, 0.25, 0.02);
        }
    }

    // ------------------------------------------------------------------
    // showing where they work
    // ------------------------------------------------------------------

    /** For 10 seconds: a ring around their area and green sparks on the chests they use. */
    public void show(Player p, Worker w) {
        new org.bukkit.scheduler.BukkitRunnable() {
            int n;

            @Override
            public void run() {
                if (!p.isOnline() || get(w.id()) == null || n++ >= 20) {
                    cancel();
                    return;
                }
                for (Chest c : chests(w)) {
                    spark(p, c.block(), 0x5AE85A);
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

    private static void spark(Player p, Block b, int rgb) {
        if (b.getWorld().equals(p.getWorld())) {
            p.spawnParticle(Particle.DUST, b.getLocation().add(0.5, 1.1, 0.5), 6, 0.25, 0.15, 0.25, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(rgb), 1.4f));
        }
    }

    /** Remembers a chest as linked the old way (what a pre-7.0.1 save holds; the self test). False when they have the most. */
    public boolean linkChest(Worker w, BlockKey k) {
        if (w.links.contains(k)) {
            return true;
        }
        if (w.links.size() >= Worker.MAX_LINKS) {
            return false;
        }
        w.links.add(k);
        dirty = true;
        chestLists.remove(w.id());
        return true;
    }

    /** Supplier: the money they leave in your wallet. */
    public void setReserve(Worker w, double reserve) {
        w.reserve = Math.max(0, reserve);
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

    /** Chests and barrels remember who placed them: workers only use their owner's chests around them. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (isChest(b.getType()) && b.getState(false) instanceof TileState t) {
            t.getPersistentDataContainer().set(Keys.PLACER, PersistentDataType.STRING,
                    e.getPlayer().getUniqueId().toString());
        }
    }
}
