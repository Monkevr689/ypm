package dev.kushcraft.jobs;

import dev.kushcraft.KushCraft;
import dev.kushcraft.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Jobs: everyone gets paid for normal work - mining ores, harvesting grown
 * crops, chopping logs, killing monsters and harvesting KushCraft plants.
 * Blocks placed by players never pay (see PlacedBlocks), spawner mobs don't
 * pay, and there's an hourly cap so farms can't print money.
 */
public final class Jobs implements Listener {

    public enum Job {
        MINER("Miner", "Mine natural ores."),
        FARMER("Farmer", "Harvest fully grown crops."),
        WOODCUTTER("Woodcutter", "Chop logs from trees."),
        HUNTER("Hunter", "Kill monsters."),
        GROWER("Grower", "Harvest KushCraft plants.");

        private final String display;
        private final String description;

        Job(String display, String description) {
            this.display = display;
            this.description = description;
        }

        public String display() {
            return display;
        }

        public String description() {
            return description;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Earnings in the current hour. */
    public static final class Earnings {
        long hourStart;
        double total;
        final Map<Job, Double> perJob = new EnumMap<>(Job.class);

        public double total() {
            return total;
        }

        public double of(Job j) {
            return perJob.getOrDefault(j, 0.0);
        }

        public long minutesLeft() {
            return Math.max(0, (hourStart + 3_600_000L - System.currentTimeMillis()) / 60_000L);
        }
    }

    /** Crops that only pay when fully grown. Others (melons, cane...) pay unless placed. */
    private static final Set<Material> AGED = EnumSet.of(Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.NETHER_WART, Material.COCOA, Material.TORCHFLOWER_CROP);

    private final KushCraft plugin;
    private final PlacedBlocks placed;
    private final Map<Job, Map<String, Double>> rates = new EnumMap<>(Job.class);
    private final Map<UUID, Earnings> earnings = new HashMap<>();

    public Jobs(KushCraft plugin) {
        this.plugin = plugin;
        this.placed = new PlacedBlocks(this::tracked);
    }

    public PlacedBlocks placed() {
        return placed;
    }

    public void load() {
        rates.clear();
        ConfigurationSection pay = plugin.getConfig().getConfigurationSection("jobs.pay");
        for (Job j : Job.values()) {
            Map<String, Double> m = new LinkedHashMap<>();
            ConfigurationSection s = pay == null ? null : pay.getConfigurationSection(j.key());
            if (s != null) {
                for (String k : s.getKeys(false)) {
                    m.put(k.toLowerCase(Locale.ROOT), s.getDouble(k));
                }
            }
            rates.put(j, m);
        }
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("jobs.enabled", true);
    }

    public Map<String, Double> rates(Job j) {
        return rates.getOrDefault(j, Map.of());
    }

    /** Pay for one block/mob of this key ("diamond_ore", "zombie", "logs", "cannabis"...). */
    public double rate(Job j, String key) {
        return rates(j).getOrDefault(key.toLowerCase(Locale.ROOT), 0.0);
    }

    private boolean tracked(Block b) {
        Material m = b.getType();
        String k = m.name().toLowerCase(Locale.ROOT);
        return rate(Job.MINER, k) > 0 || (Tag.LOGS.isTagged(m) && rate(Job.WOODCUTTER, "logs") > 0)
                || (!AGED.contains(m) && rate(Job.FARMER, k) > 0);
    }

    public Earnings earnings(Player p) {
        Earnings e = earnings.computeIfAbsent(p.getUniqueId(), id -> new Earnings());
        long now = System.currentTimeMillis();
        if (now - e.hourStart >= 3_600_000L) {
            e.hourStart = now;
            e.total = 0;
            e.perJob.clear();
        }
        return e;
    }

    public double cap() {
        return plugin.getConfig().getDouble("jobs.hourly-cap", 2000);
    }

    public double pay(Player p, Job job, double amount) {
        return pay(p, job, amount, true);
    }

    /** Pays a player for work, respecting the hourly cap. Returns what was paid. */
    public double pay(Player p, Job job, double amount, boolean announce) {
        if (!enabled() || amount <= 0 || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
            return 0;
        }
        Earnings e = earnings(p);
        double room = cap() - e.total;
        if (room <= 0) {
            if (announce && plugin.getConfig().getBoolean("jobs.action-bar", true)) {
                p.sendActionBar(Text.mm("<gray>Jobs: hourly limit reached - back in " + e.minutesLeft() + " min."));
            }
            return 0;
        }
        double paid = Math.min(room, amount);
        e.total += paid;
        e.perJob.merge(job, paid, Double::sum);
        plugin.economy().deposit(p, paid);
        if (announce && plugin.getConfig().getBoolean("jobs.action-bar", true)) {
            p.sendActionBar(Text.mm("<gold>+" + plugin.economy().format(paid) + " <gray>" + job.display()
                    + " <dark_gray>• this hour " + plugin.economy().format(e.total)));
        }
        return paid;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Material m = b.getType();
        boolean wasPlaced = placed.unmark(b);
        if (wasPlaced || !enabled()) {
            return;
        }
        String k = m.name().toLowerCase(Locale.ROOT);
        Player p = e.getPlayer();
        double ore = rate(Job.MINER, k);
        if (ore > 0) {
            pay(p, Job.MINER, ore);
            return;
        }
        if (Tag.LOGS.isTagged(m)) {
            pay(p, Job.WOODCUTTER, rate(Job.WOODCUTTER, "logs"));
            return;
        }
        double crop = rate(Job.FARMER, k);
        if (crop > 0) {
            if (AGED.contains(m) && b.getBlockData() instanceof Ageable a && a.getAge() < a.getMaximumAge()) {
                return; // not grown yet
            }
            pay(p, Job.FARMER, crop);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || dead instanceof Player || dead.fromMobSpawner()) {
            return;
        }
        double r = rate(Job.HUNTER, dead.getType().name());
        if (r > 0) {
            pay(killer, Job.HUNTER, r);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Earnings x = earnings.get(e.getPlayer().getUniqueId());
        if (x != null && System.currentTimeMillis() - x.hourStart >= 3_600_000L) {
            earnings.remove(e.getPlayer().getUniqueId());
        }
    }
}
