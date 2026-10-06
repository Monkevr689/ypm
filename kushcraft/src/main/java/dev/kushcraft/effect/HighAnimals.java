package dev.kushcraft.effect;

import dev.kushcraft.KushCraft;
import dev.kushcraft.Keys;
import dev.kushcraft.catalog.Catalog;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Right-click an animal with a joint, an edible or any other drug and it
 * gets high: bloodshot red eyes (two little red dots on its face), and it
 * slows right down - or gets the zoomies on uppers, or sees colours on
 * psychedelics. It wears off after a minute or two.
 */
public final class HighAnimals implements Listener {

    private record High(long until, Catalog.Category kind) {
    }

    private final KushCraft plugin;
    private final Map<UUID, High> high = new HashMap<>();
    private final Map<UUID, Long> fedAt = new HashMap<>();
    private final Color red = Color.fromRGB(230, 20, 24);

    public HighAnimals(KushCraft plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 3L, 3L);
    }

    /** True right after this player gave an animal something (so they don't use it themselves too). */
    public boolean justFed(Player p) {
        Long t = fedAt.get(p.getUniqueId());
        return t != null && System.currentTimeMillis() - t < 400;
    }

    public boolean isHigh(Entity e) {
        High h = high.get(e.getUniqueId());
        return h != null && h.until() > System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Animals animal)
                || !plugin.getConfig().getBoolean("animals.enabled", true)) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemType t = Items.type(hand);
        if (t == null || !t.isDrug() || !p.hasPermission("kushcraft.use")) {
            return;
        }
        e.setCancelled(true);
        fedAt.put(p.getUniqueId(), System.currentTimeMillis());
        if (isHigh(animal)) {
            p.sendActionBar(Text.mm("<gray>It's already high as a kite."));
            return;
        }
        Catalog.Entry entry = Catalog.of(t);
        Catalog.Category kind = entry == null ? Catalog.Category.WEED : entry.category();
        if (p.getGameMode() != GameMode.CREATIVE) {
            takeOne(p, hand, t);
        }
        makeHigh(animal, kind, plugin.getConfig().getInt("animals.seconds", 90));
        String name = Text.titleCase(animal.getType().name());
        p.sendActionBar(Text.mm("<red>The " + name.toLowerCase(java.util.Locale.ROOT)
                + "'s eyes go red... <gray>it's high now."));
        plugin.awards().partyAnimal(p);
    }

    /** Gets an animal high for this many seconds (also used by /kush selftest). */
    public void makeHigh(LivingEntity animal, Catalog.Category kind, int seconds) {
        long until = System.currentTimeMillis() + seconds * 1000L;
        high.put(animal.getUniqueId(), new High(until, kind));
        animal.getPersistentDataContainer().set(Keys.HIGH, PersistentDataType.STRING, until + ":" + kind.name());
        int ticks = seconds * 20;
        switch (kind) {
            case UPPERS -> {
                animal.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 2, true, false));
                animal.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, 1, true, false));
            }
            case DOWNERS -> animal.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 3, true, false));
            default -> animal.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 1, true, false));
        }
        Location head = animal.getEyeLocation();
        animal.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, head, 4, 0.15, 0.1, 0.15, 0.01);
        animal.getWorld().playSound(head, "minecraft:entity.generic.eat", SoundCategory.NEUTRAL, 0.8f, 0.7f);
    }

    /** One hit off a joint / blunt / vape pen, or one of anything else. */
    private static void takeOne(Player p, ItemStack hand, ItemType t) {
        int max = Items.maxHits(t);
        Strain s = Items.strain(hand);
        if (max > 0 && s != null) {
            int hits = Items.hits(hand) <= 0 ? max : Items.hits(hand);
            int left = hits - 1;
            int q = Items.quality(hand);
            if (hand.getAmount() <= 1) {
                p.getInventory().setItemInMainHand(left > 0 ? Items.strainItem(t, s, q, 1, left) : null);
            } else {
                hand.setAmount(hand.getAmount() - 1);
                if (left > 0) {
                    dev.kushcraft.util.InventoryUtil.give(p, Items.strainItem(t, s, q, 1, left));
                }
            }
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        long now = System.currentTimeMillis();
        for (Entity en : e.getEntities()) {
            String v = en.getPersistentDataContainer().get(Keys.HIGH, PersistentDataType.STRING);
            if (v == null) {
                continue;
            }
            try {
                String[] parts = v.split(":");
                long until = Long.parseLong(parts[0]);
                if (until > now) {
                    high.put(en.getUniqueId(), new High(until, Catalog.Category.valueOf(parts[1])));
                } else {
                    en.getPersistentDataContainer().remove(Keys.HIGH);
                }
            } catch (RuntimeException ex) {
                en.getPersistentDataContainer().remove(Keys.HIGH);
            }
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Iterator<Map.Entry<UUID, High>> it = high.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, High> e = it.next();
            Entity en = Bukkit.getEntity(e.getKey());
            if (!(en instanceof LivingEntity le) || !en.isValid()) {
                if (en == null || now > e.getValue().until()) {
                    it.remove();
                }
                continue;
            }
            if (now > e.getValue().until()) {
                le.getPersistentDataContainer().remove(Keys.HIGH);
                it.remove();
                continue;
            }
            redEyes(le);
            Location head = le.getEyeLocation();
            switch (e.getValue().kind()) {
                case PSYCH -> {
                    Color c = Color.fromRGB(java.awt.Color.HSBtoRGB(r.nextFloat(), 0.9f, 1f) & 0xFFFFFF);
                    le.getWorld().spawnParticle(Particle.DUST, head.clone().add(r.nextGaussian() * 0.5, 0.3 + r.nextDouble() * 0.4,
                            r.nextGaussian() * 0.5), 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1f));
                }
                case UPPERS -> {
                    if (r.nextInt(6) == 0) {
                        le.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, head, 2, 0.2, 0.2, 0.2, 0.05);
                    }
                }
                default -> {
                    if (r.nextInt(14) == 0) {
                        le.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, head.clone().add(0, 0.3, 0), 1,
                                0.05, 0.05, 0.05, 0.005);
                    }
                }
            }
            if (r.nextInt(120) == 0) {
                le.getWorld().spawnParticle(Particle.NOTE, head.clone().add(0, 0.6, 0), 1, 0.2, 0.1, 0.2, 1);
            }
        }
    }

    /** Two small red dots where the eyes are: on the front of the head, either side of the middle. */
    private void redEyes(LivingEntity le) {
        Location eye = le.getEyeLocation();
        double yaw = Math.toRadians(le.getLocation().getYaw());
        Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector side = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
        double[] face = face(le);
        double scale = le instanceof Ageable a && !a.isAdult() ? 0.6 : 1.0;
        Location mid = eye.clone().add(forward.clone().multiply(face[0] * scale)).add(0, face[2] * scale, 0);
        Particle.DustOptions dust = new Particle.DustOptions(red, (float) (0.55 * scale + 0.15));
        for (int s = -1; s <= 1; s += 2) {
            Location l = mid.clone().add(side.clone().multiply(s * face[1] * scale));
            le.getWorld().spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, dust);
        }
    }

    /** {how far in front of the body the face is, half the distance between the eyes, height tweak}. */
    private static double[] face(LivingEntity le) {
        return switch (le.getType()) {
            case COW, MOOSHROOM -> new double[]{0.82, 0.14, 0.02};
            case PIG -> new double[]{0.82, 0.13, -0.02};
            case SHEEP -> new double[]{0.78, 0.11, 0.0};
            case CHICKEN -> new double[]{0.3, 0.08, 0.02};
            case HORSE, DONKEY, MULE, SKELETON_HORSE, ZOMBIE_HORSE -> new double[]{0.95, 0.17, -0.05};
            case LLAMA, TRADER_LLAMA, CAMEL -> new double[]{0.62, 0.13, 0.0};
            case GOAT -> new double[]{0.55, 0.12, 0.0};
            case WOLF -> new double[]{0.52, 0.1, 0.02};
            case CAT, OCELOT -> new double[]{0.42, 0.07, 0.0};
            case FOX -> new double[]{0.45, 0.07, 0.0};
            case RABBIT -> new double[]{0.25, 0.06, 0.0};
            case PANDA, POLAR_BEAR -> new double[]{0.9, 0.16, 0.0};
            default -> new double[]{le.getWidth() / 2 + 0.05, Math.max(0.06, le.getWidth() * 0.15), 0.0};
        };
    }
}
