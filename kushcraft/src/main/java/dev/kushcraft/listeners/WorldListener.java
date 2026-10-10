package dev.kushcraft.listeners;

import dev.kushcraft.KushCraft;
import dev.kushcraft.Keys;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.strains.Strain;
import dev.kushcraft.util.Protection;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Chunk loading for plant/machine models and wild seed drops. */
public final class WorldListener implements Listener {

    private static final Set<Material> GRASS = new HashSet<>();
    private static final Set<Material> SHROOMS = new HashSet<>();

    static {
        for (String n : new String[]{"SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "DEAD_BUSH", "BUSH",
                "SHORT_DRY_GRASS", "TALL_DRY_GRASS"}) {
            Material m = Material.matchMaterial(n);
            if (m != null) {
                GRASS.add(m);
            }
        }
        SHROOMS.add(Material.RED_MUSHROOM);
        SHROOMS.add(Material.BROWN_MUSHROOM);
    }

    private final KushCraft plugin;

    public WorldListener(KushCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        World w = e.getWorld();
        int x = e.getChunk().getX(), z = e.getChunk().getZ();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (w.isChunkLoaded(x, z)) {
                plugin.plants().chunkLoaded(w, x, z);
                plugin.machines().chunkLoaded(w, x, z);
                plugin.workers().chunkLoaded(w, x, z);
            }
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        plugin.plants().chunkUnloaded(e.getWorld(), e.getChunk().getX(), e.getChunk().getZ());
        plugin.machines().chunkUnloaded(e.getWorld(), e.getChunk().getX(), e.getChunk().getZ());
        plugin.workers().chunkUnloaded(e.getWorld(), e.getChunk().getX(), e.getChunk().getZ());
    }

    /** Our display entities are never saved; if one ever was, remove it. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (en.getPersistentDataContainer().has(Keys.VISUAL, PersistentDataType.STRING)) {
                en.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (Protection.isChecking() || e.getPlayer().getGameMode() == GameMode.CREATIVE || !e.isDropItems()) {
            return;
        }
        Block b = e.getBlock();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location drop = b.getLocation().add(0.5, 0.3, 0.5);
        if (GRASS.contains(b.getType())) {
            if (r.nextDouble() < plugin.getConfig().getDouble("wild.grass-seed-chance", 0.04)) {
                Strain s = plugin.strains().wildFor(b);
                b.getWorld().dropItemNaturally(drop, Items.strainItem(ItemType.SEED_PACK, s, 3, 1));
                if (s.exotic() != dev.kushcraft.strains.Exotic.NONE) {
                    Bukkit.broadcast(dev.kushcraft.util.Text.msg("<white>" + dev.kushcraft.util.Text.escape(
                            e.getPlayer().getName()) + " <gray>found wild " + s.rarity().colored() + " <gray>seeds: "
                            + s.colored() + "<gray>!"));
                }
            }
            String biome = b.getBiome().getKey().getKey().toLowerCase(Locale.ROOT);
            if ((biome.contains("jungle") || biome.contains("savanna"))
                    && r.nextDouble() < plugin.getConfig().getDouble("wild.coca-seed-chance", 0.03)) {
                b.getWorld().dropItemNaturally(drop, Items.create(ItemType.COCA_SEEDS));
            }
            if (b.getType() == Material.DEAD_BUSH && (biome.contains("desert") || biome.contains("badlands"))
                    && r.nextDouble() < plugin.getConfig().getDouble("wild.peyote-seed-chance", 0.08)) {
                b.getWorld().dropItemNaturally(drop, Items.create(ItemType.PEYOTE_SEEDS));
            }
        } else if (b.getType() == Material.WHEAT && b.getBlockData() instanceof Ageable age
                && age.getAge() >= age.getMaximumAge()) {
            // ergot: the fungus LSD is made from
            if (r.nextDouble() < plugin.getConfig().getDouble("wild.ergot-chance", 0.06)) {
                b.getWorld().dropItemNaturally(drop, Items.create(ItemType.ERGOT));
            }
        } else if (b.getType() == Material.POPPY) {
            if (r.nextDouble() < plugin.getConfig().getDouble("wild.poppy-seed-chance", 0.15)) {
                b.getWorld().dropItemNaturally(drop, Items.create(ItemType.POPPY_SEEDS));
            }
        } else if (SHROOMS.contains(b.getType())) {
            double chance = plugin.getConfig().getDouble("wild.mushroom-spore-chance", 0.12);
            if (b.getBiome().getKey().getKey().toLowerCase(Locale.ROOT).contains("mushroom")) {
                chance *= 2.5;
            }
            if (r.nextDouble() < chance) {
                b.getWorld().dropItemNaturally(drop, Items.create(ItemType.MUSHROOM_SPORES));
            }
        }
    }
}
