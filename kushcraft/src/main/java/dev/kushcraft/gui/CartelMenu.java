package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.cartel.Cartel;
import dev.kushcraft.cartel.Cartels;
import dev.kushcraft.item.Items;
import dev.kushcraft.shop.Market;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Cartel: your cartel (banner, bank, level, members), the daily contracts
 * everyone can fill, your cartel's shipment and the way to Top Dealers.
 * Without a cartel the top row starts one or shows your invites.
 * Layout matches tools/gui.py cartel().
 */
public final class CartelMenu extends TabMenu {

    static final int BANNER = at(1, 1);
    static final int BANK = at(1, 3);
    static final int LEVEL = at(1, 5);
    static final int MEMBERS = at(1, 7);
    static final int[] CONTRACTS = {at(3, 1), at(3, 2), at(3, 3)};
    static final int SHIPMENT = at(3, 6);
    static final int TOP = at(5, 4);

    private List<Market.Order> shownOrders = List.of();
    private List<Cartel> shownInvites = List.of();
    private long leaveClick;
    private int ticks;

    public CartelMenu(Player player) {
        super(player, Tab.CARTEL);
    }

    private static Cartels cartels() {
        return KushCraft.get().cartels();
    }

    @Override
    protected void page() {
        KushCraft plugin = KushCraft.get();
        Cartel c = cartels().enabled() ? cartels().of(player) : null;
        if (!cartels().enabled()) {
            set(BANNER, Items.icon("ui_cancel", "<gray>Cartels are off on this server."));
        } else if (c == null) {
            noCartel();
        } else {
            cartelRow(c);
        }
        List<Market.Order> orders = plugin.market().orders();
        shownOrders = List.copyOf(orders.subList(0, Math.min(orders.size(), CONTRACTS.length)));
        for (int i = 0; i < shownOrders.size(); i++) {
            set(CONTRACTS[i], contractIcon(shownOrders.get(i), c != null));
        }
        set(SHIPMENT, shipmentIcon(c));
        set(TOP, Items.icon("award_crown", "<gold>Top Dealers", plugin.ranks().label(player),
                "<dark_gray>Click: the leaderboards"));
    }

    private void noCartel() {
        Cartels cs = cartels();
        set(BANNER, Items.glint(Items.tintedIcon("cartel_banner", 0x9A9AA8, "<white>Start a cartel <gold>"
                + money(cs.createCost()), List.of("<gray>Shared bank, levels, shipments.",
                "<dark_gray>Click and type a name.")), false));
        shownInvites = cs.invitesFor(player.getUniqueId());
        int[] slots = {BANK, LEVEL, MEMBERS};
        for (int i = 0; i < shownInvites.size() && i < slots.length; i++) {
            Cartel inv = shownInvites.get(i);
            set(slots[i], Items.glint(Items.tintedIcon("cartel_banner", inv.color(), "<green>Join " + inv.colored(),
                    List.of("<gray>" + inv.members().size() + " members · " + cs.tier(inv).name(),
                            "<dark_gray>You were invited.")), true));
        }
    }

    private void cartelRow(Cartel c) {
        Cartels cs = cartels();
        boolean boss = c.isLeader(player.getUniqueId());
        int place = cs.place(c);
        List<String> banner = new ArrayList<>();
        banner.add("<white>#" + place + " <gray>cartel · <gold>" + money(c.sales()) + " <dark_gray>sold");
        banner.add("<gray>Boss: <white>" + Text.escape(Cartels.name(c.leader())));
        if (boss) {
            banner.add("<dark_gray>Right-click: colour");
        }
        banner.add("<dark_gray>Shift-click twice: leave");
        set(BANNER, Items.tintedIcon("cartel_banner", c.color(), c.colored(), banner));

        List<String> bank = new ArrayList<>();
        bank.add("<gray>+" + pct(KushCraft.get().getConfig().getDouble("cartel.bank-cut", 0.05)) + " of every sale");
        bank.add("<dark_gray>Click: put in $1,000 · Shift: $10,000");
        if (boss) {
            bank.add("<dark_gray>Right-click: take out $1,000");
        }
        set(BANK, Items.icon("ui_bank", "<gold>" + money(c.bank()), bank));

        Cartels.Tier tier = cs.tier(c), next = cs.next(c);
        List<String> lvl = new ArrayList<>();
        lvl.add(perks(tier));
        if (next != null) {
            lvl.add("<gray>Next: <white>" + next.name() + " <dark_gray>- " + perks(next));
            lvl.add((c.bank() >= next.cost() ? "<green>" : "<red>") + money(next.cost()) + " <dark_gray>from the bank"
                    + (boss ? "" : " (boss)"));
        }
        set(LEVEL, Items.glint(Items.amount(Items.icon("ui_upgrade", "<gold>" + tier.name() + " <gray>(level "
                + c.level() + ")", lvl), c.level()), boss && next != null && c.bank() >= next.cost()));

        set(MEMBERS, Items.icon("ui_members", "<white>" + c.members().size() + "/" + tier.members() + " members",
                "<dark_gray>Click: members & invites"));
    }

    private static String perks(Cartels.Tier t) {
        List<String> out = new ArrayList<>();
        if (t.sell() > 0) {
            out.add("+" + pct(t.sell()) + " sales");
        }
        if (t.grow() > 0) {
            out.add("+" + pct(t.grow()) + " growth");
        }
        if (t.lab() > 0) {
            out.add(pct(t.lab()) + " faster lab");
        }
        return out.isEmpty() ? "<dark_gray>No bonus yet" : "<green>" + String.join(" · ", out);
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    private ItemStack contractIcon(Market.Order o, boolean inCartel) {
        int have = InventoryUtil.count(player, it -> Items.type(it) == o.type());
        long mins = Math.max(0, (o.expires() - System.currentTimeMillis()) / 60_000L);
        ItemStack it = CatalogIcons.sample(o.type());
        it.setAmount(Math.max(1, Math.min(99, o.amount())));
        boolean ok = have >= o.amount();
        it.editMeta(m -> {
            m.itemName(Text.mm("<yellow>Contract: <white>" + o.amount() + "x " + o.type().display()));
            List<String> lore = new ArrayList<>();
            lore.add("<gold>Pays " + money(o.reward()) + (inCartel ? " <dark_gray>+ bank cut" : ""));
            lore.add((ok ? "<green>" : "<gray>") + have + "/" + o.amount() + " <dark_gray>· " + mins + " min left");
            m.lore(Text.lines(lore));
        });
        return Items.glint(it, ok);
    }

    private ItemStack shipmentIcon(Cartel c) {
        if (c == null) {
            return Items.icon("ui_shipment", "<gray>Shipments", "<dark_gray>Cartels get big shipments.");
        }
        Cartel.Shipment s = c.shipment();
        if (s == null) {
            long mins = Math.max(0, (c.nextShipment() - System.currentTimeMillis()) / 60_000L) + 1;
            return Items.icon("ui_shipment", "<gray>Next shipment in " + mins + " min");
        }
        int have = InventoryUtil.count(player, it -> Items.type(it) == s.type());
        long hours = Math.max(0, (s.expires() - System.currentTimeMillis()) / 3_600_000L);
        ItemStack it = CatalogIcons.sample(s.type());
        it.setAmount(Math.max(1, Math.min(99, s.left())));
        it.editMeta(m -> {
            m.itemName(Text.mm("<gold>Shipment: <white>" + s.amount() + "x " + s.type().display()));
            m.lore(Text.lines(List.of(
                    Text.bar(s.delivered() / (double) s.amount(), 10, "gold", "dark_gray") + " <white>"
                            + s.delivered() + "/" + s.amount(),
                    "<gold>" + money(s.reward()) + " <gray>to the bank · " + hours + "h left",
                    have > 0 ? "<green>Click: deliver " + Math.min(have, s.left()) : "<dark_gray>Every member can deliver.")));
        });
        return Items.glint(it, have > 0);
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        KushCraft plugin = KushCraft.get();
        Cartels cs = cartels();
        Cartel c = cs.enabled() ? cs.of(player) : null;
        if (slot == TOP) {
            openChild(new TopMenu(player));
            return;
        }
        for (int i = 0; i < shownOrders.size(); i++) {
            if (CONTRACTS[i] == slot) {
                if (plugin.market().complete(player, shownOrders.get(i))) {
                    successSound();
                } else {
                    failSound();
                }
                render();
                return;
            }
        }
        if (!cs.enabled()) {
            return;
        }
        if (slot == SHIPMENT) {
            result(cs.deliver(player));
            return;
        }
        if (c == null) {
            if (slot == BANNER) {
                ChatInput.ask(player, "<green>Name your cartel!</green> <gray>(3-16 letters)", name -> {
                    result(cs.create(player, name.trim()));
                    reopen();
                }, this::reopen);
                return;
            }
            int[] slots = {BANK, LEVEL, MEMBERS};
            for (int i = 0; i < shownInvites.size() && i < slots.length; i++) {
                if (slot == slots[i]) {
                    result(cs.join(player, shownInvites.get(i)));
                    return;
                }
            }
            return;
        }
        boolean boss = c.isLeader(player.getUniqueId());
        if (slot == BANNER) {
            if (click.isShiftClick()) {
                if (System.currentTimeMillis() - leaveClick < 5000) {
                    result(cs.leave(player));
                } else {
                    leaveClick = System.currentTimeMillis();
                    player.sendActionBar(Text.mm("<red>Shift-click again to leave " + c.colored()));
                    failSound();
                }
            } else if (click.isRightClick() && boss) {
                result(cs.recolor(player));
            }
        } else if (slot == BANK) {
            if (click.isRightClick()) {
                result(cs.withdraw(player, click.isShiftClick() ? 10_000 : 1_000));
            } else {
                result(cs.deposit(player, click.isShiftClick() ? 10_000 : 1_000));
            }
        } else if (slot == LEVEL) {
            result(cs.upgrade(player));
        } else if (slot == MEMBERS) {
            openChild(new CartelMembersMenu(player));
        }
    }

    private void result(String error) {
        if (error != null) {
            player.sendActionBar(Text.mm("<red>" + error));
            failSound();
        } else {
            player.playSound(player.getLocation(), "minecraft:block.chain.place", SoundCategory.PLAYERS, 0.7f, 1.4f);
        }
        if (player.getOpenInventory().getTopInventory() == inv) {
            render();
        }
    }

    private void reopen() {
        if (player.isOnline()) {
            new CartelMenu(player).open();
        }
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (Selling.clicked(player, slot, item, click)) {
            render();
        }
    }

    @Override
    public void tick() {
        if (++ticks % 10 == 0) {
            render();
        }
    }
}
