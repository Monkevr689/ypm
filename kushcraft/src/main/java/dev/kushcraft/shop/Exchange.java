package dev.kushcraft.shop;

import dev.kushcraft.KushCraft;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Trade: spend the money from your product on vanilla items (lab
 * ingredients, ores, farming, wood, building blocks, colours, decoration,
 * redstone, tools, mob drops, the Nether). It only sells: nothing can be
 * sold back, so the only way to make money is selling drugs.
 *
 * Items are expensive on purpose - a diamond costs a lot of weed. Buying
 * raises an item's price a little and it drifts back to normal over time.
 */
public final class Exchange {

    public record Offer(Material material, double price, int amount) {
    }

    /** A shelf of the Trade tab (an item can be on more than one shelf). */
    public record Category(String id, String name, Material icon, List<Offer> offers) {
    }

    private final KushCraft plugin;
    private final File file;
    private final List<Offer> offers = new ArrayList<>();
    private final List<Category> categories = new ArrayList<>();
    private final Map<Material, Offer> byMaterial = new EnumMap<>(Material.class);
    private final Map<Material, Double> demand = new EnumMap<>(Material.class);
    private boolean dirty;

    public Exchange(KushCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "exchange.yml");
    }

    public void load() {
        offers.clear();
        byMaterial.clear();
        categories.clear();
        double mult = Math.max(0, plugin.getConfig().getDouble("exchange.price-multiplier", 1.0));
        ConfigurationSection cats = plugin.getConfig().getConfigurationSection("exchange.categories");
        if (cats != null) {
            for (String id : cats.getKeys(false)) {
                ConfigurationSection c = cats.getConfigurationSection(id);
                if (c == null) {
                    continue;
                }
                Material icon = Material.matchMaterial(c.getString("icon", "chest"));
                List<Offer> list = read(c.getMapList("items"), mult);
                if (!list.isEmpty()) {
                    categories.add(new Category(id, c.getString("name", id), icon == null ? Material.CHEST : icon,
                            List.copyOf(list)));
                }
            }
        }
        // a plain list (2.x configs) becomes one shelf
        List<Offer> flat = read(plugin.getConfig().getMapList("exchange.items"), mult);
        if (!flat.isEmpty()) {
            categories.add(new Category("all", "Resources", Material.CHEST, List.copyOf(flat)));
        }
        demand.clear();
        if (file.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection d = y.getConfigurationSection("demand");
            if (d != null) {
                for (String k : d.getKeys(false)) {
                    Material m = Material.matchMaterial(k);
                    if (m != null) {
                        demand.put(m, Math.max(1, d.getDouble(k)));
                    }
                }
            }
        }
    }

    private List<Offer> read(List<Map<?, ?>> list, double mult) {
        List<Offer> out = new ArrayList<>();
        for (Map<?, ?> m : list) {
            Material mat = Material.matchMaterial(String.valueOf(m.get("item")));
            if (mat == null || !mat.isItem() || mat.isAir()) {
                plugin.getLogger().warning("exchange: unknown item " + m.get("item"));
                continue;
            }
            double price = (m.get("price") instanceof Number n ? n.doubleValue() : 0) * mult;
            int amount = m.get("amount") instanceof Number n ? n.intValue() : 1;
            if (price <= 0) {
                continue;
            }
            Offer known = byMaterial.get(mat);
            if (known != null) {
                // already on another shelf: the same offer (one price) shows on both
                if (!out.contains(known)) {
                    out.add(known);
                }
                continue;
            }
            Offer o = new Offer(mat, price, Math.max(1, Math.min(64, amount)));
            out.add(o);
            offers.add(o);
            byMaterial.put(mat, o);
        }
        return out;
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        demand.forEach((m, v) -> y.set("demand." + m.name().toLowerCase(java.util.Locale.ROOT), Math.round(v * 1000) / 1000.0));
        try {
            y.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save exchange.yml", ex);
        }
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick();
            if (dirty) {
                save();
            }
        }, 20L * 60, 20L * 60);
    }

    /** Once a minute every price drifts back towards normal. */
    public void tick() {
        double step = plugin.getConfig().getDouble("exchange.recovery-per-minute", 0.02);
        demand.replaceAll((m, v) -> Math.max(1, v - step));
        demand.values().removeIf(v -> v == 1.0);
        dirty = true;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("exchange.enabled", true) && !offers.isEmpty();
    }

    /** Everything for sale, in config order. */
    public List<Offer> offers() {
        return Collections.unmodifiableList(offers);
    }

    /** The shelves of the Trade tab, in config order. */
    public List<Category> categories() {
        return Collections.unmodifiableList(categories);
    }

    public Offer offer(Material m) {
        return byMaterial.get(m);
    }

    public double multiplier(Material m) {
        return demand.getOrDefault(m, 1.0);
    }

    /** What a player pays for one item right now. */
    public double buyPrice(Offer o) {
        return round(o.price() * multiplier(o.material()));
    }

    public void bought(Material m, int n) {
        Offer o = byMaterial.get(m);
        if (o == null || n <= 0) {
            return;
        }
        // each "unit" (the amount bought per click) raises the price by price-step
        double step = plugin.getConfig().getDouble("exchange.price-step", 0.01) * n / o.amount();
        double max = plugin.getConfig().getDouble("exchange.max-price", 1.25);
        demand.put(m, Math.max(1, Math.min(max, multiplier(m) + step)));
        dirty = true;
    }

    /** "<green>100%" style text for lore. */
    public String trend(Material m) {
        int pct = (int) Math.round(multiplier(m) * 100);
        String col = pct > 102 ? "gold" : "green";
        return "<" + col + ">" + pct + "%" + (pct > 102 ? " ⬆" : "") + "</" + col + ">";
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
