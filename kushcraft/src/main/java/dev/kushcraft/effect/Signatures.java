package dev.kushcraft.effect;

import dev.kushcraft.KushCraft;
import dev.kushcraft.Keys;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.WeatherType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Every drug's one-of-a-kind effect (EffectType#signature): companions (a
 * spirit fox, machine elves), double jumps and gliding, low gravity, step-up
 * legs, colours and flowers only the user sees, damage changes and more.
 * Nothing here helps in a fight against other players.
 */
public final class Signatures implements Listener {

    private static final Material[] COLOURS = {Material.RED_CONCRETE, Material.ORANGE_CONCRETE,
            Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.MAGENTA_CONCRETE,
            Material.PURPLE_CONCRETE, Material.PINK_CONCRETE, Material.CYAN_CONCRETE, Material.LIME_WOOL,
            Material.MAGENTA_WOOL, Material.YELLOW_WOOL, Material.LIGHT_BLUE_WOOL, Material.ORANGE_WOOL};
    private static final Material[] FLOWERS = {Material.POPPY, Material.POPPY, Material.POPPY, Material.RED_TULIP,
            Material.ORANGE_TULIP};

    private final KushCraft plugin;
    private final EffectManager effects;
    private final Map<UUID, State> states = new HashMap<>();
    private final Random random = new Random();
    private final NamespacedKey stepKey;
    private final NamespacedKey gravityKey;
    private final NamespacedKey safeFallKey;
    private final NamespacedKey wingsKey;

    private record Fake(Location at, long until) {
    }

    private static final class State {
        final List<UUID> companions = new ArrayList<>();
        final Deque<Fake> fakes = new ArrayDeque<>();
        Block lastStep;
        boolean wings;
        boolean airJumped;
        int balloon;
    }

    Signatures(KushCraft plugin, EffectManager effects) {
        this.plugin = plugin;
        this.effects = effects;
        this.stepKey = new NamespacedKey(plugin, "quick_step");
        this.gravityKey = new NamespacedKey(plugin, "moon_gravity");
        this.safeFallKey = new NamespacedKey(plugin, "moon_safe_fall");
        this.wingsKey = new NamespacedKey(plugin, "fairy_wings");
    }

    private State state(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), k -> new State());
    }

    // ------------------------------------------------------------------
    // start / end
    // ------------------------------------------------------------------

    /** A signature effect began (fresh = it wasn't running already). */
    void start(Player p, EffectType t, boolean fresh) {
        if (fresh) {
            p.sendActionBar(Text.mm(t.signatureLine()));
            p.playSound(p.getLocation(), "minecraft:block.amethyst_block.chime", SoundCategory.PLAYERS, 0.8f, 1.6f);
            plugin.awards().signature(p, t.name());
        }
        State s = state(p);
        switch (t) {
            case QUICK_STEP -> modifier(p, Attribute.STEP_HEIGHT, stepKey, 0.65, AttributeModifier.Operation.ADD_NUMBER);
            case MOON_GRAVITY -> {
                modifier(p, Attribute.GRAVITY, gravityKey, -0.65, AttributeModifier.Operation.ADD_SCALAR);
                modifier(p, Attribute.SAFE_FALL_DISTANCE, safeFallKey, 12, AttributeModifier.Operation.ADD_NUMBER);
            }
            case SPIRIT_FOX -> {
                if (s.companions.isEmpty()) {
                    spawnFox(p, s);
                }
            }
            case MACHINE_ELVES -> {
                if (s.companions.isEmpty()) {
                    spawnElves(p, s);
                }
            }
            case FAIRY_WINGS -> wings(p, s, true);
            case SUNNY -> p.setPlayerWeather(WeatherType.CLEAR);
            case BALLOON -> {
                s.balloon = 4;
                p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 70, 0, true, false, false));
            }
            default -> {
            }
        }
    }

    void end(Player p, EffectType t) {
        State s = states.get(p.getUniqueId());
        switch (t) {
            case QUICK_STEP -> unmodifier(p, Attribute.STEP_HEIGHT, stepKey);
            case MOON_GRAVITY -> {
                unmodifier(p, Attribute.GRAVITY, gravityKey);
                unmodifier(p, Attribute.SAFE_FALL_DISTANCE, safeFallKey);
            }
            case SPIRIT_FOX, MACHINE_ELVES -> {
                if (s != null) {
                    despawn(s);
                }
            }
            case FAIRY_WINGS -> {
                if (s != null) {
                    wings(p, s, false);
                }
            }
            case SUNNY -> p.resetPlayerWeather();
            case KALEIDOSCOPE, POPPY_TRAIL -> {
                if (s != null) {
                    restoreAll(p, s);
                }
            }
            default -> {
            }
        }
        if (t == EffectType.CACTUS_SKIN || t == EffectType.MACHINE_ELVES) {
            if (!effects.has(p, EffectType.TRIPPY)) {
                p.resetPlayerTime();
            }
        }
    }

    /** Leaving / dying / effects cleared: undo everything. */
    void clear(Player p) {
        State s = states.remove(p.getUniqueId());
        unmodifier(p, Attribute.STEP_HEIGHT, stepKey);
        unmodifier(p, Attribute.GRAVITY, gravityKey);
        unmodifier(p, Attribute.SAFE_FALL_DISTANCE, safeFallKey);
        if (s != null) {
            despawn(s);
            wings(p, s, false);
            restoreAll(p, s);
        }
    }

    void shutdown() {
        for (Map.Entry<UUID, State> e : states.entrySet()) {
            despawn(e.getValue());
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                wings(p, e.getValue(), false);
                restoreAll(p, e.getValue());
            }
        }
        states.clear();
    }

    private void modifier(Player p, Attribute a, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null || inst.getModifier(key) != null) {
            return;
        }
        // transient: never saved with the player, so a crash can't leave it on
        inst.addTransientModifier(new AttributeModifier(key, amount, op));
    }

    private void unmodifier(Player p, Attribute a, NamespacedKey key) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst != null && inst.getModifier(key) != null) {
            inst.removeModifier(key);
        }
    }

    /** Fairy Wings: flight is switched on so the jump key in mid-air can be caught (never really flying). */
    private void wings(Player p, State s, boolean on) {
        boolean survival = p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
        if (on) {
            if (survival && !p.getAllowFlight()) {
                s.wings = true;
                p.getPersistentDataContainer().set(wingsKey, PersistentDataType.BYTE, (byte) 1);
                p.setAllowFlight(true);
            }
        } else if (s.wings) {
            s.wings = false;
            p.getPersistentDataContainer().remove(wingsKey);
            if (survival) {
                p.setFlying(false);
                p.setAllowFlight(false);
            }
        }
    }

    // ------------------------------------------------------------------
    // companions
    // ------------------------------------------------------------------

    private void tag(Entity e) {
        e.setPersistent(false);
        e.setInvulnerable(true);
        e.getPersistentDataContainer().set(Keys.VISUAL, PersistentDataType.STRING, "companion");
    }

    private void spawnFox(Player p, State s) {
        Location at = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(-1.5));
        try {
            Fox fox = p.getWorld().spawn(at, Fox.class, f -> {
                tag(f);
                f.setFoxType(random.nextBoolean() ? Fox.Type.SNOW : Fox.Type.RED);
                f.setFirstTrustedPlayer(p); // a trusting fox follows instead of running away
                f.setGlowing(true);
                f.setCollidable(false);
                f.setRemoveWhenFarAway(false);
                f.customName(Text.mm("<gold>" + net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .escapeTags(p.getName()) + "'s spirit"));
                f.setCustomNameVisible(false);
            });
            s.companions.add(fox.getUniqueId());
            p.getWorld().spawnParticle(Particle.SOUL, at.clone().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.02);
            p.playSound(at, "minecraft:entity.fox.ambient", SoundCategory.NEUTRAL, 1f, 1.4f);
        } catch (RuntimeException ignored) {
            // some worlds don't allow spawning here
        }
    }

    private void spawnElves(Player p, State s) {
        for (int i = 0; i < 3; i++) {
            Location at = p.getEyeLocation().add(Math.cos(i * 2.1) * 2, 0.5, Math.sin(i * 2.1) * 2);
            try {
                Allay a = p.getWorld().spawn(at, Allay.class, e -> {
                    tag(e);
                    e.setAI(false);
                    e.setGravity(false);
                    e.setSilent(true);
                    e.setGlowing(true);
                    e.setCollidable(false);
                    e.setCanDuplicate(false);
                    e.setCanPickupItems(false);
                });
                s.companions.add(a.getUniqueId());
            } catch (RuntimeException ignored) {
                // no room
            }
        }
        p.playSound(p.getLocation(), "minecraft:entity.allay.ambient_with_item", SoundCategory.NEUTRAL, 1f, 0.6f);
    }

    private void despawn(State s) {
        for (UUID id : s.companions) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.getWorld().spawnParticle(Particle.POOF, e.getLocation().add(0, 0.4, 0), 10, 0.2, 0.2, 0.2, 0.02);
                e.remove();
            }
        }
        s.companions.clear();
    }

    // ------------------------------------------------------------------
    // client-side blocks (only the user sees them)
    // ------------------------------------------------------------------

    private void fake(Player p, State s, Block b, Material m, int seconds) {
        s.fakes.add(new Fake(b.getLocation(), Bukkit.getCurrentTick() + seconds * 20L));
        p.sendBlockChange(b.getLocation(), m.createBlockData());
        while (s.fakes.size() > 160) {
            restore(p, s.fakes.poll());
        }
    }

    private void restore(Player p, Fake f) {
        if (f.at().getWorld() != null && f.at().getWorld().equals(p.getWorld())) {
            p.sendBlockChange(f.at(), f.at().getBlock().getBlockData());
        }
    }

    private void restoreAll(Player p, State s) {
        for (Fake f : s.fakes) {
            restore(p, f);
        }
        s.fakes.clear();
    }

    // ------------------------------------------------------------------
    // ticking
    // ------------------------------------------------------------------

    /** Once a second for every active signature. */
    void tick(Player p, EffectType t, int left) {
        State s = state(p);
        Location loc = p.getLocation();
        Location head = p.getEyeLocation();
        switch (t) {
            case SMOKE_RINGS -> {
                if (left % 3 == 0) {
                    ring(p, head.clone().add(head.getDirection().multiply(1.3)), head.getDirection(), 0.45,
                            Particle.SMOKE, 14);
                }
            }
            case HOTBOX -> {
                p.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0, 1, 0), 3, 1.2, 0.6, 1.2,
                        0.005);
                if (left % 6 == 0) {
                    for (Player o : p.getWorld().getPlayers()) {
                        if (o != p && o.getLocation().distanceSquared(loc) < 9) {
                            effects.apply(o, new Dose().add(EffectType.GIGGLES, 8).high(1));
                            o.sendActionBar(Text.mm("<gray>Contact high from " + Text.escape(p.getName()) + "'s hotbox"));
                        }
                    }
                }
            }
            case CLOUD_CHASER -> {
                p.getWorld().spawnParticle(Particle.CLOUD, head.clone().add(0, -0.3, 0), 4, 0.5, 0.3, 0.5, 0.01);
                if (!p.isOnGround() && p.getVelocity().getY() < -0.2) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, true, false, false));
                }
            }
            case FAIRY_RING -> {
                double a = (left % 8) / 8.0 * Math.PI * 2;
                for (int i = 0; i < 8; i++) {
                    double ang = a + i * Math.PI / 4;
                    p.getWorld().spawnParticle(Particle.FALLING_SPORE_BLOSSOM,
                            loc.clone().add(Math.cos(ang) * 1.6, 0.2, Math.sin(ang) * 1.6), 1, 0, 0, 0, 0);
                }
                if (left % 3 == 0) {
                    for (Entity e : p.getNearbyEntities(10, 4, 10)) {
                        if (e instanceof Animals an && an.getLocation().distanceSquared(loc) > 6) {
                            an.getPathfinder().moveTo(p, 1.1);
                        }
                    }
                }
            }
            case SUNNY -> {
                p.setPlayerWeather(WeatherType.CLEAR);
                if (random.nextInt(3) == 0) {
                    p.spawnParticle(Particle.WAX_ON, head.clone().add(0, 1, 0), 3, 1.5, 0.5, 1.5, 0);
                }
            }
            case SWEET_TOOTH -> {
                if (random.nextInt(3) == 0) {
                    p.getWorld().spawnParticle(Particle.DUST, head.clone().add(0, 0.4, 0), 3, 0.3, 0.2, 0.3, 0,
                            new Particle.DustOptions(Color.fromRGB(0x6A3A1E), 1f));
                }
            }
            case KALEIDOSCOPE -> {
                if (left % 2 == 0) {
                    for (int i = 0; i < 18; i++) {
                        Block b = surface(loc, 7);
                        if (b != null) {
                            fake(p, s, b, COLOURS[random.nextInt(COLOURS.length)], 4);
                        }
                    }
                }
            }
            case SPIRIT_FOX -> {
                for (UUID id : s.companions) {
                    if (Bukkit.getEntity(id) instanceof Fox fox) {
                        fox.setTarget(null);
                        fox.setSleeping(false);
                        if (!fox.getWorld().equals(p.getWorld()) || fox.getLocation().distanceSquared(loc) > 400) {
                            fox.teleport(loc);
                        } else if (fox.getLocation().distanceSquared(loc) > 9) {
                            fox.getPathfinder().moveTo(p, 1.3);
                        }
                        fox.getWorld().spawnParticle(Particle.SOUL, fox.getLocation().add(0, 0.6, 0), 1, 0.2, 0.2, 0.2,
                                0.01);
                    }
                }
                if (s.companions.isEmpty()) {
                    spawnFox(p, s);
                }
            }
            case CACTUS_SKIN -> {
                if (!effects.has(p, EffectType.TRIPPY)) {
                    p.setPlayerTime(12600, false);
                }
                if (random.nextInt(4) == 0) {
                    p.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 3, 0.4, 0.5, 0.4, 0,
                            new Particle.DustOptions(Color.fromRGB(0x6AC84A), 0.8f));
                }
            }
            case MACHINE_ELVES -> {
                if (s.companions.isEmpty()) {
                    spawnElves(p, s);
                }
                if (!effects.has(p, EffectType.TRIPPY)) {
                    p.setPlayerTime(18000, false);
                }
                if (random.nextInt(6) == 0) {
                    p.playSound(loc, "minecraft:entity.allay.ambient_without_item", SoundCategory.NEUTRAL, 0.7f,
                            0.5f + random.nextFloat());
                }
            }
            case VINE_SIGHT -> {
                if (left % 2 == 0) {
                    oreSight(p);
                }
            }
            case TWEAKING -> {
                effects.potion(p, PotionEffectType.HASTE, 2);
                if (random.nextInt(3) == 0) {
                    Location l = p.getLocation();
                    p.setRotation(l.getYaw() + (random.nextFloat() - 0.5f) * 16, l.getPitch() + (random.nextFloat() - 0.5f) * 8);
                }
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, head, 2, 0.3, 0.3, 0.3, 0.05);
            }
            case CHEMIST -> {
                if (random.nextInt(3) == 0) {
                    p.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 4, 0.4, 0.6, 0.4, 0,
                            new Particle.DustOptions(Color.fromRGB(0x3AC8FF), 1f));
                }
            }
            case RAVE -> rave(p);
            case NUMB -> {
                if (random.nextInt(5) == 0) {
                    p.spawnParticle(Particle.DUST, head, 4, 0.5, 0.3, 0.5, 0,
                            new Particle.DustOptions(Color.fromRGB(0xB48AD8), 1.2f));
                }
            }
            case SLOW_MO -> {
                for (Entity e : p.getNearbyEntities(8, 4, 8)) {
                    if (e instanceof Monster m) {
                        m.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2, true, false, false));
                        m.getWorld().spawnParticle(Particle.DUST, m.getLocation().add(0, 1, 0), 2, 0.3, 0.4, 0.3, 0,
                                new Particle.DustOptions(Color.fromRGB(0xA050D8), 1f));
                    }
                }
            }
            case MOON_GRAVITY -> {
                if (!p.isOnGround()) {
                    p.getWorld().spawnParticle(Particle.WHITE_ASH, loc, 4, 0.3, 0.1, 0.3, 0);
                }
            }
            case CHILL_PILL -> {
                for (EffectType bad : new EffectType[]{EffectType.PARANOIA, EffectType.BAD_TRIP, EffectType.DIZZY}) {
                    effects.remove(p, bad);
                }
                for (PotionEffectType pt : new PotionEffectType[]{PotionEffectType.NAUSEA, PotionEffectType.DARKNESS}) {
                    PotionEffect cur = p.getPotionEffect(pt);
                    if (cur != null && cur.isAmbient()) {
                        p.removePotionEffect(pt);
                    }
                }
                if (random.nextInt(5) == 0) {
                    p.getWorld().spawnParticle(Particle.DUST, head.clone().add(0, 0.5, 0), 3, 0.4, 0.2, 0.4, 0,
                            new Particle.DustOptions(Color.fromRGB(0x9AE8C8), 1f));
                }
            }
            case BEER_GOGGLES -> {
                effects.potion(p, PotionEffectType.HERO_OF_THE_VILLAGE, 1);
                if (random.nextInt(12) == 0) {
                    p.sendActionBar(Text.mm("<gold>" + pick("everyone looks so good tonight", "*hic*",
                            "you love these villagers", "best. trade. ever.")));
                }
            }
            case BALLOON -> {
                if (s.balloon > 0) {
                    s.balloon--;
                } else if (!p.isOnGround()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, true, false, false));
                }
                p.getWorld().spawnParticle(Particle.DUST, head.clone().add(0, 0.9, 0), 3, 0.15, 0.15, 0.15, 0,
                        new Particle.DustOptions(Color.fromRGB(0xFF8AC8), 1.6f));
                if (random.nextInt(5) == 0) {
                    p.getWorld().playSound(loc, "minecraft:entity.puffer_fish.blow_up", SoundCategory.PLAYERS, 0.5f, 2f);
                }
            }
            default -> {
            }
        }
    }

    /** Five times a second: wings, gliding, trails, elves, sprint hunger. */
    void fast(Player p, java.util.Set<EffectType> active) {
        long ticks = Bukkit.getCurrentTick() / 4;
        State s = state(p);
        // flowers and colours fade back to normal
        while (!s.fakes.isEmpty() && s.fakes.peek().until() < Bukkit.getCurrentTick()) {
            restore(p, s.fakes.poll());
        }
        if (active.contains(EffectType.NOSE_CANDY) && p.isSprinting()) {
            p.setExhaustion(0);
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 0.2, 0), 2, 0.2, 0.05, 0.2, 0,
                    new Particle.DustOptions(Color.WHITE, 0.9f));
        }
        if (active.contains(EffectType.QUICK_STEP) && p.isSprinting()) {
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 0.1, 0), 2, 0.2, 0, 0.2, 0.02);
        }
        if (active.contains(EffectType.FAIRY_WINGS)) {
            if (p.isOnGround()) {
                s.airJumped = false;
                if (!s.wings) {
                    wings(p, s, true);
                }
            }
            if (s.airJumped && random.nextInt(2) == 0) {
                p.getWorld().spawnParticle(Particle.WAX_ON, p.getLocation().add(0, 0.8, 0), 2, 0.3, 0.3, 0.3, 0.2);
            }
        }
        if (active.contains(EffectType.ANGEL_WINGS) && !p.isOnGround() && !p.isFlying() && !p.isGliding()
                && !p.isInWater() && !p.isSneaking() && p.getVelocity().getY() < -0.3) {
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() > 0.01) {
                p.setVelocity(dir.normalize().multiply(0.55).setY(-0.14));
                p.setFallDistance(0);
                Vector back = dir.clone().multiply(-0.4);
                Location wing = p.getLocation().add(back).add(0, 1.2, 0);
                p.getWorld().spawnParticle(Particle.END_ROD, wing, 2, 0.5, 0.15, 0.5, 0.01);
            }
        }
        if (active.contains(EffectType.POPPY_TRAIL) && p.isOnGround()) {
            Block feet = p.getLocation().getBlock();
            if (!feet.equals(s.lastStep)) {
                s.lastStep = feet;
                for (BlockFace f : new BlockFace[]{BlockFace.SELF, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
                        BlockFace.WEST}) {
                    Block b = feet.getRelative(f);
                    if (b.getType().isAir() && Tag.DIRT.isTagged(b.getRelative(BlockFace.DOWN).getType())
                            && random.nextInt(f == BlockFace.SELF ? 1 : 3) == 0) {
                        fake(p, s, b, FLOWERS[random.nextInt(FLOWERS.length)], 8);
                    }
                }
            }
        }
        if (active.contains(EffectType.MACHINE_ELVES)) {
            int i = 0;
            for (UUID id : s.companions) {
                Entity e = Bukkit.getEntity(id);
                if (e instanceof Allay) {
                    double ang = ticks * 0.12 + i * Math.PI * 2 / 3;
                    Location at = p.getEyeLocation().add(Math.cos(ang) * 2.2, 0.3 + Math.sin(ticks * 0.2 + i) * 0.4,
                            Math.sin(ang) * 2.2);
                    at.setYaw((float) Math.toDegrees(Math.atan2(-(p.getX() - at.getX()), p.getZ() - at.getZ())));
                    e.teleport(at);
                    i++;
                }
            }
        }
    }

    private void rave(Player p) {
        Location loc = p.getLocation();
        float pitch = new float[]{0.6f, 0.8f, 0.6f, 1.0f}[(Bukkit.getCurrentTick() / 20) % 4];
        p.getWorld().playSound(loc, "minecraft:block.note_block.basedrum", SoundCategory.PLAYERS, 0.8f, pitch);
        p.getWorld().playSound(loc, "minecraft:block.note_block.hat", SoundCategory.PLAYERS, 0.5f, 1.6f);
        for (int i = 0; i < 10; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            Location at = loc.clone().add(Math.cos(ang) * 2, 0.4 + random.nextDouble() * 1.8, Math.sin(ang) * 2);
            Color c = Color.fromRGB(java.awt.Color.HSBtoRGB(random.nextFloat(), 0.9f, 1f) & 0xFFFFFF);
            p.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1.5f));
        }
        for (Entity e : p.getNearbyEntities(6, 3, 6)) {
            if (e instanceof Animals a && a.isOnGround()) {
                a.setVelocity(a.getVelocity().setY(0.38));
            }
        }
    }

    /** A grass / dirt / stone / sand block on the surface near l, or null. */
    private Block surface(Location l, int r) {
        int x = l.getBlockX() + random.nextInt(-r, r + 1);
        int z = l.getBlockZ() + random.nextInt(-r, r + 1);
        for (int dy = 2; dy >= -3; dy--) {
            Block b = l.getWorld().getBlockAt(x, l.getBlockY() + dy, z);
            if (b.getType().isOccluding() && b.getRelative(BlockFace.UP).getType().isAir()) {
                return b;
            }
        }
        return null;
    }

    /** Vine Sight: every ore within 8 blocks sparkles in its colour (only for the user). */
    private void oreSight(Player p) {
        Location l = p.getLocation();
        int shown = 0;
        for (int dx = -8; dx <= 8 && shown < 60; dx++) {
            for (int dy = -6; dy <= 6 && shown < 60; dy++) {
                for (int dz = -8; dz <= 8 && shown < 60; dz++) {
                    Block b = l.getWorld().getBlockAt(l.getBlockX() + dx, l.getBlockY() + dy, l.getBlockZ() + dz);
                    Integer c = oreColor(b.getType());
                    if (c != null) {
                        p.spawnParticle(Particle.DUST, b.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0,
                                new Particle.DustOptions(Color.fromRGB(c), 1.3f));
                        shown++;
                    }
                }
            }
        }
    }

    private static Integer oreColor(Material m) {
        return switch (m) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> 0x3A3A3A;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> 0xD8A880;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> 0xE8743A;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> 0xFFD83A;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> 0xFF2A2A;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> 0x2A4AFF;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> 0x5AF8F0;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> 0x3AE85A;
            case NETHER_QUARTZ_ORE -> 0xF4F0E8;
            default -> null;
        };
    }

    private void ring(Player p, Location centre, Vector normal, double r, Particle particle, int points) {
        Vector n = normal.clone().normalize();
        Vector a = n.getCrossProduct(new Vector(0, 1, 0));
        if (a.lengthSquared() < 1e-4) {
            a = new Vector(1, 0, 0);
        }
        a.normalize();
        Vector b = n.getCrossProduct(a).normalize();
        for (int i = 0; i < points; i++) {
            double ang = i * Math.PI * 2 / points;
            Vector off = a.clone().multiply(Math.cos(ang) * r).add(b.clone().multiply(Math.sin(ang) * r));
            p.getWorld().spawnParticle(particle, centre.clone().add(off), 1, 0, 0, 0, 0);
        }
    }

    @SafeVarargs
    private <T> T pick(T... options) {
        return options[random.nextInt(options.length)];
    }

    // ------------------------------------------------------------------
    // events
    // ------------------------------------------------------------------

    /** Fairy Wings: the jump key in mid-air is a second jump. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        State s = states.get(p.getUniqueId());
        if (s == null || !s.wings || !e.isFlying() || !effects.has(p, EffectType.FAIRY_WINGS)) {
            return;
        }
        e.setCancelled(true);
        p.setFlying(false);
        p.setAllowFlight(false);
        s.wings = false;
        p.getPersistentDataContainer().remove(wingsKey);
        if (s.airJumped) {
            return;
        }
        s.airJumped = true;
        Vector dir = p.getLocation().getDirection().setY(0);
        Vector v = (dir.lengthSquared() > 0.01 ? dir.normalize().multiply(0.45) : new Vector()).setY(0.72);
        p.setVelocity(v);
        p.setFallDistance(0);
        p.getWorld().spawnParticle(Particle.WAX_ON, p.getLocation().add(0, 0.6, 0), 20, 0.4, 0.2, 0.4, 0.6);
        p.getWorld().playSound(p.getLocation(), "minecraft:block.amethyst_block.resonate", SoundCategory.PLAYERS, 1f, 1.6f);
    }

    /** Cactus Skin, Numb and Bounce. Damage from other players is never changed. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !states.containsKey(p.getUniqueId())) {
            return;
        }
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (effects.has(p, EffectType.CACTUS_SKIN)
                && (cause == EntityDamageEvent.DamageCause.CONTACT || cause == EntityDamageEvent.DamageCause.HOT_FLOOR)) {
            e.setCancelled(true);
            return;
        }
        if (cause == EntityDamageEvent.DamageCause.FALL && effects.has(p, EffectType.BOUNCE)) {
            float dist = p.getFallDistance();
            e.setCancelled(true);
            if (dist > 4) {
                double up = Math.min(1.3, 0.35 + dist * 0.045);
                Bukkit.getScheduler().runTask(plugin, () -> p.setVelocity(p.getVelocity().setY(up)));
                p.getWorld().playSound(p.getLocation(), "minecraft:block.slime_block.fall", SoundCategory.PLAYERS, 1f, 1f);
                p.getWorld().spawnParticle(Particle.ITEM_SLIME, p.getLocation(), 14, 0.4, 0.1, 0.4, 0.05);
            }
            return;
        }
        if (effects.has(p, EffectType.NUMB) && !fromPlayer(e)) {
            e.setDamage(e.getDamage() * 0.65);
        }
    }

    private static boolean fromPlayer(EntityDamageEvent e) {
        if (!(e instanceof EntityDamageByEntityEvent by)) {
            return false;
        }
        Entity d = by.getDamager();
        if (d instanceof Player) {
            return true;
        }
        return d instanceof Projectile pr && pr.getShooter() instanceof Player;
    }

    /** Sweet Tooth: food heals too. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (e.getItem().getType().isEdible() && effects.has(p, EffectType.SWEET_TOOTH)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                var max = p.getAttribute(Attribute.MAX_HEALTH);
                if (max != null && !p.isDead()) {
                    p.setHealth(Math.min(max.getValue(), p.getHealth() + 4));
                }
                p.getWorld().spawnParticle(Particle.HEART, p.getEyeLocation().add(0, 0.5, 0), 3, 0.3, 0.2, 0.3, 0);
            });
        }
    }

    /** A crash or restart while Fairy Wings was on: take the flight away again. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.getPersistentDataContainer().has(wingsKey, PersistentDataType.BYTE)
                && !effects.has(p, EffectType.FAIRY_WINGS)) {
            p.getPersistentDataContainer().remove(wingsKey);
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                p.setFlying(false);
                p.setAllowFlight(false);
            }
        }
    }

    /** For the self test: a companion entity count. */
    public int companions(Player p) {
        State s = states.get(p.getUniqueId());
        return s == null ? 0 : s.companions.size();
    }

    /** A living companion never counts as a real mob (self test, cleanup). */
    public static boolean isCompanion(LivingEntity e) {
        return "companion".equals(e.getPersistentDataContainer().get(Keys.VISUAL, PersistentDataType.STRING));
    }
}
