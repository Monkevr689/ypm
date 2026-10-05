package dev.kushcraft.strain;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effect.EffectType;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/** Loads strains from strains.yml and stores strains created in game. */
public final class StrainRegistry {

    private final KushCraft plugin;
    private final File file;
    private final Map<String, Strain> strains = new LinkedHashMap<>();
    private YamlConfiguration yaml;

    public StrainRegistry(KushCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "strains.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("strains.yml", false);
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        strains.clear();
        ConfigurationSection sec = yaml.getConfigurationSection("strains");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                try {
                    strains.put(id, read(id, s));
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Skipping broken strain '" + id + "': " + e.getMessage());
                }
            }
        }
        if (strains.isEmpty()) {
            plugin.getLogger().warning("strains.yml has no strains - adding OG Kush so the plugin keeps working.");
            strains.put("og_kush", new Strain("og_kush", "OG Kush", StrainType.HYBRID, 0xA8E05A, 20,
                    List.of(EffectType.GIGGLES, EffectType.MUNCHIES, EffectType.FOCUS), List.of(), null, null));
        }
        plugin.getLogger().info("Loaded " + strains.size() + " strains.");
    }

    private Strain read(String id, ConfigurationSection s) {
        String name = s.getString("name", id);
        StrainType type = StrainType.parse(s.getString("type", "HYBRID"));
        int color = parseColor(s.getString("color", "#7AD04A"));
        int potency = s.getInt("potency", 18);
        List<EffectType> effects = new ArrayList<>();
        for (String e : s.getStringList("effects")) {
            EffectType t = EffectType.parse(e);
            if (t != null && !effects.contains(t)) {
                effects.add(t);
            }
        }
        List<String> biomes = new ArrayList<>();
        for (String b : s.getStringList("wild-biomes")) {
            biomes.add(b.toLowerCase(Locale.ROOT).replace("minecraft:", ""));
        }
        UUID creator = null;
        String c = s.getString("creator");
        if (c != null) {
            try {
                creator = UUID.fromString(c);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new Strain(id, name, type, color, potency, effects, biomes, creator, s.getString("creator-name"));
    }

    public static int parseColor(String s) {
        try {
            return Integer.parseInt(s.trim().replace("#", ""), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0x7AD04A;
        }
    }

    public Strain get(String id) {
        return id == null ? null : strains.get(id);
    }

    public Strain getOrDefault(String id) {
        Strain s = get(id);
        return s != null ? s : strains.values().iterator().next();
    }

    public Collection<Strain> all() {
        return Collections.unmodifiableCollection(strains.values());
    }

    public Strain byName(String name) {
        for (Strain s : strains.values()) {
            if (s.name().equalsIgnoreCase(name) || s.id().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    public int countCreatedBy(UUID player) {
        int n = 0;
        for (Strain s : strains.values()) {
            if (player.equals(s.creator())) {
                n++;
            }
        }
        return n;
    }

    /** The landrace you find by breaking grass in this biome. */
    public Strain wildFor(Block block) {
        String biome = block.getBiome().getKey().getKey();
        List<Strain> matches = new ArrayList<>();
        for (Strain s : strains.values()) {
            if (s.wildBiomes().contains(biome)) {
                matches.add(s);
            }
        }
        if (!matches.isEmpty()) {
            return matches.get(ThreadLocalRandom.current().nextInt(matches.size()));
        }
        // no explicit match: pick a non-custom strain whose type likes this climate
        Climate climate = Climate.of(block);
        for (Strain s : strains.values()) {
            if (!s.isCustom() && s.type().climate() == climate && !s.wildBiomes().isEmpty()) {
                matches.add(s);
            }
        }
        if (matches.isEmpty()) {
            for (Strain s : strains.values()) {
                if (!s.isCustom()) {
                    matches.add(s);
                }
            }
        }
        if (matches.isEmpty()) {
            matches.addAll(strains.values());
        }
        return matches.get(ThreadLocalRandom.current().nextInt(matches.size()));
    }

    public boolean nameTaken(String name) {
        for (Strain s : strains.values()) {
            if (s.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** Registers a strain bred by a player and saves strains.yml. */
    public Strain create(String name, StrainType type, int color, int potency, List<EffectType> effects,
                         UUID creator, String creatorName) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "strain";
        }
        String id = base;
        int n = 2;
        while (strains.containsKey(id)) {
            id = base + "_" + n++;
        }
        Strain s = new Strain(id, name, type, color, potency, effects, List.of(), creator, creatorName);
        strains.put(id, s);
        ConfigurationSection sec = yaml.getConfigurationSection("strains");
        if (sec == null) {
            sec = yaml.createSection("strains");
        }
        ConfigurationSection out = sec.createSection(id);
        out.set("name", name);
        out.set("type", type.name());
        out.set("color", String.format(Locale.ROOT, "#%06X", color));
        out.set("potency", s.potency());
        List<String> eff = new ArrayList<>();
        for (EffectType e : effects) {
            eff.add(e.name());
        }
        out.set("effects", eff);
        out.set("wild-biomes", List.of());
        out.set("creator", creator.toString());
        out.set("creator-name", creatorName);
        save();
        return s;
    }

    /** Renames a strain (id stays the same, so existing seeds/buds keep working). */
    public Strain rename(Strain old, String newName) {
        Strain s = new Strain(old.id(), newName, old.type(), old.color(), old.potency(), old.effects(),
                old.wildBiomes(), old.creator(), old.creatorName());
        strains.put(s.id(), s);
        ConfigurationSection sec = yaml.getConfigurationSection("strains." + s.id());
        if (sec != null) {
            sec.set("name", newName);
            save();
        }
        return s;
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save strains.yml", e);
        }
    }
}
