package dev.kushcraft.listeners;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.plants.Plant;
import dev.kushcraft.util.BlockKey;
import dev.kushcraft.util.Protection;
import dev.kushcraft.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Clicking plants and keeping them safe from blocks, water, pistons and explosions. */
public final class PlantListener implements Listener {

    private final KushCraft plugin;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public PlantListener(KushCraft plugin) {
        this.plugin = plugin;
    }

    private boolean debounce(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastClick.put(p.getUniqueId(), now);
        return last != null && now - last < 150;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction hit)) {
            return;
        }
        Plant plant = plugin.plants().fromEntity(hit);
        if (plant == null) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND || debounce(e.getPlayer())) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemType t = Items.type(hand);
        if (t == ItemType.FERTILIZER) {
            if (plugin.plants().fertilize(plant)) {
                if (p.getGameMode() != GameMode.CREATIVE) {
                    hand.setAmount(hand.getAmount() - 1);
                }
                p.sendActionBar(Text.mm("<green>Fertilized! <gray>Faster growth and +1 quality."));
            } else {
                p.sendActionBar(Text.mm("<gray>This plant doesn't need more fertilizer."));
            }
            return;
        }
        if (t == null && hand.getType() == Material.BONE_MEAL) {
            if (plugin.plants().boneMeal(plant) && p.getGameMode() != GameMode.CREATIVE) {
                hand.setAmount(hand.getAmount() - 1);
            }
            return;
        }
        if (plant.mature()) {
            if (Protection.canBuild(p, plant.key().block())) {
                int around = p.isSneaking() ? harvestAround(p, plant) : 0;
                plugin.plants().harvest(plant, p);
                if (around > 0) {
                    p.sendActionBar(Text.mm("<green>Harvested " + (around + 1) + " plants."));
                }
            } else {
                p.sendActionBar(Text.mm("<red>That's not your plant."));
            }
            return;
        }
        plugin.plants().showInfo(p, plant);
    }

    /** Sneak + right-click a ripe plant: every other ripe plant of yours close by is harvested too. */
    private int harvestAround(Player p, Plant clicked) {
        int r = plugin.getConfig().getInt("harvest.sneak-radius", 4);
        if (r <= 0) {
            return 0;
        }
        int n = 0;
        // only the plants in the chunks around it (not every plant on the server)
        for (Plant other : plugin.plants().near(clicked.key().world(), clicked.key().x(), clicked.key().z(), r)) {
            if (other == clicked || !other.mature() || !p.getUniqueId().equals(other.owner())
                    || !other.key().world().equals(clicked.key().world()) || other.key().distanceSq(clicked.key()) > r * r
                    || !Protection.canBuild(p, other.key().block())) {
                continue;
            }
            plugin.plants().harvest(other, p);
            n++;
        }
        return n;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPunch(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Interaction hit)) {
            return;
        }
        Plant plant = plugin.plants().fromEntity(hit);
        if (plant == null) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getDamager() instanceof Player p) || debounce(p)) {
            return;
        }
        if (!Protection.canBuild(p, plant.key().block())) {
            p.sendActionBar(Text.mm("<red>That's not your plant."));
            return;
        }
        if (!plant.mature() && plant.stage() >= 2 && !p.isSneaking()) {
            plugin.plants().showInfo(p, plant);
            p.sendActionBar(Text.mm("<yellow>Not ready yet! <gray>Sneak + punch to pull it out anyway."));
            return;
        }
        plugin.plants().destroy(plant, p);
    }

    // ---- protecting plants ---------------------------------------------

    private Plant above(Block b) {
        return plugin.plants().at(BlockKey.of(b).up());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSoilBreak(BlockBreakEvent e) {
        if (Protection.isChecking()) {
            return;
        }
        Plant p = above(e.getBlock());
        if (p != null) {
            plugin.plants().destroy(p, e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.plants().at(BlockKey.of(e.getBlockPlaced())) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (plugin.plants().at(BlockKey.of(e.getToBlock())) != null) {
            e.setCancelled(true);
        }
    }

    private boolean touchesPlant(List<Block> blocks) {
        for (Block b : blocks) {
            if (plugin.plants().at(BlockKey.of(b)) != null || above(b) != null) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonPush(BlockPistonExtendEvent e) {
        if (touchesPlant(e.getBlocks())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonPull(BlockPistonRetractEvent e) {
        if (touchesPlant(e.getBlocks())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrample(EntityChangeBlockEvent e) {
        if (e.getBlock().getType() == Material.FARMLAND && above(e.getBlock()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        for (Block b : e.blockList()) {
            Plant p = above(b);
            if (p != null) {
                plugin.plants().destroy(p, null);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        for (Block b : e.blockList()) {
            Plant p = above(b);
            if (p != null) {
                plugin.plants().destroy(p, null);
            }
        }
    }
}
