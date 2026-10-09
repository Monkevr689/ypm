package dev.smpsuite.skill;

import dev.smpsuite.Keys;
import dev.smpsuite.SMPSuite;
import dev.smpsuite.data.PlayerData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * All bonus damage goes through here: Combat (percent), Foraging (flat, melee)
 * and the Strength gem (flat, mobs only) are added up and then capped as one -
 * at most +20% against mobs and +8% against players by default - so no mix of
 * skills and gems turns into a runaway multiplier. Teammates can't hurt each other.
 */
public final class Combat implements Listener {

    /** True while a gem ability deals its own damage: no skill bonus on top of it. */
    public static boolean gemHit;

    private final SMPSuite plugin;

    public Combat(SMPSuite plugin) {
        this.plugin = plugin;
    }

    /** The player behind a hit (the attacker, or whoever shot the projectile). */
    public static Player attacker(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent e) {
        Player a = attacker(e.getDamager());
        if (a != null && e.getEntity() instanceof Player victim && a != victim && plugin.teams().sameTeam(a, victim)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim) || victim instanceof ArmorStand) {
            return;
        }
        // a gem's own projectile (Astral Daggers, Fireball): its own damage, halved against players
        if (e.getDamager() instanceof Projectile pr) {
            Double gem = pr.getPersistentDataContainer().get(Keys.PROJECTILE, PersistentDataType.DOUBLE);
            if (gem != null) {
                e.setDamage(victim instanceof Player ? gem * plugin.gems().vsPlayers() : gem);
                return;
            }
        }
        if (gemHit) {
            return;
        }
        Player a = attacker(e.getDamager());
        if (a == null || a == victim || !plugin.skills().enabled()) {
            return;
        }
        boolean melee = e.getDamager() == a && (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK);
        double base = e.getDamage();
        double bonus = bonus(a, base, melee, victim instanceof Player);
        if (bonus > 0) {
            e.setDamage(base + bonus);
        }
    }

    /** The capped bonus damage a hit gets (also used by the self test and the menu). */
    public double bonus(Player a, double base, boolean melee, boolean vsPlayer) {
        return bonus(plugin.store().get(a), plugin.gems().strengthBonus(a), base, melee, vsPlayer);
    }

    /** The same from a player's levels and their gem's flat bonus (vs mobs only). */
    public double bonus(PlayerData d, double gemFlat, double base, boolean melee, boolean vsPlayer) {
        if (base <= 0) {
            return 0;
        }
        Skills skills = plugin.skills();
        double half = vsPlayer ? 0.5 : 1.0;
        double extra = base * skills.combatPercent(d.level(Skill.COMBAT)) * half;
        if (melee) {
            extra += skills.foragingDamage(d.level(Skill.FORAGING)) * half;
            if (!vsPlayer) {
                extra += gemFlat;
            }
        }
        return Math.min(extra, base * cap(vsPlayer));
    }

    public double cap(boolean vsPlayer) {
        return Math.max(0, plugin.getConfig().getDouble(vsPlayer ? "balance.damage-bonus-cap-vs-players"
                : "balance.damage-bonus-cap-vs-mobs", vsPlayer ? 0.08 : 0.20));
    }
}
