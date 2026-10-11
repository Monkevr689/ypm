package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.economy.Economy;
import dev.kushcraft.items.Items;
import dev.kushcraft.trade.TradeSession;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The trade window. Your side is on the left (4 x 4 slots), theirs on the right. Click an item in your
 * own inventory to put it on the table (right-click: just one), click it on the table to take it back.
 * Money with the + button. Both press Ready and the swap happens. Closing the window cancels the trade.
 * Layout: tools/gui.py tradewin().
 */
public final class TradeWindow extends Menu {

    static final int YOU = 0;
    static final int ADD_MONEY = 1;
    static final int CLEAR_MONEY = 2;
    static final int STATUS = 4;
    static final int THEIR_MONEY = 6;
    static final int THEM = 8;
    static final int READY = 13;
    static final int THEIR_READY = 22;
    static final int LOCK = 31;
    static final int CANCEL = 45;

    private final TradeSession session;
    private final int side;

    public TradeWindow(Player player, TradeSession session, int side) {
        super(player, 6, "tradewin", "Trade", false);
        this.session = session;
        this.side = side;
    }

    private static KushCraft plugin() {
        return KushCraft.get();
    }

    private static int mineSlot(int i) {
        return 9 + (i / 4) * 9 + (i % 4);
    }

    private static int theirSlot(int i) {
        return 9 + (i / 4) * 9 + 5 + (i % 4);
    }

    @Override
    public void render() {
        inv.clear();
        KushCraft plugin = plugin();
        int other = 1 - side;
        Player them = session.player(other);
        backButton(CANCEL);
        set(CANCEL, Items.icon("ui_cancel", "<red>Cancel the trade", "<gray>Everyone gets their things back."));
        String fee = Math.round(plugin.getConfig().getDouble("trade.money-fee", 0.02) * 100) + "%";
        set(YOU, AdminPlayersMenu.head(player, "<aqua>You", List.of("<gray>Offering: <gold>"
                + plugin.economy().format(Economy.dollars(session.money(side))))));
        set(ADD_MONEY, Items.icon("ui_plus", "<green>Add money", "<gray>You have <gold>" + plugin.economy().format(
                        plugin.economy().balance(player)), "<white>Click: +$100  <white>Right: +$1,000",
                "<white>Shift: +$10,000  <white>Shift-right: +$100,000", "<dark_gray>" + fee + " of the money is a fee"));
        set(CLEAR_MONEY, Items.icon("ui_cancel", "<red>Take your money back", "<gray>Clears the money you offer."));
        set(THEIR_MONEY, Items.icon("ui_wallet", "<gold>" + plugin.economy().format(Economy.dollars(session.money(other))),
                "<gray>" + Text.escape(session.name(other)) + " offers this."));
        if (them != null) {
            set(THEM, AdminPlayersMenu.head(them, "<gold>" + Text.escape(them.getName()), List.of("<gray>Their side of the table.")));
        }
        for (int i = 0; i < TradeSession.SLOTS; i++) {
            ItemStack mine = session.item(side, i);
            if (mine != null) {
                set(mineSlot(i), withHint(mine, "<dark_gray>Click: take it back"));
            }
            ItemStack theirs = session.item(other, i);
            if (theirs != null) {
                set(theirSlot(i), theirs);
            }
        }
        int locked = session.lockedFor();
        boolean iAmReady = session.ready(side);
        set(READY, Items.glint(Items.icon(iAmReady ? "ui_confirm" : "ui_lock", iAmReady ? "<green><bold>You are ready" : locked > 0
                        ? "<yellow>Locked for " + locked + "s" : "<white><bold>Ready?",
                iAmReady ? List.of("<gray>Click to take it back.") : List.of("<gray>Check both sides, then click.",
                        "<dark_gray>Any change takes both Ready marks away.")), iAmReady));
        boolean theyReady = session.ready(other);
        set(THEIR_READY, Items.glint(Items.icon(theyReady ? "ui_confirm" : "ui_cancel",
                theyReady ? "<green>" + Text.escape(session.name(other)) + " is ready" : "<gray>" + Text.escape(session.name(other))
                        + " isn't ready yet"), theyReady));
        set(STATUS, Items.icon("ui_info", locked > 0 ? "<yellow>Something changed" : "<white>Trade with " + Text.escape(session.name(other)),
                locked > 0 ? List.of("<gray>Check it again.") : List.of("<gray>Put items and money on the table.",
                        "<gray>Both press Ready to swap.")));
        if (locked > 0) {
            set(LOCK, Items.amount(Items.icon("ui_lock", "<yellow>Ready is locked", "<gray>" + locked + "s left"), locked));
        }
    }

    private static ItemStack withHint(ItemStack it, String hint) {
        ItemStack c = it.clone();
        List<net.kyori.adventure.text.Component> lore = c.lore() == null ? new ArrayList<>() : new ArrayList<>(c.lore());
        lore.add(Text.mm(hint));
        c.lore(lore);
        return c;
    }

    @Override
    public void click(int slot, ClickType click) {
        String problem = null;
        if (slot == ADD_MONEY) {
            long cents = switch (click) {
                case LEFT -> 10_000L;
                case RIGHT -> 100_000L;
                case SHIFT_LEFT -> 1_000_000L;
                case SHIFT_RIGHT -> 10_000_000L;
                default -> 0L;
            };
            if (cents > 0) {
                problem = session.addMoney(side, cents);
            }
        } else if (slot == CLEAR_MONEY) {
            session.clearMoney(side);
        } else if (slot == READY) {
            problem = session.toggleReady(side);
        } else {
            for (int i = 0; i < TradeSession.SLOTS; i++) {
                if (slot == mineSlot(i)) {
                    problem = session.takeBack(side, i);
                }
            }
        }
        if (problem != null) {
            failSound();
            player.sendActionBar(Text.mm("<red>" + problem));
        } else {
            clickSound();
        }
        if (!session.over()) {
            render();
        }
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        String problem = session.add(side, slot, click.isRightClick());
        if (problem != null) {
            failSound();
            player.sendActionBar(Text.mm("<red>" + problem));
        } else {
            clickSound();
        }
    }

    @Override
    public void tick() {
        if (!session.over()) {
            render();
        }
    }

    @Override
    public void closed() {
        if (!session.over() && session.window(side) == this) {
            session.window(side, null);
            session.cancel(player.getName() + " closed the window.");
        }
    }
}
