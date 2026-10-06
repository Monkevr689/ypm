package dev.kushcraft.shop;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * The living part of the economy:
 *  - demand: every item sold lowers that product's price a little; it
 *    recovers over time, so flooding the market with one thing pays less
 *  - a "hot item" that pays a bonus for a while
 *  - daily orders: hand in N of something for a big bonus
 */
public final class Market {

    public record Order(int id, ItemType type, int amount, double reward, long expires) {
    }

    private static final List<ItemType> POOL = List.of(ItemType.BUD_DRIED, ItemType.JOINT, ItemType.BLUNT,
            ItemType.HASH, ItemType.MOON_ROCK, ItemType.SPACE_BROWNIE, ItemType.GUMMIES, ItemType.WAX,
            ItemType.VAPE_PEN, ItemType.MAGIC_MUSHROOM,
            ItemType.SHROOM_TEA, ItemType.LUCID_TAB, ItemType.PEYOTE_BUTTON, ItemType.MESCALINE, ItemType.DMT,
            ItemType.COCAINE, ItemType.CRACK, ItemType.BLUE_CRYSTAL, ItemType.ECSTASY, ItemType.PIXIE_DUST,
            ItemType.ANGEL_DUST, ItemType.OPIUM, ItemType.HEROIN, ItemType.LEAN, ItemType.KETAMINE,
            ItemType.COCA_LEAVES, ItemType.POPPY_POD);

    private final KushCraft plugin;
    private final File file;
    private final Map<ItemType, Double> demand = new EnumMap<>(ItemType.class);
    private final List<Order> orders = new ArrayList<>();
    private ItemType hot;
    private long hotUntil;
    private int nextId = 1;
    private boolean dirty;

    public Market(KushCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "market.yml");
    }

    // ------------------------------------------------------------------
    // storage + ticking
    // ------------------------------------------------------------------

    public void load() {
        demand.clear();
        orders.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection d = y.getConfigurationSection("demand");
        if (d != null) {
            for (String k : d.getKeys(false)) {
                ItemType t = ItemType.parse(k);
                if (t != null) {
                    demand.put(t, d.getDouble(k));
                }
            }
        }
        hot = ItemType.parse(y.getString("hot.item"));
        hotUntil = y.getLong("hot.until");
        nextId = y.getInt("next-id", 1);
        for (Map<?, ?> m : y.getMapList("orders")) {
            ItemType t = ItemType.parse(String.valueOf(m.get("item")));
            if (t == null) {
                continue;
            }
            orders.add(new Order(((Number) m.get("id")).intValue(), t, ((Number) m.get("amount")).intValue(),
                    ((Number) m.get("reward")).doubleValue(), ((Number) m.get("expires")).longValue()));
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<ItemType, Double> e : demand.entrySet()) {
            y.set("demand." + e.getKey().id(), Math.round(e.getValue() * 1000) / 1000.0);
        }
        if (hot != null) {
            y.set("hot.item", hot.id());
            y.set("hot.until", hotUntil);
        }
        y.set("next-id", nextId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Order o : orders) {
            list.add(Map.of("id", o.id(), "item", o.type().id(), "amount", o.amount(), "reward", o.reward(),
                    "expires", o.expires()));
        }
        y.set("orders", list);
        try {
            y.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save market.yml", ex);
        }
    }

    public void start() {
        tick();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick();
            if (dirty) {
                save();
            }
        }, 20L * 60, 20L * 60);
    }

    /** Once a minute: demand recovers, the hot item rotates, orders expire and refill. */
    public void tick() {
        double recover = plugin.getConfig().getDouble("market.recovery-per-minute", 0.02);
        for (Map.Entry<ItemType, Double> e : demand.entrySet()) {
            double v = e.getValue();
            if (v < 1) {
                e.setValue(Math.min(1, v + recover));
                dirty = true;
            }
        }
        long now = System.currentTimeMillis();
        if (hot == null || now > hotUntil) {
            List<ItemType> sellable = sellablePool();
            if (!sellable.isEmpty()) {
                hot = sellable.get(ThreadLocalRandom.current().nextInt(sellable.size()));
                hotUntil = now + plugin.getConfig().getLong("market.hot-item-minutes", 60) * 60_000L;
                dirty = true;
            }
        }
        boolean changed = orders.removeIf(o -> now > o.expires());
        int want = Math.max(0, plugin.getConfig().getInt("market.orders", 3));
        while (orders.size() < want) {
            Order o = newOrder();
            if (o == null) {
                break;
            }
            orders.add(o);
            changed = true;
        }
        dirty |= changed;
    }

    private List<ItemType> sellablePool() {
        List<ItemType> out = new ArrayList<>();
        for (ItemType t : POOL) {
            if (plugin.shop().basePrice(t) > 0) {
                out.add(t);
            }
        }
        return out;
    }

    private Order newOrder() {
        List<ItemType> pool = sellablePool();
        pool.removeIf(t -> {
            for (Order o : orders) {
                if (o.type() == t) {
                    return true;
                }
            }
            return false;
        });
        if (pool.isEmpty()) {
            return null;
        }
        ItemType t = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        double base = plugin.shop().basePrice(t);
        // big batches: you need to sell a lot to fill an order
        int amount = (int) Math.max(4, Math.min(64, Math.round(600 / Math.max(1, base))));
        double bonus = plugin.getConfig().getDouble("market.order-bonus", 1.6);
        double reward = Math.max(5, Math.round(base * amount * bonus / 5.0) * 5.0);
        long minutes = plugin.getConfig().getLong("market.order-minutes", 60);
        return new Order(nextId++, t, amount, reward, System.currentTimeMillis() + minutes * 60_000L);
    }

    // ------------------------------------------------------------------
    // prices
    // ------------------------------------------------------------------

    public double demand(ItemType t) {
        return demand.getOrDefault(t, 1.0);
    }

    public boolean isHot(ItemType t) {
        return t != null && t == hot && System.currentTimeMillis() < hotUntil;
    }

    public ItemType hot() {
        return System.currentTimeMillis() < hotUntil ? hot : null;
    }

    public long hotMinutesLeft() {
        return Math.max(0, (hotUntil - System.currentTimeMillis()) / 60_000L);
    }

    /** Price multiplier right now (demand x hot bonus). */
    public double multiplier(ItemType t) {
        double m = demand(t);
        if (isHot(t)) {
            m *= plugin.getConfig().getDouble("market.hot-item-bonus", 1.5);
        }
        return m;
    }

    /** "<green>100%" style text for lore. */
    public String trend(ItemType t) {
        int pct = (int) Math.round(multiplier(t) * 100);
        String col = pct >= 110 ? "gold" : pct >= 95 ? "green" : pct >= 75 ? "yellow" : "red";
        return "<" + col + ">" + pct + "%" + (isHot(t) ? " ⬆ HOT" : pct < 95 ? " ⬇" : "") + "</" + col + ">";
    }

    public void sold(ItemType t, int amount) {
        double drop = plugin.getConfig().getDouble("market.demand-drop", 0.01);
        double min = plugin.getConfig().getDouble("market.min-price", 0.5);
        demand.put(t, Math.max(min, demand(t) - drop * amount));
        dirty = true;
    }

    // ------------------------------------------------------------------
    // orders
    // ------------------------------------------------------------------

    public List<Order> orders() {
        return orders;
    }

    /** Hands in an order with items from the player's inventory. */
    public boolean complete(Player p, Order o) {
        if (!orders.contains(o)) {
            p.sendActionBar(Text.mm("<red>Someone else already filled that order."));
            return false;
        }
        int have = InventoryUtil.count(p, it -> Items.type(it) == o.type());
        if (have < o.amount()) {
            p.sendActionBar(Text.mm("<red>You need " + o.amount() + "x " + o.type().display() + " (you have " + have + ")."));
            return false;
        }
        InventoryUtil.remove(p, it -> Items.type(it) == o.type(), o.amount());
        plugin.economy().deposit(p, o.reward());
        plugin.ranks().sold(p, o.reward());
        plugin.awards().order(p);
        orders.remove(o);
        Order next = newOrder();
        if (next != null) {
            orders.add(next);
        }
        dirty = true;
        Bukkit.broadcast(Text.msg("<white>" + Text.escape(p.getName()) + " <gray>completed an order: <white>"
                + o.amount() + "x " + o.type().display() + " <gray>for <gold>" + plugin.economy().format(o.reward())));
        return true;
    }
}
