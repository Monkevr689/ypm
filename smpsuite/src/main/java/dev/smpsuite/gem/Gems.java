package dev.smpsuite.gem;

import dev.smpsuite.Keys;
import dev.smpsuite.Msg;
import dev.smpsuite.SMPSuite;
import dev.smpsuite.data.PlayerData;
import dev.smpsuite.skill.Skills;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bliss gems: one per player, bound to them, working from the off hand.
 * Energy (0-10) comes mostly from normal survival play - every bit of skill
 * XP charges the gem - and a little from PvP. A gem at 0 energy sleeps until
 * it's recharged by playing, so PvP-avoidant players are never locked out.
 */
public final class Gems {

    public static final String BLOODLUST = "gem-bloodlust";
    public static final String RICH_RUSH = "gem-rich-rush";

    private final SMPSuite plugin;
    private final GemAbilities abilities;
    /** killer>victim -> last time energy changed hands. */
    private final Map<String, Long> pvp = new HashMap<>();
    private int ticks;

    public Gems(SMPSuite plugin) {
        this.plugin = plugin;
        this.abilities = new GemAbilities(plugin, this);
    }

    public GemAbilities abilities() {
        return abilities;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("gems.enabled", true);
    }

    private int cfg(String key, int def) {
        return plugin.getConfig().getInt("gems." + key, def);
    }

    public int maxEnergy() {
        return Math.max(1, cfg("max-energy", 10));
    }

    public int energyFor(boolean secondary) {
        return secondary ? cfg("secondary-energy", 4) : cfg("primary-energy", 2);
    }

    public double vsPlayers() {
        return Math.max(0, plugin.getConfig().getDouble("balance.gem-damage-vs-players", 0.5));
    }

    // ------------------------------------------------------------------
    // the item
    // ------------------------------------------------------------------

    public static boolean isGem(ItemStack it) {
        return it != null && !it.getType().isAir() && it.hasItemMeta()
                && it.getPersistentDataContainer().has(Keys.GEM, PersistentDataType.STRING);
    }

    public static GemType typeOf(ItemStack it) {
        return isGem(it) ? GemType.parse(it.getPersistentDataContainer().get(Keys.GEM, PersistentDataType.STRING)) : null;
    }

    public static UUID ownerOf(ItemStack it) {
        if (!isGem(it)) {
            return null;
        }
        try {
            return UUID.fromString(it.getPersistentDataContainer().getOrDefault(Keys.GEM_OWNER, PersistentDataType.STRING, ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** True when this gem item is the player's current gem (theirs, right type, latest copy). */
    public boolean isValid(Player p, ItemStack it) {
        if (!isGem(it)) {
            return false;
        }
        PlayerData d = plugin.store().get(p);
        Integer serial = it.getPersistentDataContainer().get(Keys.GEM_SERIAL, PersistentDataType.INTEGER);
        return d.gem != null && d.gem == typeOf(it) && p.getUniqueId().equals(ownerOf(it))
                && serial != null && serial == d.gemSerial;
    }

    /** A fresh gem item for a player's current gem. */
    public ItemStack item(PlayerData d) {
        GemType t = d.gem;
        ItemStack it = new ItemStack(t.base());
        it.editMeta(meta -> {
            describe(meta, d);
            meta.setEnchantmentGlintOverride(true);
            meta.setMaxStackSize(1);
            var cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of(t.model()));
            meta.setCustomModelDataComponent(cmd);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(Keys.GEM, PersistentDataType.STRING, t.id());
            pdc.set(Keys.GEM_OWNER, PersistentDataType.STRING, d.id().toString());
            pdc.set(Keys.GEM_SERIAL, PersistentDataType.INTEGER, d.gemSerial);
        });
        return it;
    }

    private void describe(ItemMeta meta, PlayerData d) {
        GemType t = d.gem;
        meta.displayName(Msg.mm("<" + t.hex() + "><bold>" + t.display() + " Gem"));
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Bound to <white>" + Msg.escape(d.name));
        lore.add("<yellow>⚡</yellow> " + Msg.bar(d.energy / (double) maxEnergy(), maxEnergy(), "yellow", "dark_gray")
                + " <white>" + d.energy + "/" + maxEnergy() + (d.energy <= 0 ? " <red>(asleep)" : ""));
        lore.add("");
        lore.add("<gray>Passives <dark_gray>(" + cfg("passive-energy", 1) + "⚡)");
        for (String s : t.passives()) {
            lore.add(" <dark_gray>•</dark_gray> <white>" + s);
        }
        lore.add("<gold>F</gold> <white>" + t.primary() + "</white> <dark_gray>(" + energyFor(false) + "⚡)");
        lore.add(" <gray>" + t.primaryInfo());
        lore.add("<gold>Shift + F</gold> <white>" + t.secondary() + "</white> <dark_gray>("
                + (t == GemType.WEALTH ? "always" : energyFor(true) + "⚡") + ")");
        lore.add(" <gray>" + t.secondaryInfo());
        lore.add("");
        lore.add("<dark_gray>Works in your off hand. Skill XP charges it.");
        meta.lore(Msg.lines(lore));
    }

    /** Rewrites the energy line on the player's gem after a change. */
    public void refreshItems(Player p) {
        PlayerData d = plugin.store().get(p);
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isValid(p, it)) {
                it.editMeta(meta -> describe(meta, d));
                inv.setItem(i, it);
            }
        }
    }

    /** The gem in a player's off hand when it works for them (theirs, with energy), else null. */
    public GemType active(Player p) {
        if (!enabled()) {
            return null;
        }
        ItemStack off = p.getInventory().getItemInOffHand();
        if (!isValid(p, off)) {
            return null;
        }
        PlayerData d = plugin.store().get(p);
        return d.energy >= Math.max(1, cfg("passive-energy", 1)) ? d.gem : null;
    }

    public boolean has(Player p, GemType t) {
        return active(p) == t;
    }

    /** Gives a player a gem item for their current gem (a fresh serial: older copies stop working). */
    public void giveNew(Player p) {
        PlayerData d = plugin.store().get(p);
        if (d.gem == null) {
            return;
        }
        d.gemSerial++;
        d.dirty = true;
        ItemStack gem = item(d);
        if (p.getInventory().getItemInOffHand().getType().isAir()) {
            p.getInventory().setItemInOffHand(gem);
        } else {
            for (ItemStack left : p.getInventory().addItem(gem).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
    }

    /** First join: a random gem. */
    public void firstJoin(Player p) {
        PlayerData d = plugin.store().get(p);
        if (!enabled() || d.gemGiven || d.gem != null || !plugin.getConfig().getBoolean("gems.give-on-first-join", true)) {
            return;
        }
        GemType[] all = GemType.values();
        d.gem = all[ThreadLocalRandom.current().nextInt(all.length)];
        d.energy = Math.min(maxEnergy(), cfg("start-energy", 5));
        d.gemGiven = true;
        giveNew(p);
        Msg.send(p, "You were given the " + d.gem.colored() + " gem! <gray>Keep it in your off hand: "
                + "<white>F</white> and <white>Shift + F</white> use it. <white>/gem</white> tells you more.");
        p.playSound(p.getLocation(), "minecraft:block.amethyst_block.resonate", SoundCategory.PLAYERS, 1f, 1f);
    }

    /** Sets a player's gem (admin, reroll): takes away the old item, gives the new one. */
    public void setGem(Player p, GemType t) {
        PlayerData d = plugin.store().get(p);
        if (d.gem == GemType.WEALTH && t != GemType.WEALTH) {
            returnPockets(p, d);
        }
        removeItems(p);
        d.gem = t;
        d.gemGiven = true;
        giveNew(p);
    }

    /** Removes the player's own gem items (all copies) from their inventory. */
    public void removeItems(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isGem(it) && p.getUniqueId().equals(ownerOf(it))) {
                inv.setItem(i, null);
            }
        }
    }

    private void returnPockets(Player p, PlayerData d) {
        for (int i = 0; i < d.pockets.length; i++) {
            ItemStack it = d.pockets[i];
            if (it != null && !it.getType().isAir()) {
                for (ItemStack left : p.getInventory().addItem(it).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), left);
                }
            }
            d.pockets[i] = null;
        }
        d.dirty = true;
    }

    /** True when the player carries their valid gem anywhere in their inventory. */
    public boolean carries(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isValid(p, it)) {
                return true;
            }
        }
        return isValid(p, p.getItemOnCursor());
    }

    // ------------------------------------------------------------------
    // energy
    // ------------------------------------------------------------------

    /** Skill XP charges the gem: +1 energy every xp-per-energy. */
    public void charge(Player p, double xp) {
        PlayerData d = plugin.store().get(p);
        if (!enabled() || d.gem == null) {
            return;
        }
        if (chargeData(d, xp) > 0) {
            refreshItems(p);
            Msg.send(p, "<yellow>⚡ Your " + d.gem.colored() + " gem charged up from your work! <gray>(" + d.energy
                    + "/" + maxEnergy() + "⚡)");
        }
    }

    /** Adds skill XP to a gem's charge; returns the energy it gained (0 or 1). */
    public int chargeData(PlayerData d, double xp) {
        if (d.energy >= maxEnergy()) {
            d.charge = 0;
            return 0;
        }
        double per = Math.max(1, plugin.getConfig().getDouble("gems.xp-per-energy", 2000));
        d.charge += xp;
        d.dirty = true;
        if (d.charge < per) {
            return 0;
        }
        d.charge -= per;
        d.energy++;
        if (d.energy >= maxEnergy()) {
            d.charge = 0;
        }
        return 1;
    }

    public void addEnergy(Player p, int delta, String why) {
        PlayerData d = plugin.store().get(p);
        int before = d.energy;
        d.energy = Math.max(0, Math.min(maxEnergy(), d.energy + delta));
        d.dirty = true;
        if (d.energy == before) {
            return;
        }
        refreshItems(p);
        if (why != null) {
            Msg.send(p, why + " <gray>(" + d.energy + "/" + maxEnergy() + "⚡)");
        }
        if (d.energy == 0) {
            Msg.send(p, "<red>Your gem fell asleep.</red> <gray>Earn skill XP to wake it up again.");
        }
    }

    public void setEnergy(Player p, int energy) {
        PlayerData d = plugin.store().get(p);
        d.energy = Math.max(0, Math.min(maxEnergy(), energy));
        d.dirty = true;
        refreshItems(p);
    }

    /** PvP: the killer takes a little energy from the victim (not between teammates, once per pair per cooldown). */
    public void pvpKill(Player killer, Player victim) {
        if (!enabled() || plugin.teams().sameTeam(killer, victim)) {
            return;
        }
        String k = killer.getUniqueId() + ">" + victim.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = pvp.get(k);
        if (last != null && now - last < cfg("pvp-cooldown-minutes", 30) * 60_000L) {
            return;
        }
        pvp.put(k, now);
        PlayerData kd = plugin.store().get(killer);
        PlayerData vd = plugin.store().get(victim);
        if (vd.gem != null && cfg("pvp-death-energy", 1) > 0) {
            addEnergy(victim, -cfg("pvp-death-energy", 1), "<red>You lost energy in a fight.</red>");
        }
        if (kd.gem != null && cfg("pvp-kill-energy", 1) > 0) {
            addEnergy(killer, cfg("pvp-kill-energy", 1), "<yellow>⚡ You won energy in a fight.");
        }
    }

    // ------------------------------------------------------------------
    // cooldowns and using abilities
    // ------------------------------------------------------------------

    public static String cooldownKey(GemType t, boolean secondary) {
        return "gem-" + t.id() + "-" + (secondary ? "secondary" : "primary");
    }

    /** A gem ability's cooldown, a little shorter at high energy. */
    public long cooldown(GemType t, boolean secondary, int energy) {
        long base = Math.max(0, plugin.getConfig().getLong("gems.cooldowns." + t.id() + "-"
                + (secondary ? "secondary" : "primary"), 60));
        if (plugin.getConfig().getBoolean("gems.high-energy-cooldown-bonus", true)) {
            if (energy >= 10) {
                return Math.round(base * 0.85);
            }
            if (energy >= 8) {
                return Math.round(base * 0.90);
            }
        }
        return base;
    }

    /** F / Shift + F with the gem in the off hand (or /gem use). */
    public void use(Player p, boolean secondary) {
        PlayerData d = plugin.store().get(p);
        if (!enabled()) {
            p.sendActionBar(Msg.mm("<red>Gems are turned off on this server."));
            return;
        }
        ItemStack off = p.getInventory().getItemInOffHand();
        if (!isValid(p, off)) {
            p.sendActionBar(Msg.mm(isGem(off) ? "<red>That gem isn't yours (or it's an old copy). <gray>/gem recover"
                    : "<gray>Hold your gem in your off hand."));
            return;
        }
        GemType t = d.gem;
        boolean pockets = t == GemType.WEALTH && secondary;
        int need = pockets ? 0 : energyFor(secondary);
        if (d.energy < need) {
            p.sendActionBar(Msg.mm("<red>Needs " + need + "⚡ energy <gray>(you have " + d.energy
                    + ") - skill XP recharges your gem"));
            return;
        }
        String key = cooldownKey(t, secondary);
        long left = d.cooldownLeft(key);
        if (left > 0) {
            p.sendActionBar(Msg.mm("<gray>" + (secondary ? t.secondary() : t.primary()) + " is ready in <white>"
                    + Msg.time(left)));
            return;
        }
        if (abilities.run(p, t, secondary) && !pockets) {
            d.cooldown(key, cooldown(t, secondary, d.energy));
        }
    }

    // ------------------------------------------------------------------
    // passives
    // ------------------------------------------------------------------

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::passives, 40L, 40L);
    }

    private void passives() {
        ticks++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            GemType g = active(p);
            Skills.setModifier(p, Attribute.KNOCKBACK_RESISTANCE, Keys.MOD_GEM_STRENGTH, g == GemType.STRENGTH ? 0.2 : 0);
            if (g == null) {
                continue;
            }
            switch (g) {
                case FIRE -> effect(p, PotionEffectType.FIRE_RESISTANCE, 0);
                case SPEED -> {
                    effect(p, PotionEffectType.SPEED, 0);
                    if (p.isInWater()) {
                        effect(p, PotionEffectType.DOLPHINS_GRACE, 0);
                    }
                }
                case WEALTH -> {
                    effect(p, PotionEffectType.LUCK, 0);
                    effect(p, PotionEffectType.HERO_OF_THE_VILLAGE, 0);
                }
                case FLUX -> {
                    if (p.isInWaterOrRain()) {
                        effect(p, PotionEffectType.CONDUIT_POWER, 0);
                    }
                }
                case LIFE -> {
                    if (ticks % 2 == 0) {
                        growCrops(p);
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void effect(Player p, PotionEffectType type, int amp) {
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null && (cur.getAmplifier() > amp || cur.getDuration() > 60)) {
            return; // a stronger or longer effect (a potion, an ability) stays as it is
        }
        p.addPotionEffect(new PotionEffect(type, 100, amp, true, false, true));
    }

    /** Life gem: a few growing crops near the player grow one stage. */
    private void growCrops(Player p) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Block c = p.getLocation().getBlock();
        int grown = 0;
        for (int i = 0; i < 16 && grown < 3; i++) {
            Block b = c.getRelative(r.nextInt(13) - 6, r.nextInt(5) - 2, r.nextInt(13) - 6);
            if (Tag.CROPS.isTagged(b.getType()) && b.getBlockData() instanceof Ageable a && a.getAge() < a.getMaximumAge()) {
                a.setAge(a.getAge() + 1);
                b.setBlockData(a);
                b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0);
                grown++;
            }
        }
    }

    // ------------------------------------------------------------------
    // hooks for the skills (one bonus each, never stacked)
    // ------------------------------------------------------------------

    /** Strength gem: +1 melee damage against mobs (added before the shared cap). */
    public double strengthBonus(Player p) {
        return has(p, GemType.STRENGTH) ? 1.0 : 0;
    }

    public boolean fireSmelt(Player p) {
        return has(p, GemType.FIRE);
    }

    public boolean wealthBonus(Player p) {
        return has(p, GemType.WEALTH);
    }

    public boolean richRush(Player p) {
        return has(p, GemType.WEALTH) && plugin.store().get(p).isActive(RICH_RUSH);
    }

    public boolean bloodlust(Player p) {
        return has(p, GemType.STRENGTH) && plugin.store().get(p).isActive(BLOODLUST);
    }

    public Material baseOf(GemType t) {
        return t.base();
    }
}
