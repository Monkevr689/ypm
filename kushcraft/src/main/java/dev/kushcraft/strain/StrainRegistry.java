package dev.kushcraft.strain;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effect.EffectType;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    /** strains.yml files older than this get the new built-in strains and looks added. */
    static final int FILE_VERSION = 3;

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
        upgrade();
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
            strains.put("og_kush", new Strain("og_kush", "OG Kush", StrainType.HYBRID,
                    new Look(0x8FD14F, Look.DEFAULT_LEAF, Look.DEFAULT_PISTIL, BudShape.CLASSIC, Exotic.NONE),
                    Climate.TEMPERATE, 20, List.of(EffectType.GIGGLES, EffectType.MUNCHIES, EffectType.FOCUS),
                    List.of(), 1, "Pine", 25, true, null, null));
        }
        plugin.getLogger().info("Loaded " + strains.size() + " strains.");
    }

    /**
     * Brings an older strains.yml up to date: adds the built-in strains it is
     * missing and fills in the new looks, climates, flavours and prices of the
     * built-in ones. Player strains and anything you changed are kept.
     */
    private void upgrade() {
        int version = yaml.getInt("version", 1);
        if (version >= FILE_VERSION) {
            return;
        }
        InputStream in = plugin.getResource("strains.yml");
        if (in == null) {
            return;
        }
        YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        ConfigurationSection defs = def.getConfigurationSection("strains");
        ConfigurationSection mine = yaml.getConfigurationSection("strains");
        if (mine == null) {
            mine = yaml.createSection("strains");
        }
        int added = 0;
        if (defs != null) {
            for (String id : defs.getKeys(false)) {
                ConfigurationSection d = defs.getConfigurationSection(id);
                ConfigurationSection m = mine.getConfigurationSection(id);
                if (d == null) {
                    continue;
                }
                if (m == null) {
                    mine.createSection(id, d.getValues(false));
                    added++;
                    continue;
                }
                if (m.isString("creator")) {
                    continue; // a player's strain that happens to have the same id
                }
                for (String key : d.getKeys(false)) {
                    if (!m.isSet(key) || key.equals("wild-biomes")) {
                        m.set(key, d.get(key));
                    }
                }
            }
        }
        yaml.set("version", FILE_VERSION);
        save();
        plugin.getLogger().info("Updated strains.yml: " + added + " new strains, new looks and climates."
                + " Your own strains were kept.");
    }

    private Strain read(String id, ConfigurationSection s) {
        String name = s.getString("name", id);
        StrainType type = StrainType.parse(s.getString("type", "HYBRID"));
        int color = parseColor(s.getString("color", "#7AD04A"));
        int potency = s.getInt("potency", 18);
        List<EffectType> effects = new ArrayList<>();
        for (String e : s.getStringList("effects")) {
            EffectType t = EffectType.parse(e);
            if (t != null && t.selectable() && !effects.contains(t)) {
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
        Look legacy = Look.legacy(color, type, id);
        Look look = new Look(color,
                s.isString("leaf") ? parseColor(s.getString("leaf")) : legacy.leaf(),
                s.isString("pistil") ? parseColor(s.getString("pistil")) : legacy.pistil(),
                BudShape.parse(s.getString("shape"), legacy.shape()),
                Exotic.parse(s.getString("exotic")));
        return new Strain(id, name, type, look, Climate.parse(s.getString("climate"), type.defaultClimate()), potency,
                effects, biomes, s.getDouble("wild-weight", 1.0), s.getString("flavor", ""), s.getDouble("price", 0),
                s.getBoolean("shop", true), creator, s.getString("creator-name"));
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

    /** Built-in strains sold as seeds, cheapest first. */
    public List<Strain> shopStrains() {
        List<Strain> out = new ArrayList<>();
        for (Strain s : strains.values()) {
            if (s.inShop()) {
                out.add(s);
            }
        }
        out.sort((a, b) -> a.seedPrice() != b.seedPrice() ? Double.compare(a.seedPrice(), b.seedPrice())
                : a.name().compareToIgnoreCase(b.name()));
        return out;
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

    /** The landrace you find by breaking grass in this biome (rare ones turn up less often). */
    public Strain wildFor(Block block) {
        String biome = block.getBiome().getKey().getKey();
        List<Strain> matches = new ArrayList<>();
        for (Strain s : strains.values()) {
            if (s.wildBiomes().contains(biome)) {
                matches.add(s);
            }
        }
        if (matches.isEmpty()) {
            // no explicit match: a built-in strain that loves this climate
            Climate climate = Climate.of(block);
            for (Strain s : strains.values()) {
                if (!s.isCustom() && s.climate() == climate && !s.wildBiomes().isEmpty()) {
                    matches.add(s);
                }
            }
        }
        if (matches.isEmpty()) {
            for (Strain s : strains.values()) {
                if (!s.isCustom() && !s.wildBiomes().isEmpty()) {
                    matches.add(s);
                }
            }
        }
        if (matches.isEmpty()) {
            matches.addAll(strains.values());
        }
        return pick(matches, ThreadLocalRandom.current().nextDouble());
    }

    /** Weighted pick (wild-weight); roll in 0..1. */
    static Strain pick(List<Strain> list, double roll) {
        double total = 0;
        for (Strain s : list) {
            total += s.wildWeight();
        }
        if (total <= 0) {
            return list.get((int) (roll * list.size()) % list.size());
        }
        double x = roll * total;
        for (Strain s : list) {
            x -= s.wildWeight();
            if (x < 0) {
                return s;
            }
        }
        return list.get(list.size() - 1);
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
    public Strain create(String name, Breeding.Result r, UUID creator, String creatorName) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "strain";
        }
        String id = base;
        int n = 2;
        while (strains.containsKey(id)) {
            id = base + "_" + n++;
        }
        Strain s = new Strain(id, name, r.type(), r.look(), r.climate(), r.potency(), r.effects(), List.of(), 0,
                r.flavor(), 0, false, creator, creatorName);
        strains.put(id, s);
        ConfigurationSection sec = yaml.getConfigurationSection("strains");
        if (sec == null) {
            sec = yaml.createSection("strains");
        }
        ConfigurationSection out = sec.createSection(id);
        out.set("name", name);
        out.set("type", r.type().name());
        out.set("color", hex(r.look().bud()));
        out.set("leaf", hex(r.look().leaf()));
        out.set("pistil", hex(r.look().pistil()));
        out.set("shape", r.look().shape().id());
        if (r.look().exotic() != Exotic.NONE) {
            out.set("exotic", r.look().exotic().id());
        }
        out.set("climate", r.climate().id());
        out.set("potency", s.potency());
        List<String> eff = new ArrayList<>();
        for (EffectType e : r.effects()) {
            eff.add(e.name());
        }
        out.set("effects", eff);
        out.set("flavor", r.flavor());
        out.set("wild-biomes", List.of());
        out.set("creator", creator.toString());
        out.set("creator-name", creatorName);
        save();
        return s;
    }

    static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** Renames a strain (id stays the same, so existing seeds/buds keep working). */
    public Strain rename(Strain old, String newName) {
        Strain s = old.renamed(newName);
        strains.put(s.id(), s);
        ConfigurationSection sec = yaml.getConfigurationSection("strains." + s.id());
        if (sec != null) {
            sec.set("name", newName);
            save();
        }
        return s;
    }

    /** Forgets a strain (selftest clean-up). */
    public void remove(String id) {
        strains.remove(id);
        yaml.set("strains." + id, null);
        save();
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save strains.yml", e);
        }
    }
}
