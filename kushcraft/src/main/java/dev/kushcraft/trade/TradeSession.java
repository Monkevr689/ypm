package dev.kushcraft.trade;

import dev.kushcraft.KushCraft;
import dev.kushcraft.economy.Economy;
import dev.kushcraft.economy.Tx;
import dev.kushcraft.menus.TradeWindow;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One player-to-player trade. Each side puts items (taken out of their inventory into this trade, so
 * nothing can be spent or dropped while it is on the table) and money on the table, and both press Ready.
 * Anything that changes on either side takes both Ready marks away and locks the buttons for a few
 * seconds, so nobody can swap an offer at the last moment. When both are ready the swap happens in one
 * server tick: the money moves in one step (see Economy.exchange), the items go to the other side.
 * Closing the window, leaving, dying or a timeout cancels it and everyone gets their own things back.
 */
public final class TradeSession {

    /** Item slots per side (4 rows of 4 in the window). */
    public static final int SLOTS = 16;

    private final KushCraft plugin;
    private final Trades trades;
    private final UUID[] ids = new UUID[2];
    private final String[] names = new String[2];
    private final ItemStack[][] offer = new ItemStack[2][SLOTS];
    private final long[] money = new long[2];
    private final boolean[] ready = new boolean[2];
    private final TradeWindow[] windows = new TradeWindow[2];
    private long lockUntil;
    private long touched = System.currentTimeMillis();
    private boolean over;

    TradeSession(KushCraft plugin, Trades trades, Player a, Player b) {
        this.plugin = plugin;
        this.trades = trades;
        ids[0] = a.getUniqueId();
        ids[1] = b.getUniqueId();
        names[0] = a.getName();
        names[1] = b.getName();
    }

    // ------------------------------------------------------------------
    // looking
    // ------------------------------------------------------------------

    public int side(Player p) {
        return p.getUniqueId().equals(ids[0]) ? 0 : p.getUniqueId().equals(ids[1]) ? 1 : -1;
    }

    public String name(int side) {
        return names[side];
    }

    public UUID id(int side) {
        return ids[side];
    }

    public Player player(int side) {
        return Bukkit.getPlayer(ids[side]);
    }

    /** A copy of what a side has on the table in slot i (null = empty). */
    public ItemStack item(int side, int i) {
        ItemStack it = offer[side][i];
        return it == null ? null : it.clone();
    }

    public long money(int side) {
        return money[side];
    }

    public boolean ready(int side) {
        return ready[side];
    }

    public boolean over() {
        return over;
    }

    public long touched() {
        return touched;
    }

    /** Seconds the Ready buttons stay locked after a change (0 = open). */
    public int lockedFor() {
        return (int) Math.max(0, Math.ceil((lockUntil - System.currentTimeMillis()) / 1000.0));
    }

    public boolean empty() {
        for (int s = 0; s < 2; s++) {
            if (money[s] > 0) {
                return false;
            }
            for (ItemStack it : offer[s]) {
                if (it != null) {
                    return false;
                }
            }
        }
        return true;
    }

    public void window(int side, TradeWindow w) {
        windows[side] = w;
    }

    public TradeWindow window(int side) {
        return windows[side];
    }

    public void refresh() {
        for (TradeWindow w : windows) {
            if (w != null) {
                w.render();
            }
        }
    }

    // ------------------------------------------------------------------
    // changing the offers
    // ------------------------------------------------------------------

    private void changed() {
        ready[0] = false;
        ready[1] = false;
        touched = System.currentTimeMillis();
        lockUntil = touched + Math.max(0, plugin.getConfig().getLong("trade.lock-seconds", 3)) * 1000L;
        refresh();
    }

    /** Moves (part of) an item from a player's inventory onto the table. */
    public String add(int side, int invSlot, boolean justOne) {
        if (over) {
            return "The trade is over.";
        }
        Player p = player(side);
        if (p == null) {
            return "They left.";
        }
        ItemStack have = p.getInventory().getItem(invSlot);
        if (have == null || have.getType().isAir()) {
            return null;
        }
        int n = justOne ? 1 : have.getAmount();
        ItemStack put = have.clone();
        put.setAmount(n);
        int target = -1;
        for (int i = 0; i < SLOTS && target < 0; i++) {
            ItemStack at = offer[side][i];
            if (at != null && at.isSimilar(put) && at.getAmount() + n <= at.getMaxStackSize()) {
                target = i;
            }
        }
        for (int i = 0; i < SLOTS && target < 0; i++) {
            if (offer[side][i] == null) {
                target = i;
            }
        }
        if (target < 0) {
            return "Your side of the table is full.";
        }
        if (offer[side][target] == null) {
            offer[side][target] = put;
        } else {
            offer[side][target].setAmount(offer[side][target].getAmount() + n);
        }
        if (n >= have.getAmount()) {
            p.getInventory().setItem(invSlot, null);
        } else {
            have.setAmount(have.getAmount() - n);
            p.getInventory().setItem(invSlot, have);
        }
        plugin.persistence().took(p);
        changed();
        return null;
    }

    /** Takes a stack off the table back into the owner's inventory. */
    public String takeBack(int side, int i) {
        if (over || i < 0 || i >= SLOTS || offer[side][i] == null) {
            return null;
        }
        Player p = player(side);
        if (p == null) {
            return "They left.";
        }
        ItemStack it = offer[side][i];
        if (!Bazaar.fits(p, it)) {
            return "Your inventory is full.";
        }
        offer[side][i] = null;
        InventoryUtil.give(p, it);
        changed();
        return null;
    }

    /** Offers more money (never more than they have). */
    public String addMoney(int side, long cents) {
        if (over) {
            return "The trade is over.";
        }
        long balance = Economy.cents(plugin.economy().balance(ids[side]));
        if (money[side] + cents > balance) {
            money[side] = balance;
            changed();
            return balance == 0 ? "You have no money." : "That's all you have.";
        }
        money[side] += cents;
        changed();
        return null;
    }

    public void clearMoney(int side) {
        if (!over && money[side] > 0) {
            money[side] = 0;
            changed();
        }
    }

    // ------------------------------------------------------------------
    // ready and the swap
    // ------------------------------------------------------------------

    public String toggleReady(int side) {
        if (over) {
            return "The trade is over.";
        }
        if (ready[side]) {
            ready[side] = false;
            refresh();
            return null;
        }
        if (lockedFor() > 0) {
            return "Wait " + lockedFor() + "s: something just changed.";
        }
        if (empty()) {
            return "There is nothing on the table.";
        }
        touched = System.currentTimeMillis();
        ready[side] = true;
        refresh();
        if (ready[0] && ready[1]) {
            complete();
        }
        return null;
    }

    private void complete() {
        Player a = player(0);
        Player b = player(1);
        if (a == null || b == null) {
            cancel("The other player left.");
            return;
        }
        // money first: both must still have it
        long balA = Economy.cents(plugin.economy().balance(ids[0]));
        long balB = Economy.cents(plugin.economy().balance(ids[1]));
        if (money[0] > balA || money[1] > balB) {
            String who = money[0] > balA ? names[0] : names[1];
            ready[0] = false;
            ready[1] = false;
            tell("<red>" + Text.escape(who) + " no longer has the money they offered. Check the offers again.");
            clampMoney(balA, balB);
            changed();
            return;
        }
        ItemStack[] toA = items(1);
        ItemStack[] toB = items(0);
        if (!Bazaar.fits(a, toA) || !Bazaar.fits(b, toB)) {
            ready[0] = false;
            ready[1] = false;
            tell("<red>" + Text.escape(!Bazaar.fits(a, toA) ? names[0] : names[1]) + " doesn't have room in their inventory."
                    + " <gray>Make space and press Ready again.");
            refresh();
            return;
        }
        String gaveA = summary(toB, money[0]);
        String gaveB = summary(toA, money[1]);
        double fee = plugin.getConfig().getDouble("trade.money-fee", 0.02);
        if (!plugin.economy().exchange(ids[0], money[0], ids[1], money[1], fee, Tx.PLAYER_TRADE,
                "traded with " + names[1] + ": gave " + gaveA + ", got " + gaveB,
                "traded with " + names[0] + ": gave " + gaveB + ", got " + gaveA)) {
            ready[0] = false;
            ready[1] = false;
            tell("<red>The trade could not be completed.");
            refresh();
            return;
        }
        over = true;
        for (int s = 0; s < 2; s++) {
            java.util.Arrays.fill(offer[s], null);
        }
        InventoryUtil.give(a, toA);
        InventoryUtil.give(b, toB);
        trades.finished(this);
        for (Player p : new Player[]{a, b}) {
            p.sendMessage(Text.msg("<green><bold>Trade complete!</bold></green> <gray>Check your inventory and your balance."));
            p.playSound(p.getLocation(), "minecraft:entity.player.levelup", 0.6f, 1.4f);
        }
        closeWindows();
    }

    private void clampMoney(long balA, long balB) {
        money[0] = Math.min(money[0], balA);
        money[1] = Math.min(money[1], balB);
    }

    private ItemStack[] items(int side) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack it : offer[side]) {
            if (it != null) {
                out.add(it.clone());
            }
        }
        return out.toArray(new ItemStack[0]);
    }

    private String summary(ItemStack[] items, long cents) {
        StringBuilder b = new StringBuilder();
        for (ItemStack it : items) {
            b.append(b.isEmpty() ? "" : ", ").append(Bazaar.describe(it));
        }
        if (cents > 0) {
            b.append(b.isEmpty() ? "" : " + ").append(plugin.economy().format(Economy.dollars(cents)));
        }
        String out = b.isEmpty() ? "nothing" : b.toString();
        return out.length() > 80 ? out.substring(0, 77) + "..." : out;
    }

    private void tell(String miniMessage) {
        for (int s = 0; s < 2; s++) {
            Player p = player(s);
            if (p != null) {
                p.sendMessage(Text.msg(miniMessage));
            }
        }
    }

    // ------------------------------------------------------------------
    // ending
    // ------------------------------------------------------------------

    /** Ends the trade: everyone gets their own items back, the money never moved. */
    public void cancel(String reason) {
        cancel(reason, null, null);
    }

    /**
     * Same, but for a player who just died: their items go into the death drops (a death event clears the
     * inventory after the handlers ran, so anything handed back to it would vanish). Null drops = give back normally.
     */
    public void cancel(String reason, UUID dying, List<ItemStack> deathDrops) {
        if (over) {
            return;
        }
        over = true;
        for (int s = 0; s < 2; s++) {
            Player p = player(s);
            ItemStack[] mine = items(s);
            java.util.Arrays.fill(offer[s], null);
            if (p != null && deathDrops != null && ids[s].equals(dying)) {
                deathDrops.addAll(List.of(mine));
                p.sendMessage(Text.msg("<yellow>Trade cancelled.</yellow> <gray>" + reason));
            } else if (p != null) {
                if (mine.length > 0) {
                    InventoryUtil.give(p, mine);
                }
                p.sendMessage(Text.msg("<yellow>Trade cancelled.</yellow> <gray>" + reason
                        + (mine.length > 0 ? " Your items are back in your inventory." : "")));
            } else {
                // offline (can't happen while they hold the window, but never lose their things)
                trades.holdForOffline(ids[s], mine);
            }
        }
        trades.finished(this);
        closeWindows();
    }

    private void closeWindows() {
        for (int s = 0; s < 2; s++) {
            TradeWindow w = windows[s];
            windows[s] = null;
            Player p = player(s);
            if (w != null && p != null && p.getOpenInventory().getTopInventory() == w.getInventory()) {
                p.closeInventory();
            }
        }
    }
}
