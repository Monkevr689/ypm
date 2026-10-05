package dev.kushcraft.listener;

import dev.kushcraft.KushCraft;
import dev.kushcraft.Keys;
import dev.kushcraft.effect.EffectType;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.recipe.Recipes;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.Set;

/** Join/quit, death, munchies, and stopping KushCraft items being used as vanilla paper. */
public final class PlayerListener implements Listener {

    private static final Set<InventoryType> NO_CUSTOM = EnumSet.of(InventoryType.MERCHANT, InventoryType.CARTOGRAPHY,
            InventoryType.CRAFTER);

    private final KushCraft plugin;

    public PlayerListener(KushCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.economy().join(p);
        p.discoverRecipes(Recipes.keys());
        plugin.effects().join(p);
        if (plugin.getConfig().getBoolean("give-guide-on-first-join", true)
                && !p.getPersistentDataContainer().has(Keys.GOT_GUIDE, PersistentDataType.BYTE)) {
            p.getPersistentDataContainer().set(Keys.GOT_GUIDE, PersistentDataType.BYTE, (byte) 1);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    InventoryUtil.give(p, Items.create(ItemType.GROWER_GUIDE));
                    p.sendMessage(Text.msg("<gray>This server runs <green>KushCraft</green>! Type <white>/kush</white>"
                            + " or right-click the <green>KushCraft Menu</green> item to get started."));
                }
            }, 60L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.effects().quit(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        plugin.effects().clear(e.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!plugin.effects().has(p, EffectType.MUNCHIES) || !e.getItem().getType().isEdible()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.setFoodLevel(Math.min(20, p.getFoodLevel() + 4));
            p.setSaturation(Math.min(p.getFoodLevel(), p.getSaturation() + 4));
            p.sendActionBar(Text.mm("<gold>Munchies: <white>that hit the spot!"));
        });
    }

    /** KushCraft items are paper underneath - keep them out of vanilla recipes. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (Items.isCustom(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGuardedInventory(InventoryClickEvent e) {
        if (!NO_CUSTOM.contains(e.getView().getTopInventory().getType())) {
            return;
        }
        boolean top = e.getClickedInventory() == e.getView().getTopInventory();
        ItemStack moving = null;
        if (top) {
            moving = e.getCursor();
            if (e.getClick() == ClickType.NUMBER_KEY) {
                moving = e.getWhoClicked().getInventory().getItem(e.getHotbarButton());
            } else if (e.getClick() == ClickType.SWAP_OFFHAND) {
                moving = e.getWhoClicked().getInventory().getItemInOffHand();
            }
        } else if (e.isShiftClick()) {
            moving = e.getCurrentItem();
        }
        if (Items.isCustom(moving)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGuardedDrag(InventoryDragEvent e) {
        int topSize = e.getView().getTopInventory().getSize();
        if (!NO_CUSTOM.contains(e.getView().getTopInventory().getType()) || !Items.isCustom(e.getOldCursor())) {
            return;
        }
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
