package dev.smpsuite.skill;

import dev.smpsuite.Keys;
import dev.smpsuite.SMPSuite;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Remembers blocks players placed that would otherwise give skill XP (ores,
 * logs, dirt, melons...), in the chunk's own data, so placing and breaking
 * them again gives nothing. Pistons carry the mark along.
 */
public final class PlacedBlocks implements Listener {

    private final SMPSuite plugin;

    public PlacedBlocks(SMPSuite plugin) {
        this.plugin = plugin;
    }

    private static int pack(Block b) {
        return ((b.getY() + 2048) << 8) | ((b.getX() & 15) << 4) | (b.getZ() & 15);
    }

    private static int[] read(Chunk c) {
        int[] a = c.getPersistentDataContainer().get(Keys.PLACED, PersistentDataType.INTEGER_ARRAY);
        return a == null ? new int[0] : a;
    }

    /** True when a player placed this block (and it still sits where they put it). */
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
        Chunk c = b.getChunk();
        int[] a = read(c);
        int[] more = Arrays.copyOf(a, a.length + 1);
        more[a.length] = pack(b);
        c.getPersistentDataContainer().set(Keys.PLACED, PersistentDataType.INTEGER_ARRAY, more);
    }

    public void unmark(Block b) {
        Chunk c = b.getChunk();
        int[] a = read(c);
        int key = pack(b);
        int n = 0;
        int[] out = new int[a.length];
        for (int v : a) {
            if (v != key) {
                out[n++] = v;
            }
        }
        if (n == a.length) {
            return;
        }
        PersistentDataContainer pdc = c.getPersistentDataContainer();
        if (n == 0) {
            pdc.remove(Keys.PLACED);
        } else {
            pdc.set(Keys.PLACED, PersistentDataType.INTEGER_ARRAY, Arrays.copyOf(out, n));
        }
    }

    /** Blocks worth remembering: anything in a skill's block table. */
    private boolean tracked(Block b) {
        return plugin.skills().skillOf(b.getType()) != null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (tracked(e.getBlockPlaced())) {
            mark(e.getBlockPlaced());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent e) {
        move(e.getBlocks(), e.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent e) {
        move(e.getBlocks(), e.getDirection());
    }

    private void move(List<Block> blocks, BlockFace dir) {
        List<Block> marked = new ArrayList<>();
        for (Block b : blocks) {
            if (isPlaced(b)) {
                marked.add(b);
            }
        }
        for (Block b : marked) {
            unmark(b);
        }
        for (Block b : marked) {
            mark(b.getRelative(dir));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        for (Block b : e.blockList()) {
            unmark(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        for (Block b : e.blockList()) {
            unmark(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        unmark(e.getBlock());
    }
}
