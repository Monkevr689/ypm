package dev.kushcraft.economy;

import dev.kushcraft.KushCraft;
import dev.kushcraft.storage.PlayerRecord;
import dev.kushcraft.storage.PlayerStore;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Money. KushCraft owns the server's money: every balance is a whole number
 * of cents in the players table, changed only on the server thread, and every
 * change writes a row to the transaction log (Ledger) in the same database
 * transaction. Other plugins reach it through Vault (see VaultBridge).
 *
 * Nothing here trusts the client: amounts come from the server's own prices,
 * are checked for NaN/infinity/negatives and rounded to cents once.
 */
public final class Economy {

    public record Rich(UUID id, String name, double balance) {
    }

    private final KushCraft plugin;
    private final PlayerStore store;
    private final Ledger ledger;

    public Economy(KushCraft plugin, PlayerStore store, Ledger ledger) {
        this.plugin = plugin;
        this.store = store;
        this.ledger = ledger;
    }

    public Ledger ledger() {
        return ledger;
    }

    // ------------------------------------------------------------------
    // cents
    // ------------------------------------------------------------------

    /** Dollars to cents; -1 for anything that isn't a sane positive amount. */
    public static long cents(double dollars) {
        if (Double.isNaN(dollars) || Double.isInfinite(dollars) || dollars < 0 || dollars > 9e13) {
            return -1;
        }
        return Math.round(dollars * 100.0);
    }

    public static double dollars(long cents) {
        return cents / 100.0;
    }

    public double startingBalance() {
        return Math.max(0, plugin.getConfig().getDouble("economy.starting-balance", 250));
    }

    // ------------------------------------------------------------------
    // accounts
    // ------------------------------------------------------------------

    /** The player's row (made with the starting money when they never had one). */
    public PlayerRecord account(UUID id) {
        PlayerRecord r = store.get(id);
        if (r != null) {
            return r;
        }
        OfflinePlayer o = Bukkit.getOfflinePlayer(id);
        r = store.getOrCreate(id, o.getName());
        long start = cents(startingBalance());
        if (start > 0) {
            r.balance(start);
            ledger.log(id, Tx.START, start, start, null, null);
        }
        return r;
    }

    /** Remembers the name and when they were here. */
    public void join(Player p) {
        PlayerRecord r = account(p.getUniqueId());
        r.name(p.getName());
        r.seen(System.currentTimeMillis());
        r.lastActive = System.currentTimeMillis();
    }

    public void quit(Player p) {
        PlayerRecord r = store.get(p.getUniqueId());
        if (r != null) {
            r.seen(System.currentTimeMillis());
        }
    }

    /** True for players KushCraft knows (joined at least once). */
    public boolean known(UUID id) {
        return store.get(id) != null;
    }

    // ------------------------------------------------------------------
    // balances
    // ------------------------------------------------------------------

    public double balance(OfflinePlayer p) {
        return balance(p.getUniqueId());
    }

    public double balance(UUID id) {
        PlayerRecord r = store.get(id);
        return r == null ? (Bukkit.isPrimaryThread() ? dollars(account(id).balance()) : startingBalance()) : dollars(r.balance());
    }

    public boolean has(OfflinePlayer p, double amount) {
        long c = cents(amount);
        return c >= 0 && account(p.getUniqueId()).balance() >= c;
    }

    /**
     * Takes money for a reason. False (and nothing changes) when they don't have it.
     * Server thread only.
     */
    public boolean withdraw(OfflinePlayer p, double amount, Tx type, String detail) {
        return withdraw(p.getUniqueId(), amount, type, null, detail);
    }

    public boolean withdraw(UUID id, double amount, Tx type, String other, String detail) {
        check();
        long c = cents(amount);
        if (c < 0) {
            return false;
        }
        if (c == 0) {
            return true;
        }
        PlayerRecord r = account(id);
        if (r.balance() < c) {
            return false;
        }
        r.balance(r.balance() - c);
        ledger.log(id, type, -c, r.balance(), other, detail);
        return true;
    }

    /** Pays money for a reason. Server thread only. */
    public void deposit(OfflinePlayer p, double amount, Tx type, String detail) {
        deposit(p.getUniqueId(), amount, type, null, detail);
    }

    public void deposit(UUID id, double amount, Tx type, String other, String detail) {
        check();
        long c = cents(amount);
        if (c <= 0) {
            return;
        }
        PlayerRecord r = account(id);
        r.balance(r.balance() + c);
        ledger.log(id, type, c, r.balance(), other, detail);
        tookFrom(id, type);
    }

    /**
     * Small, frequent money changes (wages, Runner sales, worker supply buys): the same as
     * withdraw/deposit, but the log adds them up per worker per minute. amount &lt; 0 = take.
     * Returns false when a payment couldn't be made (not enough money).
     */
    public boolean frequent(UUID id, double amount, Tx type, String key, String detail) {
        check();
        long c = Math.round(amount * 100.0);
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return false;
        }
        if (c == 0) {
            return true;
        }
        PlayerRecord r = account(id);
        if (c < 0 && r.balance() < -c) {
            return false;
        }
        r.balance(r.balance() + c);
        ledger.sum(id, type, c, r.balance(), key, detail);
        return true;
    }

    /** /pay: both sides in one go (it can't half happen). */
    public boolean transfer(Player from, OfflinePlayer to, double amount) {
        check();
        long c = cents(amount);
        if (c <= 0 || from.getUniqueId().equals(to.getUniqueId())) {
            return false;
        }
        PlayerRecord a = account(from.getUniqueId());
        if (a.balance() < c) {
            return false;
        }
        PlayerRecord b = account(to.getUniqueId());
        a.balance(a.balance() - c);
        b.balance(b.balance() + c);
        ledger.log(from.getUniqueId(), Tx.PAY_OUT, -c, a.balance(), to.getUniqueId().toString(), to.getName());
        ledger.log(to.getUniqueId(), Tx.PAY_IN, c, b.balance(), from.getUniqueId().toString(), from.getName());
        return true;
    }

    /** Admin: set a balance (logged with who did it). */
    public void set(OfflinePlayer p, double amount, String by) {
        check();
        long c = Math.max(0, cents(amount));
        PlayerRecord r = account(p.getUniqueId());
        long diff = c - r.balance();
        r.balance(c);
        ledger.log(p.getUniqueId(), Tx.ADMIN, diff, c, by, "balance set to " + format(dollars(c)));
    }

    /** Marks the player's inventory to be saved before this payment is written (they handed items in). */
    private void tookFrom(UUID id, Tx type) {
        if (type.took()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                plugin.persistence().took(p);
            }
        }
    }

    private static void check() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("KushCraft money can only change on the server thread");
        }
    }

    // ------------------------------------------------------------------
    // lifetime sales (the dealer titles)
    // ------------------------------------------------------------------

    /** Lifetime money made selling product (this season). */
    public double sales(OfflinePlayer p) {
        PlayerRecord r = store.get(p.getUniqueId());
        return r == null ? 0 : dollars(r.sales());
    }

    public void addSales(OfflinePlayer p, double amount) {
        long c = cents(amount);
        if (c > 0) {
            PlayerRecord r = account(p.getUniqueId());
            r.sales(r.sales() + c);
        }
    }

    /** Admin: set lifetime sales (and so the title). */
    public void setSales(OfflinePlayer p, double amount, String by) {
        PlayerRecord r = account(p.getUniqueId());
        long c = Math.max(0, cents(amount));
        r.sales(c);
        ledger.log(p.getUniqueId(), Tx.ADMIN, 0, r.balance(), by, "lifetime sales set to " + format(dollars(c)));
    }

    /** Every player who sold product, best seller first (balance = lifetime sales). */
    public List<Rich> topSales() {
        List<Rich> out = new ArrayList<>();
        for (PlayerRecord r : store.all()) {
            if (r.sales() > 0) {
                out.add(new Rich(r.id(), r.name() == null ? "?" : r.name(), dollars(r.sales())));
            }
        }
        // ties: the name decides, so the order never flickers
        out.sort((a, b) -> a.balance() != b.balance() ? Double.compare(b.balance(), a.balance())
                : a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    /** Richest players. */
    public List<Rich> top(int limit) {
        List<Rich> out = new ArrayList<>();
        for (PlayerRecord r : store.all()) {
            out.add(new Rich(r.id(), r.name() == null ? "?" : r.name(), dollars(r.balance())));
        }
        out.sort((a, b) -> Double.compare(b.balance(), a.balance()));
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    /** All the money on the server (the admin's inflation check). */
    public double total() {
        long t = 0;
        for (PlayerRecord r : store.all()) {
            t += r.balance();
        }
        return dollars(t);
    }

    public String format(double amount) {
        return plugin.getConfig().getString("economy.symbol", "$") + Text.number(Math.round(amount * 100) / 100.0);
    }
}
