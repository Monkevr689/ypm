package dev.kushcraft.economy;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effects.Dose;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.strains.Strain;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Dealer prices from config.yml. */
public final class Shop {

    public record BuyEntry(ItemType type, String strain, int amount, double price) {
    }

    private final KushCraft plugin;
    private final List<BuyEntry> buy = new ArrayList<>();
    private final List<BuyEntry> seeds = new ArrayList<>();
    private final List<BuyEntry> gear = new ArrayList<>();
    private final List<BuyEntry> hires = new ArrayList<>();
    private final Map<ItemType, Double> sell = new EnumMap<>(ItemType.class);

    public Shop(KushCraft plugin) {
        this.plugin = plugin;
    }

    public void load() {
        buy.clear();
        seeds.clear();
        gear.clear();
        hires.clear();
        sell.clear();
        // cannabis seeds: every built-in strain at its own price (strains.yml), Mythic ones last
        double mult = Math.max(0, plugin.getConfig().getDouble("shop.seed-price-multiplier", 1.0));
        double mythicMult = Math.max(0, plugin.getConfig().getDouble("shop.mythic-seed-multiplier", 3.0));
        boolean mythicSeeds = plugin.getConfig().getBoolean("shop.mythic-seeds", true);
        List<BuyEntry> mythic = new ArrayList<>();
        for (Strain s : plugin.strains().shopStrains()) {
            boolean isMythic = s.rarity().animated();
            if (isMythic && !mythicSeeds) {
                continue;
            }
            double m = mult * (isMythic ? mythicMult : 1.0);
            // scaled prices are rounded to $5 so they stay easy to read
            double price = m == 1.0 ? s.seedPrice() : Math.max(5, Math.round(s.seedPrice() * m / 5.0) * 5.0);
            (isMythic ? mythic : seeds).add(new BuyEntry(ItemType.SEED_PACK, s.id(), 1, price));
        }
        for (Map<?, ?> m : plugin.getConfig().getMapList("shop.buy")) {
            ItemType t = ItemType.parse(String.valueOf(m.get("item")));
            if (t == null) {
                plugin.getLogger().warning("shop.buy: unknown item " + m.get("item"));
                continue;
            }
            if (t.retired() || t == ItemType.SEED_PACK) {
                continue; // old stations are replaced by the Drug Lab; strain seeds come from strains.yml
            }
            Object strain = m.get("strain");
            if (t.strainBound() && (strain == null || plugin.strains().get(String.valueOf(strain)) == null)) {
                plugin.getLogger().warning("shop.buy: " + t.id() + " needs a valid strain (got " + strain + ")");
                continue;
            }
            int amount = m.get("amount") instanceof Number n ? n.intValue() : 1;
            double price = m.get("price") instanceof Number n ? n.doubleValue() : 0;
            BuyEntry e = new BuyEntry(t, strain == null ? null : String.valueOf(strain), Math.max(1, amount), Math.max(0, price));
            (isSeed(t) ? seeds : dev.kushcraft.workers.WorkerType.of(t) != null ? hires : gear).add(e);
        }
        seeds.addAll(mythic);
        buy.addAll(seeds);
        buy.addAll(gear);
        buy.addAll(hires);
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("shop.sell");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                ItemType t = ItemType.parse(k);
                if (t != null) {
                    sell.put(t, s.getDouble(k));
                }
            }
        }
    }

    public List<BuyEntry> buyEntries() {
        return Collections.unmodifiableList(buy);
    }

    /** Strain seeds (cheapest first), then spores and the other seeds, then the Mythic seeds. */
    public List<BuyEntry> seeds() {
        return Collections.unmodifiableList(seeds);
    }

    /** Everything else: papers, solvent, blocks... */
    public List<BuyEntry> gear() {
        return Collections.unmodifiableList(gear);
    }

    /** Workers for hire (Shop > Gear & Workers). */
    public List<BuyEntry> hires() {
        return Collections.unmodifiableList(hires);
    }

    static boolean isSeed(ItemType t) {
        return t == ItemType.SEED_PACK || t == ItemType.MUSHROOM_SPORES || t == ItemType.COCA_SEEDS
                || t == ItemType.POPPY_SEEDS || t == ItemType.PEYOTE_SEEDS;
    }

    public ItemStack create(BuyEntry e) {
        if (e.type().strainBound()) {
            Strain s = plugin.strains().getOrDefault(e.strain());
            return Items.strainItem(e.type(), s, 3, e.amount());
        }
        return Items.create(e.type(), e.amount());
    }

    /** Config sell price of one plain item (no quality / strain / market adjustments). */
    public double basePrice(ItemType t) {
        Double base = sell.get(t);
        return base == null ? 0 : Math.max(0, base);
    }

    /** Price the dealer pays for ONE of this item right now, 0 if he doesn't want it. */
    public double sellPrice(ItemStack item) {
        ItemType t = Items.type(item);
        if (t == null) {
            return 0;
        }
        double base = basePrice(t);
        if (base <= 0) {
            return 0;
        }
        double price = base * plugin.market().multiplier(t);
        if (t.strainBound()) {
            Strain s = Items.strain(item);
            if (s != null) {
                price *= s.potencyFactor() * s.rarity().priceFactor();
            }
            if (t != ItemType.SEED_PACK) {
                price *= Dose.qualityFactor(Items.quality(item)) / Dose.qualityFactor(3);
            }
            int max = Items.maxHits(t);
            if (max > 0) {
                price *= Items.hits(item) / (double) max;
            }
        }
        return Math.max(0.01, Math.round(price * 100) / 100.0);
    }

    /** What this player gets for ONE item right now, including their title and cartel bonus. */
    public double sellPrice(ItemStack item, org.bukkit.entity.Player p) {
        double base = sellPrice(item);
        return base <= 0 ? 0 : Math.round(base * bonus(p) * 100) / 100.0;
    }

    /** Sale multiplier of a player: dealer title + cartel level + Smooth Talker (1.2 = +20%). */
    public double bonus(org.bukkit.entity.Player p) {
        return plugin.titles().multiplier(p) + plugin.cartels().sellBonus(p.getUniqueId())
                + (plugin.effects().has(p, dev.kushcraft.effects.EffectType.SMOOTH_TALKER) ? 0.10 : 0);
    }
}
