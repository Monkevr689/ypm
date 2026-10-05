package dev.kushcraft.shop;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.Items;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * The resource exchange: trade money for vanilla resources (ores, food,
 * wood, blocks, mob drops...) and sell them back.
 *
 * Fair prices: every item has a base price (rare = expensive) from
 * config.yml. You buy at the base price and sell for sell-ratio of it, so
 * nothing can be bought and sold back for a profit. Prices move a little:
 * buying raises an item's price, selling lowers it, and it drifts back to
 * normal over time.
 */
public final class Exchange {

    public record Offer(Material material, double price, int amount, String category) {
    }

    public record Category(String id, String name, Material icon, List<Offer> offers) {
    }

    private final KushCraft plugin;
    private final File file;
    private final List<Category> categories = new ArrayList<>();
    private final Map<Material, Offer> byMaterial = new EnumMap<>(Material.class);
    private final Map<Material, Double> demand = new EnumMap<>(Material.class);
    private boolean dirty;

    public Exchange(KushCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "exchange.yml");
    }

    public void load() {
        categories.clear();
        byMaterial.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("exchange.categories");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                List<Offer> offers = new ArrayList<>();
                for (Map<?, ?> m : sec.getMapList(id + ".items")) {
                    Material mat = Material.matchMaterial(String.valueOf(m.get("item")));
                    if (mat == null || !mat.isItem() || mat.isAir()) {
                        plugin.getLogger().warning("exchange: unknown item " + m.get("item"));
                        continue;
                    }
                    double price = m.get("price") instanceof Number n ? n.doubleValue() : 0;
                    int amount = m.get("amount") instanceof Number n ? n.intValue() : 1;
                    if (price <= 0 || byMaterial.containsKey(mat)) {
                        continue;
                    }
                    Offer o = new Offer(mat, price, Math.max(1, Math.min(64, amount)), id);
                    offers.add(o);
                    byMaterial.put(mat, o);
                }
                if (!offers.isEmpty()) {
                    Material icon = Material.matchMaterial(sec.getString(id + ".icon", ""));
                    categories.add(new Category(id, sec.getString(id + ".name", Text.titleCase(id)),
                            icon != null ? icon : offers.get(0).material(), Collections.unmodifiableList(offers)));
                }
            }
        }
        demand.clear();
        if (file.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection d = y.getConfigurationSection("demand");
            if (d != null) {
                for (String k : d.getKeys(false)) {
                    Material m = Material.matchMaterial(k);
                    if (m != null) {
                        demand.put(m, d.getDouble(k));
                    }
                }
            }
        }
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
        demand.replaceAll((m, v) -> v > 1 ? Math.max(1, v - step) : Math.min(1, v + step));
        demand.values().removeIf(v -> v == 1.0);
        dirty = true;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("exchange.enabled", true) && !categories.isEmpty();
    }

    public List<Category> categories() {
        return categories;
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

    /** What a player gets for one item right now (0 = not traded). */
    public double sellPrice(Material m) {
        Offer o = byMaterial.get(m);
        if (o == null) {
            return 0;
        }
        return round(o.price() * multiplier(m) * plugin.getConfig().getDouble("exchange.sell-ratio", 0.5));
    }

    /** Only plain items can be sold: no names, enchantments, damage or KushCraft items. */
    public boolean sellable(ItemStack it) {
        return it != null && !it.getType().isAir() && byMaterial.containsKey(it.getType()) && !Items.isCustom(it)
                && it.isSimilar(new ItemStack(it.getType()));
    }

    public void bought(Material m, int n) {
        move(m, n, +1);
    }

    public void sold(Material m, int n) {
        move(m, n, -1);
    }

    /** Each "unit" (the amount sold per click) moves the price by price-step. */
    private void move(Material m, int n, int sign) {
        Offer o = byMaterial.get(m);
        if (o == null || n <= 0) {
            return;
        }
        double step = plugin.getConfig().getDouble("exchange.price-step", 0.01) * n / o.amount();
        double min = plugin.getConfig().getDouble("exchange.min-price", 0.8);
        double max = plugin.getConfig().getDouble("exchange.max-price", 1.25);
        demand.put(m, Math.max(min, Math.min(max, multiplier(m) + sign * step)));
        dirty = true;
    }

    /** "<green>100%" style text for lore. */
    public String trend(Material m) {
        int pct = (int) Math.round(multiplier(m) * 100);
        String col = pct > 102 ? "gold" : pct >= 98 ? "green" : "yellow";
        return "<" + col + ">" + pct + "%" + (pct > 102 ? " ⬆" : pct < 98 ? " ⬇" : "") + "</" + col + ">";
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
