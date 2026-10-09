package dev.smpsuite.gem;

import dev.smpsuite.Keys;
import dev.smpsuite.Msg;
import dev.smpsuite.SMPSuite;
import dev.smpsuite.data.PlayerData;
import dev.smpsuite.skill.Combat;
import org.bukkit.damage.DamageSource;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * What each gem's two abilities do. Anything that touches another player
 * first asks the server whether this player may hit them (PvP off, a claim,
 * a teammate - then they're left alone), and gem damage to players is halved.
 */
public final class GemAbilities {

    private final SMPSuite plugin;
    private final Gems gems;

    GemAbilities(SMPSuite plugin, Gems gems) {
        this.plugin = plugin;
        this.gems = gems;
    }

    /** Runs an ability. False when nothing happened (then there's no cooldown). */
    boolean run(Player p, GemType t, boolean secondary) {
        return switch (t) {
            case ASTRA -> secondary ? daggers(p) : drift(p);
            case FIRE -> secondary ? campfire(p) : fireball(p);
            case FLUX -> secondary ? staticBurst(p) : beam(p);
            case LIFE -> secondary ? circleOfLife(p) : vortex(p);
            case PUFF -> secondary ? breezyBash(p) : dash(p);
            case SPEED -> secondary ? slipstream(p) : terminalVelocity(p);
            case STRENGTH -> secondary ? bloodlust(p) : frailer(p);
            case WEALTH -> secondary ? pockets(p) : richRush(p);
        };
    }

    private void announce(Player p, GemType t, String name, String what) {
        p.sendActionBar(Msg.mm("<" + t.hex() + "><bold>" + name + "!</bold></" + t.hex() + "> <gray>" + what));
    }

    // ------------------------------------------------------------------
    // who may be affected
    // ------------------------------------------------------------------

    /** May this player hit that player here? (asks the server and protection plugins) */
    public boolean mayHit(Player p, Player target) {
        if (target == p || target.isDead() || target.getGameMode() == org.bukkit.GameMode.CREATIVE
                || target.getGameMode() == org.bukkit.GameMode.SPECTATOR || !p.getWorld().getPVP()
                || plugin.teams().sameTeam(p, target)) {
            return false;
        }
        DamageSource src = DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(p).withDirectEntity(p).build();
        EntityDamageByEntityEvent test = new EntityDamageByEntityEvent(p, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                src, 0.01);
        Combat.gemHit = true;
        try {
            org.bukkit.Bukkit.getPluginManager().callEvent(test);
        } finally {
            Combat.gemHit = false;
        }
        return !test.isCancelled();
    }

    /** Hostile mobs, and players this player may hit. Never animals, villagers, pets or teammates. */
    private boolean foe(Player p, Entity e) {
        if (!(e instanceof LivingEntity le) || le == p || le instanceof ArmorStand || le.isDead()) {
            return false;
        }
        if (le instanceof Player target) {
            return mayHit(p, target);
        }
        return le instanceof Enemy;
    }

    private List<LivingEntity> foesAround(Player p, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(r, r, r)) {
            if (foe(p, e) && e.getLocation().distanceSquared(p.getLocation()) <= r * r) {
                out.add((LivingEntity) e);
            }
        }
        return out;
    }

    /** Gem damage: halved against players, and no skill bonus on top. */
    private void hurt(Player p, LivingEntity target, double damage) {
        double amount = target instanceof Player ? damage * gems.vsPlayers() : damage;
        Combat.gemHit = true;
        try {
            target.damage(amount, p);
        } finally {
            Combat.gemHit = false;
        }
    }

    /** The player and their teammates within r blocks. */
    private List<Player> team(Player p, double r) {
        List<Player> out = new ArrayList<>();
        out.add(p);
        out.addAll(plugin.teams().teammatesNear(p, r));
        return out;
    }

    // ------------------------------------------------------------------
    // Astra
    // ------------------------------------------------------------------

    private boolean drift(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult hit = p.getWorld().rayTraceBlocks(eye, dir, 8, FluidCollisionMode.NEVER, true);
        double dist = hit == null ? 8 : Math.max(0, hit.getHitPosition().distance(eye.toVector()) - 0.8);
        for (double t = dist; t >= 1; t -= 0.5) {
            Location feet = eye.clone().add(dir.clone().multiply(t)).subtract(0, p.getEyeHeight(), 0);
            if (feet.getBlock().isPassable() && feet.clone().add(0, 1, 0).getBlock().isPassable()) {
                Location from = p.getLocation();
                feet.setYaw(from.getYaw());
                feet.setPitch(from.getPitch());
                from.getWorld().spawnParticle(Particle.REVERSE_PORTAL, from.add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.05);
                p.teleport(feet);
                p.setFallDistance(0);
                p.getWorld().spawnParticle(Particle.PORTAL, feet.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.3);
                p.playSound(feet, "minecraft:entity.enderman.teleport", SoundCategory.PLAYERS, 0.8f, 1.3f);
                announce(p, GemType.ASTRA, "Dimensional Drift", "");
                return true;
            }
        }
        p.sendActionBar(Msg.mm("<gray>No room to drift that way."));
        return false;
    }

    private boolean daggers(Player p) {
        Vector dir = p.getEyeLocation().getDirection().normalize();
        for (int i = -1; i <= 1; i++) {
            Vector v = dir.clone().rotateAroundY(Math.toRadians(i * 6)).multiply(2.0);
            Snowball s = p.launchProjectile(Snowball.class, v);
            s.setItem(new ItemStack(Material.AMETHYST_SHARD));
            s.getPersistentDataContainer().set(Keys.PROJECTILE, PersistentDataType.DOUBLE, 4.0);
        }
        p.playSound(p.getLocation(), "minecraft:block.amethyst_block.hit", SoundCategory.PLAYERS, 1f, 1.5f);
        announce(p, GemType.ASTRA, "Astral Daggers", "");
        return true;
    }

    // ------------------------------------------------------------------
    // Fire
    // ------------------------------------------------------------------

    private boolean fireball(Player p) {
        SmallFireball f = p.launchProjectile(SmallFireball.class, p.getEyeLocation().getDirection().multiply(1.4));
        f.setIsIncendiary(false);
        f.setYield(0);
        f.getPersistentDataContainer().set(Keys.PROJECTILE, PersistentDataType.DOUBLE, 5.0);
        p.playSound(p.getLocation(), "minecraft:entity.blaze.shoot", SoundCategory.PLAYERS, 0.8f, 1f);
        announce(p, GemType.FIRE, "Fireball", "");
        return true;
    }

    private boolean campfire(Player p) {
        Location c = p.getLocation();
        new BukkitRunnable() {
            int n;

            @Override
            public void run() {
                if (++n > 10 || !p.isOnline()) {
                    cancel();
                    return;
                }
                c.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, c.clone().add(0, 0.3, 0), 3, 0.3, 0.1, 0.3, 0.01);
                c.getWorld().spawnParticle(Particle.FLAME, c.clone().add(0, 0.2, 0), 8, 1.5, 0.1, 1.5, 0.01);
                for (Player m : team(p, 30)) {
                    if (m.getWorld().equals(c.getWorld()) && m.getLocation().distanceSquared(c) <= 25) {
                        m.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 50, 0, true, true, true));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        p.playSound(c, "minecraft:block.campfire.crackle", SoundCategory.PLAYERS, 1f, 1f);
        announce(p, GemType.FIRE, "Cozy Campfire", "stay close for 10s");
        return true;
    }

    // ------------------------------------------------------------------
    // Flux
    // ------------------------------------------------------------------

    private boolean beam(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult hit = p.getWorld().rayTrace(eye, dir, 24, FluidCollisionMode.NEVER, true, 0.4, e -> foe(p, e));
        double len = hit == null ? 24 : hit.getHitPosition().distance(eye.toVector());
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0x3AE8E0), 1.0f);
        for (double t = 0.5; t < len; t += 0.5) {
            p.getWorld().spawnParticle(Particle.DUST, eye.clone().add(dir.clone().multiply(t)), 1, 0, 0, 0, 0, dust);
        }
        p.playSound(eye, "minecraft:entity.guardian.attack", SoundCategory.PLAYERS, 0.7f, 2f);
        if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
            hurt(p, target, 6);
            boolean player = target instanceof Player;
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, player ? 20 : 40, 2, false, true, true));
            target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
        }
        announce(p, GemType.FLUX, "Flux Beam", "");
        return true;
    }

    private boolean staticBurst(Player p) {
        Location c = p.getLocation();
        for (LivingEntity e : foesAround(p, 5)) {
            Vector away = e.getLocation().toVector().subtract(c.toVector()).setY(0);
            away = away.lengthSquared() < 0.01 ? new Vector(0, 0, 0) : away.normalize();
            if (e instanceof Player) {
                e.setVelocity(away.multiply(0.6).setY(0.25));
            } else {
                hurt(p, e, 2);
                e.setVelocity(away.multiply(1.2).setY(0.4));
            }
        }
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            c.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, c.clone().add(Math.cos(a) * 2.5, 1, Math.sin(a) * 2.5),
                    2, 0.1, 0.1, 0.1, 0.05);
        }
        p.playSound(c, "minecraft:entity.lightning_bolt.thunder", SoundCategory.PLAYERS, 0.4f, 1.8f);
        announce(p, GemType.FLUX, "Static Burst", "");
        return true;
    }

    // ------------------------------------------------------------------
    // Life
    // ------------------------------------------------------------------

    private boolean vortex(Player p) {
        for (Player m : team(p, 6)) {
            m.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 1, false, true, true));
            m.getWorld().spawnParticle(Particle.HEART, m.getLocation().add(0, 1.8, 0), 5, 0.4, 0.3, 0.4, 0);
        }
        p.playSound(p.getLocation(), "minecraft:block.beacon.activate", SoundCategory.PLAYERS, 0.6f, 1.6f);
        announce(p, GemType.LIFE, "Vitality Vortex", "you and your team heal");
        return true;
    }

    private boolean circleOfLife(Player p) {
        Block c = p.getLocation().getBlock();
        int n = 0;
        for (int dx = -5; dx <= 5 && n < 40; dx++) {
            for (int dz = -5; dz <= 5 && n < 40; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    Block b = c.getRelative(dx, dy, dz);
                    boolean crop = Tag.CROPS.isTagged(b.getType()) && b.getBlockData() instanceof Ageable a
                            && a.getAge() < a.getMaximumAge();
                    if ((crop || Tag.SAPLINGS.isTagged(b.getType())) && b.applyBoneMeal(BlockFace.UP)) {
                        n++;
                    }
                }
            }
        }
        if (n == 0) {
            p.sendActionBar(Msg.mm("<gray>No growing crops or saplings around you."));
            return false;
        }
        p.playSound(p.getLocation(), "minecraft:item.bone_meal.use", SoundCategory.PLAYERS, 1f, 1f);
        announce(p, GemType.LIFE, "Circle of Life", n + " plants grew");
        return true;
    }

    // ------------------------------------------------------------------
    // Puff
    // ------------------------------------------------------------------

    private boolean dash(Player p) {
        Vector v = p.getEyeLocation().getDirection().normalize().multiply(1.5);
        v.setY(Math.max(v.getY(), 0.35));
        p.setVelocity(v);
        p.setFallDistance(0);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 20, 0.3, 0.1, 0.3, 0.05);
        p.playSound(p.getLocation(), "minecraft:entity.breeze.wind_burst", SoundCategory.PLAYERS, 0.8f, 1.2f);
        announce(p, GemType.PUFF, "Dash", "");
        return true;
    }

    private boolean breezyBash(Player p) {
        Location c = p.getLocation();
        for (LivingEntity e : foesAround(p, 6)) {
            Vector away = e.getLocation().toVector().subtract(c.toVector()).setY(0);
            away = away.lengthSquared() < 0.01 ? new Vector(0, 0, 0) : away.normalize();
            // mobs fly up; players are only pushed back (no fall-damage kills in PvP)
            e.setVelocity(e instanceof Player ? away.multiply(0.8).setY(0.3) : away.multiply(0.3).setY(1.25));
        }
        c.getWorld().spawnParticle(Particle.CLOUD, c.clone().add(0, 0.5, 0), 60, 3, 0.3, 3, 0.05);
        p.playSound(c, "minecraft:entity.breeze.wind_burst", SoundCategory.PLAYERS, 1f, 0.8f);
        announce(p, GemType.PUFF, "Breezy Bash", "");
        return true;
    }

    // ------------------------------------------------------------------
    // Speed
    // ------------------------------------------------------------------

    private boolean terminalVelocity(Player p) {
        // Haste never stacks: Minecraft keeps the strongest (Haste Pulse's Haste III wins)
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 2, false, true, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 300, 1, false, true, true));
        p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
        p.playSound(p.getLocation(), "minecraft:item.trident.riptide_1", SoundCategory.PLAYERS, 0.8f, 1.4f);
        announce(p, GemType.SPEED, "Terminal Velocity", "Speed III and Haste II for 15s");
        return true;
    }

    private boolean slipstream(Player p) {
        for (Player m : team(p, 8)) {
            m.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 400, 1, false, true, true));
            m.getWorld().spawnParticle(Particle.FIREWORK, m.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.02);
        }
        p.playSound(p.getLocation(), "minecraft:entity.firework_rocket.launch", SoundCategory.PLAYERS, 0.8f, 1.2f);
        announce(p, GemType.SPEED, "Slipstream", "Speed II for you and your team");
        return true;
    }

    // ------------------------------------------------------------------
    // Strength
    // ------------------------------------------------------------------

    private boolean frailer(Player p) {
        for (LivingEntity e : foesAround(p, 6)) {
            boolean player = e instanceof Player;
            e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, player ? 80 : 160, player ? 0 : 1, false, true, true));
            e.getWorld().spawnParticle(Particle.SMOKE, e.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.02);
        }
        p.playSound(p.getLocation(), "minecraft:entity.wither.ambient", SoundCategory.PLAYERS, 0.4f, 1.6f);
        announce(p, GemType.STRENGTH, "Frailer", "everything around you is weakened");
        return true;
    }

    private boolean bloodlust(Player p) {
        plugin.store().get(p).activate(Gems.BLOODLUST, 12);
        p.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, p.getLocation().add(0, 1.5, 0), 8, 0.4, 0.3, 0.4, 0);
        p.playSound(p.getLocation(), "minecraft:entity.ravager.roar", SoundCategory.PLAYERS, 0.4f, 1.4f);
        announce(p, GemType.STRENGTH, "Bloodlust", "hits on mobs heal you for 12s");
        return true;
    }

    // ------------------------------------------------------------------
    // Wealth
    // ------------------------------------------------------------------

    private boolean richRush(Player p) {
        plugin.store().get(p).activate(Gems.RICH_RUSH, 30);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0);
        p.playSound(p.getLocation(), "minecraft:entity.villager.celebrate", SoundCategory.PLAYERS, 0.8f, 1.2f);
        announce(p, GemType.WEALTH, "Rich Rush", "ores drop double for 30s");
        return true;
    }

    private boolean pockets(Player p) {
        new PocketsMenu(plugin, p).open();
        return true;
    }

    /** Pockets contents count as the player's (used by the menu). */
    static PlayerData data(SMPSuite plugin, Player p) {
        return plugin.store().get(p);
    }
}
