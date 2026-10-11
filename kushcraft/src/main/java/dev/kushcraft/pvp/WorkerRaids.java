package dev.kushcraft.pvp;

import dev.kushcraft.KushCraft;
import dev.kushcraft.economy.Tx;
import dev.kushcraft.util.Protection;
import dev.kushcraft.util.Text;
import dev.kushcraft.workers.Worker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Worker raids (pvp.worker-raids, OFF by default - review before turning it
 * on). Other players can attack your workers; at 0 health a worker is
 * knocked out: everything in their satchel drops on the ground where they
 * stand, and they don't work for knockout-minutes. That's the economy's
 * physical footprint: a rich player's crew and chests are worth raiding.
 *
 * Kept apart from everything else on purpose: with it off, workers can't be
 * hurt at all (as in 8.0). It only lets a player hit a worker where they
 * could break a block (claims, spawn protection), never their own worker or
 * one of their cartel's, and logs every knock-out (WORKER_RAID).
 */
public final class WorkerRaids implements Listener {

    private final KushCraft plugin;

    public WorkerRaids(KushCraft plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("pvp.worker-raids.enabled", false);
    }

    public double maxHealth() {
        return Math.max(1, plugin.getConfig().getDouble("pvp.worker-raids.health", 40));
    }

    public long knockoutMillis() {
        return (long) (Math.max(0, plugin.getConfig().getDouble("pvp.worker-raids.knockout-minutes", 30)) * 60_000L);
    }

    /** Runs before Workers cancels all damage to workers (it cancels at HIGH). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Worker w = plugin.workers().fromEntity(e.getEntity());
        if (w == null || !enabled()) {
            return;
        }
        Player attacker = e.getDamager() instanceof Player p ? p
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p2 ? p2 : null;
        e.setCancelled(true);
        if (attacker == null || w.knockedOut()) {
            return;
        }
        if (attacker.getUniqueId().equals(w.owner()) || sameCartel(attacker.getUniqueId(), w.owner())) {
            attacker.sendActionBar(Text.mm("<gray>That's one of your own crew."));
            return;
        }
        Location at = e.getEntity().getLocation();
        if (!Protection.canBuild(attacker, at.getBlock())) {
            attacker.sendActionBar(Text.mm("<gray>This worker is in a protected area."));
            return;
        }
        if (!plugin.rates().allow(attacker.getUniqueId(), "raid-hit", 400)) {
            return; // one hit every 0.4 s, like a player's attack cooldown
        }
        double hp = plugin.workers().hurt(w, e.getFinalDamage() > 0 ? e.getDamage() : 1, maxHealth());
        at.getWorld().playSound(at, "minecraft:entity.villager.hurt", SoundCategory.NEUTRAL, 1f, 1f);
        at.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, at.clone().add(0, 1.2, 0), 4, 0.2, 0.3, 0.2, 0);
        alert(w, attacker, false);
        if (hp <= 0) {
            knockOut(w, attacker, at);
        }
    }

    private boolean sameCartel(UUID a, UUID b) {
        var ca = plugin.cartels().of(a);
        return ca != null && ca == plugin.cartels().of(b);
    }

    private void knockOut(Worker w, Player by, Location at) {
        int items = 0;
        for (ItemStack it : plugin.workers().empty(w)) {
            at.getWorld().dropItemNaturally(at, it);
            items += it.getAmount();
        }
        plugin.workers().knockOut(w, System.currentTimeMillis() + knockoutMillis());
        plugin.economy().ledger().log(w.owner(), Tx.WORKER_RAID, 0, null, by.getUniqueId().toString(),
                w.type().display() + " " + w.name() + " knocked out by " + by.getName() + ", dropped " + items + " items at "
                        + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ());
        at.getWorld().playSound(at, "minecraft:entity.villager.death", SoundCategory.NEUTRAL, 1f, 0.9f);
        by.sendActionBar(Text.mm("<red>Knocked out " + Text.escape(w.name()) + " <gray>- " + items + " items dropped."));
        alert(w, by, true);
    }

    private void alert(Worker w, Player by, boolean out) {
        if (!plugin.getConfig().getBoolean("pvp.worker-raids.alert-owner", true)) {
            return;
        }
        Player owner = Bukkit.getPlayer(w.owner());
        if (owner == null || (!out && !plugin.rates().allow(owner.getUniqueId(), "raid-alert-" + w.id(), 10_000))) {
            return;
        }
        Location h = w.home();
        String where = h == null ? "" : " at " + h.getBlockX() + " " + h.getBlockY() + " " + h.getBlockZ();
        owner.sendMessage(Text.msg(out
                ? "<red>Your " + w.type().display() + " " + Text.escape(w.name()) + " was knocked out by "
                + Text.escape(by.getName()) + where + " and dropped their satchel!"
                : "<gold>" + Text.escape(by.getName()) + " <red>is attacking your " + w.type().display() + " "
                + Text.escape(w.name()) + where + "!"));
    }
}
