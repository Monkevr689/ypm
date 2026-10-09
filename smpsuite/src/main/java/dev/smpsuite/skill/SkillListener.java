package dev.smpsuite.skill;

import dev.smpsuite.SMPSuite;
import dev.smpsuite.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Golem;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Skill XP from normal play, plus the drop bonuses (double drops, Bountiful
 * Harvest, Treasure Sense, the Fire gem's smelting and the Wealth gem's Rich
 * Rush - never two doublings on one block).
 */
public final class SkillListener implements Listener {

    /** Crops that only count when fully grown, and the seed that replants them. */
    static final Map<Material, Material> CROPS = new EnumMap<>(Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.NETHER_WART, Material.NETHER_WART,
            Material.COCOA, Material.COCOA_BEANS,
            Material.PITCHER_CROP, Material.PITCHER_POD,
            Material.SWEET_BERRY_BUSH, Material.SWEET_BERRIES));

    private static final Set<Material> ORES = Set.of(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE, Material.REDSTONE_ORE,
            Material.DEEPSLATE_REDSTONE_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.NETHER_QUARTZ_ORE, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.EMERALD_ORE,
            Material.DEEPSLATE_EMERALD_ORE, Material.ANCIENT_DEBRIS);

    private static final Material[] TREASURE = {Material.FLINT, Material.CLAY_BALL, Material.BONE, Material.GOLD_NUGGET,
            Material.IRON_NUGGET, Material.STRING, Material.FEATHER, Material.GLOW_INK_SAC};

    private final SMPSuite plugin;
    /** A break this tick whose drops are coming: who broke it and whether it counts. */
    private final Map<String, Pending> pending = new HashMap<>();
    /** killer + victim -> last time (for the player-kill cooldown). */
    private final Map<String, Long> playerKills = new HashMap<>();

    private record Pending(UUID player, Skill skill, boolean eligible, boolean crop) {
    }

    public SkillListener(SMPSuite plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, pending::clear, 1L, 1L);
    }

    private static String key(Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private static boolean playing(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    public static boolean mature(BlockData data) {
        return data instanceof Ageable a && a.getAge() >= a.getMaximumAge();
    }

    // ------------------------------------------------------------------
    // blocks
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();
        Skills skills = plugin.skills();
        Skill s = skills.skillOf(b.getType());
        boolean placed = s != null && plugin.placed().isPlaced(b);
        if (placed) {
            plugin.placed().unmark(b);
        }
        if (s == null || !playing(p)) {
            return;
        }
        boolean eligible = !placed || !plugin.getConfig().getBoolean("skills.placed-blocks-give-nothing", true);
        boolean crop = CROPS.containsKey(b.getType());
        if (crop && !mature(b.getBlockData())) {
            eligible = false;
        }
        if (s == Skill.MINING && b.getDrops(p.getInventory().getItemInMainHand(), p).isEmpty()) {
            eligible = false; // wrong tool: nothing drops, nothing earned
        }
        if (eligible) {
            skills.addXp(p, s, skills.blockXp(s, b.getType()), false);
        }
        pending.put(key(b.getLocation()), new Pending(p.getUniqueId(), s, eligible, crop));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        BlockState state = e.getBlockState();
        Material m = state.getType();
        List<Item> items = e.getItems();
        // the Fire gem smelts any ore you mine
        if (ORES.contains(m) && plugin.gems().fireSmelt(p)) {
            for (Item it : items) {
                Material smelted = smelt(it.getItemStack().getType());
                if (smelted != null) {
                    it.setItemStack(new ItemStack(smelted, it.getItemStack().getAmount()));
                }
            }
        }
        Pending pd = pending.remove(key(state.getLocation()));
        if (pd == null || !pd.eligible() || !pd.player().equals(p.getUniqueId())) {
            return;
        }
        PlayerData d = plugin.store().get(p);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean twice = false;
        boolean replant = false;
        switch (pd.skill()) {
            case MINING -> {
                if (ORES.contains(m)) {
                    // the Wealth gem's Rich Rush or the Mining bonus - one doubling, never both
                    twice = plugin.gems().richRush(p) || r.nextDouble() < plugin.skills().doubleDrop(Skill.MINING,
                            d.level(Skill.MINING));
                }
            }
            case FARMING -> {
                if (plugin.abilities().isActive(p, Abilities.HARVEST) && pd.crop()) {
                    twice = true;
                    replant = m != Material.SWEET_BERRY_BUSH && m != Material.PITCHER_CROP;
                } else {
                    twice = r.nextDouble() < plugin.skills().doubleDrop(Skill.FARMING, d.level(Skill.FARMING));
                }
            }
            case FORAGING -> twice = Tag.LOGS.isTagged(m)
                    && r.nextDouble() < plugin.skills().doubleDrop(Skill.FORAGING, d.level(Skill.FORAGING));
            case EXCAVATION -> {
                twice = r.nextDouble() < plugin.skills().doubleDrop(Skill.EXCAVATION, d.level(Skill.EXCAVATION));
                if (plugin.abilities().isActive(p, Abilities.TREASURE)
                        && r.nextDouble() < plugin.getConfig().getDouble("skills.abilities.treasure-sense.chance", 0.08)) {
                    Material t = r.nextDouble() < 0.01 ? Material.EMERALD : TREASURE[r.nextInt(TREASURE.length)];
                    Location at = state.getLocation().add(0.5, 0.5, 0.5);
                    items.add(at.getWorld().dropItemNaturally(at, new ItemStack(t)));
                    at.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, at, 6, 0.3, 0.3, 0.3, 0);
                }
            }
            default -> {
            }
        }
        if (twice) {
            for (Item it : items) {
                ItemStack st = it.getItemStack();
                if (st.getType() == m) {
                    continue; // silk touch: the block itself never doubles
                }
                int max = st.getMaxStackSize();
                int amount = st.getAmount();
                st.setAmount(Math.min(max, amount * 2));
                it.setItemStack(st);
            }
        }
        if (replant) {
            Material seed = CROPS.get(m);
            for (Item it : items) {
                ItemStack st = it.getItemStack();
                if (st.getType() == seed) {
                    if (st.getAmount() <= 1) {
                        it.remove();
                    } else {
                        st.setAmount(st.getAmount() - 1);
                        it.setItemStack(st);
                    }
                    BlockData fresh = state.getBlockData().clone();
                    if (fresh instanceof Ageable a) {
                        a.setAge(0);
                    }
                    Block at = state.getBlock();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (at.getType().isAir()) {
                            at.setBlockData(fresh);
                        }
                    });
                    break;
                }
            }
        }
    }

    static Material smelt(Material m) {
        return switch (m) {
            case RAW_IRON -> Material.IRON_INGOT;
            case RAW_GOLD -> Material.GOLD_INGOT;
            case RAW_COPPER -> Material.COPPER_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            default -> null;
        };
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent e) {
        Player p = e.getPlayer();
        if (!playing(p)) {
            return;
        }
        Material m = e.getHarvestedBlock().getType();
        if (m == Material.CAVE_VINES_PLANT) {
            m = Material.CAVE_VINES;
        }
        Skills skills = plugin.skills();
        double xp = skills.blockXp(Skill.FARMING, m);
        if (xp <= 0) {
            return;
        }
        skills.addXp(p, Skill.FARMING, xp, false);
        PlayerData d = plugin.store().get(p);
        boolean twice = plugin.abilities().isActive(p, Abilities.HARVEST)
                || ThreadLocalRandom.current().nextDouble() < skills.doubleDrop(Skill.FARMING, d.level(Skill.FARMING));
        if (twice) {
            for (ItemStack it : e.getItemsHarvested()) {
                it.setAmount(Math.min(it.getMaxStackSize(), it.getAmount() * 2));
            }
        }
    }

    // ------------------------------------------------------------------
    // fishing
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.store().get(p);
        Skills skills = plugin.skills();
        if (e.getState() == PlayerFishEvent.State.FISHING) {
            FishHook hook = e.getHook();
            double factor = (1 - skills.fasterBites(d.level(Skill.FISHING)))
                    * (d.isActive(Abilities.CAST) ? 0.5 : 1.0);
            if (factor < 0.999 && skills.enabled()) {
                int min = Math.max(10, (int) (hook.getMinWaitTime() * factor));
                int max = Math.max(min + 1, (int) (hook.getMaxWaitTime() * factor));
                hook.setWaitTime(min, max);
            }
            return;
        }
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item caught)
                || !playing(p)) {
            return;
        }
        ItemStack st = caught.getItemStack();
        String kind = fishKind(st);
        skills.addXp(p, Skill.FISHING, skills.special("fishing." + kind), false);
        if (kind.equals("fish") && ThreadLocalRandom.current().nextDouble() < skills.doubleCatch(d.level(Skill.FISHING))) {
            for (ItemStack left : p.getInventory().addItem(new ItemStack(st.getType(), 1)).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
    }

    static String fishKind(ItemStack st) {
        return switch (st.getType()) {
            case COD, SALMON, TROPICAL_FISH, PUFFERFISH -> "fish";
            case ENCHANTED_BOOK, NAME_TAG, NAUTILUS_SHELL, SADDLE, BOW -> "treasure";
            case FISHING_ROD -> st.getEnchantments().isEmpty() ? "junk" : "treasure";
            default -> "junk";
        };
    }

    // ------------------------------------------------------------------
    // combat, breeding, shearing, vitality
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || killer == dead || !playing(killer)) {
            return;
        }
        Skills skills = plugin.skills();
        double xp;
        if (dead instanceof Player victim) {
            if (plugin.teams().sameTeam(killer, victim)) {
                return;
            }
            String k = killer.getUniqueId() + ">" + victim.getUniqueId();
            long now = System.currentTimeMillis();
            Long last = playerKills.get(k);
            long cd = plugin.getConfig().getLong("skills.player-kill-cooldown-minutes", 15) * 60_000L;
            if (last != null && now - last < cd) {
                return;
            }
            playerKills.put(k, now);
            xp = skills.special("combat.player");
        } else if (dead instanceof AbstractVillager || dead instanceof Golem) {
            return;
        } else {
            xp = skills.mobXp(dead.getType(), dead instanceof Enemy);
            if (dead.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
                xp *= plugin.getConfig().getDouble("skills.spawner-mob-factor", 0.25);
            }
        }
        skills.addXp(killer, Skill.COMBAT, xp, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (e.getBreeder() instanceof Player p && playing(p)) {
            plugin.skills().addXp(p, Skill.FARMING, plugin.skills().special("farming.breed"), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) {
        if (playing(e.getPlayer())) {
            plugin.skills().addXp(e.getPlayer(), Skill.FARMING, plugin.skills().special("farming.shear"), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p) || !playing(p)
                || (e.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED
                && e.getRegainReason() != EntityRegainHealthEvent.RegainReason.REGEN)) {
            return;
        }
        double perHeart = plugin.getConfig().getDouble("skills.vitality-per-heart-healed", 1.0);
        double healed = Math.min(e.getAmount(), p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                - p.getHealth());
        if (healed > 0 && perHeart > 0) {
            plugin.skills().addXp(p, Skill.VITALITY, healed / 2 * perHeart, false);
        }
    }

    public void quit(UUID id) {
        String s = id.toString();
        playerKills.keySet().removeIf(k -> k.startsWith(s));
    }
}
