package dev.kushcraft.economy;

import dev.kushcraft.KushCraft;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Makes KushCraft's wallet the server's Vault economy, so shops, crates, vote
 * rewards and any other plugin that pays or charges through Vault use the
 * same money (and every one of those changes is in the transaction log as
 * VAULT_IN / VAULT_OUT).
 *
 * Vault isn't needed to build KushCraft: the provider is a dynamic proxy for
 * net.milkbowl.vault.economy.Economy, made only when Vault is installed. It
 * registers with the highest priority so it wins over EssentialsX's own
 * provider. Calls from other threads read the balance directly and run
 * changes on the server thread (waiting for them).
 */
public final class VaultBridge {

    private final KushCraft plugin;
    private Object provider;
    private Class<?> service;

    public VaultBridge(KushCraft plugin) {
        this.plugin = plugin;
    }

    /** Registers the provider when Vault is installed. Returns a line for the log. */
    public String register() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return "Economy: KushCraft wallet (Vault isn't installed, so other plugins can't pay into it).";
        }
        try {
            service = Class.forName("net.milkbowl.vault.economy.Economy");
            Class<?> response = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
            Class<?> typeEnum = Class.forName("net.milkbowl.vault.economy.EconomyResponse$ResponseType");
            Constructor<?> make = response.getConstructor(double.class, double.class, typeEnum, String.class);
            Object success = enumValue(typeEnum, "SUCCESS");
            Object failure = enumValue(typeEnum, "FAILURE");
            Object notImpl = enumValue(typeEnum, "NOT_IMPLEMENTED");
            InvocationHandler h = (proxy, m, args) -> handle(m, args, make, success, failure, notImpl);
            provider = Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[]{service}, h);
            register(service, provider);
            RegisteredServiceProvider<?> top = Bukkit.getServicesManager().getRegistration(service);
            boolean ours = top != null && top.getProvider() == provider;
            return ours ? "Economy: KushCraft wallet, registered as the Vault economy (other plugins pay into it)."
                    : "Economy: KushCraft wallet. WARNING: Vault still prefers " + top.getPlugin().getName()
                    + "'s economy - other plugins would use a different money. Turn that plugin's economy off.";
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "Economy: KushCraft wallet (could not register with Vault: " + e + ").";
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void register(Class<?> svc, Object prov) {
        Bukkit.getServicesManager().register((Class) svc, prov, plugin, ServicePriority.Highest);
    }

    public void unregister() {
        if (provider != null) {
            Bukkit.getServicesManager().unregister(provider);
            provider = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> e, String name) {
        return Enum.valueOf((Class) e, name);
    }

    // ------------------------------------------------------------------
    // the Vault methods
    // ------------------------------------------------------------------

    private Object handle(Method m, Object[] a, Constructor<?> make, Object ok, Object fail, Object notImpl)
            throws ReflectiveOperationException {
        String n = m.getName();
        Economy eco = plugin.economy();
        switch (n) {
            case "isEnabled":
                return plugin.isEnabled();
            case "getName":
                return "KushCraft";
            case "hasBankSupport":
                return false;
            case "fractionalDigits":
                return 2;
            case "format":
                return eco.format(((Number) a[0]).doubleValue());
            case "currencyNamePlural":
                return plugin.getConfig().getString("economy.currency-plural", "dollars");
            case "currencyNameSingular":
                return plugin.getConfig().getString("economy.currency-singular", "dollar");
            case "hasAccount":
            case "createPlayerAccount":
                return player(a[0]) != null;
            case "getBalance": {
                UUID id = player(a[0]);
                return id == null ? 0.0 : eco.balance(id);
            }
            case "has": {
                UUID id = player(a[0]);
                double amount = ((Number) a[a.length - 1]).doubleValue();
                return id != null && amount >= 0 && eco.balance(id) >= amount;
            }
            case "withdrawPlayer":
            case "depositPlayer": {
                UUID id = player(a[0]);
                double amount = ((Number) a[a.length - 1]).doubleValue();
                boolean take = n.equals("withdrawPlayer");
                if (id == null || Economy.cents(amount) < 0) {
                    return make.newInstance(amount, 0.0, fail, "Bad player or amount");
                }
                String who = caller();
                Boolean done = onServerThread(() -> {
                    if (take) {
                        return eco.withdraw(id, amount, Tx.VAULT_OUT, who, "via Vault");
                    }
                    eco.deposit(id, amount, Tx.VAULT_IN, who, "via Vault");
                    return true;
                });
                double bal = eco.balance(id);
                if (done == null) {
                    return make.newInstance(amount, bal, fail, "KushCraft did not answer in time");
                }
                return done ? make.newInstance(amount, bal, ok, null)
                        : make.newInstance(amount, bal, fail, "Not enough money");
            }
            case "getBanks":
                return List.of();
            case "createBank":
            case "deleteBank":
            case "bankBalance":
            case "bankHas":
            case "bankWithdraw":
            case "bankDeposit":
            case "isBankOwner":
            case "isBankMember":
                return make.newInstance(0.0, 0.0, notImpl, "KushCraft has no banks (cartel banks are in /kush)");
            case "equals":
                return a != null && a.length == 1 && a[0] == provider;
            case "hashCode":
                return System.identityHashCode(this);
            case "toString":
                return "KushCraft Vault economy";
            default:
                Class<?> r = m.getReturnType();
                if (r == boolean.class) {
                    return false;
                }
                if (r == double.class) {
                    return 0.0;
                }
                if (r == int.class) {
                    return 0;
                }
                return null;
        }
    }

    /** OfflinePlayer, or a name (old Vault calls). */
    private static UUID player(Object o) {
        if (o instanceof OfflinePlayer p) {
            return p.getUniqueId();
        }
        if (o instanceof String name) {
            OfflinePlayer p = Bukkit.getOfflinePlayerIfCached(name);
            return p == null ? null : p.getUniqueId();
        }
        return null;
    }

    /** The plugin that called Vault (for the log), from the stack. */
    private static String caller() {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String c = e.getClassName();
            if (!c.startsWith("dev.kushcraft") && !c.startsWith("java.") && !c.startsWith("jdk.")
                    && !c.startsWith("com.sun.") && !c.startsWith("net.milkbowl") && !c.startsWith("sun.")
                    && !c.contains("$Proxy") && !c.startsWith("org.bukkit") && !c.startsWith("io.papermc")
                    && !c.startsWith("net.minecraft")) {
                return c.length() > 60 ? c.substring(c.length() - 60) : c;
            }
        }
        return "unknown plugin";
    }

    /**
     * Runs a change on the server thread; from another thread it waits up to 5 seconds (null = timed
     * out). A change that timed out never happens later: whichever side claims it first wins.
     */
    private <T> T onServerThread(Callable<T> work) {
        try {
            if (Bukkit.isPrimaryThread()) {
                return work.call();
            }
            java.util.concurrent.atomic.AtomicInteger state = new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.Future<T> f = Bukkit.getScheduler().callSyncMethod(plugin,
                    () -> state.compareAndSet(0, 1) ? work.call() : null);
            try {
                return f.get(5, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                if (state.compareAndSet(0, 2)) {
                    return null; // given up: it will not run
                }
                return f.get(); // already running: wait for it
            }
        } catch (Exception e) {
            return null;
        }
    }
}
