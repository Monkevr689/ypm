package dev.kushcraft.plant;

import dev.kushcraft.KushCraft;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.BlockKey;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plants that grow by themselves out in the world: every little while a
 * spot 20-56 blocks from each player is tried, and if it's open grass (or
 * sand, or mycelium) under the sky, far from anyone's farm, a wild plant
 * of the biome grows there - cannabis landraces everywhere, coca in jungles
 * and savannas, poppies on plains and meadows, peyote in deserts, magic
 * mushrooms in dark forests, swamps and mushroom fields. Anyone can pick
 * them; unpicked ones wither after a few hours.
 */
public final class WildPlants {

    private final KushCraft plugin;

    public WildPlants(KushCraft plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long every = 20L * Math.max(5, plugin.getConfig().getInt("wild.plant-seconds", 45));
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, every, every);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("wild.plants", true);
    }

    /** Wild plants on the server right now. */
    public int count() {
        int n = 0;
        for (Plant p : plugin.plants().all()) {
            if (p.wild()) {
                n++;
            }
        }
        return n;
    }

    private void tick() {
        if (!enabled()) {
            return;
        }
        int max = plugin.getConfig().getInt("wild.max-plants", 80);
        int total = count();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (total >= max) {
                return;
            }
            if (p.getGameMode() == GameMode.SPECTATOR || p.getWorld().getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            if (tryNear(p.getLocation()) != null) {
                total++;
            }
        }
    }

    /** Tries one random spot around a location. Returns the new plant or null. */
    public Plant tryNear(Location around) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double ang = r.nextDouble(Math.PI * 2);
        double dist = 20 + r.nextDouble(36);
        int x = (int) Math.floor(around.getX() + Math.cos(ang) * dist);
        int z = (int) Math.floor(around.getZ() + Math.sin(ang) * dist);
        World w = around.getWorld();
        if (!w.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        int near = 0;
        int maxNear = plugin.getConfig().getInt("wild.max-near-player", 6);
        for (Plant p : plugin.plants().all()) {
            Location c = p.key().center();
            if (c == null || !c.getWorld().equals(w)) {
                continue;
            }
            double d2 = (c.getX() - x) * (c.getX() - x) + (c.getZ() - z) * (c.getZ() - z);
            if (!p.wild() && d2 < 16 * 16) {
                return null; // somebody's farm
            }
            if (p.wild() && (d2 < 8 * 8 || (c.distanceSquared(around) < 56 * 56 && ++near >= maxNear))) {
                return null;
            }
        }
        Block soil = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Block space = soil.getRelative(0, 1, 0);
        if (space.getLightFromSky() < 12 || plugin.jobs().placed().isPlaced(soil) || plugin.machines().at(soil) != null) {
            return null;
        }
        Plant.Kind kind = kindFor(soil.getBiome().getKey().getKey().toLowerCase(Locale.ROOT), soil.getType());
        return kind == null ? null : grow(soil, kind);
    }

    /** Grows a wild plant of this kind on top of soil if there is room and the soil suits it. */
    public Plant grow(Block soil, Plant.Kind kind) {
        Block space = soil.getRelative(0, 1, 0);
        BlockKey key = BlockKey.of(space);
        boolean plantInTheWay = space.getType() == Material.SHORT_GRASS || space.getType() == Material.FERN;
        if (!space.getType().isAir() && !plantInTheWay) {
            return null;
        }
        if (plugin.plants().at(key) != null || plugin.machines().at(key) != null || !plugin.plants().isSoil(soil, kind)) {
            return null;
        }
        Strain strain = kind == Plant.Kind.CANNABIS ? plugin.strains().wildFor(soil) : null;
        if (kind == Plant.Kind.CANNABIS && strain == null) {
            return null;
        }
        if (plantInTheWay) {
            space.setType(Material.AIR, false);
        }
        Plant p = plugin.plants().plantAt(key, kind, strain, null);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // some are ripe already, the rest grow on by themselves
        p.growth(r.nextDouble() < 0.5 ? 100 : 35 + r.nextDouble(60));
        double hours = Math.max(0.1, plugin.getConfig().getDouble("wild.plant-hours", 6));
        p.wildUntil(System.currentTimeMillis() + (long) (hours * 3_600_000L));
        plugin.plants().refresh(p);
        plugin.plants().markDirty();
        return p;
    }

    /** What grows wild on this soil in this biome. */
    public static Plant.Kind kindFor(String biome, Material soil) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (soil == Material.SAND || soil == Material.RED_SAND || soil == Material.TERRACOTTA) {
            return biome.contains("desert") || biome.contains("badlands") ? Plant.Kind.PEYOTE : null;
        }
        if (soil == Material.MYCELIUM || biome.contains("mushroom")) {
            return Plant.Kind.MUSHROOM;
        }
        if (biome.contains("dark_forest") || biome.contains("swamp")) {
            return r.nextDouble() < 0.5 ? Plant.Kind.MUSHROOM : Plant.Kind.CANNABIS;
        }
        if (biome.contains("jungle") || biome.contains("savanna")) {
            return r.nextDouble() < 0.4 ? Plant.Kind.COCA : Plant.Kind.CANNABIS;
        }
        if (biome.contains("plains") || biome.contains("meadow") || biome.contains("flower")) {
            return r.nextDouble() < 0.3 ? Plant.Kind.POPPY : Plant.Kind.CANNABIS;
        }
        if (biome.contains("ocean") || biome.contains("river") || biome.contains("beach") || biome.contains("frozen")
                || biome.contains("snowy") || biome.contains("ice")) {
            return null;
        }
        return Plant.Kind.CANNABIS;
    }
}
