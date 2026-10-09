package dev.smpsuite.skill;

import dev.smpsuite.Msg;
import dev.smpsuite.SMPSuite;
import dev.smpsuite.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The skill abilities: Shift + right-click with the skill's tool. Each unlocks
 * at a skill level and has a real cooldown, so they're a toolkit, not spam.
 * Axes, hoes and shovels trigger on air only (so tilling, stripping and paths
 * still work while sneaking); pickaxes and rods anywhere.
 */
public final class Abilities implements Listener {

    public static final String HASTE = "haste-pulse";
    public static final String FELLER = "tree-feller";
    public static final String HARVEST = "bountiful-harvest";
    public static final String CAST = "lucky-cast";
    public static final String TREASURE = "treasure-sense";

    private final SMPSuite plugin;
    /** Players whose axe is readied, until when. */
    private final Map<UUID, Long> readied = new HashMap<>();
    /** Players felling a tree right now (their breaks are part of it). */
    private final Set<UUID> felling = new HashSet<>();
    private final Map<UUID, Long> lastHint = new HashMap<>();

    public Abilities(SMPSuite plugin) {
        this.plugin = plugin;
    }

    private String path(String ability, String key) {
        return "skills.abilities." + ability + "." + key;
    }

    public int unlockLevel(String ability) {
        return plugin.getConfig().getInt(path(ability, "unlock-level"), 10);
    }

    public long cooldown(String ability) {
        return Math.max(0, plugin.getConfig().getLong(path(ability, "cooldown"), 240));
    }

    public long seconds(String ability) {
        return Math.max(1, plugin.getConfig().getLong(path(ability, "seconds"), 30));
    }

    /** How to use a skill's ability (for messages and menus). */
    public String howTo(Skill s) {
        return switch (s) {
            case MINING -> "Shift + right-click with a pickaxe: Haste III for " + seconds(HASTE) + "s.";
            case FORAGING -> "Shift + right-click the air with an axe, then break a log: the whole tree falls.";
            case FARMING -> "Shift + right-click the air with a hoe: crops drop double and replant for "
                    + seconds(HARVEST) + "s.";
            case FISHING -> "Shift + right-click with a rod: fish bite twice as fast, with Luck, for "
                    + seconds(CAST) + "s.";
            case EXCAVATION -> "Shift + right-click the air with a shovel: digging finds small treasures for "
                    + seconds(TREASURE) + "s.";
            default -> "";
        };
    }

    public boolean isActive(Player p, String ability) {
        return plugin.store().get(p).isActive(ability);
    }

    public boolean isReadied(Player p) {
        Long t = readied.get(p.getUniqueId());
        return t != null && t > System.currentTimeMillis();
    }

    // ------------------------------------------------------------------
    // triggering
    // ------------------------------------------------------------------

    static Skill toolSkill(Material m) {
        String n = m.name();
        if (n.endsWith("_PICKAXE")) {
            return Skill.MINING;
        }
        if (n.endsWith("_AXE")) {
            return Skill.FORAGING;
        }
        if (n.endsWith("_HOE")) {
            return Skill.FARMING;
        }
        if (n.endsWith("_SHOVEL")) {
            return Skill.EXCAVATION;
        }
        if (m == Material.FISHING_ROD) {
            return Skill.FISHING;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND || !p.isSneaking() || !plugin.skills().enabled()
                || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack tool = e.getItem();
        Skill s = tool == null ? null : toolSkill(tool.getType());
        if (s == null || s.ability() == null) {
            return;
        }
        boolean air = e.getAction() == Action.RIGHT_CLICK_AIR;
        if (!air && s != Skill.MINING && s != Skill.FISHING) {
            return; // let sneaking players till, strip and make paths as usual
        }
        if (s == Skill.FISHING && p.getFishHook() != null) {
            return; // reeling in
        }
        PlayerData d = plugin.store().get(p);
        String id = s.ability();
        if (d.level(s) < unlockLevel(id)) {
            hint(p, "<gray>" + s.abilityName() + " unlocks at " + s.display() + " " + unlockLevel(id) + ".");
            return;
        }
        long left = d.cooldownLeft(id);
        if (left > 0) {
            hint(p, "<gray>" + s.abilityName() + " is ready in <white>" + Msg.time(left) + "</white>.");
            return;
        }
        if (s == Skill.FORAGING && isReadied(p)) {
            return;
        }
        e.setCancelled(true);
        activate(p, d, s);
    }

    /** At most one "not ready yet" message every few seconds (sneaking players click a lot). */
    private void hint(Player p, String text) {
        long now = System.currentTimeMillis();
        Long last = lastHint.get(p.getUniqueId());
        if (last != null && now - last < 3000) {
            return;
        }
        lastHint.put(p.getUniqueId(), now);
        p.sendActionBar(Msg.mm(text));
    }

    /** Starts a skill's ability (checks are done by the caller; also used by the self test). */
    public void activate(Player p, PlayerData d, Skill s) {
        String id = s.ability();
        Location at = p.getLocation().add(0, 1, 0);
        switch (s) {
            case MINING -> {
                int amp = Math.max(0, plugin.getConfig().getInt(path(HASTE, "amplifier"), 2));
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, (int) seconds(HASTE) * 20, amp, false, true, true));
                d.cooldown(id, cooldown(id));
                p.getWorld().spawnParticle(Particle.WAX_OFF, at, 20, 0.4, 0.5, 0.4, 0.05);
                p.playSound(at, "minecraft:block.beacon.power_select", SoundCategory.PLAYERS, 0.7f, 1.6f);
                p.sendActionBar(Msg.mm(s.color() + "<bold>Haste Pulse!</bold> <gray>Haste " + roman(amp + 1) + " for "
                        + seconds(HASTE) + "s"));
            }
            case FORAGING -> {
                long ready = Math.max(3, plugin.getConfig().getLong(path(FELLER, "ready-seconds"), 10));
                readied.put(p.getUniqueId(), System.currentTimeMillis() + ready * 1000L);
                p.playSound(at, "minecraft:item.axe.strip", SoundCategory.PLAYERS, 1f, 0.7f);
                p.sendActionBar(Msg.mm(s.color() + "<bold>Tree Feller ready</bold> <gray>- break a log within "
                        + ready + "s"));
            }
            case FARMING -> {
                d.activate(id, seconds(id));
                d.cooldown(id, cooldown(id));
                p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 25, 1.5, 0.4, 1.5, 0);
                p.playSound(at, "minecraft:block.composter.ready", SoundCategory.PLAYERS, 1f, 1.2f);
                p.sendActionBar(Msg.mm(s.color() + "<bold>Bountiful Harvest!</bold> <gray>double crops for "
                        + seconds(id) + "s"));
            }
            case FISHING -> {
                d.activate(id, seconds(id));
                d.cooldown(id, cooldown(id));
                p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, (int) seconds(id) * 20, 0, false, true, true));
                p.getWorld().spawnParticle(Particle.FISHING, at, 20, 0.5, 0.3, 0.5, 0.05);
                p.playSound(at, "minecraft:entity.fishing_bobber.splash", SoundCategory.PLAYERS, 1f, 1.4f);
                p.sendActionBar(Msg.mm(s.color() + "<bold>Lucky Cast!</bold> <gray>faster bites and Luck for "
                        + seconds(id) + "s"));
            }
            case EXCAVATION -> {
                d.activate(id, seconds(id));
                d.cooldown(id, cooldown(id));
                p.getWorld().spawnParticle(Particle.ENCHANT, at, 30, 0.6, 0.6, 0.6, 0.5);
                p.playSound(at, "minecraft:block.amethyst_block.chime", SoundCategory.PLAYERS, 1f, 1.1f);
                p.sendActionBar(Msg.mm(s.color() + "<bold>Treasure Sense!</bold> <gray>digging finds treasure for "
                        + seconds(id) + "s"));
            }
            default -> {
            }
        }
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(n);
        };
    }

    // ------------------------------------------------------------------
    // Tree Feller
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        if (felling.contains(p.getUniqueId()) || !Tag.LOGS.isTagged(b.getType()) || !isReadied(p)
                || plugin.placed().isPlaced(b)) {
            return;
        }
        readied.remove(p.getUniqueId());
        Location origin = b.getLocation();
        Bukkit.getScheduler().runTask(plugin, () -> fell(p, origin));
    }

    /** The connected natural logs of a tree around a spot (not the spot itself), at most max. */
    public List<Block> treeAround(Block origin, int max) {
        List<Block> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        seen.add(key(origin));
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        while (!queue.isEmpty() && out.size() < max) {
            Block at = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block n = at.getRelative(dx, dy, dz);
                        if (Math.abs(n.getX() - ox) > 12 || Math.abs(n.getZ() - oz) > 12 || n.getY() < oy - 2
                                || n.getY() > oy + 40 || !seen.add(key(n))) {
                            continue;
                        }
                        if (Tag.LOGS.isTagged(n.getType()) && !plugin.placed().isPlaced(n)) {
                            out.add(n);
                            queue.add(n);
                            if (out.size() >= max) {
                                return out;
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    private static long key(Block b) {
        return ((long) b.getX() & 0x3FFFFFF) << 38 | ((long) b.getZ() & 0x3FFFFFF) << 12 | (b.getY() & 0xFFF);
    }

    private void fell(Player p, Location origin) {
        if (!p.isOnline()) {
            return;
        }
        int max = Math.max(1, plugin.getConfig().getInt(path(FELLER, "max-logs"), 150));
        List<Block> logs = treeAround(origin.getBlock(), max);
        int broken = 0;
        felling.add(p.getUniqueId());
        try {
            for (Block log : logs) {
                ItemStack tool = p.getInventory().getItemInMainHand();
                if (toolSkill(tool.getType()) != Skill.FORAGING || nearlyBroken(tool)) {
                    break; // they switched tools, or one more log would break the axe
                }
                // as if the player broke it: protection plugins, XP, drops and durability all apply
                if (p.breakBlock(log)) {
                    broken++;
                }
            }
        } finally {
            felling.remove(p.getUniqueId());
        }
        PlayerData d = plugin.store().get(p);
        d.cooldown(FELLER, cooldown(FELLER));
        if (broken > 0) {
            p.playSound(origin, "minecraft:entity.zombie.break_wooden_door", SoundCategory.PLAYERS, 0.6f, 0.8f);
        }
        p.sendActionBar(Msg.mm(Skill.FORAGING.color() + "<bold>Timber!</bold> <gray>" + (broken + 1) + " logs felled"));
    }

    private static boolean nearlyBroken(ItemStack tool) {
        if (!(tool.getItemMeta() instanceof Damageable dm) || dm.isUnbreakable()) {
            return false;
        }
        int max = tool.getType().getMaxDurability();
        return max > 0 && dm.getDamage() + 2 >= max;
    }

    public void quit(UUID id) {
        readied.remove(id);
        felling.remove(id);
        lastHint.remove(id);
    }
}
