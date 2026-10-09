package dev.smpsuite.data;

import dev.smpsuite.SMPSuite;
import dev.smpsuite.gem.GemType;
import dev.smpsuite.skill.Skill;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * One YAML file per player (players/&lt;uuid&gt;.yml), loaded on join and saved on
 * quit and every few minutes. Offline players are loaded on demand. A small
 * index of everyone's levels is kept in memory for leaderboards and team stats.
 */
public final class PlayerStore {

    /** Levels of a player who may be offline (for /skills top and team stats). */
    public record Summary(String name, int[] levels) {
        public int total() {
            int n = 0;
            for (int l : levels) {
                n += l;
            }
            return n;
        }
    }

    private final SMPSuite plugin;
    private final File dir;
    private final Map<UUID, PlayerData> loaded = new HashMap<>();
    private final Map<UUID, Summary> index = new HashMap<>();

    public PlayerStore(SMPSuite plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "players");
    }

    public void start() {
        dir.mkdirs();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                try {
                    UUID id = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                    PlayerData d = read(id, YamlConfiguration.loadConfiguration(f));
                    index(d);
                } catch (IllegalArgumentException ignored) {
                    // not a player file
                }
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            get(p);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveDirty, 20L * 120, 20L * 120);
    }

    public void stop() {
        for (PlayerData d : loaded.values()) {
            save(d);
        }
    }

    /** An online player's data (loaded on first use). */
    public PlayerData get(Player p) {
        PlayerData d = get(p.getUniqueId());
        if (!p.getName().equals(d.name)) {
            d.name = p.getName();
            d.dirty = true;
            index(d);
        }
        return d;
    }

    public PlayerData get(UUID id) {
        PlayerData d = loaded.get(id);
        if (d == null) {
            File f = file(id);
            d = f.exists() ? read(id, YamlConfiguration.loadConfiguration(f)) : new PlayerData(id);
            loaded.put(id, d);
        }
        return d;
    }

    public boolean known(UUID id) {
        return loaded.containsKey(id) || file(id).exists();
    }

    /** Saves and forgets a player who left. */
    public void unload(UUID id) {
        PlayerData d = loaded.remove(id);
        if (d != null) {
            save(d);
        }
    }

    public Collection<Summary> summaries() {
        return Collections.unmodifiableCollection(index.values());
    }

    public Map<UUID, Summary> index() {
        return Collections.unmodifiableMap(index);
    }

    public void index(PlayerData d) {
        int[] lv = new int[Skill.values().length];
        for (Skill s : Skill.values()) {
            lv[s.ordinal()] = d.level(s);
        }
        index.put(d.id(), new Summary(d.name, lv));
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    private void saveDirty() {
        for (PlayerData d : loaded.values()) {
            if (d.dirty) {
                save(d);
            }
        }
        // offline players loaded for a moment (admin commands, PvP energy) are let go
        loaded.keySet().removeIf(id -> Bukkit.getPlayer(id) == null && !loaded.get(id).dirty);
    }

    public void save(PlayerData d) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", d.name);
        for (Skill s : Skill.values()) {
            y.set("skills." + s.id() + ".level", d.level(s));
            y.set("skills." + s.id() + ".xp", Math.round(d.xp(s) * 100) / 100.0);
        }
        y.set("gem.type", d.gem == null ? null : d.gem.id());
        y.set("gem.energy", d.energy);
        y.set("gem.charge", Math.round(d.charge * 10) / 10.0);
        y.set("gem.serial", d.gemSerial);
        y.set("gem.given", d.gemGiven);
        for (int i = 0; i < d.pockets.length; i++) {
            ItemStack it = d.pockets[i];
            if (it != null && !it.getType().isAir()) {
                y.set("gem.pockets." + i, Base64.getEncoder().encodeToString(it.serializeAsBytes()));
            }
        }
        long now = System.currentTimeMillis();
        d.cooldowns.forEach((k, v) -> {
            if (v > now) {
                y.set("cooldowns." + k, v);
            }
        });
        y.set("pay.earned", Math.round(d.earnedThisHour * 100) / 100.0);
        y.set("pay.since", d.hourStart);
        y.set("settings.action-bar", d.actionBar);
        try {
            dir.mkdirs();
            y.save(file(d.id()));
            d.dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save player " + d.id(), e);
        }
    }

    private PlayerData read(UUID id, YamlConfiguration y) {
        PlayerData d = new PlayerData(id);
        d.name = y.getString("name", "");
        for (Skill s : Skill.values()) {
            d.set(s, y.getInt("skills." + s.id() + ".level"), y.getDouble("skills." + s.id() + ".xp"));
        }
        d.gem = GemType.parse(y.getString("gem.type"));
        d.energy = y.getInt("gem.energy");
        d.charge = y.getDouble("gem.charge");
        d.gemSerial = y.getInt("gem.serial");
        d.gemGiven = y.getBoolean("gem.given", d.gem != null);
        ConfigurationSection pk = y.getConfigurationSection("gem.pockets");
        if (pk != null) {
            for (String k : pk.getKeys(false)) {
                try {
                    int i = Integer.parseInt(k);
                    if (i >= 0 && i < d.pockets.length) {
                        d.pockets[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(pk.getString(k, "")));
                    }
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Could not read a pocket item of " + id + ": " + e.getMessage());
                }
            }
        }
        ConfigurationSection cd = y.getConfigurationSection("cooldowns");
        if (cd != null) {
            for (String k : cd.getKeys(false)) {
                d.cooldowns.put(k, cd.getLong(k));
            }
        }
        d.earnedThisHour = y.getDouble("pay.earned");
        d.hourStart = y.getLong("pay.since");
        d.actionBar = y.getBoolean("settings.action-bar", true);
        d.dirty = false;
        return d;
    }
}
