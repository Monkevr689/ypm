package dev.kushcraft.shop;

import dev.kushcraft.KushCraft;
import dev.kushcraft.Keys;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/**
 * Money. Uses Vault when it's installed (and enabled in the config),
 * otherwise a simple wallet stored on the player.
 */
public final class Economy {

    private final KushCraft plugin;
    private Object vault;
    private Method vGet, vWithdraw, vDeposit, vSuccess;

    public Economy(KushCraft plugin) {
        this.plugin = plugin;
    }

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

    public double balance(Player p) {
        if (vault != null) {
            try {
                return (double) vGet.invoke(vault, p);
            } catch (Exception e) {
                return 0;
            }
        }
        Double d = p.getPersistentDataContainer().get(Keys.BALANCE, PersistentDataType.DOUBLE);
        if (d == null) {
            d = plugin.getConfig().getDouble("economy.starting-balance", 100);
            p.getPersistentDataContainer().set(Keys.BALANCE, PersistentDataType.DOUBLE, d);
        }
        return d;
    }

    public boolean withdraw(Player p, double amount) {
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
        p.getPersistentDataContainer().set(Keys.BALANCE, PersistentDataType.DOUBLE, b - amount);
        return true;
    }

    public void deposit(Player p, double amount) {
        if (amount <= 0) {
            return;
        }
        if (vault != null) {
            try {
                vDeposit.invoke(vault, p, amount);
                return;
            } catch (Exception e) {
                plugin.getLogger().warning("Vault deposit failed: " + e.getMessage());
                return;
            }
        }
        p.getPersistentDataContainer().set(Keys.BALANCE, PersistentDataType.DOUBLE, balance(p) + amount);
    }

    /** Admin: set the built-in wallet (Vault balances are managed by your economy plugin). */
    public void set(Player p, double amount) {
        if (vault != null) {
            double diff = amount - balance(p);
            if (diff > 0) {
                deposit(p, diff);
            } else {
                withdraw(p, -diff);
            }
            return;
        }
        p.getPersistentDataContainer().set(Keys.BALANCE, PersistentDataType.DOUBLE, Math.max(0, amount));
    }

    public String format(double amount) {
        return plugin.getConfig().getString("economy.symbol", "$") + Text.number(Math.round(amount * 100) / 100.0);
    }
}
