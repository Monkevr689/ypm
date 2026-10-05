package dev.kushcraft.shop;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effect.Dose;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.strain.Strain;
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
    private final Map<ItemType, Double> sell = new EnumMap<>(ItemType.class);

    public Shop(KushCraft plugin) {
        this.plugin = plugin;
    }

    public void load() {
        buy.clear();
        sell.clear();
        for (Map<?, ?> m : plugin.getConfig().getMapList("shop.buy")) {
            ItemType t = ItemType.parse(String.valueOf(m.get("item")));
            if (t == null) {
                plugin.getLogger().warning("shop.buy: unknown item " + m.get("item"));
                continue;
            }
            Object strain = m.get("strain");
            if (t.strainBound() && (strain == null || plugin.strains().get(String.valueOf(strain)) == null)) {
                plugin.getLogger().warning("shop.buy: " + t.id() + " needs a valid strain (got " + strain + ")");
                continue;
            }
            int amount = m.get("amount") instanceof Number n ? n.intValue() : 1;
            double price = m.get("price") instanceof Number n ? n.doubleValue() : 0;
            buy.add(new BuyEntry(t, strain == null ? null : String.valueOf(strain), Math.max(1, amount), Math.max(0, price)));
        }
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

    public ItemStack create(BuyEntry e) {
        if (e.type().strainBound()) {
            Strain s = plugin.strains().getOrDefault(e.strain());
            return Items.strainItem(e.type(), s, 3, e.amount());
        }
        return Items.create(e.type(), e.amount());
    }

    /** Price the dealer pays for ONE of this item, 0 if he doesn't want it. */
    public double sellPrice(ItemStack item) {
        ItemType t = Items.type(item);
        if (t == null) {
            return 0;
        }
        Double base = sell.get(t);
        if (base == null || base <= 0) {
            return 0;
        }
        double price = base;
        if (t.strainBound()) {
            Strain s = Items.strain(item);
            if (s != null) {
                price *= s.potencyFactor();
            }
            if (t != ItemType.SEED_PACK) {
                price *= Dose.qualityFactor(Items.quality(item)) / Dose.qualityFactor(3);
            }
            if (t == ItemType.JOINT) {
                price *= Items.hits(item) / (double) Items.JOINT_HITS;
            } else if (t == ItemType.BLUNT) {
                price *= Items.hits(item) / (double) Items.BLUNT_HITS;
            }
        }
        return Math.max(0.01, Math.round(price * 100) / 100.0);
    }
}
