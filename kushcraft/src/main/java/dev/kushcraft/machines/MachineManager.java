package dev.kushcraft.machines;

import dev.kushcraft.KushCraft;
import dev.kushcraft.Keys;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.strains.Strain;
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
public final class MachineManager implements dev.kushcraft.storage.Persistence.Source {

    private final KushCraft plugin;
    private final File file;
    private final Map<BlockKey, Machine> machines = new HashMap<>();
    private final Map<String, Set<BlockKey>> byChunk = new HashMap<>();
    private final Set<BlockKey> lamps = new HashSet<>();

    public MachineManager(KushCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "machines.yml");
    }

    // ------------------------------------------------------------------
    // storage: one database row per machine
    // ------------------------------------------------------------------

    /** Positions of machines removed since the last database write. */
    private final Set<String> deleted = new HashSet<>();

    public void load() {
        for (Machine m : machines.values()) {
            despawn(m);
        }
        machines.clear();
        byChunk.clear();
        lamps.clear();
        deleted.clear();
        long shift = plugin.downtime();
        List<Machine> rows = plugin.db().call(c -> {
            List<Machine> out = new ArrayList<>();
            try (java.sql.Statement st = c.createStatement();
                 java.sql.ResultSet rs = st.executeQuery("SELECT * FROM machines")) {
                while (rs.next()) {
                    BlockKey key = BlockKey.parse(rs.getString("pos"));
                    MachineType type = MachineType.parse(rs.getString("type"));
                    if (key == null || type == null) {
                        continue;
                    }
                    UUID owner = null;
                    try {
                        String o = rs.getString("owner");
                        owner = o == null ? null : UUID.fromString(o);
                    } catch (IllegalArgumentException ignored) {
                        // no owner
                    }
                    Machine m = new Machine(key, type, (float) rs.getDouble("yaw"), owner);
                    m.level = Math.max(1, rs.getInt("level"));
                    String job = rs.getString("job");
                    if (job != null) {
                        m.job = job;
                        // the server being off doesn't count as cooking time
                        m.jobStart = rs.getLong("job_start") + shift;
                        m.jobEnd = rs.getLong("job_end") + shift;
                        m.output = dev.kushcraft.storage.ItemCodec.decode(rs.getBytes("output"));
                        if (m.output == null) {
                            m.clearJob();
                        }
                    }
                    readRacks(m, rs.getString("racks"), shift);
                    m.dirty = shift > 0;
                    out.add(m);
                }
            }
            return out;
        });
        for (Machine m : rows) {
            add(m);
        }
        loadCleanup();
        if (rows.isEmpty() && file.exists() && !plugin.legacyImported()) {
            importYaml();
        }
        plugin.getLogger().info("Loaded " + machines.size() + " machines.");
    }

    private static void readRacks(Machine m, String text, long shift) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String part : text.split(";")) {
            String[] f = part.split("\\|");
            if (f.length != 6) {
                continue;
            }
            try {
                int i = Integer.parseInt(f[0]);
                if (i >= 0 && i < Machine.RACKS) {
                    m.racks[i] = new Machine.Rack(f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                            Long.parseLong(f[4]) + shift, Long.parseLong(f[5]) + shift);
                }
            } catch (NumberFormatException ignored) {
                // a broken rack
            }
        }
    }

    private static String writeRacks(Machine m) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Machine.RACKS; i++) {
            Machine.Rack r = m.racks[i];
            if (r != null) {
                if (!b.isEmpty()) {
                    b.append(';');
                }
                b.append(i).append('|').append(r.strain()).append('|').append(r.quality()).append('|').append(r.amount())
                        .append('|').append(r.start()).append('|').append(r.done());
            }
        }
        return b.isEmpty() ? null : b.toString();
    }

    /** 8.0 and older kept machines in machines.yml: read it once. */
    private void importYaml() {
        plugin.legacyFile(file);
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
                // no owner
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
            m.dirty = true;
            add(m);
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

    /** Every machine gets saved again (admin "finish all labs"). Single changes mark themselves. */
    public void markDirty() {
        for (Machine m : machines.values()) {
            m.dirty = true;
        }
    }

    private record Row(String pos, String type, float yaw, String owner, int level, String job, long jobStart,
                       long jobEnd, ItemStack output, String racks) {
    }

    @Override
    public void collect(dev.kushcraft.storage.Persistence.Batch b, boolean full) {
        List<Machine> changed = new ArrayList<>();
        for (Machine m : machines.values()) {
            if (m.dirty) {
                changed.add(m);
            }
        }
        if (changed.isEmpty() && deleted.isEmpty()) {
            return;
        }
        List<Row> rows = new ArrayList<>(changed.size());
        for (Machine m : changed) {
            m.dirty = false;
            rows.add(new Row(m.key().serialize(), m.type().name(), m.yaw(), m.owner() == null ? null : m.owner().toString(),
                    m.level, m.job, m.jobStart, m.jobEnd, m.output == null ? null : m.output.clone(), writeRacks(m)));
        }
        List<String> gone = new ArrayList<>(deleted);
        deleted.clear();
        b.write(c -> {
            if (!gone.isEmpty()) {
                try (java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM machines WHERE pos=?")) {
                    for (String k : gone) {
                        ps.setString(1, k);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            if (!rows.isEmpty()) {
                try (java.sql.PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO machines(pos, type, yaw, owner, level, job, job_start, job_end, output, racks)
                        VALUES(?,?,?,?,?,?,?,?,?,?)
                        ON CONFLICT(pos) DO UPDATE SET type=excluded.type, yaw=excluded.yaw, owner=excluded.owner,
                          level=excluded.level, job=excluded.job, job_start=excluded.job_start, job_end=excluded.job_end,
                          output=excluded.output, racks=excluded.racks""")) {
                    for (Row r : rows) {
                        ps.setString(1, r.pos());
                        ps.setString(2, r.type());
                        ps.setDouble(3, r.yaw());
                        ps.setString(4, r.owner());
                        ps.setInt(5, r.level());
                        ps.setString(6, r.job());
                        ps.setLong(7, r.jobStart());
                        ps.setLong(8, r.jobEnd());
                        ps.setBytes(9, dev.kushcraft.storage.ItemCodec.encode(r.output()));
                        ps.setString(10, r.racks());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        });
        b.onFailure(() -> {
            changed.forEach(m -> m.dirty = true);
            for (String k : gone) {
                BlockKey key = BlockKey.parse(k);
                if (key == null || !machines.containsKey(key)) {
                    deleted.add(k);
                }
            }
        });
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        for (World w : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk c : w.getLoadedChunks()) {
                chunkLoaded(w, c.getX(), c.getZ());
            }
        }
    }

    /** Takes every machine model out of the world (shutdown, reset). The data and blocks stay. */
    public void shutdown() {
        for (Machine m : machines.values()) {
            despawn(m);
        }
    }

    private void add(Machine m) {
        machines.put(m.key(), m);
        deleted.remove(m.key().serialize());
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
        deleted.add(m.key().serialize());
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
        spawn(m);
        if (type == MachineType.GROW_LAMP) {
            Block above = block.getRelative(0, 1, 0);
            if (above.getType().isAir()) {
                above.setType(Material.LIGHT);
            }
        }
        return m;
    }

    /** The block (and a Grow Lamp's light) of a machine that's gone. */
    private static void clearBlocks(Block b, boolean lamp) {
        if (b != null && b.getType() == Material.BARRIER) {
            b.setType(Material.AIR);
        }
        if (lamp && b != null) {
            Block above = b.getRelative(0, 1, 0);
            if (above.getType() == Material.LIGHT) {
                above.setType(Material.AIR);
            }
        }
    }

    /**
     * Season reset: every machine leaves the world. Blocks in loaded chunks are cleared now; the
     * positions of the rest are returned (the reset stores them, they're cleared when they load).
     */
    public List<String> removeAllBlocks() {
        List<String> later = new ArrayList<>();
        for (Machine m : new ArrayList<>(machines.values())) {
            despawn(m);
            if (m.key().isLoaded()) {
                clearBlocks(m.key().block(), true);
            } else {
                later.add(m.key().serialize());
            }
        }
        return later;
    }

    /** Positions (by chunk) whose leftover machine block is cleared when the chunk loads (after a reset). */
    private final Map<String, Set<BlockKey>> cleanup = new HashMap<>();

    private void loadCleanup() {
        cleanup.clear();
        List<String> rows = plugin.db().call(c -> {
            List<String> out = new ArrayList<>();
            try (java.sql.Statement st = c.createStatement();
                 java.sql.ResultSet rs = st.executeQuery("SELECT pos FROM cleanup")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            return out;
        });
        for (String r : rows) {
            BlockKey k = BlockKey.parse(r);
            if (k != null) {
                cleanup.computeIfAbsent(k.chunkId(), x -> new HashSet<>()).add(k);
            }
        }
    }

    private void cleanUp(String chunk) {
        Set<BlockKey> keys = cleanup.remove(chunk);
        if (keys == null) {
            return;
        }
        List<String> done = new ArrayList<>();
        for (BlockKey k : keys) {
            if (!machines.containsKey(k)) {
                clearBlocks(k.block(), true);
            }
            done.add(k.serialize());
        }
        plugin.db().run(c -> {
            try (java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM cleanup WHERE pos=?")) {
                for (String d : done) {
                    ps.setString(1, d);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
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
        if (!cleanup.isEmpty()) {
            cleanUp(BlockKey.chunkId(w.getName(), cx, cz));
        }
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
