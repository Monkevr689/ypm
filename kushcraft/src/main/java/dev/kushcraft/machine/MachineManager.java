package dev.kushcraft.machine;

import dev.kushcraft.KushCraft;
import dev.kushcraft.Keys;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.BlockKey;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Stores machines and keeps their 3D models in the world. */
public final class MachineManager {

    private final KushCraft plugin;
    private final File file;
    private final Map<BlockKey, Machine> machines = new HashMap<>();
    private final Map<String, Set<BlockKey>> byChunk = new HashMap<>();
    private final Set<BlockKey> lamps = new HashSet<>();
    private boolean dirty;

    public MachineManager(KushCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "machines.yml");
    }

    // ------------------------------------------------------------------
    // storage
    // ------------------------------------------------------------------

    public void load() {
        machines.clear();
        byChunk.clear();
        lamps.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("machines");
        if (sec == null) {
            return;
        }
        for (String k : sec.getKeys(false)) {
            BlockKey key = BlockKey.parse(k);
            ConfigurationSection s = sec.getConfigurationSection(k);
            if (key == null || s == null) {
                continue;
            }
            MachineType type = MachineType.parse(s.getString("type", ""));
            if (type == null) {
                continue;
            }
            UUID owner = null;
            try {
                String o = s.getString("owner");
                owner = o == null ? null : UUID.fromString(o);
            } catch (IllegalArgumentException ignored) {
            }
            Machine m = new Machine(key, type, (float) s.getDouble("yaw"), owner);
            m.level = Math.max(1, s.getInt("level", 1));
            if (s.isString("job")) {
                m.job = s.getString("job");
                m.jobStart = s.getLong("job-start");
                m.jobEnd = s.getLong("job-end");
                m.output = decode(s.getString("output"));
                if (m.output == null) {
                    m.clearJob();
                }
            }
            if (s.getInt("rack-amount") > 0) {
                // saved by 2.x: one rack
                m.racks[0] = new Machine.Rack(s.getString("rack-strain"), s.getInt("rack-quality", 3),
                        s.getInt("rack-amount"), s.getLong("rack-done") - 60_000L, s.getLong("rack-done"));
            }
            ConfigurationSection rs = s.getConfigurationSection("racks");
            if (rs != null) {
                for (String rk : rs.getKeys(false)) {
                    ConfigurationSection r = rs.getConfigurationSection(rk);
                    int i;
                    try {
                        i = Integer.parseInt(rk);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    if (r != null && i >= 0 && i < Machine.RACKS && r.getInt("amount") > 0 && r.isString("strain")) {
                        m.racks[i] = new Machine.Rack(r.getString("strain"), r.getInt("quality", 3), r.getInt("amount"),
                                r.getLong("start"), r.getLong("done"));
                    }
                }
            }
            add(m);
        }
        plugin.getLogger().info("Loaded " + machines.size() + " machines.");
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        ConfigurationSection sec = y.createSection("machines");
        for (Machine m : machines.values()) {
            ConfigurationSection s = sec.createSection(m.key().serialize());
            s.set("type", m.type().name());
            s.set("yaw", (double) m.yaw());
            if (m.owner() != null) {
                s.set("owner", m.owner().toString());
            }
            if (m.level > 1) {
                s.set("level", m.level);
            }
            if (m.job != null && m.output != null) {
                s.set("job", m.job);
                s.set("job-start", m.jobStart);
                s.set("job-end", m.jobEnd);
                s.set("output", encode(m.output));
            }
            for (int i = 0; i < Machine.RACKS; i++) {
                Machine.Rack r = m.racks[i];
                if (r != null) {
                    String k = "racks." + i + ".";
                    s.set(k + "strain", r.strain());
                    s.set(k + "quality", r.quality());
                    s.set(k + "amount", r.amount());
                    s.set(k + "start", r.start());
                    s.set(k + "done", r.done());
                }
            }
        }
        try {
            y.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save machines.yml", e);
        }
    }

    private static String encode(ItemStack item) {
        return item == null ? null : Base64.getEncoder().encodeToString(item.serializeAsBytes());
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
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
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
        for (Machine m : machines.values()) {
            despawn(m);
        }
        save();
    }

    private void add(Machine m) {
        machines.put(m.key(), m);
        byChunk.computeIfAbsent(m.key().chunkId(), k -> new HashSet<>()).add(m.key());
        if (m.type() == MachineType.GROW_LAMP) {
            lamps.add(m.key());
        }
    }

    private void forget(Machine m) {
        machines.remove(m.key());
        lamps.remove(m.key());
        Set<BlockKey> set = byChunk.get(m.key().chunkId());
        if (set != null) {
            set.remove(m.key());
            if (set.isEmpty()) {
                byChunk.remove(m.key().chunkId());
            }
        }
        markDirty();
    }

    public Machine at(BlockKey key) {
        return machines.get(key);
    }

    public Machine at(Block b) {
        return b == null ? null : machines.get(BlockKey.of(b));
    }

    public Collection<Machine> all() {
        return machines.values();
    }

    /** Machines in the chunks within r blocks of x, z (a quick look-up by chunk; check the distance yourself). */
    public List<Machine> near(String world, int x, int z, int r) {
        List<Machine> out = new ArrayList<>();
        for (int cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++) {
            for (int cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++) {
                Set<BlockKey> keys = byChunk.get(BlockKey.chunkId(world, cx, cz));
                if (keys == null) {
                    continue;
                }
                for (BlockKey k : keys) {
                    Machine m = machines.get(k);
                    if (m != null) {
                        out.add(m);
                    }
                }
            }
        }
        return out;
    }

    public boolean lampNear(BlockKey key, int radius) {
        double r2 = (double) radius * radius;
        for (BlockKey l : lamps) {
            if (l.world().equals(key.world()) && l.distanceSq(key) <= r2) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // place / break
    // ------------------------------------------------------------------

    /** Called after the barrier block was placed by the player. */
    public Machine place(Player player, Block block, MachineType type, int level) {
        float yaw = Math.round(player.getLocation().getYaw() / 90f) * 90f;
        Machine m = placeAt(block, type, yaw, player.getUniqueId());
        m.level(level);
        Location c = m.key().center();
        c.getWorld().playSound(c, "minecraft:block.wood.place", SoundCategory.BLOCKS, 1f, 0.9f);
        player.sendActionBar(Text.mm("<green>" + type.item().display() + " placed! <gray>Right-click to use, punch to pick up."));
        return m;
    }

    /** Registers a machine on an existing barrier block (used by placing and /kush selftest). */
    public Machine placeAt(Block block, MachineType type, float yaw, UUID owner) {
        if (block.getType() != Material.BARRIER) {
            block.setType(Material.BARRIER);
        }
        Machine m = new Machine(BlockKey.of(block), type, yaw, owner);
        add(m);
        markDirty();
        spawn(m);
        if (type == MachineType.GROW_LAMP) {
            Block above = block.getRelative(0, 1, 0);
            if (above.getType().isAir()) {
                above.setType(Material.LIGHT);
            }
        }
        return m;
    }

    /** Removes the machine, drops its item (unless creative) and anything stored inside. */
    public void breakMachine(Machine m, Player player) {
        Block b = m.key().block();
        Location c = m.key().center();
        despawn(m);
        forget(m);
        if (b != null && b.getType() == Material.BARRIER) {
            b.setType(Material.AIR);
        }
        if (m.type() == MachineType.GROW_LAMP && b != null) {
            Block above = b.getRelative(0, 1, 0);
            if (above.getType() == Material.LIGHT) {
                above.setType(Material.AIR);
            }
        }
        if (c == null) {
            return;
        }
        World w = c.getWorld();
        boolean creative = player != null && player.getGameMode() == GameMode.CREATIVE;
        if (!creative) {
            w.dropItemNaturally(c, Items.machine(m.type().item(), m.level()));
        }
        // contents always drop so nothing is lost
        if (m.output != null && m.jobDone()) {
            w.dropItemNaturally(c, m.output);
        } else if (m.output != null && player != null) {
            player.sendMessage(Text.msg("<red>The unfinished lab batch was ruined."));
        }
        for (Machine.Rack r : m.racks) {
            if (r != null) {
                Strain s = plugin.strains().getOrDefault(r.strain());
                w.dropItemNaturally(c, Items.strainItem(r.dry() ? ItemType.BUD_DRIED : ItemType.BUD_FRESH, s,
                        r.quality(), r.amount()));
            }
        }
        w.playSound(c, "minecraft:block.wood.break", SoundCategory.BLOCKS, 1f, 0.9f);
        w.spawnParticle(Particle.BLOCK, c, 30, 0.3, 0.3, 0.3, 0, m.type().particle().createBlockData());
        // plants sitting on a planter box pop off
        if (m.type() == MachineType.PLANTER_BOX) {
            var plant = plugin.plants().at(m.key().up());
            if (plant != null) {
                plugin.plants().destroy(plant, player);
            }
        }
    }

    // ------------------------------------------------------------------
    // drying rack
    // ------------------------------------------------------------------

    public void useRack(Player player, Machine m) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        ItemType t = Items.type(hand);
        if (m.rackAmount() > 0 && m.rackDry()) {
            Strain s = plugin.strains().getOrDefault(m.rackStrain());
            InventoryUtil.give(player, Items.strainItem(ItemType.BUD_DRIED, s, m.rackQuality(), m.rackAmount()));
            player.sendActionBar(Text.mm("<green>Collected " + m.rackAmount() + "x dried " + s.colored()));
            m.emptyRack();
            markDirty();
            refresh(m);
            player.playSound(player.getLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 0.8f, 1f);
            return;
        }
        if (t == ItemType.BUD_FRESH) {
            Strain s = Items.strain(hand);
            int q = Items.quality(hand);
            if (s == null) {
                return;
            }
            if (m.rackAmount() > 0 && (!s.id().equals(m.rackStrain()) || q != m.rackQuality())) {
                player.sendActionBar(Text.mm("<red>This rack is drying a different strain. Wait until it's done."));
                return;
            }
            int add = Math.min(64 - m.rackAmount(), hand.getAmount());
            if (add <= 0) {
                player.sendActionBar(Text.mm("<red>The rack is full (64 buds)."));
                return;
            }
            hand.setAmount(hand.getAmount() - add);
            int seconds = dryingSeconds();
            m.fillRack(s.id(), q, m.rackAmount() + add, System.currentTimeMillis() + seconds * 1000L);
            markDirty();
            refresh(m);
            player.sendActionBar(Text.mm("<green>Hung " + add + " buds to dry. <gray>Ready in " + Text.time(seconds) + "."));
            player.playSound(player.getLocation(), "minecraft:block.azalea_leaves.place", SoundCategory.BLOCKS, 1f, 1f);
            return;
        }
        if (m.rackAmount() > 0) {
            long left = Math.max(0, (m.rackDone() - System.currentTimeMillis()) / 1000);
            Strain s = plugin.strains().getOrDefault(m.rackStrain());
            player.sendActionBar(Text.mm("<yellow>Drying " + m.rackAmount() + "x " + s.colored()
                    + " <gray>- ready in " + Text.time((int) left)));
        } else {
            player.sendActionBar(Text.mm("<gray>Right-click with <green>Fresh Buds</green> to hang them up."));
        }
    }

    /** How long buds take to dry (config drying.seconds). */
    public int dryingSeconds() {
        return Math.max(1, plugin.getConfig().getInt("drying.seconds", 30));
    }

    // ------------------------------------------------------------------
    // ticking (lab bubbles, finished notifications, rack visuals)
    // ------------------------------------------------------------------

    private void tick() {
        for (Machine m : machines.values()) {
            if (!m.key().isLoaded()) {
                continue;
            }
            Location c = m.key().center();
            switch (m.type()) {
                case LAB_STATION -> {
                    int dry = m.racksDry();
                    if (dry > m.dryShown) {
                        c.getWorld().playSound(c, "minecraft:block.azalea_leaves.break", SoundCategory.BLOCKS, 0.8f, 1.3f);
                        Player owner = m.owner() == null ? null : Bukkit.getPlayer(m.owner());
                        if (owner != null && owner.getWorld().equals(c.getWorld()) && owner.getLocation().distanceSquared(c) < 64 * 64) {
                            owner.sendActionBar(Text.mm("<green>Your buds are dry! <gray>(Drug Lab > Dry)"));
                        }
                    }
                    m.dryShown = dry;
                    if (m.job == null) {
                        break;
                    }
                    if (!m.jobDone()) {
                        c.getWorld().spawnParticle(Particle.BUBBLE_POP, c.clone().add(0, 0.65, 0), 3, 0.2, 0.05, 0.2, 0.01);
                        if (Math.random() < 0.3) {
                            c.getWorld().spawnParticle(Particle.WITCH, c.clone().add(0, 0.9, 0), 2, 0.15, 0.1, 0.15, 0);
                        }
                        if (Math.random() < 0.15) {
                            c.getWorld().playSound(c, "minecraft:block.brewing_stand.brew", SoundCategory.BLOCKS, 0.3f, 1.4f);
                        }
                    } else if (!m.notified) {
                        m.notified = true;
                        c.getWorld().playSound(c, "minecraft:block.note_block.chime", SoundCategory.BLOCKS, 1f, 1.2f);
                        c.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, c.clone().add(0, 0.8, 0), 12, 0.3, 0.2, 0.3, 0);
                        Player owner = m.owner() == null ? null : Bukkit.getPlayer(m.owner());
                        if (owner != null && owner.getWorld().equals(c.getWorld()) && owner.getLocation().distanceSquared(c) < 64 * 64) {
                            owner.sendActionBar(Text.mm("<green>Your Lab Station batch is ready!"));
                        }
                    } else if (Math.random() < 0.2) {
                        c.getWorld().spawnParticle(Particle.WAX_ON, c.clone().add(0, 0.8, 0), 2, 0.2, 0.1, 0.2, 0);
                    }
                }
                case DRYING_RACK -> {
                    if (m.rackAmount() > 0 && !modelFor(m).equals(m.shownModel)) {
                        refresh(m);
                        c.getWorld().playSound(c, "minecraft:block.grass.step", SoundCategory.BLOCKS, 0.6f, 0.8f);
                    }
                }
                case GROW_LAMP -> {
                    if (Math.random() < 0.15) {
                        c.getWorld().spawnParticle(Particle.DUST, c.clone().add(0, 0.35, 0), 2, 0.3, 0.05, 0.3, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(0xFF4AD8), 0.8f));
                    }
                }
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // visuals
    // ------------------------------------------------------------------

    private String modelFor(Machine m) {
        if (m.type() == MachineType.DRYING_RACK && m.rackAmount() > 0) {
            return m.rackDry() ? "machine_drying_rack_dry" : "machine_drying_rack_fresh";
        }
        return m.type().model();
    }

    private ItemStack visualItem(Machine m) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(Keys.model(modelFor(m)));
        if (m.type() == MachineType.DRYING_RACK && m.rackAmount() > 0) {
            Items.tint(meta, plugin.strains().getOrDefault(m.rackStrain()).color());
        }
        it.setItemMeta(meta);
        return it;
    }

    public void spawn(Machine m) {
        despawn(m);
        Location c = m.key().center();
        if (c == null || !m.key().isLoaded()) {
            return;
        }
        c.setYaw(m.yaw());
        ItemDisplay d = c.getWorld().spawn(c, ItemDisplay.class, e -> {
            e.setItemStack(visualItem(m));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setPersistent(false);
            e.setViewRange(1.0f);
            e.setDisplayWidth(1.5f);
            e.setDisplayHeight(1.6f);
            if (m.type() == MachineType.GROW_LAMP) {
                e.setBrightness(new Display.Brightness(15, 15));
            }
            e.getPersistentDataContainer().set(Keys.VISUAL, PersistentDataType.STRING, m.key().serialize());
            e.getPersistentDataContainer().set(Keys.MACHINE, PersistentDataType.STRING, m.key().serialize());
        });
        m.displayId = d.getUniqueId();
        m.shownModel = modelFor(m);
    }

    public void refresh(Machine m) {
        Entity e = m.displayId == null ? null : Bukkit.getEntity(m.displayId);
        if (e instanceof ItemDisplay d) {
            d.setItemStack(visualItem(m));
            m.shownModel = modelFor(m);
        } else if (m.key().isLoaded()) {
            spawn(m);
        }
    }

    public void despawn(Machine m) {
        if (m.displayId != null) {
            Entity e = Bukkit.getEntity(m.displayId);
            if (e != null) {
                e.remove();
            }
            m.displayId = null;
        }
        m.shownModel = null;
    }

    public void chunkLoaded(World w, int cx, int cz) {
        Set<BlockKey> keys = byChunk.get(BlockKey.chunkId(w.getName(), cx, cz));
        if (keys == null) {
            return;
        }
        for (BlockKey k : new ArrayList<>(keys)) {
            Machine m = machines.get(k);
            if (m == null) {
                continue;
            }
            Block b = k.block();
            if (b != null && b.getType() != Material.BARRIER) {
                // the barrier vanished (world edit, restore...), put it back
                b.setType(Material.BARRIER);
            }
            spawn(m);
        }
    }

    public void chunkUnloaded(World w, int cx, int cz) {
        Set<BlockKey> keys = byChunk.get(BlockKey.chunkId(w.getName(), cx, cz));
        if (keys == null) {
            return;
        }
        for (BlockKey k : keys) {
            Machine m = machines.get(k);
            if (m != null) {
                despawn(m);
            }
        }
    }
}
