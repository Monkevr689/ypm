package dev.smpsuite.data;

import dev.smpsuite.gem.GemType;
import dev.smpsuite.skill.Skill;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Everything SMPSuite remembers about one player. */
public final class PlayerData {

    private final UUID id;
    public String name = "";
    final int[] level = new int[Skill.values().length];
    final double[] xp = new double[Skill.values().length];

    // gem
    public GemType gem;
    public int energy;
    /** Skill XP collected towards the next point of energy. */
    public double charge;
    /** Only the gem item with this serial works (lost gems can be replaced, old copies die). */
    public int gemSerial;
    public boolean gemGiven;
    public final ItemStack[] pockets = new ItemStack[9];

    /** Ability id -> when it's ready again (epoch ms). */
    public final Map<String, Long> cooldowns = new HashMap<>();
    /** Ability id -> until when it's running (epoch ms), not saved. */
    public final transient Map<String, Long> active = new HashMap<>();

    // jobs pay this hour
    public double earnedThisHour;
    public long hourStart;

    public boolean actionBar = true;
    public boolean teamChat;
    public boolean dirty;

    public PlayerData(UUID id) {
        this.id = id;
    }

    public UUID id() {
        return id;
    }

    public int level(Skill s) {
        return level[s.ordinal()];
    }

    public double xp(Skill s) {
        return xp[s.ordinal()];
    }

    public void set(Skill s, int lvl, double x) {
        level[s.ordinal()] = Math.max(0, lvl);
        xp[s.ordinal()] = Math.max(0, x);
        dirty = true;
    }

    public int totalLevel() {
        int n = 0;
        for (int l : level) {
            n += l;
        }
        return n;
    }

    public long cooldownLeft(String ability) {
        Long t = cooldowns.get(ability);
        return t == null ? 0 : Math.max(0, (t - System.currentTimeMillis() + 999) / 1000);
    }

    public void cooldown(String ability, long seconds) {
        cooldowns.put(ability, System.currentTimeMillis() + seconds * 1000L);
        dirty = true;
    }

    public boolean isActive(String ability) {
        Long t = active.get(ability);
        return t != null && t > System.currentTimeMillis();
    }

    public void activate(String ability, long seconds) {
        active.put(ability, System.currentTimeMillis() + seconds * 1000L);
    }
}
