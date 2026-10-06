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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
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
 * and collects them when they're dry. They walk over, work, walk back home
 * and get paid a small wage for every job.
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

    public int maxPerPlayer() {
        return Math.max(0, plugin.getConfig().getInt("workers.max-per-player", 4));
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
        return 0.14 + 0.03 * (w.level() - 1);
    }

    /** Wage for one job. */
    public double wage(WorkerType t) {
        return Math.max(0, plugin.getConfig().getDouble("workers." + t.id() + ".wage", 3));
    }

    /** Price to hire one in the Shop (for the menus). */
    public double hirePrice(WorkerType t) {
        for (var e : plugin.shop().gear()) {
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
        if (have >= maxPerPlayer() && !p.hasPermission("kushcraft.admin")) {
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
        return true;
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
            m.setDescription(Text.mm(w.paused ? "<red>Paused" : "<gray>" + w.type().display()
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
        return switch (w.type()) {
            case FARMHAND -> planFarmhand(w);
            case DRYER -> planDryer(w);
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

    private double dist2(BlockKey k, Worker w) {
        Location h = w.home();
        double dx = k.x() + 0.5 - h.getX(), dy = k.y() - h.getY(), dz = k.z() + 0.5 - h.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static ItemStack first(Worker w, Predicate<ItemStack> match) {
        for (ItemStack it : w.satchel.getStorageContents()) {
            if (it != null && !it.getType().isAir() && match.test(it)) {
                return it;
            }
        }
        return null;
    }

    // ---- farmhand ----

    private boolean planFarmhand(Worker w) {
        int r = radius(w);
        // 1. harvest the closest ripe plant
        Plant ripe = null;
        double best = Double.MAX_VALUE;
        for (Plant p : plugin.plants().all()) {
            if (p.mature() && w.owner().equals(p.owner()) && near(p.key(), w, r) && p.key().isLoaded()) {
                double d = dist2(p.key(), w);
                if (d < best) {
                    best = d;
                    ripe = p;
                }
            }
        }
        if (ripe != null) {
            if (w.freeSlots() < 2) {
                w.status = "<red>Satchel full! Empty it so they can harvest.";
                return false;
            }
            if (!canPay(w)) {
                return false;
            }
            Plant target = ripe;
            String what = plantName(target);
            w.status = "Harvesting " + what;
            go(w, target.key(), () -> {
                if (plugin.plants().at(target.key()) != target || !target.mature()) {
                    return false;
                }
                List<ItemStack> got = plugin.plants().pick(target, true);
                drop(w, stash(w, got), target.key());
                pay(w);
                w.status = "Harvested " + what;
                return true;
            });
            return true;
        }
        // 2. fertilize a growing plant
        ItemStack fert = first(w, it -> Items.type(it) == ItemType.FERTILIZER);
        if (fert != null) {
            for (Plant p : plugin.plants().all()) {
                if (!p.fertilized() && !p.mature() && w.owner().equals(p.owner()) && near(p.key(), w, r)
                        && p.key().isLoaded()) {
                    Plant target = p;
                    w.status = "Fertilizing " + plantName(p);
                    go(w, target.key(), () -> {
                        ItemStack f = first(w, it -> Items.type(it) == ItemType.FERTILIZER);
                        if (f == null || plugin.plants().at(target.key()) != target || !plugin.plants().fertilize(target)) {
                            return false;
                        }
                        f.setAmount(f.getAmount() - 1);
                        dirty = true;
                        return true;
                    });
                    return true;
                }
            }
        }
        // 3. plant seeds from the satchel on empty farmland / planters
        ItemStack seed = first(w, it -> {
            Plant.Kind k = PlantManager.kindOf(Items.type(it));
            return k != null && (k != Plant.Kind.CANNABIS || Items.strain(it) != null);
        });
        if (seed != null) {
            Plant.Kind kind = PlantManager.kindOf(Items.type(seed));
            Block soil = emptySoil(w, kind, r);
            if (soil != null) {
                if (!canPay(w)) {
                    return false;
                }
                BlockKey spot = BlockKey.of(soil).up();
                ItemStack chosen = seed;
                w.status = "Planting " + Text.plain(chosen.effectiveName());
                go(w, spot, () -> {
                    Block s = spot.block();
                    if (chosen.getAmount() <= 0 || s == null || !s.getType().isAir() || plugin.plants().at(spot) != null
                            || !plugin.plants().isSoil(s.getRelative(BlockFace.DOWN), kind)) {
                        return false;
                    }
                    Strain strain = kind == Plant.Kind.CANNABIS ? Items.strain(chosen) : null;
                    chosen.setAmount(chosen.getAmount() - 1);
                    plugin.plants().plantAt(spot, kind, strain, w.owner());
                    Location c = spot.bottomCenter();
                    c.getWorld().playSound(c, "minecraft:item.crop.plant", SoundCategory.BLOCKS, 1f, 1f);
                    pay(w);
                    return true;
                });
                return true;
            }
        }
        w.status = "Waiting for your plants to ripen";
        return false;
    }

    private String plantName(Plant p) {
        return p.kind() == Plant.Kind.CANNABIS ? Text.plain(Text.mm(plugin.strains().getOrDefault(p.strainId()).colored()))
                : p.kind().display();
    }

    /** Empty farmland or one of the owner's Planters near home where this kind can grow. */
    private Block emptySoil(Worker w, Plant.Kind kind, int r) {
        Location h = w.home();
        World world = h.getWorld();
        int hx = h.getBlockX(), hy = h.getBlockY(), hz = h.getBlockZ();
        Block found = null;
        double best = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r || !world.isChunkLoaded((hx + dx) >> 4, (hz + dz) >> 4)) {
                    continue;
                }
                for (int dy = -3; dy <= 1; dy++) {
                    Block soil = world.getBlockAt(hx + dx, hy + dy, hz + dz);
                    Machine m = plugin.machines().at(soil);
                    boolean planter = m != null && m.type() == MachineType.PLANTER_BOX && w.owner().equals(m.owner());
                    if (!planter && soil.getType() != Material.FARMLAND) {
                        continue;
                    }
                    Block above = soil.getRelative(BlockFace.UP);
                    BlockKey key = BlockKey.of(above);
                    if (!above.getType().isAir() || plugin.plants().at(key) != null || plugin.machines().at(key) != null
                            || !plugin.plants().isSoil(soil, kind)) {
                        continue;
                    }
                    double d = dx * dx + dz * dz + dy * dy;
                    if (d < best) {
                        best = d;
                        found = soil;
                    }
                }
            }
        }
        return found;
    }

    // ---- dryer ----

    private boolean planDryer(Worker w) {
        int r = radius(w);
        List<Machine> labs = new ArrayList<>();
        for (Machine m : plugin.machines().all()) {
            if (m.type() == MachineType.LAB_STATION && w.owner().equals(m.owner()) && near(m.key(), w, r)
                    && m.key().isLoaded()) {
                labs.add(m);
            }
        }
        if (labs.isEmpty()) {
            w.status = "<red>No Drug Lab of yours within " + r + " blocks.";
            return false;
        }
        // 1. take dry buds off the racks
        for (Machine lab : labs) {
            if (lab.racksDry() > 0) {
                if (w.freeSlots() < lab.racksDry()) {
                    w.status = "<red>Satchel full! Empty it so they can collect.";
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
                go(w, lab.key(), () -> {
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
        // 3. fetch fresh buds from a Farmhand nearby
        if (first(w, it -> Items.type(it) == ItemType.BUD_FRESH) == null && w.freeSlots() > 0) {
            for (Worker f : of(w.owner())) {
                if (f.type() != WorkerType.FARMHAND || f.pos == null || !f.worldName().equals(w.worldName())
                        || f.home().distanceSquared(w.home()) > 4.0 * r * r
                        || first(f, it -> Items.type(it) == ItemType.BUD_FRESH) == null) {
                    continue;
                }
                w.status = "Fetching buds from " + f.name();
                go(w, BlockKey.of(f.home()), () -> {
                    int moved = 0;
                    ItemStack[] items = f.satchel.getStorageContents();
                    for (int i = 0; i < items.length; i++) {
                        if (Items.type(items[i]) != ItemType.BUD_FRESH) {
                            continue;
                        }
                        int had = items[i].getAmount();
                        List<ItemStack> left = new ArrayList<>(w.satchel.addItem(items[i].clone()).values());
                        int kept = left.isEmpty() ? 0 : left.get(0).getAmount();
                        moved += had - kept;
                        f.satchel.setItem(i, kept > 0 ? left.get(0) : null);
                        if (kept > 0) {
                            break;
                        }
                    }
                    dirty = true;
                    return moved > 0;
                });
                return true;
            }
        }
        w.status = "Waiting for fresh buds";
        return false;
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

    /** Whatever didn't fit in the satchel falls on the ground. */
    private void drop(Worker w, List<ItemStack> left, BlockKey at) {
        Location l = at.center();
        if (left.isEmpty() || l == null) {
            return;
        }
        for (ItemStack it : left) {
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
        if (len > 24) {
            return false;
        }
        int n = (int) Math.ceil(len / 0.3);
        for (int i = 1; i < n; i++) {
            Location p = a.clone().add(d.clone().multiply(i / (double) n));
            Block feet = p.getBlock();
            if (!feet.isPassable() || !feet.getRelative(BlockFace.UP).isPassable()) {
                return false;
            }
        }
        return true;
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
                w.current = new Worker.Step(home, null, null);
            }
            w.workTicks = 0;
        }
        Worker.Step s = w.current;
        Location to = s.stand();
        double dist = to == null || !to.getWorld().equals(w.pos.getWorld()) ? 0 : w.pos.distance(to);
        if (dist > 0.02) {
            Vector dir = to.toVector().subtract(w.pos.toVector());
            double step = speed(w);
            if (dist <= step) {
                w.pos = to.clone();
            } else {
                w.pos.add(dir.clone().normalize().multiply(step));
            }
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
            w.pos.setYaw(yaw);
            w.pos.setPitch(0);
            if (e != null) {
                e.teleport(w.pos);
                if (e instanceof LivingEntity le) {
                    le.setBodyYaw(yaw);
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
}
