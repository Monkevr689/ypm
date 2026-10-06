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
import java.util.HashMap;
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
 * pick; a Runner sells the finished product. They walk over, work, walk back
 * home and get paid a small wage for every job (the Runner takes a cut).
 *
 * The work chain: one player's workers within chain-radius of each other are
 * a crew. Each one fetches what they need from the others (a Dryer takes
 * fresh buds from a Farmhand, a Cook takes dried buds from a Dryer or coca
 * paste from another Cook...) and from chests right next to any of them, and
 * puts what they make in the chest next to them when they have one. Nobody
 * takes what another worker needs for their own job.
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

    /** Cook: what they make from now on. */
    public void setRecipe(Worker w, dev.kushcraft.lab.LabRecipe r) {
        setJob(w, r == null ? null : r.name());
    }

    /** Cook: a LabRecipe name, Worker.ROLL_JOINT / ROLL_BLUNT, or null. */
    public void setJob(Worker w, String job) {
        w.recipe = job;
        ItemType made = w.product();
        w.status = made == null ? "Pick a drug for them" : "Ready to make " + made.display();
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
        // a satchel getting full: first put the finished stuff in a chest next to them
        if (w.type() != WorkerType.RUNNER && w.freeSlots() < 6 && planDeposit(w)) {
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
        return w.type() != WorkerType.RUNNER && planDeposit(w);
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
    // the work chain: crews, chests, fetching and handing over
    // ------------------------------------------------------------------

    /** How far apart one player's workers can be and still hand things to each other (blocks). */
    public int chainRadius() {
        return Math.max(4, plugin.getConfig().getInt("workers.chain-radius", 32));
    }

    /** The owner's other workers within chain-radius: they take from each other's satchels and chests. */
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

    /** A chest or barrel right next to a worker (2 blocks or less): they put their work in it. */
    public record Chest(Block block) {
        /** The chest's inventory right now (both halves of a double chest), or an empty one when it's gone. */
        public Inventory inv() {
            Inventory inv = inventoryOf(block);
            return inv != null ? inv : Bukkit.createInventory(null, 9);
        }
    }

    private static boolean isChest(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL;
    }

    private static Inventory inventoryOf(Block b) {
        return isChest(b.getType()) && b.getState(false) instanceof Container c ? c.getInventory() : null;
    }

    public List<Chest> chests(Worker w) {
        List<Chest> out = new ArrayList<>();
        Location h = w.home();
        if (h == null || !w.isLoaded()) {
            return out;
        }
        World world = h.getWorld();
        int hx = h.getBlockX(), hy = h.getBlockY(), hz = h.getBlockZ();
        Set<String> seen = new HashSet<>();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int x = hx + dx, z = hz + dz;
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    Block b = world.getBlockAt(x, hy + dy, z);
                    Inventory inv = isChest(b.getType()) ? inventoryOf(b) : null;
                    if (inv == null) {
                        continue;
                    }
                    Location l = inv.getLocation();
                    // both halves of a double chest are one inventory
                    String key = l == null ? b.toString() : l.getX() + "," + l.getY() + "," + l.getZ();
                    if (seen.add(key)) {
                        out.add(new Chest(b));
                    }
                }
            }
        }
        return out;
    }

    /** Somewhere to take things from: another worker's satchel, or a chest. */
    private record Source(Worker worker, Chest chest) {
        Inventory inv() {
            return worker != null ? worker.satchel : chest.inv();
        }

        BlockKey where() {
            return worker != null ? BlockKey.of(worker.home()) : BlockKey.of(chest.block());
        }

        String name() {
            return worker != null ? worker.name() : "a chest";
        }
    }

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
        dev.kushcraft.lab.LabRecipe r = w.recipe();
        if (r == null) {
            return false;
        }
        for (dev.kushcraft.lab.LabRecipe.Ingredient ing : r.ingredients()) {
            if (ing.matches(it)) {
                return true;
            }
        }
        // empty bottles get filled with water for the next batch
        return it.getType() == Material.GLASS_BOTTLE && !Items.isCustom(it) && needsWater(r);
    }

    private static boolean needsWater(dev.kushcraft.lab.LabRecipe r) {
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
            case COOK -> t == w.product();
            case RUNNER -> false;
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

    /** Moves matching items from one inventory to another; returns how many moved. */
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
        dirty |= moved > 0;
        return moved;
    }

    /** Where a worker can fetch from: their own chests, then the crew's satchels (things they don't need) and chests. */
    private List<Source> sources(Worker w, boolean fromWorkers) {
        List<Source> out = new ArrayList<>();
        Set<BlockKey> seen = new HashSet<>();
        for (Chest c : chests(w)) {
            if (seen.add(BlockKey.of(c.block()))) {
                out.add(new Source(null, c));
            }
        }
        for (Worker o : crew(w)) {
            if (fromWorkers) {
                out.add(new Source(o, null));
            }
            for (Chest c : chests(o)) {
                if (seen.add(BlockKey.of(c.block()))) {
                    out.add(new Source(null, c));
                }
            }
        }
        return out;
    }

    /**
     * Plans a trip to fetch matching items from a crew member's satchel (only what they don't
     * need themselves) or a chest. Returns false when nobody has any.
     */
    private boolean fetch(Worker w, Predicate<ItemStack> want, boolean fromWorkers, String what) {
        if (w.freeSlots() == 0) {
            return false;
        }
        for (Source src : sources(w, fromWorkers)) {
            Predicate<ItemStack> ok = src.worker() == null ? want : it -> want.test(it) && !uses(src.worker(), it);
            if (first(src.inv(), ok) == null) {
                continue;
            }
            w.status = "Fetching " + what + " from " + src.name();
            go(w, src.where(), () -> {
                int n = move(src.inv(), w.satchel, ok, Integer.MAX_VALUE);
                if (n > 0) {
                    w.status = "Got " + n + " " + what + " from " + src.name();
                }
                return n > 0;
            });
            return true;
        }
        return false;
    }

    /** True when some source has matching items (without planning a trip). */
    private boolean available(Worker w, Predicate<ItemStack> want, boolean fromWorkers) {
        for (Source src : sources(w, fromWorkers)) {
            Predicate<ItemStack> ok = src.worker() == null ? want : it -> want.test(it) && !uses(src.worker(), it);
            if (first(src.inv(), ok) != null) {
                return true;
            }
        }
        return false;
    }

    /** Puts what a worker made (and empty bottles) in a chest next to them. */
    private boolean planDeposit(Worker w) {
        Predicate<ItemStack> out = it -> (produces(w, it) && !uses(w, it)) || junk(w, it);
        if (first(w, out) == null) {
            return false;
        }
        for (Chest c : chests(w)) {
            if (c.inv().firstEmpty() < 0) {
                continue;
            }
            w.status = "Putting their work in the chest";
            go(w, BlockKey.of(c.block()), () -> {
                if (inventoryOf(c.block()) == null) {
                    return false; // the chest is gone
                }
                int n = move(w.satchel, c.inv(), out, Integer.MAX_VALUE);
                if (n > 0) {
                    w.status = "Put " + n + " items in the chest";
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

    // ---- farmhand ----

    private boolean planFarmhand(Worker w) {
        int r = radius(w);
        Location h = w.home();
        // 1. harvest ripe plants, a few in one round (closest first, then the closest to that one)
        List<Plant> ripe = new ArrayList<>();
        for (Plant p : plugin.plants().all()) {
            if (p.mature() && w.owner().equals(p.owner()) && near(p.key(), w, r) && p.key().isLoaded()) {
                ripe.add(p);
            }
        }
        if (!ripe.isEmpty()) {
            if (w.freeSlots() < 2) {
                w.status = "<red>Satchel full! Empty it (or put a chest next to them).";
                return false;
            }
            if (!canPay(w)) {
                return false;
            }
            List<Plant> round = route(ripe, h, 2 + w.level());
            w.status = "Harvesting " + round.size() + " plant" + (round.size() > 1 ? "s" : "");
            for (Plant target : round) {
                String what = plantName(target);
                go(w, target.key(), () -> {
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
            if (!p.fertilized() && !p.mature() && w.owner().equals(p.owner()) && near(p.key(), w, r) && p.key().isLoaded()) {
                growing.add(p);
            }
        }
        if (fert != null && !growing.isEmpty()) {
            List<Plant> round = route(growing, h, Math.min(fert.getAmount(), 2 + w.level()));
            w.status = "Fertilizing " + round.size() + " plant" + (round.size() > 1 ? "s" : "");
            for (Plant target : round) {
                go(w, target.key(), () -> {
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
                    go(w, spot, () -> {
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
        } else if (available(w, isSeed, false) && hasEmptySoil(w, r) && fetch(w, isSeed, false, "seeds")) {
            // 4. out of seeds but there's room to plant: seeds from a chest
            return true;
        }
        if (fert == null && !growing.isEmpty() && fetch(w, it -> Items.type(it) == ItemType.FERTILIZER, false, "fertilizer")) {
            return true;
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
     * Spare seeds become fertilizer: when the satchel gets full and there's no chest, every seed
     * past 16 of a kind is composted (4 seeds = 1 fertilizer).
     */
    public void compost(Worker w) {
        if (w.freeSlots() >= 6 || !chests(w).isEmpty()) {
            return;
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
            String key = t.name() + ":" + (s == null ? "" : s.id());
            int have = kept.getOrDefault(key, 0);
            int keep = Math.max(0, Math.min(it.getAmount(), 16 - have));
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

    /** Empty farmland or the owner's Planters near home where this kind (null: anything) can grow, closest first. */
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
                            || (kind != null && !plugin.plants().isSoil(soil, kind))) {
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

    private List<Machine> labsNear(Worker w, int r) {
        List<Machine> labs = new ArrayList<>();
        for (Machine m : plugin.machines().all()) {
            if (m.type() == MachineType.LAB_STATION && w.owner().equals(m.owner()) && near(m.key(), w, r)
                    && m.key().isLoaded()) {
                labs.add(m);
            }
        }
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
                    w.status = "<red>Satchel full! Empty it (or put a chest next to them).";
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
        // 3. fetch fresh buds from the crew (Farmhands, chests)
        if (first(w, it -> Items.type(it) == ItemType.BUD_FRESH) == null
                && fetch(w, it -> Items.type(it) == ItemType.BUD_FRESH && Items.strain(it) != null, true, "fresh buds")) {
            return true;
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
        dev.kushcraft.lab.LabRecipe recipe = w.recipe();
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
        // 1. collect finished batches of their recipe
        for (Machine lab : labs) {
            if (lab.busy() && lab.jobDone() && lab.output() != null && recipe.name().equals(lab.job())) {
                if (w.freeSlots() < 1) {
                    w.status = "<red>Satchel full! Empty it (or put a chest next to them).";
                    return false;
                }
                w.status = "Collecting a batch";
                go(w, lab.key(), () -> {
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
        // 2. start a batch at a free lab
        dev.kushcraft.lab.LabRecipe.Ingredient missing = dev.kushcraft.lab.Cooking.missing(w.satchel, recipe, null, 0);
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
                    var res = dev.kushcraft.lab.Cooking.start(w.satchel, lab, recipe, dev.kushcraft.lab.Cooking.MAX_BATCHES,
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
        // 4. fetch what's missing from the crew (Farmhands, Dryers, other Cooks, chests)
        Predicate<ItemStack> need = missing.strainSource() ? it -> Items.type(it) == missing.custom() && Items.strain(it) != null
                : missing::matches;
        if (fetch(w, need, true, missing.name())) {
            return true;
        }
        if (missing.vanilla() == Material.POTION
                && fetch(w, it -> it.getType() == Material.GLASS_BOTTLE && !Items.isCustom(it), false, "bottles")) {
            return true;
        }
        w.status = "<yellow>Needs " + missing.amount() + " " + missing.name() + " <gray>(satchel or a chest next to them)";
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
        if (g == null && fetch(w, it -> Items.type(it) == ItemType.BUD_DRIED && Items.strain(it) != null, true,
                "dried buds")) {
            return true;
        }
        if (wraps == 0 && fetch(w, it -> Items.type(it) == wrap, false, wrap.display())) {
            return true;
        }
        w.status = "<yellow>Needs " + (g == null ? budsEach + " Dried Bud" : wrap.display())
                + " <gray>(satchel or a chest next to them)";
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
        int n = dev.kushcraft.util.InventoryUtil.remove(w.satchel,
                it -> it.getType() == Material.GLASS_BOTTLE && !Items.isCustom(it), Integer.MAX_VALUE);
        if (n <= 0) {
            return 0;
        }
        ItemStack water = new ItemStack(Material.POTION, n);
        water.editMeta(org.bukkit.inventory.meta.PotionMeta.class,
                m -> m.setBasePotionType(org.bukkit.potion.PotionType.WATER));
        List<ItemStack> stacks = new ArrayList<>();
        for (int left = n; left > 0; left -= water.getMaxStackSize()) {
            ItemStack s = water.clone();
            s.setAmount(Math.min(left, water.getMaxStackSize()));
            stacks.add(s);
        }
        drop(w, stash(w, stacks), BlockKey.of(w.home()));
        Location c = w.home();
        c.getWorld().playSound(c, "minecraft:item.bottle.fill", SoundCategory.NEUTRAL, 1f, 1f);
        w.status = "Filled " + n + " bottles with water";
        return n;
    }

    // ---- runner ----

    /** Share of every sale the Runner keeps (0.1 = 10%). */
    public double runnerCut() {
        return Math.max(0, Math.min(0.9, plugin.getConfig().getDouble("workers.runner.cut", 0.1)));
    }

    private boolean planRunner(Worker w) {
        // 1. sell what they carry (at a Dealer Stand of yours nearby, else where they stand)
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
                go(w, stand.key(), sell);
            } else {
                here(w, sell);
            }
            return true;
        }
        // 2. collect finished product nobody in the crew needs
        List<Worker> crew = crew(w);
        Predicate<ItemStack> spare = it -> sellable(it) && crew.stream().noneMatch(o -> uses(o, it));
        for (Chest c : chests(w)) {
            // their own chest: anything that sells
            if (first(c.inv(), spare) != null) {
                return takeForSale(w, new Source(null, c), spare);
            }
        }
        for (Worker o : crew) {
            if (o.type() == WorkerType.RUNNER) {
                continue;
            }
            Predicate<ItemStack> theirs = it -> spare.test(it) && produces(o, it);
            if (first(o.satchel, theirs) != null) {
                return takeForSale(w, new Source(o, null), theirs);
            }
            for (Chest c : chests(o)) {
                if (first(c.inv(), theirs) != null) {
                    return takeForSale(w, new Source(null, c), theirs);
                }
            }
        }
        w.status = crew.isEmpty() ? "<yellow>Put product in a chest next to them, or hire workers nearby."
                : "Waiting for product to sell";
        return false;
    }

    private boolean takeForSale(Worker w, Source src, Predicate<ItemStack> match) {
        w.status = "Picking up product from " + src.name();
        go(w, src.where(), () -> move(src.inv(), w.satchel, match, Integer.MAX_VALUE) > 0);
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

    /** Everything your workers made (not what they need) goes to your inventory. Returns how many items. */
    public int collectAll(Player p) {
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
                    dirty = true;
                    return n; // inventory full
                }
                w.satchel.setItem(i, null);
                dirty = true;
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

    /** Whatever didn't fit in the satchel goes in a chest next to them, else falls on the ground. */
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
