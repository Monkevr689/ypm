package dev.kushcraft.jobs;

import dev.kushcraft.Keys;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Remembers which job blocks (ores, logs, melons...) were placed by players,
 * so breaking them again doesn't pay. Stored in the chunk itself, so it
 * survives restarts and needs no files.
 */
public final class PlacedBlocks implements Listener {

    private final Predicate<Block> tracked;

    public PlacedBlocks(Predicate<Block> tracked) {
        this.tracked = tracked;
    }

    private static int pack(Block b) {
        return ((b.getY() + 2048) << 8) | ((b.getX() & 15) << 4) | (b.getZ() & 15);
    }

    private static int[] read(Chunk c) {
        int[] a = c.getPersistentDataContainer().get(Keys.PLACED, PersistentDataType.INTEGER_ARRAY);
        return a == null ? new int[0] : a;
    }

    public boolean isPlaced(Block b) {
        int key = pack(b);
        for (int v : read(b.getChunk())) {
            if (v == key) {
                return true;
            }
        }
        return false;
    }

    public void mark(Block b) {
        if (isPlaced(b)) {
            return;
        }
        int[] a = read(b.getChunk());
        int[] n = java.util.Arrays.copyOf(a, a.length + 1);
        n[a.length] = pack(b);
        b.getChunk().getPersistentDataContainer().set(Keys.PLACED, PersistentDataType.INTEGER_ARRAY, n);
    }

    /** Forgets the block. Returns true if it had been placed by a player. */
    public boolean unmark(Block b) {
        int key = pack(b);
        int[] a = read(b.getChunk());
        int[] n = new int[a.length];
        int len = 0;
        boolean found = false;
        for (int v : a) {
            if (v == key) {
                found = true;
            } else {
                n[len++] = v;
            }
        }
        if (found) {
            PersistentDataContainer pdc = b.getChunk().getPersistentDataContainer();
            if (len == 0) {
                pdc.remove(Keys.PLACED);
            } else {
                pdc.set(Keys.PLACED, PersistentDataType.INTEGER_ARRAY, java.util.Arrays.copyOf(n, len));
            }
        }
        return found;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (tracked.test(e.getBlockPlaced())) {
            mark(e.getBlockPlaced());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        move(e.getBlocks(), e.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        move(e.getBlocks(), e.getDirection());
    }

    /** Placed blocks pushed by a piston stay "placed" at their new spot. */
    private void move(List<Block> blocks, BlockFace dir) {
        List<Block> moved = new ArrayList<>();
        for (Block b : blocks) {
            if (unmark(b)) {
                moved.add(b);
            }
        }
        for (Block b : moved) {
            mark(b.getRelative(dir));
        }
    }
}
