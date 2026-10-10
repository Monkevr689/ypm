package dev.kushcraft.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Immutable block position used as map key for plants and machines. */
public record BlockKey(String world, int x, int y, int z) {

    public static BlockKey of(Block b) {
        return new BlockKey(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    public static BlockKey of(Location l) {
        return new BlockKey(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public static BlockKey parse(String s) {
        String[] p = s.split(";");
        if (p.length != 4) {
            return null;
        }
        try {
            return new BlockKey(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String serialize() {
        return world + ";" + x + ";" + y + ";" + z;
    }

    public String chunkId() {
        return chunkId(world, x >> 4, z >> 4);
    }

    public static String chunkId(String world, int cx, int cz) {
        return world + ":" + cx + ":" + cz;
    }

    public World bukkitWorld() {
        return Bukkit.getWorld(world);
    }

    public boolean isLoaded() {
        World w = bukkitWorld();
        return w != null && w.isChunkLoaded(x >> 4, z >> 4);
    }

    public Block block() {
        World w = bukkitWorld();
        return w == null ? null : w.getBlockAt(x, y, z);
    }

    public Location center() {
        World w = bukkitWorld();
        return w == null ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
    }

    public Location bottomCenter() {
        World w = bukkitWorld();
        return w == null ? null : new Location(w, x + 0.5, y, z + 0.5);
    }

    public BlockKey up() {
        return new BlockKey(world, x, y + 1, z);
    }

    public BlockKey down() {
        return new BlockKey(world, x, y - 1, z);
    }

    public double distanceSq(BlockKey o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
