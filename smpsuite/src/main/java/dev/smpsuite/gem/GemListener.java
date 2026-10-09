package dev.smpsuite.gem;

import dev.smpsuite.SMPSuite;
import dev.smpsuite.gui.Menu;
import dev.smpsuite.skill.Combat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * F / Shift + F use the gem in the off hand. Gems are bound to their owner:
 * they can't be dropped, stored, crafted with or placed, and they stay with
 * you when you die. Also the gems' passive defences (Puff, Flux, Astra) and
 * the Strength gem's Bloodlust healing.
 */
public final class GemListener implements Listener {

    private final SMPSuite plugin;
    private final Map<UUID, Long> lastHeal = new HashMap<>();

    public GemListener(SMPSuite plugin) {
        this.plugin = plugin;
    }

    private Gems gems() {
        return plugin.gems();
    }

    // ------------------------------------------------------------------
    // using the gem
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        ItemStack off = p.getInventory().getItemInOffHand();
        if (!Gems.isGem(off)) {
            return; // F with the gem in the main hand moves it to the off hand, as usual
        }
        e.setCancelled(true);
        gems().use(p, p.isSneaking());
    }

    // ------------------------------------------------------------------
    // bound to their owner
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (Gems.isGem(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.CREATIVE) {
            return; // the player's own inventory
        }
        if (top.getHolder(false) instanceof Menu m && !m.storage()) {
            return; // menus cancel everything themselves
        }
        Player p = (Player) e.getWhoClicked();
        boolean topSlot = e.getRawSlot() >= 0 && e.getRawSlot() < top.getSize();
        ItemStack hotbar = e.getClick() == ClickType.NUMBER_KEY ? p.getInventory().getItem(e.getHotbarButton()) : null;
        ItemStack offhand = e.getClick() == ClickType.SWAP_OFFHAND ? p.getInventory().getItemInOffHand() : null;
        if ((!topSlot && e.isShiftClick() && Gems.isGem(e.getCurrentItem()))
                || (topSlot && (Gems.isGem(e.getCursor()) || Gems.isGem(hotbar) || Gems.isGem(offhand)))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.CREATIVE
                || !Gems.isGem(e.getOldCursor())) {
            return;
        }
        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (Gems.isGem(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (Gems.isGem(e.getItemInHand())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent e) {
        if (!Gems.isGem(e.getItem())) {
            return;
        }
        e.setUseItemInHand(Event.Result.DENY);
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType() == Material.DECORATED_POT) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        ItemStack hand = e.getPlayer().getInventory().getItem(e.getHand());
        if (Gems.isGem(hand) && (e.getRightClicked() instanceof ItemFrame || e.getRightClicked() instanceof Allay)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryPickupItemEvent e) {
        if (Gems.isGem(e.getItem().getItemStack())) {
            e.setCancelled(true);
        }
    }

    /** A gem item that ended up on the ground anyway (full inventory): only its owner can pick it up. */
    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        Item item = e.getEntity();
        UUID owner = Gems.ownerOf(item.getItemStack());
        if (owner != null) {
            item.setOwner(owner);
            item.setInvulnerable(true);
            item.setUnlimitedLifetime(true);
            item.setCanMobPickup(false);
        }
    }

    /** Your gem stays with you when you die; PvP moves a little energy. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!e.getKeepInventory()) {
            for (Iterator<ItemStack> it = e.getDrops().iterator(); it.hasNext(); ) {
                ItemStack st = it.next();
                if (Gems.isGem(st)) {
                    it.remove();
                    if (p.getUniqueId().equals(Gems.ownerOf(st))) {
                        e.getItemsToKeep().add(st);
                    }
                }
            }
        }
        Player killer = p.getKiller();
        if (killer != null && killer != p) {
            gems().pvpKill(killer, p);
        }
    }

    // ------------------------------------------------------------------
    // passive defences and on-hit effects
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        GemType g = gems().active(p);
        if (g == GemType.PUFF && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            e.setCancelled(true);
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 8, 0.3, 0.05, 0.3, 0.02);
        } else if (g == GemType.FLUX && e.getCause() == EntityDamageEvent.DamageCause.LIGHTNING) {
            e.setCancelled(true);
        } else if (g == GemType.ASTRA && e instanceof EntityDamageByEntityEvent ed && ed.getDamager() instanceof Projectile
                && ThreadLocalRandom.current().nextDouble() < 0.10) {
            e.setCancelled(true); // Phasing
            p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.05);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || e.getEntity() instanceof Player
                || !(e.getEntity() instanceof LivingEntity) || Combat.gemHit || !gems().bloodlust(p)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastHeal.get(p.getUniqueId());
        if (last != null && now - last < 400) {
            return;
        }
        lastHeal.put(p.getUniqueId(), now);
        heal(p, 1.0);
    }

    /** Astra: Soul Absorption - hostile kills heal half a heart. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null && e.getEntity() instanceof Enemy && gems().has(killer, GemType.ASTRA)) {
            heal(killer, 1.0);
            killer.getWorld().spawnParticle(Particle.SOUL, killer.getLocation().add(0, 1, 0), 4, 0.3, 0.4, 0.3, 0.01);
        }
    }

    private static void heal(Player p, double hp) {
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && !p.isDead()) {
            p.setHealth(Math.min(max.getValue(), p.getHealth() + hp));
        }
    }

    /** Life: food fills you up more. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && e.getItem() != null && e.getFoodLevel() > p.getFoodLevel()
                && gems().has(p, GemType.LIFE)) {
            Bukkit.getScheduler().runTask(plugin, () -> p.setSaturation(Math.min(p.getFoodLevel(), p.getSaturation() + 2f)));
        }
    }

    public void quit(UUID id) {
        lastHeal.remove(id);
    }
}
