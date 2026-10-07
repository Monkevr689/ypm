package dev.kushcraft.effect;

import dev.kushcraft.KushCraft;
import dev.kushcraft.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Runs every custom effect once per second: vanilla potion pieces, sounds,
 * particles, the high meter and the boss bar HUD.
 */
public final class EffectManager {

    private static final int MAX_SECONDS = 900;
    private static final int REFRESH_TICKS = 50;

    private final KushCraft plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private final Random random = new Random();
    private final Signatures signatures;

    public EffectManager(KushCraft plugin) {
        this.plugin = plugin;
        this.signatures = new Signatures(plugin, this);
    }

    public Signatures signatures() {
        return signatures;
    }

    private static final class State {
        final Map<EffectType, Integer> effects = new EnumMap<>(EffectType.class);
        final List<Pending> pending = new ArrayList<>();
        double high;
        BossBar bar;
        long tripTime = -1;
        Location last;
    }

    private record Pending(Dose dose, int[] secondsLeft) {
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(signatures, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::fast, 4L, 4L);
    }

    /** Five times a second: Frosty freezes water underfoot, Magnetic pulls items in, signatures move. */
    private void fast() {
        for (Map.Entry<UUID, State> e : states.entrySet()) {
            State s = e.getValue();
            boolean frosty = s.effects.containsKey(EffectType.FROSTY);
            boolean magnetic = s.effects.containsKey(EffectType.MAGNETIC);
            java.util.Set<EffectType> sigs = java.util.EnumSet.noneOf(EffectType.class);
            for (EffectType t : s.effects.keySet()) {
                if (t.signature()) {
                    sigs.add(t);
                }
            }
            if (!frosty && !magnetic && sigs.isEmpty()) {
                continue;
            }
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || p.isDead()) {
                continue;
            }
            if (!sigs.isEmpty()) {
                signatures.fast(p, sigs);
            }
            if (frosty) {
                freeze(p);
            }
            if (magnetic) {
                for (org.bukkit.entity.Entity en : p.getNearbyEntities(7, 4, 7)) {
                    if (en instanceof org.bukkit.entity.Item item && item.getPickupDelay() <= 0 && item.isValid()) {
                        Vector to = p.getLocation().add(0, 0.6, 0).toVector().subtract(item.getLocation().toVector());
                        if (to.lengthSquared() > 0.5) {
                            item.setVelocity(to.normalize().multiply(0.32));
                        }
                    }
                }
            }
        }
    }

    /** Frost Walker: still water around your feet turns to frosted ice (it melts by itself). */
    private void freeze(Player p) {
        Location l = p.getLocation();
        org.bukkit.block.Block under = l.getBlock().getRelative(0, -1, 0);
        if (p.isInWater() || under.getType().isAir()) {
            return;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5) {
                    continue;
                }
                org.bukkit.block.Block b = under.getRelative(dx, 0, dz);
                if (b.getType() == org.bukkit.Material.WATER && b.getBlockData() instanceof org.bukkit.block.data.Levelled lv
                        && lv.getLevel() == 0 && b.getRelative(0, 1, 0).getType().isAir()
                        && dev.kushcraft.util.Protection.canBuild(p, b)) {
                    b.setType(org.bukkit.Material.FROSTED_ICE);
                }
            }
        }
    }

    /** Green Thumb: a player with it stands within 10 blocks. */
    public boolean greenThumbNear(Location l) {
        if (l == null || l.getWorld() == null) {
            return false;
        }
        for (Map.Entry<UUID, State> e : states.entrySet()) {
            if (!e.getValue().effects.containsKey(EffectType.GREEN_THUMB)) {
                continue;
            }
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && p.getWorld().equals(l.getWorld()) && p.getLocation().distanceSquared(l) <= 100) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    public void apply(Player p, Dose dose) {
        State s = states.computeIfAbsent(p.getUniqueId(), k -> new State());
        if (dose.delaySeconds() > 0) {
            s.pending.add(new Pending(dose, new int[]{dose.delaySeconds()}));
            return;
        }
        applyNow(p, s, dose);
    }

    private void applyNow(Player p, State s, Dose dose) {
        boolean chill = s.effects.containsKey(EffectType.CHILL_PILL) || dose.effects().containsKey(EffectType.CHILL_PILL);
        for (Map.Entry<EffectType, Integer> e : dose.effects().entrySet()) {
            EffectType t = e.getKey();
            if (chill && (t == EffectType.PARANOIA || t == EffectType.BAD_TRIP || t == EffectType.DIZZY)) {
                continue; // a Chill Pill keeps you calm
            }
            boolean fresh = !s.effects.containsKey(t);
            s.effects.merge(t, e.getValue(), (a, b) -> Math.min(MAX_SECONDS, a + b));
            if (t.signature()) {
                signatures.start(p, t, fresh);
            }
        }
        s.high += dose.high();
        if (!dose.effects().isEmpty()) {
            plugin.awards().high(p, s.effects.size());
        }
        if (dose.effects().containsKey(EffectType.BAD_TRIP)) {
            plugin.awards().badTrip(p);
        }
        double limit = plugin.getConfig().getDouble("effects.green-out-at", 100);
        if (s.high >= limit && limit > 0) {
            s.high = limit * 0.7;
            s.effects.merge(EffectType.GREEN_OUT, 25, (a, b) -> Math.min(60, a + b));
            plugin.awards().greenOut(p);
            p.sendMessage(Text.msg("<green><bold>You greened out!</bold></green> <gray>Way too much... take it easy."));
            p.playSound(p.getLocation(), "minecraft:entity.player.hurt_sweet_berry_bush", SoundCategory.PLAYERS, 1f, 0.6f);
        }
        updateBar(p, s);
    }

    public boolean has(Player p, EffectType type) {
        State s = states.get(p.getUniqueId());
        return s != null && s.effects.containsKey(type);
    }

    /** Chemist: the owner of a Drug Lab is on meth right now (their labs cook faster). */
    public boolean chemist(UUID owner) {
        Player p = owner == null ? null : Bukkit.getPlayer(owner);
        return p != null && has(p, EffectType.CHEMIST);
    }

    /** Ends one effect right away. */
    void remove(Player p, EffectType type) {
        State s = states.get(p.getUniqueId());
        if (s != null && s.effects.remove(type) != null) {
            onEnd(p, type);
        }
    }

    /** A vanilla potion piece (for signatures). */
    void potion(Player p, PotionEffectType type, int amplifier) {
        pot(p, type, amplifier);
    }

    /** Copy of the active effects (seconds left) for menus. */
    public Map<EffectType, Integer> active(Player p) {
        State s = states.get(p.getUniqueId());
        return s == null ? Map.of() : new EnumMap<>(s.effects);
    }

    public int pending(Player p) {
        State s = states.get(p.getUniqueId());
        return s == null ? 0 : s.pending.size();
    }

    public double high(Player p) {
        State s = states.get(p.getUniqueId());
        return s == null ? 0 : s.high;
    }

    public void clear(Player p) {
        State s = states.remove(p.getUniqueId());
        if (s != null) {
            for (EffectType t : s.effects.keySet()) {
                onEnd(p, t);
            }
            if (s.bar != null) {
                p.hideBossBar(s.bar);
            }
        }
        signatures.clear(p);
    }

    public void quit(Player p) {
        signatures.clear(p);
        State s = states.get(p.getUniqueId());
        if (s != null && s.bar != null) {
            p.hideBossBar(s.bar);
        }
        if (s != null && s.tripTime >= 0) {
            p.resetPlayerTime();
            s.tripTime = -1;
        }
    }

    public void join(Player p) {
        State s = states.get(p.getUniqueId());
        if (s != null) {
            updateBar(p, s);
            // back online: signatures switch their attributes, flight and weather back on
            for (EffectType t : s.effects.keySet()) {
                if (t.signature()) {
                    signatures.start(p, t, false);
                }
            }
        }
    }

    public void shutdown() {
        signatures.shutdown();
        for (Map.Entry<UUID, State> e : states.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                quit(p);
            }
        }
        states.clear();
    }

    // ------------------------------------------------------------------
    // ticking
    // ------------------------------------------------------------------

    private void tick() {
        Iterator<Map.Entry<UUID, State>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> entry = it.next();
            Player p = Bukkit.getPlayer(entry.getKey());
            State s = entry.getValue();
            if (p == null || !p.isOnline()) {
                continue; // keep state until they come back
            }
            // delayed doses (edibles)
            for (Iterator<Pending> pi = s.pending.iterator(); pi.hasNext(); ) {
                Pending pd = pi.next();
                if (--pd.secondsLeft()[0] <= 0) {
                    pi.remove();
                    if (pd.dose().kickIn() != null) {
                        p.sendMessage(Text.msg(pd.dose().kickIn()));
                    }
                    applyNow(p, s, pd.dose());
                }
            }
            List<EffectType> ended = new ArrayList<>();
            for (Map.Entry<EffectType, Integer> e : s.effects.entrySet()) {
                int left = e.getValue() - 1;
                if (left <= 0) {
                    ended.add(e.getKey());
                } else {
                    e.setValue(left);
                    tickEffect(p, s, e.getKey(), left);
                }
            }
            for (EffectType t : ended) {
                s.effects.remove(t);
                onEnd(p, t);
                if (t == EffectType.HYPER) {
                    s.effects.put(EffectType.CRASH, 60);
                    p.sendMessage(Text.msg("<gray>The rush fades... <dark_gray>here comes the crash."));
                }
                if (t == EffectType.TRIPPY && s.tripTime >= 0) {
                    p.resetPlayerTime();
                    s.tripTime = -1;
                }
            }
            s.high = Math.max(0, s.high - 0.4);
            if (s.effects.isEmpty() && s.pending.isEmpty() && s.high <= 0) {
                if (s.bar != null) {
                    p.hideBossBar(s.bar);
                }
                it.remove();
                continue;
            }
            updateBar(p, s);
        }
    }

    private void pot(Player p, PotionEffectType type, int amplifier) {
        pot(p, type, amplifier, REFRESH_TICKS);
    }

    private void pot(Player p, PotionEffectType type, int amplifier, int ticks) {
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null && (cur.getAmplifier() > amplifier || (!cur.isAmbient() && cur.getDuration() > ticks))) {
            return; // don't fight real potions / beacons
        }
        p.addPotionEffect(new PotionEffect(type, ticks, amplifier, true, false, false));
    }

    private void onEnd(Player p, EffectType t) {
        if (t.signature()) {
            signatures.end(p, t);
        }
        for (PotionEffectType type : potions(t)) {
            PotionEffect cur = p.getPotionEffect(type);
            if (cur != null && cur.isAmbient() && cur.getDuration() <= 300) {
                p.removePotionEffect(type);
            }
        }
    }

    private static PotionEffectType[] potions(EffectType t) {
        return switch (t) {
            case GIGGLES -> new PotionEffectType[]{PotionEffectType.LUCK};
            case MUNCHIES -> new PotionEffectType[]{PotionEffectType.HUNGER};
            case COUCH_LOCK -> new PotionEffectType[]{PotionEffectType.SLOWNESS};
            case ENERGY -> new PotionEffectType[]{PotionEffectType.SPEED, PotionEffectType.HASTE};
            case EUPHORIA -> new PotionEffectType[]{PotionEffectType.REGENERATION};
            case CREATIVE -> new PotionEffectType[]{PotionEffectType.HASTE};
            case FLOATY -> new PotionEffectType[]{PotionEffectType.JUMP_BOOST, PotionEffectType.SLOW_FALLING};
            case PARANOIA -> new PotionEffectType[]{PotionEffectType.DARKNESS};
            case SLEEPY -> new PotionEffectType[]{PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE};
            case FOCUS -> new PotionEffectType[]{PotionEffectType.NIGHT_VISION, PotionEffectType.LUCK};
            case PAIN_RELIEF -> new PotionEffectType[0];
            case TRIPPY -> new PotionEffectType[]{PotionEffectType.NAUSEA};
            case HYPER -> new PotionEffectType[]{PotionEffectType.SPEED, PotionEffectType.HASTE,
                    PotionEffectType.JUMP_BOOST};
            case GLOW -> new PotionEffectType[]{PotionEffectType.GLOWING, PotionEffectType.NIGHT_VISION,
                    PotionEffectType.LEVITATION};
            case CRASH -> new PotionEffectType[]{PotionEffectType.WEAKNESS, PotionEffectType.SLOWNESS,
                    PotionEffectType.HUNGER, PotionEffectType.MINING_FATIGUE};
            case GREEN_OUT -> new PotionEffectType[]{PotionEffectType.NAUSEA, PotionEffectType.SLOWNESS,
                    PotionEffectType.BLINDNESS};
            case LUCKY -> new PotionEffectType[]{PotionEffectType.LUCK};
            case NIGHT_OWL -> new PotionEffectType[]{PotionEffectType.NIGHT_VISION};
            case AQUATIC -> new PotionEffectType[]{PotionEffectType.WATER_BREATHING, PotionEffectType.DOLPHINS_GRACE};
            case FIREPROOF -> new PotionEffectType[]{PotionEffectType.FIRE_RESISTANCE};
            case GHOST -> new PotionEffectType[0];
            case LOVED_UP -> new PotionEffectType[]{PotionEffectType.REGENERATION};
            case VISIONS -> new PotionEffectType[]{PotionEffectType.NIGHT_VISION};
            case RAGE -> new PotionEffectType[]{PotionEffectType.HASTE, PotionEffectType.HUNGER};
            case DIZZY -> new PotionEffectType[]{PotionEffectType.NAUSEA};
            case ZEN -> new PotionEffectType[]{PotionEffectType.REGENERATION};
            case GREEN_THUMB, SMOOTH_TALKER, FROSTY, MAGNETIC, SIXTH_SENSE -> new PotionEffectType[0];
            case DISSOCIATED -> new PotionEffectType[]{PotionEffectType.SLOWNESS, PotionEffectType.SLOW_FALLING,
                    PotionEffectType.BLINDNESS};
            case SYRUPY -> new PotionEffectType[]{PotionEffectType.SLOWNESS, PotionEffectType.SLOW_FALLING};
            case BAD_TRIP -> new PotionEffectType[]{PotionEffectType.DARKNESS, PotionEffectType.HUNGER,
                    PotionEffectType.WEAKNESS};
            case TWEAKING -> new PotionEffectType[]{PotionEffectType.HASTE};
            case BEER_GOGGLES -> new PotionEffectType[]{PotionEffectType.HERO_OF_THE_VILLAGE};
            case BALLOON -> new PotionEffectType[]{PotionEffectType.LEVITATION, PotionEffectType.SLOW_FALLING};
            case CLOUD_CHASER -> new PotionEffectType[]{PotionEffectType.SLOW_FALLING};
            default -> new PotionEffectType[0];
        };
    }

    private void tickEffect(Player p, State s, EffectType t, int left) {
        if (t.signature()) {
            signatures.tick(p, t, left);
            return;
        }
        Location head = p.getEyeLocation();
        boolean nausea = plugin.getConfig().getBoolean("effects.nausea", true);
        switch (t) {
            case GIGGLES -> {
                pot(p, PotionEffectType.LUCK, 0);
                if (random.nextInt(9) == 0) {
                    p.getWorld().playSound(p.getLocation(), "minecraft:entity.witch.celebrate", SoundCategory.PLAYERS,
                            0.55f, 1.5f + random.nextFloat() * 0.3f);
                    p.getWorld().spawnParticle(Particle.NOTE, head.clone().add(0, 0.6, 0), 3, 0.3, 0.2, 0.3, 1);
                    if (random.nextInt(4) == 0) {
                        p.sendActionBar(Text.mm(pick("<yellow>*giggles*", "<yellow>hehehe...",
                                "<yellow>*snort* hahaha", "<yellow>why is everything so funny")));
                    }
                }
            }
            case MUNCHIES -> pot(p, PotionEffectType.HUNGER, 0);
            case COUCH_LOCK -> {
                pot(p, PotionEffectType.SLOWNESS, 1);
                if (left % 8 == 0 && p.getFoodLevel() < 20) {
                    p.setFoodLevel(p.getFoodLevel() + 1);
                }
            }
            case ENERGY -> {
                pot(p, PotionEffectType.SPEED, 1);
                pot(p, PotionEffectType.HASTE, 0);
            }
            case EUPHORIA -> {
                pot(p, PotionEffectType.REGENERATION, 0);
                if (random.nextInt(6) == 0) {
                    p.getWorld().spawnParticle(Particle.HEART, head.clone().add(0, 0.5, 0), 1, 0.3, 0.2, 0.3, 0);
                }
            }
            case CREATIVE -> {
                pot(p, PotionEffectType.HASTE, 1);
                if (left % 12 == 0) {
                    p.giveExp(2);
                    p.spawnParticle(Particle.ENCHANT, head, 25, 0.6, 0.5, 0.6, 0.6);
                }
            }
            case FLOATY -> {
                pot(p, PotionEffectType.JUMP_BOOST, 1);
                pot(p, PotionEffectType.SLOW_FALLING, 0);
                if (random.nextInt(4) == 0) {
                    p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 2, 0.3, 0.05, 0.3, 0.01);
                }
            }
            case PARANOIA -> {
                if (random.nextInt(9) == 0) {
                    Vector back = p.getLocation().getDirection().setY(0).normalize().multiply(-3);
                    Location behind = p.getLocation().add(back);
                    String sound = pick("minecraft:entity.creeper.primed", "minecraft:entity.zombie.ambient",
                            "minecraft:entity.skeleton.ambient", "minecraft:entity.spider.ambient",
                            "minecraft:block.wooden_door.open", "minecraft:entity.enderman.stare");
                    p.playSound(behind, sound, SoundCategory.HOSTILE, 0.8f, 1f);
                    if (random.nextInt(3) == 0) {
                        p.sendActionBar(Text.mm(pick("<red>...did you hear that?", "<red>someone is watching you",
                                "<red>don't look behind you", "<red>they know...")));
                    }
                }
                if (random.nextInt(25) == 0) {
                    pot(p, PotionEffectType.DARKNESS, 0, 60);
                }
            }
            case SLEEPY -> {
                pot(p, PotionEffectType.SLOWNESS, 0);
                pot(p, PotionEffectType.MINING_FATIGUE, 0);
                if (left % 6 == 0 && p.getHealth() < p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()) {
                    p.setHealth(Math.min(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),
                            p.getHealth() + 1));
                }
                if (random.nextInt(20) == 0) {
                    p.sendActionBar(Text.mm("<yellow>*yaaawn*"));
                }
            }
            case FOCUS -> {
                pot(p, PotionEffectType.NIGHT_VISION, 0, 260);
                pot(p, PotionEffectType.LUCK, 0);
            }
            case PAIN_RELIEF -> {
                // no fall damage: dev.kushcraft.listener.PlayerListener
                if (left % 8 == 0) {
                    heal(p, 1);
                }
            }
            case TRIPPY -> {
                if (nausea && left % 20 == 0) {
                    pot(p, PotionEffectType.NAUSEA, 0, 140);
                }
                if (s.tripTime < 0) {
                    s.tripTime = p.getWorld().getTime();
                }
                s.tripTime = (s.tripTime + 240) % 24000;
                p.setPlayerTime(s.tripTime, false);
                for (int i = 0; i < 6; i++) {
                    Color c = Color.fromRGB(java.awt.Color.HSBtoRGB(random.nextFloat(), 0.9f, 1f) & 0xFFFFFF);
                    Location l = head.clone().add(random.nextGaussian() * 1.6, random.nextGaussian() * 0.9,
                            random.nextGaussian() * 1.6);
                    p.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1.4f));
                }
            }
            case HYPER -> {
                pot(p, PotionEffectType.SPEED, 2);
                pot(p, PotionEffectType.HASTE, 1);
                pot(p, PotionEffectType.JUMP_BOOST, 0);
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 3, 0.3, 0.5, 0.3, 0.05);
            }
            case GLOW -> {
                pot(p, PotionEffectType.GLOWING, 0);
                pot(p, PotionEffectType.NIGHT_VISION, 0, 260);
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 2, 0.4, 0.6, 0.4, 0.01);
                if (left % 15 == 0) {
                    pot(p, PotionEffectType.LEVITATION, 0, 30);
                }
            }
            case CRASH -> {
                pot(p, PotionEffectType.WEAKNESS, 0);
                pot(p, PotionEffectType.SLOWNESS, 0);
                pot(p, PotionEffectType.HUNGER, 1);
                pot(p, PotionEffectType.MINING_FATIGUE, 0);
            }
            case GREEN_OUT -> {
                if (nausea) {
                    pot(p, PotionEffectType.NAUSEA, 0, 100);
                }
                pot(p, PotionEffectType.SLOWNESS, 1);
                if (left % 5 == 0) {
                    pot(p, PotionEffectType.BLINDNESS, 0, 40);
                }
                p.getWorld().spawnParticle(Particle.SNEEZE, head, 2, 0.2, 0.1, 0.2, 0.01);
            }
            case LUCKY -> {
                pot(p, PotionEffectType.LUCK, 1);
                if (random.nextInt(8) == 0) {
                    p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, head.clone().add(0, 0.4, 0), 3, 0.4, 0.3, 0.4, 0);
                }
            }
            case NIGHT_OWL -> pot(p, PotionEffectType.NIGHT_VISION, 0, 260);
            case AQUATIC -> {
                pot(p, PotionEffectType.WATER_BREATHING, 0);
                pot(p, PotionEffectType.DOLPHINS_GRACE, 0);
                if (p.isInWater() && random.nextInt(3) == 0) {
                    p.getWorld().spawnParticle(Particle.BUBBLE, head, 6, 0.3, 0.3, 0.3, 0.05);
                }
            }
            case FIREPROOF -> {
                pot(p, PotionEffectType.FIRE_RESISTANCE, 0);
                if (random.nextInt(6) == 0) {
                    p.getWorld().spawnParticle(Particle.SMALL_FLAME, p.getLocation().add(0, 0.2, 0), 3, 0.3, 0.1, 0.3, 0.01);
                }
            }
            case GHOST -> {
                // monsters don't notice you: dev.kushcraft.listener.PlayerListener
                if (random.nextInt(5) == 0) {
                    p.getWorld().spawnParticle(Particle.WHITE_ASH, p.getLocation().add(0, 1, 0), 8, 0.4, 0.6, 0.4, 0.01);
                }
            }
            case LOVED_UP -> {
                pot(p, PotionEffectType.REGENERATION, 0);
                if (random.nextInt(3) == 0) {
                    p.getWorld().spawnParticle(Particle.HEART, head.clone().add(0, 0.5, 0), 2, 0.5, 0.3, 0.5, 0);
                }
                if (left % 5 == 0) {
                    for (Player other : p.getWorld().getPlayers()) {
                        if (other != p && other.getLocation().distanceSquared(p.getLocation()) < 36) {
                            pot(other, PotionEffectType.REGENERATION, 0, 110);
                            other.spawnParticle(Particle.HEART, other.getEyeLocation().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0);
                        }
                    }
                }
            }
            case VISIONS -> {
                pot(p, PotionEffectType.NIGHT_VISION, 0, 260);
                double a = (left % 12) / 12.0 * Math.PI * 2;
                for (int i = 0; i < 3; i++) {
                    double ang = a + i * Math.PI * 2 / 3;
                    Location l = head.clone().add(Math.cos(ang) * 1.6, -0.2 + 0.3 * Math.sin(ang * 2), Math.sin(ang) * 1.6);
                    p.spawnParticle(i == 0 ? Particle.SOUL : Particle.END_ROD, l, 1, 0, 0, 0, 0);
                }
                if (random.nextInt(14) == 0) {
                    p.playSound(p.getLocation(), pick("minecraft:ambient.soul_sand_valley.mood",
                            "minecraft:ambient.basalt_deltas.additions", "minecraft:block.beacon.ambient"),
                            SoundCategory.AMBIENT, 0.6f, 0.8f);
                    p.sendActionBar(Text.mm(pick("<light_purple>the spirits are talking...",
                            "<light_purple>you see the code of the world", "<light_purple>everything is connected")));
                }
            }
            case RAGE -> {
                pot(p, PotionEffectType.HASTE, 2);
                pot(p, PotionEffectType.HUNGER, 0);
                if (random.nextInt(4) == 0) {
                    p.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, head.clone().add(0, 0.4, 0), 1, 0.3, 0.2, 0.3, 0);
                }
                if (left % 4 == 0) {
                    p.playSound(p.getLocation(), "minecraft:block.note_block.basedrum", SoundCategory.PLAYERS, 0.5f, 0.6f);
                }
            }
            case DIZZY -> {
                if (nausea && left % 15 == 0) {
                    pot(p, PotionEffectType.NAUSEA, 0, 120);
                }
                if (random.nextInt(10) == 0) {
                    Location l = p.getLocation();
                    l.setYaw(l.getYaw() + (random.nextBoolean() ? 25 : -25));
                    p.setRotation(l.getYaw(), l.getPitch());
                }
            }
            case GREEN_THUMB -> {
                if (random.nextInt(3) == 0) {
                    p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 0.2, 0), 3, 0.5, 0.1, 0.5, 0);
                }
                if (random.nextInt(25) == 0) {
                    p.sendActionBar(Text.mm(pick("<green>you can hear the plants growing...",
                            "<green>the leaves lean towards you", "<green>grow, little buds, grow")));
                }
            }
            case SMOOTH_TALKER -> {
                if (random.nextInt(8) == 0) {
                    p.getWorld().spawnParticle(Particle.NOTE, head.clone().add(0, 0.5, 0), 1, 0.3, 0.1, 0.3, 1);
                }
                if (random.nextInt(30) == 0) {
                    p.sendActionBar(Text.mm(pick("<gold>you could sell sand in a desert",
                            "<gold>every word is pure gold", "<gold>the dealers love you today")));
                }
            }
            case FROSTY -> {
                p.setFreezeTicks(0);
                if (random.nextInt(2) == 0) {
                    p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 0.3, 0), 4, 0.4, 0.2, 0.4, 0.01);
                }
            }
            case MAGNETIC -> {
                if (random.nextInt(3) == 0) {
                    p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 8, 0.4, 0.5, 0.4, 0.3);
                }
            }
            case SIXTH_SENSE -> {
                if (left % 2 == 0) {
                    for (org.bukkit.entity.Entity en : p.getNearbyEntities(24, 12, 24)) {
                        if (en instanceof org.bukkit.entity.Monster m) {
                            m.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 50, 0, true, false, false));
                        }
                    }
                }
                if (random.nextInt(20) == 0) {
                    p.sendActionBar(Text.mm(pick("<light_purple>you can feel them out there...",
                            "<light_purple>nothing can sneak up on you", "<light_purple>eyes everywhere")));
                }
            }
            case ZEN -> {
                Location now = p.getLocation();
                boolean still = s.last != null && s.last.getWorld().equals(now.getWorld())
                        && s.last.distanceSquared(now) < 0.01;
                s.last = now;
                if (still) {
                    pot(p, PotionEffectType.REGENERATION, 1);
                    p.getWorld().spawnParticle(Particle.ENCHANT, head, 10, 0.6, 0.4, 0.6, 0.4);
                }
            }
            case DISSOCIATED -> {
                pot(p, PotionEffectType.SLOWNESS, 1);
                pot(p, PotionEffectType.SLOW_FALLING, 0);
                if (left % 9 == 0) {
                    pot(p, PotionEffectType.BLINDNESS, 0, 30);
                }
                if (random.nextInt(10) == 0) {
                    p.sendActionBar(Text.mm(pick("<gray>you're watching yourself from above...",
                            "<gray>is this your body?", "<gray>everything is very far away")));
                }
                p.spawnParticle(Particle.REVERSE_PORTAL, head, 6, 0.6, 0.6, 0.6, 0.01);
            }
            case SYRUPY -> {
                pot(p, PotionEffectType.SLOWNESS, 0);
                pot(p, PotionEffectType.SLOW_FALLING, 0);
                if (random.nextInt(8) == 0) {
                    p.playSound(p.getLocation(), pick("minecraft:block.honey_block.slide", "minecraft:entity.slime.squish",
                            "minecraft:block.bubble_column.whirlpool_ambient"), SoundCategory.PLAYERS, 0.5f, 0.5f);
                }
                p.spawnParticle(Particle.DRIPPING_HONEY, head.clone().add(0, 0.6, 0), 1, 0.3, 0.1, 0.3, 0);
            }
            case BAD_TRIP -> {
                pot(p, PotionEffectType.HUNGER, 0);
                pot(p, PotionEffectType.WEAKNESS, 0);
                if (left % 8 == 0) {
                    pot(p, PotionEffectType.DARKNESS, 0, 80);
                }
                if (random.nextInt(7) == 0) {
                    Vector back = p.getLocation().getDirection().setY(0).normalize().multiply(-2);
                    p.playSound(p.getLocation().add(back), pick("minecraft:entity.warden.heartbeat",
                            "minecraft:ambient.cave", "minecraft:entity.ghast.scream", "minecraft:entity.vex.ambient"),
                            SoundCategory.HOSTILE, 0.8f, 0.7f);
                    p.sendActionBar(Text.mm(pick("<dark_red>make it stop...", "<dark_red>the walls are breathing",
                            "<dark_red>you shouldn't have taken that")));
                }
            }
        }
    }

    private static void heal(Player p, double hp) {
        var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (max != null && p.getHealth() > 0 && p.getHealth() < max.getValue()) {
            p.setHealth(Math.min(max.getValue(), p.getHealth() + hp));
        }
    }

    /** Lucky players sometimes get double drops from natural ores (called by the miner job). */
    public boolean luckyDouble(Player p) {
        return has(p, EffectType.LUCKY) && random.nextDouble() < plugin.getConfig().getDouble("effects.lucky-double-chance", 0.2);
    }

    @SafeVarargs
    private <T> T pick(T... options) {
        return options[random.nextInt(options.length)];
    }

    // ------------------------------------------------------------------
    // HUD
    // ------------------------------------------------------------------

    private void updateBar(Player p, State s) {
        if (!plugin.getConfig().getBoolean("effects.boss-bar", true)) {
            return;
        }
        double limit = Math.max(1, plugin.getConfig().getDouble("effects.green-out-at", 100));
        float progress = (float) Math.max(0, Math.min(1, s.high / limit));
        StringBuilder b = new StringBuilder("<green>☘ High <white>" + (int) Math.round(progress * 100) + "%");
        int shown = 0;
        for (Map.Entry<EffectType, Integer> e : s.effects.entrySet()) {
            if (shown++ >= 4) {
                b.append(" <gray>...");
                break;
            }
            b.append("  <dark_gray>|</dark_gray> ").append(e.getKey().colored()).append(" <gray>")
                    .append(Text.time(e.getValue()));
        }
        if (!s.pending.isEmpty()) {
            b.append("  <dark_gray>|</dark_gray> <gray>kicking in...");
        }
        Component name = Text.mm(b.toString());
        BossBar.Color color = BossBar.Color.GREEN;
        if (s.effects.containsKey(EffectType.TRIPPY) || s.effects.containsKey(EffectType.GLOW)
                || s.effects.containsKey(EffectType.VISIONS) || s.effects.containsKey(EffectType.DISSOCIATED)) {
            color = BossBar.Color.PURPLE;
        }
        if (s.effects.containsKey(EffectType.GREEN_OUT) || s.effects.containsKey(EffectType.CRASH)
                || s.effects.containsKey(EffectType.HYPER) || s.effects.containsKey(EffectType.RAGE)
                || s.effects.containsKey(EffectType.BAD_TRIP)) {
            color = BossBar.Color.RED;
        }
        if (s.bar == null) {
            s.bar = BossBar.bossBar(name, progress, color, BossBar.Overlay.NOTCHED_10);
            p.showBossBar(s.bar);
        } else {
            s.bar.name(name);
            s.bar.progress(progress);
            s.bar.color(color);
            p.showBossBar(s.bar);
        }
    }
}
