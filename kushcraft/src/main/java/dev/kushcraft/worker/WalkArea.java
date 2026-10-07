package dev.kushcraft.worker;

import dev.kushcraft.util.BlockKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a worker can walk: every spot reachable on foot from their home
 * (two blocks of room, something solid underneath, steps of one block up
 * or down, through open or wooden doors and gates), within a radius. A
 * worker only works on things right next to a spot they can walk to - so
 * nothing through walls. The chests and barrels they can reach are found
 * on the way. Paths go along the walk tree (home is the root).
 */
final class WalkArea {

    static final int UP = 6;
    static final int DOWN = 6;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    final World world;
    final int hx;
    final int hy;
    final int hz;
    final int radius;
    final long made;
    /** cell -> the cell you came from (home -> itself). */
    private final Map<Long, Long> parent = new HashMap<>();
    /** Chests / barrels next to a walkable spot, with that spot (first found = closest to home). */
    private final Map<Long, Long> chests = new LinkedHashMap<>();

    private WalkArea(World world, int hx, int hy, int hz, int radius, long made) {
        this.world = world;
        this.hx = hx;
        this.hy = hy;
        this.hz = hz;
        this.radius = radius;
        this.made = made;
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static int x(long k) {
        return (int) (k >> 38) << 6 >> 6;
    }

    static int z(long k) {
        return (int) ((k >> 12) & 0x3FFFFFF) << 6 >> 6;
    }

    static int y(long k) {
        return (int) (k & 0xFFF) << 20 >> 20;
    }

    /** Walks out from home (at most maxCells spots). */
    static WalkArea build(Location home, int radius, int maxCells, long now) {
        World w = home.getWorld();
        WalkArea a = new WalkArea(w, home.getBlockX(), home.getBlockY(), home.getBlockZ(), radius, now);
        long root = key(a.hx, a.hy, a.hz);
        a.parent.put(root, root);
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(root);
        a.scanChests(root);
        while (!queue.isEmpty() && a.parent.size() < maxCells) {
            long c = queue.poll();
            int cx = x(c), cy = y(c), cz = z(c);
            for (int[] d : DIRS) {
                int nx = cx + d[0], nz = cz + d[1];
                if (Math.abs(nx - a.hx) > radius || Math.abs(nz - a.hz) > radius
                        || (nx - a.hx) * (nx - a.hx) + (nz - a.hz) * (nz - a.hz) > radius * radius
                        || !w.isChunkLoaded(nx >> 4, nz >> 4)) {
                    continue;
                }
                boolean diagonal = d[0] != 0 && d[1] != 0;
                for (int dy : new int[]{0, 1, -1}) {
                    int ny = cy + dy;
                    if (ny - a.hy > UP || a.hy - ny > DOWN) {
                        continue;
                    }
                    long n = key(nx, ny, nz);
                    if (a.parent.containsKey(n)) {
                        break;
                    }
                    if (!walkable(w.getBlockAt(nx, ny, nz))) {
                        continue;
                    }
                    // a step up needs head room above where you stand, a step down above where you land
                    if (dy == 1 && !passable(w.getBlockAt(cx, cy + 2, cz))) {
                        continue;
                    }
                    if (dy == -1 && !passable(w.getBlockAt(nx, cy + 1, nz))) {
                        continue;
                    }
                    // no cutting corners through walls
                    if (diagonal && (dy != 0 || !walkable(w.getBlockAt(cx + d[0], cy, cz))
                            || !walkable(w.getBlockAt(cx, cy, cz + d[1])))) {
                        continue;
                    }
                    a.parent.put(n, c);
                    queue.add(n);
                    a.scanChests(n);
                    break;
                }
            }
        }
        return a;
    }

    private void scanChests(long cell) {
        int cx = x(cell), cy = y(cell), cz = z(cell);
        for (int[] d : SIDES) {
            for (int dy = -1; dy <= 1; dy++) {
                Block b = world.getBlockAt(cx + d[0], cy + dy, cz + d[1]);
                if (Workers.isChest(b.getType())) {
                    chests.putIfAbsent(key(b.getX(), b.getY(), b.getZ()), cell);
                }
            }
        }
        Block below = world.getBlockAt(cx, cy - 1, cz);
        if (Workers.isChest(below.getType())) {
            chests.putIfAbsent(key(below.getX(), below.getY(), below.getZ()), cell);
        }
    }

    static boolean passable(Block b) {
        if (b.isPassable() && !b.isLiquid()) {
            return true;
        }
        Material m = b.getType();
        // carpets and thin snow: you just walk over them
        return door(b) || Tag.WOOL_CARPETS.isTagged(m) || m == Material.MOSS_CARPET || m == Material.PALE_MOSS_CARPET
                || m == Material.SNOW;
    }

    /** Wooden doors and fence gates: workers open them (iron ones stay shut). */
    private static boolean door(Block b) {
        Material m = b.getType();
        return b.getBlockData() instanceof Openable && (Tag.WOODEN_DOORS.isTagged(m) || Tag.FENCE_GATES.isTagged(m));
    }

    /** Two blocks of room and something to stand on (not on top of a fence, wall or machine). */
    static boolean walkable(Block feet) {
        if (!passable(feet) || !passable(feet.getRelative(0, 1, 0))) {
            return false;
        }
        Block below = feet.getRelative(0, -1, 0);
        Material m = below.getType();
        if (below.isPassable() || below.isLiquid()) {
            return false;
        }
        return m != Material.BARRIER && !Tag.FENCES.isTagged(m) && !Tag.WALLS.isTagged(m)
                && !Tag.FENCE_GATES.isTagged(m);
    }

    boolean contains(int x, int y, int z) {
        return parent.containsKey(key(x, y, z));
    }

    int size() {
        return parent.size();
    }

    /** Chest / barrel positions they can reach, closest to home first. */
    List<BlockKey> chests() {
        List<BlockKey> out = new ArrayList<>();
        for (long k : chests.keySet()) {
            out.add(new BlockKey(world.getName(), x(k), y(k), z(k)));
        }
        return out;
    }

    boolean reachesChest(BlockKey k) {
        return k.world().equals(world.getName()) && chests.containsKey(key(k.x(), k.y(), k.z()));
    }

    /**
     * A spot to stand on to work on the target: a walkable spot right next
     * to it (same level, one up or one down), or on it when self is true
     * (plants: they step in among the crops). The one closest to from.
     * Null when they can't get there.
     */
    Location standFor(BlockKey t, boolean self, Location from) {
        if (!t.world().equals(world.getName())) {
            return null;
        }
        Long chest = chests.get(key(t.x(), t.y(), t.z()));
        if (chest != null) {
            return centre(chest);
        }
        Location best = null;
        double bestD = Double.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0 && (dy != 0 || !self)) {
                        continue;
                    }
                    int x = t.x() + dx, y = t.y() + dy, z = t.z() + dz;
                    if (!contains(x, y, z)) {
                        continue;
                    }
                    Location l = new Location(world, x + 0.5, y, z + 0.5);
                    // standing in the target's own cell (crops) only when nothing next to it is free
                    double d = (from != null ? l.distanceSquared(from) : 0) + (dx == 0 && dz == 0 ? 400 : 0)
                            + (dx != 0 && dz != 0 ? 0.5 : 0);
                    if (d < bestD) {
                        bestD = d;
                        best = l;
                    }
                }
            }
        }
        return best;
    }

    private Location centre(long k) {
        return new Location(world, x(k) + 0.5, y(k), z(k) + 0.5);
    }

    /** Spots to walk through from a to b (both in the area), b included; empty when either isn't. */
    List<Location> path(Location a, Location b) {
        long ka = key(a.getBlockX(), a.getBlockY(), a.getBlockZ());
        long kb = key(b.getBlockX(), b.getBlockY(), b.getBlockZ());
        if (!parent.containsKey(ka) || !parent.containsKey(kb)) {
            return List.of();
        }
        List<Long> up = chain(ka);
        Set<Long> onA = new HashSet<>(up);
        List<Long> down = new ArrayList<>();
        long c = kb;
        while (!onA.contains(c)) {
            down.add(c);
            long p = parent.get(c);
            if (p == c) {
                break;
            }
            c = p;
        }
        List<Location> out = new ArrayList<>();
        for (long k : up) {
            if (k == c) {
                break;
            }
            if (k != ka) {
                out.add(centre(k));
            }
        }
        if (c != ka) {
            out.add(centre(c));
        }
        Collections.reverse(down);
        for (long k : down) {
            out.add(centre(k));
        }
        return out;
    }

    private List<Long> chain(long from) {
        List<Long> out = new ArrayList<>();
        long c = from;
        out.add(c);
        while (true) {
            long p = parent.get(c);
            if (p == c) {
                break;
            }
            out.add(p);
            c = p;
        }
        return out;
    }
}
