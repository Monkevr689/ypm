package dev.smpsuite.skill;

import dev.smpsuite.Keys;
import dev.smpsuite.Msg;
import dev.smpsuite.SMPSuite;
import dev.smpsuite.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Skill XP, the levelling curve, the capped passive bonuses and jobs pay.
 * Every bonus is read through one method here so the caps can't be skipped.
 */
public final class Skills {

    private final SMPSuite plugin;
    private final Map<Skill, Map<Material, Double>> blockXp = new EnumMap<>(Skill.class);
    private final Map<EntityType, Double> mobXp = new EnumMap<>(EntityType.class);
    private final Map<String, Double> specialXp = new HashMap<>();
    private final TreeMap<Integer, Double> hearts = new TreeMap<>();
    private final Map<UUID, Feedback> feedback = new HashMap<>();

    private int maxLevel;
    private double base;
    private double exponent;

    /** What the action bar shows next (several XP gains in one tick add up). */
    private static final class Feedback {
        Skill skill;
        double xp;
        double money;
        boolean due;
    }

    public Skills(SMPSuite plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // config
    // ------------------------------------------------------------------

    public void load() {
        ConfigurationSection c = cfg();
        maxLevel = Math.max(1, c.getInt("max-level", 50));
        base = Math.max(1, c.getDouble("curve.base", 40));
        exponent = Math.max(0.5, c.getDouble("curve.exponent", 1.1));
        blockXp.clear();
        mobXp.clear();
        specialXp.clear();
        for (Skill s : new Skill[]{Skill.MINING, Skill.FARMING, Skill.FORAGING, Skill.EXCAVATION}) {
            Map<Material, Double> map = new EnumMap<>(Material.class);
            ConfigurationSection sec = c.getConfigurationSection("xp." + s.id());
            if (sec != null) {
                for (String k : sec.getKeys(false)) {
                    double v = sec.getDouble(k);
                    if (k.startsWith("#")) {
                        Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS,
                                NamespacedKey.minecraft(k.substring(1).toLowerCase(Locale.ROOT)), Material.class);
                        if (tag == null) {
                            plugin.getLogger().warning("skills.xp." + s.id() + ": unknown block tag " + k);
                            continue;
                        }
                        for (Material m : tag.getValues()) {
                            map.putIfAbsent(m, v);
                        }
                        continue;
                    }
                    Material m = Material.matchMaterial(k);
                    if (m != null && m.isBlock()) {
                        map.put(m, v);
                    } else if (s == Skill.FARMING) {
                        specialXp.put("farming." + k, v); // breed, shear
                    } else {
                        plugin.getLogger().warning("skills.xp." + s.id() + ": unknown block " + k);
                    }
                }
            }
            blockXp.put(s, map);
        }
        ConfigurationSection fish = c.getConfigurationSection("xp.fishing");
        if (fish != null) {
            for (String k : fish.getKeys(false)) {
                specialXp.put("fishing." + k, fish.getDouble(k));
            }
        }
        ConfigurationSection combat = c.getConfigurationSection("xp.combat");
        if (combat != null) {
            for (String k : combat.getKeys(false)) {
                if (k.startsWith("default-") || k.equals("player")) {
                    specialXp.put("combat." + k, combat.getDouble(k));
                    continue;
                }
                try {
                    mobXp.put(EntityType.valueOf(k.toUpperCase(Locale.ROOT)), combat.getDouble(k));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("skills.xp.combat: unknown mob " + k);
                }
            }
        }
        hearts.clear();
        ConfigurationSection h = c.getConfigurationSection("bonuses.vitality.hearts");
        if (h != null) {
            for (String k : h.getKeys(false)) {
                try {
                    hearts.put(Integer.parseInt(k), h.getDouble(k));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("skills.bonuses.vitality.hearts: " + k + " is not a level");
                }
            }
        }
    }

    private ConfigurationSection cfg() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("skills");
        return c != null ? c : plugin.getConfig().createSection("skills");
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("skills.enabled", true);
    }

    public int maxLevel() {
        return maxLevel;
    }

    // ------------------------------------------------------------------
    // XP tables
    // ------------------------------------------------------------------

    /** XP for breaking this block with this skill's table (0 when it isn't in it). */
    public double blockXp(Skill s, Material m) {
        Map<Material, Double> map = blockXp.get(s);
        return map == null ? 0 : map.getOrDefault(m, 0.0);
    }

    /** The skill a block belongs to (mining, farming, foraging or excavation), or null. */
    public Skill skillOf(Material m) {
        for (Skill s : new Skill[]{Skill.MINING, Skill.FORAGING, Skill.EXCAVATION, Skill.FARMING}) {
            if (blockXp(s, m) > 0) {
                return s;
            }
        }
        return null;
    }

    public double special(String key) {
        return specialXp.getOrDefault(key, 0.0);
    }

    /** Combat XP for killing this mob (listed, else the hostile / animal default). */
    public double mobXp(EntityType t, boolean hostile) {
        Double v = mobXp.get(t);
        if (v != null) {
            return v;
        }
        return special(hostile ? "combat.default-hostile" : "combat.default-animal");
    }

    // ------------------------------------------------------------------
    // the curve
    // ------------------------------------------------------------------

    public double curveMultiplier(Skill s) {
        return Math.max(0.1, plugin.getConfig().getDouble("skills.curve-multiplier." + s.id(), 1.0));
    }

    /** XP needed to go from this level to the next. */
    public double xpToNext(Skill s, int level) {
        return Math.round(base * Math.pow(level + 1, exponent) * curveMultiplier(s));
    }

    /** All XP needed to reach a level from 0. */
    public double totalXpFor(Skill s, int level) {
        double t = 0;
        for (int l = 0; l < level; l++) {
            t += xpToNext(s, l);
        }
        return t;
    }

    // ------------------------------------------------------------------
    // bonuses (all capped)
    // ------------------------------------------------------------------

    private double capped(String path, int level) {
        double per = plugin.getConfig().getDouble("skills.bonuses." + path + "-per-level", 0);
        double cap = plugin.getConfig().getDouble("skills.bonuses." + path + "-cap", 0);
        return Math.max(0, Math.min(cap, per * Math.min(level, maxLevel)));
    }

    public double miningEfficiency(int level) {
        return capped("mining.efficiency", level);
    }

    /** Chance (0..1) that a block of this skill drops double. */
    public double doubleDrop(Skill s, int level) {
        return switch (s) {
            case MINING -> capped("mining.double-drop", level);
            case FARMING -> capped("farming.double-drop", level);
            case FORAGING -> capped("foraging.double-drop", level);
            case EXCAVATION -> capped("excavation.double-drop", level);
            default -> 0;
        };
    }

    public double fasterBites(int level) {
        return capped("fishing.faster-bites", level);
    }

    public double doubleCatch(int level) {
        return capped("fishing.double-catch", level);
    }

    /** Flat bonus melee damage from Foraging (before the PvP halving and the global cap). */
    public double foragingDamage(int level) {
        return capped("foraging.damage", level);
    }

    /** Bonus damage share from Combat (before the PvP halving and the global cap). */
    public double combatPercent(int level) {
        return capped("combat.damage-percent", level);
    }

    /** Extra hearts from Vitality at this level (before the max-hearts cap). */
    public double vitalityHearts(int level) {
        var e = hearts.floorEntry(Math.min(level, maxLevel));
        return e == null ? 0 : Math.max(0, e.getValue());
    }

    /** The next Vitality level that adds hearts, or -1. */
    public int nextHeartLevel(int level) {
        Integer k = hearts.higherKey(level);
        return k == null || k > maxLevel ? -1 : k;
    }

    public double maxHearts() {
        return Math.max(10, plugin.getConfig().getDouble("balance.max-hearts", 12));
    }

    /** Puts the mining efficiency and Vitality hearts on the player (idempotent). */
    public void applyAttributes(Player p) {
        PlayerData d = plugin.store().get(p);
        boolean on = enabled();
        setModifier(p, Attribute.MINING_EFFICIENCY, Keys.MOD_MINING, on ? miningEfficiency(d.level(Skill.MINING)) : 0);
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp == null) {
            return;
        }
        removeModifier(hp, Keys.MOD_VITALITY);
        double without = hp.getValue();
        double want = on ? vitalityHearts(d.level(Skill.VITALITY)) * 2 : 0;
        // hard ceiling over everything: never above max-hearts in total
        double allowed = Math.max(0, maxHearts() * 2 - without);
        double amount = Math.min(want, allowed);
        if (amount > 0) {
            hp.addModifier(new AttributeModifier(Keys.MOD_VITALITY, amount, AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ANY));
        }
    }

    public static void setModifier(Player p, Attribute a, NamespacedKey key, double amount) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null) {
            return;
        }
        AttributeModifier old = inst.getModifier(key);
        if (old != null && Math.abs(old.getAmount() - amount) < 1e-9) {
            return;
        }
        removeModifier(inst, key);
        if (amount != 0) {
            inst.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ANY));
        }
    }

    private static void removeModifier(AttributeInstance inst, NamespacedKey key) {
        if (inst.getModifier(key) != null) {
            inst.removeModifier(key);
        }
    }

    // ------------------------------------------------------------------
    // gaining XP
    // ------------------------------------------------------------------

    /**
     * Gives skill XP from playing. Unless shared (a teammate's share): Vitality
     * gets its share, the gem charges, teammates nearby get theirs and the job pays.
     */
    public void addXp(Player p, Skill s, double amount, boolean shared) {
        if (!enabled() || amount <= 0 || p.getGameMode() == org.bukkit.GameMode.CREATIVE
                || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }
        PlayerData d = plugin.store().get(p);
        gain(p, d, s, amount);
        Feedback f = feedback.computeIfAbsent(p.getUniqueId(), k -> new Feedback());
        if (f.skill != s) {
            f.skill = s;
            f.xp = 0;
            f.money = 0;
        }
        f.xp += amount;
        f.due = true;
        if (shared) {
            return;
        }
        if (s.direct()) {
            double share = plugin.getConfig().getDouble("skills.vitality-share", 0.15);
            if (share > 0) {
                gain(p, d, Skill.VITALITY, amount * share);
            }
            f.money += pay(p, d, s, amount);
            plugin.teams().shareXp(p, s, amount);
        }
        plugin.gems().charge(p, amount);
    }

    /** Adds XP to one skill and handles level-ups. */
    private void gain(Player p, PlayerData d, Skill s, double amount) {
        int level = d.level(s);
        double xp = d.xp(s) + amount;
        int before = level;
        while (level < maxLevel && xp >= xpToNext(s, level)) {
            xp -= xpToNext(s, level);
            level++;
        }
        if (level >= maxLevel) {
            xp = 0;
        }
        d.set(s, level, xp);
        if (level > before) {
            levelUp(p, d, s, before, level);
        }
    }

    /** Sets a level directly (admin), keeping bonuses in step. */
    public void setLevel(Player p, Skill s, int level) {
        PlayerData d = plugin.store().get(p);
        d.set(s, Math.max(0, Math.min(maxLevel, level)), 0);
        plugin.store().index(d);
        applyAttributes(p);
    }

    private void levelUp(Player p, PlayerData d, Skill s, int from, int to) {
        plugin.store().index(d);
        applyAttributes(p);
        p.showTitle(net.kyori.adventure.title.Title.title(Msg.mm(s.color() + "<bold>" + s.display() + " " + to),
                Msg.mm("<gray>level up!"), net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(200),
                        java.time.Duration.ofMillis(1500), java.time.Duration.ofMillis(400))));
        p.playSound(p.getLocation(), "minecraft:entity.player.levelup", SoundCategory.PLAYERS, 0.8f, 1.2f);
        StringBuilder b = new StringBuilder(s.colored() + " <white>" + from + " → <bold>" + to + "</bold></white> ");
        String bonus = bonusLine(s, to);
        if (!bonus.isEmpty()) {
            b.append("<dark_gray>· <gray>").append(bonus);
        }
        Msg.send(p, b.toString());
        String ability = s.ability();
        if (ability != null) {
            int unlock = plugin.abilities().unlockLevel(ability);
            if (from < unlock && to >= unlock) {
                Msg.send(p, "<gold>Unlocked " + s.abilityName() + "!</gold> <gray>" + plugin.abilities().howTo(s));
            }
        }
        if (s == Skill.VITALITY && vitalityHearts(to) > vitalityHearts(from)) {
            Msg.send(p, "<red>❤</red> <gray>You have <white>" + Msg.num(10 + Math.min(vitalityHearts(to), maxHearts() - 10))
                    + "</white> hearts now.");
        }
    }

    /** Short description of what a skill's bonus is at this level. */
    public String bonusLine(Skill s, int level) {
        return switch (s) {
            case MINING -> "+" + Msg.num(miningEfficiency(level)) + " mining speed, " + Msg.pct(doubleDrop(s, level))
                    + " double ores";
            case FARMING -> Msg.pct(doubleDrop(s, level)) + " double crops";
            case FISHING -> Msg.pct(fasterBites(level)) + " faster bites, " + Msg.pct(doubleCatch(level)) + " double catch";
            case FORAGING -> "+" + Msg.num(foragingDamage(level)) + " damage, " + Msg.pct(doubleDrop(s, level))
                    + " double logs";
            case EXCAVATION -> Msg.pct(doubleDrop(s, level)) + " double drops";
            case COMBAT -> "+" + Msg.pct(combatPercent(level)) + " damage";
            case VITALITY -> "+" + Msg.num(Math.min(vitalityHearts(level), maxHearts() - 10)) + " hearts";
        };
    }

    // ------------------------------------------------------------------
    // jobs pay
    // ------------------------------------------------------------------

    public double payRate(Skill s) {
        return Math.max(0, plugin.getConfig().getDouble("pay.per-xp." + s.id(), 0));
    }

    public double payLevelBonus(int level) {
        double per = plugin.getConfig().getDouble("pay.level-bonus-per-level", 0.01);
        double cap = plugin.getConfig().getDouble("pay.level-bonus-cap", 0.5);
        return Math.max(0, Math.min(cap, per * level));
    }

    /** What this XP pays (before the hourly cap). */
    public double payFor(Player p, Skill s, int level, double xp) {
        double pay = xp * payRate(s) * (1 + payLevelBonus(level));
        if (p != null && plugin.gems().wealthBonus(p)) {
            pay *= 1.10;
        }
        return pay;
    }

    private double pay(Player p, PlayerData d, Skill s, double xp) {
        if (!plugin.getConfig().getBoolean("pay.enabled", true) || !plugin.money().available()) {
            return 0;
        }
        double amount = payFor(p, s, d.level(s), xp);
        double cap = plugin.getConfig().getDouble("pay.hourly-cap", 5000);
        long now = System.currentTimeMillis();
        if (now - d.hourStart > 3_600_000L) {
            d.hourStart = now;
            d.earnedThisHour = 0;
        }
        if (cap > 0) {
            amount = Math.min(amount, Math.max(0, cap - d.earnedThisHour));
        }
        if (amount < 0.005) {
            return 0;
        }
        amount = Math.round(amount * 100) / 100.0;
        if (plugin.money().deposit(p, amount)) {
            d.earnedThisHour += amount;
            d.dirty = true;
            return amount;
        }
        return 0;
    }

    /** Money left before this hour's cap (for the menu). */
    public double payLeftThisHour(PlayerData d) {
        double cap = plugin.getConfig().getDouble("pay.hourly-cap", 5000);
        if (cap <= 0) {
            return Double.MAX_VALUE;
        }
        if (System.currentTimeMillis() - d.hourStart > 3_600_000L) {
            return cap;
        }
        return Math.max(0, cap - d.earnedThisHour);
    }

    // ------------------------------------------------------------------
    // the action bar
    // ------------------------------------------------------------------

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::flushFeedback, 10L, 10L);
        // other plugins can change max health: keep the hearts cap honest
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                applyAttributes(p);
            }
        }, 600L, 600L);
    }

    private void flushFeedback() {
        if (!plugin.getConfig().getBoolean("skills.action-bar", true)) {
            feedback.clear();
            return;
        }
        for (var it = feedback.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Feedback f = e.getValue();
            if (!f.due) {
                it.remove();
                continue;
            }
            f.due = false;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            PlayerData d = plugin.store().get(p);
            if (!d.actionBar) {
                continue;
            }
            Skill s = f.skill;
            int lv = d.level(s);
            String progress = lv >= maxLevel ? "MAX" : Msg.pct(d.xp(s) / xpToNext(s, lv));
            String money = f.money >= 0.01 ? " <dark_gray>·</dark_gray> <gold>+" + plugin.money().format(f.money) : "";
            p.sendActionBar(Msg.mm(s.color() + "+" + Msg.num(f.xp) + " " + s.display() + " XP</" + s.color().substring(1)
                    + " <gray>Lv " + lv + " (" + progress + ")" + money));
        }
    }

    public void quit(UUID id) {
        feedback.remove(id);
    }
}
