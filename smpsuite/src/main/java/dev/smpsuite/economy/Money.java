package dev.smpsuite.economy;

import dev.smpsuite.SMPSuite;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Jobs pay goes into Vault's economy when it's installed, otherwise into
 * KushCraft's wallet, otherwise nowhere (pay is off). Both are reached by
 * reflection, so neither is needed to build or run SMPSuite.
 */
public final class Money {

    private final SMPSuite plugin;
    private Object target;
    private Method deposit;
    private Method balance;
    private Method format;
    private String mode = "off";

    public Money(SMPSuite plugin) {
        this.plugin = plugin;
    }

    /** Called one tick after start-up (Vault providers register on enable). */
    public void hook() {
        target = null;
        mode = "off";
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault != null && vault.isEnabled()) {
            try {
                Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy", true, vault.getClass().getClassLoader());
                RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(eco);
                if (rsp != null) {
                    target = rsp.getProvider();
                    deposit = eco.getMethod("depositPlayer", OfflinePlayer.class, double.class);
                    balance = eco.getMethod("getBalance", OfflinePlayer.class);
                    format = eco.getMethod("format", double.class);
                    mode = "Vault (" + rsp.getPlugin().getName() + ")";
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                plugin.getLogger().warning("Could not hook Vault: " + e.getMessage());
                target = null;
            }
        }
        if (target == null) {
            Plugin kc = Bukkit.getPluginManager().getPlugin("KushCraft");
            if (kc != null && kc.isEnabled()) {
                try {
                    Object eco = kc.getClass().getMethod("economy").invoke(kc);
                    deposit = eco.getClass().getMethod("deposit", OfflinePlayer.class, double.class);
                    balance = eco.getClass().getMethod("balance", OfflinePlayer.class);
                    format = eco.getClass().getMethod("format", double.class);
                    target = eco;
                    mode = "KushCraft wallet";
                } catch (ReflectiveOperationException | RuntimeException e) {
                    plugin.getLogger().warning("Could not use KushCraft's wallet: " + e.getMessage());
                    target = null;
                }
            }
        }
        plugin.getLogger().info(target == null
                ? "Jobs pay: off (no Vault economy and no KushCraft installed)."
                : "Jobs pay: paid through " + mode + ".");
    }

    public boolean available() {
        return target != null;
    }

    public String mode() {
        return mode;
    }

    public boolean deposit(OfflinePlayer p, double amount) {
        if (target == null || amount <= 0) {
            return false;
        }
        try {
            deposit.invoke(target, p, amount);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Could not pay " + p.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public double balance(OfflinePlayer p) {
        if (target == null) {
            return 0;
        }
        try {
            return ((Number) balance.invoke(target, p)).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    public String format(double amount) {
        if (target != null) {
            try {
                return String.valueOf(format.invoke(target, amount));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // fall through
            }
        }
        return String.format(Locale.ROOT, "$%,.2f", amount);
    }
}
