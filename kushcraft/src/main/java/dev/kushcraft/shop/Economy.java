package dev.kushcraft.shop;

import dev.kushcraft.KushCraft;
import dev.kushcraft.Keys;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Money. Uses Vault when it's installed (and enabled in the config),
 * otherwise a simple wallet saved in plugins/KushCraft/balances.yml.
 */
public final class Economy {

    public record Rich(UUID id, String name, double balance) {
    }

    private final KushCraft plugin;
    private final File file;
    private final Map<UUID, Double> wallet = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Double> sales = new HashMap<>();
    private boolean dirty;
    private Object vault;
    private Method vGet, vWithdraw, vDeposit, vSuccess;

    public Economy(KushCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.yml");
    }

    // ------------------------------------------------------------------
    // storage
    // ------------------------------------------------------------------

    public void load() {
        wallet.clear();
        names.clear();
        sales.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec == null) {
            return;
        }
        for (String k : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                names.put(id, sec.getString(k + ".name", "?"));
                if (sec.isSet(k + ".balance")) {
                    wallet.put(id, sec.getDouble(k + ".balance"));
                }
                if (sec.isSet(k + ".sales")) {
                    sales.put(id, sec.getDouble(k + ".sales"));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            String k = "players." + e.getKey();
            y.set(k + ".name", e.getValue());
            Double b = wallet.get(e.getKey());
            if (b != null) {
                y.set(k + ".balance", Math.round(b * 100) / 100.0);
            }
            Double sold = sales.get(e.getKey());
            if (sold != null) {
                y.set(k + ".sales", Math.round(sold * 100) / 100.0);
            }
        }
        try {
            y.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save balances.yml", ex);
        }
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                save();
            }
        }, 20L * 30, 20L * 30);
    }

    /** Remembers the player's name and moves an old (v1.0) wallet into balances.yml. */
    public void join(Player p) {
        String old = names.put(p.getUniqueId(), p.getName());
        if (!p.getName().equals(old)) {
            dirty = true;
        }
        if (!wallet.containsKey(p.getUniqueId())) {
            Double legacy = p.getPersistentDataContainer().get(Keys.BALANCE, PersistentDataType.DOUBLE);
            wallet.put(p.getUniqueId(), legacy != null ? legacy : plugin.getConfig().getDouble("economy.starting-balance", 100));
            dirty = true;
        }
    }

    // ------------------------------------------------------------------
    // vault
    // ------------------------------------------------------------------

    public void hook() {
        vault = null;
        if (!plugin.getConfig().getBoolean("economy.use-vault", true)
                || Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Economy: using the built-in KushCraft wallet.");
            return;
        }
        try {
            Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(eco);
            if (rsp == null) {
                plugin.getLogger().info("Vault found but no economy plugin - using the built-in wallet.");
                return;
            }
            vault = rsp.getProvider();
            vGet = eco.getMethod("getBalance", OfflinePlayer.class);
            vWithdraw = eco.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            vDeposit = eco.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            vSuccess = Class.forName("net.milkbowl.vault.economy.EconomyResponse").getMethod("transactionSuccess");
            plugin.getLogger().info("Economy: hooked into Vault.");
        } catch (Exception e) {
            vault = null;
            plugin.getLogger().warning("Could not hook Vault (" + e.getMessage() + ") - using the built-in wallet.");
        }
    }

    public boolean usingVault() {
        return vault != null;
    }

    // ------------------------------------------------------------------
    // money
    // ------------------------------------------------------------------

    public double balance(OfflinePlayer p) {
        if (vault != null) {
            try {
                return (double) vGet.invoke(vault, p);
            } catch (Exception e) {
                return 0;
            }
        }
        Double d = wallet.get(p.getUniqueId());
        if (d == null) {
            d = plugin.getConfig().getDouble("economy.starting-balance", 100);
            wallet.put(p.getUniqueId(), d);
            dirty = true;
        }
        return d;
    }

    public boolean withdraw(OfflinePlayer p, double amount) {
        if (amount <= 0) {
            return true;
        }
        if (vault != null) {
            try {
                if (balance(p) < amount) {
                    return false;
                }
                return (boolean) vSuccess.invoke(vWithdraw.invoke(vault, p, amount));
            } catch (Exception e) {
                return false;
            }
        }
        double b = balance(p);
        if (b + 1e-9 < amount) {
            return false;
        }
        wallet.put(p.getUniqueId(), b - amount);
        dirty = true;
        return true;
    }

    public void deposit(OfflinePlayer p, double amount) {
        if (amount <= 0) {
            return;
        }
        if (vault != null) {
            try {
                vDeposit.invoke(vault, p, amount);
            } catch (Exception e) {
                plugin.getLogger().warning("Vault deposit failed: " + e.getMessage());
            }
            return;
        }
        wallet.put(p.getUniqueId(), balance(p) + amount);
        dirty = true;
    }

    /** Admin: set a balance. */
    public void set(OfflinePlayer p, double amount) {
        if (vault != null) {
            double diff = amount - balance(p);
            if (diff > 0) {
                deposit(p, diff);
            } else {
                withdraw(p, -diff);
            }
            return;
        }
        wallet.put(p.getUniqueId(), Math.max(0, amount));
        dirty = true;
    }

    /** Lifetime money made selling product (Market and orders) - decides the dealer rank. */
    public double sales(OfflinePlayer p) {
        return sales.getOrDefault(p.getUniqueId(), 0.0);
    }

    public void addSales(OfflinePlayer p, double amount) {
        sales.merge(p.getUniqueId(), amount, Double::sum);
        dirty = true;
    }

    /** Admin: set lifetime sales (and so the rank). */
    public void setSales(OfflinePlayer p, double amount) {
        sales.put(p.getUniqueId(), Math.max(0, amount));
        dirty = true;
    }

    /** Every player who ever sold product, best seller first (balance = lifetime sales). */
    public List<Rich> topSales() {
        List<Rich> out = new ArrayList<>();
        for (Map.Entry<UUID, Double> e : sales.entrySet()) {
            if (e.getValue() > 0) {
                out.add(new Rich(e.getKey(), names.getOrDefault(e.getKey(), "?"), e.getValue()));
            }
        }
        // ties: the name decides, so the order never flickers
        out.sort((a, b) -> a.balance() != b.balance() ? Double.compare(b.balance(), a.balance())
                : a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    /** Richest players (everyone that ever joined since KushCraft was installed). */
    public List<Rich> top(int limit) {
        List<Rich> out = new ArrayList<>();
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            double b = vault != null ? balance(Bukkit.getOfflinePlayer(e.getKey())) : wallet.getOrDefault(e.getKey(), 0.0);
            out.add(new Rich(e.getKey(), e.getValue(), b));
        }
        out.sort((a, b) -> Double.compare(b.balance(), a.balance()));
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    public String format(double amount) {
        return plugin.getConfig().getString("economy.symbol", "$") + Text.number(Math.round(amount * 100) / 100.0);
    }
}
