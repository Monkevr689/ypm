package dev.kushcraft.listener;

import dev.kushcraft.KushCraft;
import dev.kushcraft.Keys;
import dev.kushcraft.effect.EffectType;
import dev.kushcraft.gui.TabMenu;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.recipe.Recipes;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
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
        plugin.ranks().showInTab(p);
        p.discoverRecipes(Recipes.keys());
        plugin.effects().join(p);
        if (plugin.getConfig().getBoolean("give-guide-on-first-join", true)
                && !p.getPersistentDataContainer().has(Keys.GOT_GUIDE, PersistentDataType.BYTE)) {
            p.getPersistentDataContainer().set(Keys.GOT_GUIDE, PersistentDataType.BYTE, (byte) 1);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    InventoryUtil.give(p, Items.create(ItemType.GROWER_GUIDE));
                    int kit = starterKit(p);
                    p.sendMessage(Text.msg("<gray>This server runs <green>KushCraft</green>! Type <white>/kush</white>, press"
                            + " <white>Shift+F</white> or right-click the <green>KushCraft Menu</green> book to start."));
                    p.sendMessage(Text.msg("<aqua>New here? <gray>The glowing <aqua>Next</aqua> button in the menu shows"
                            + " your next step." + (kit > 0 ? " <green>You got a starter kit!" : "")));
                }
            }, 60L);
        }
    }

    /** Gives the new-players.starter-kit from config.yml. Returns how many stacks were given. */
    public static int starterKit(Player p) {
        KushCraft plugin = KushCraft.get();
        int n = 0;
        for (java.util.Map<?, ?> m : plugin.getConfig().getMapList("new-players.starter-kit")) {
            ItemType t = ItemType.parse(String.valueOf(m.get("item")));
            int amount = m.get("amount") instanceof Number num ? num.intValue() : 1;
            if (t == null) {
                continue;
            }
            if (t.strainBound()) {
                var s = plugin.strains().get(String.valueOf(m.get("strain")));
                if (s == null) {
                    continue;
                }
                InventoryUtil.give(p, Items.strainItem(t, s, 3, amount));
            } else {
                InventoryUtil.give(p, Items.create(t, amount));
            }
            n++;
        }
        return n;
    }

    /** Shift + F (sneak + swap hands) opens the menu from anywhere. */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!p.isSneaking() || !plugin.getConfig().getBoolean("menu.shift-f", true) || !p.hasPermission("kushcraft.use")) {
            return;
        }
        e.setCancelled(true);
        TabMenu.openMain(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.effects().quit(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        plugin.effects().clear(p);
        loseCash(p);
    }

    /** What dying costs with this much cash (rounded down to the cent). */
    public static double cashLost(double balance, double pct) {
        return balance <= 0 ? 0 : Math.floor(balance * Math.max(0, Math.min(1, pct)) * 100) / 100.0;
    }

    /** death.cash-lost of the wallet is gone (nobody gets it). Returns the amount lost. */
    public double loseCash(Player p) {
        double pct = Math.max(0, Math.min(1, plugin.getConfig().getDouble("death.cash-lost", 0.2)));
        double lost = cashLost(plugin.economy().balance(p), pct);
        if (pct <= 0 || lost < 0.01 || !plugin.economy().withdraw(p, lost)) {
            return 0;
        }
        p.sendMessage(Text.msg("<red>You died and lost <gold>" + plugin.economy().format(lost) + "</gold> <gray>("
                + Math.round(pct * 100) + "% of your cash)."));
        return lost;
    }

    /** Pain Relief: no fall damage. */
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p
                && plugin.effects().has(p, EffectType.PAIN_RELIEF)) {
            e.setCancelled(true);
        }
    }

    /** Ghost: monsters don't notice you (bosses still do). */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p && e.getEntity() instanceof Monster
                && !(e.getEntity() instanceof Boss) && !(e.getEntity() instanceof Warden)
                && plugin.effects().has(p, EffectType.GHOST)) {
            e.setCancelled(true);
        }
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

    /** KushCraft items are paper underneath - keep them out of vanilla recipes (ours are fine). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        if (e.getRecipe() instanceof org.bukkit.Keyed k && dev.kushcraft.recipe.Recipes.usesCustomItems(k.getKey())) {
            return;
        }
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
