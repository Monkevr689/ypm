package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effect.EffectType;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.lab.LabRecipe;
import dev.kushcraft.shop.Market;
import dev.kushcraft.shop.Ranks;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Home: your rank, how high you are, the hot item, what you unlock next and
 * the daily orders. Layout matches tools/gui.py home().
 */
public final class HomeMenu extends TabMenu {

    private static final int RANK = at(1, 1);
    private static final int STATUS = at(1, 3);
    private static final int HOT = at(1, 5);
    private static final int NEXT = at(1, 7);
    private static final int[] ORDER_SLOTS = {at(4, 0), at(4, 2), at(4, 4), at(4, 6), at(4, 8)};
    private static final int CLOSE = at(5, 0);
    private static final int PACK = at(5, 4);
    private static final int ADMIN = at(5, 8);

    private List<Market.Order> shownOrders = List.of();
    private int[] orderSlots = new int[0];

    public HomeMenu(Player player) {
        super(player, Tab.HOME);
    }

    public static void open(Player p) {
        new HomeMenu(p).open();
    }

    @Override
    protected void page() {
        KushCraft plugin = KushCraft.get();
        backButton(CLOSE);
        Ranks ranks = plugin.ranks();
        Ranks.Rank rank = ranks.of(player);
        Ranks.Rank next = ranks.next(rank);
        double sold = plugin.economy().sales(player);
        List<String> rl = new ArrayList<>();
        rl.add("<gray>Sold so far: <gold>" + money(sold));
        rl.add("<gray>Sale bonus: <green>+" + Math.round(rank.bonus() * 100) + "%");
        if (next != null) {
            double f = (sold - rank.sales()) / Math.max(1, next.sales() - rank.sales());
            rl.add("");
            rl.add("<gray>Next: " + next.colored() + " <gray>at " + money(next.sales()));
            rl.add("<gray>" + Text.bar(Math.max(0, Math.min(1, f)), 20, "gold", "dark_gray"));
        } else {
            rl.add("<gold>Top rank reached!");
        }
        rl.add("");
        rl.add("<dark_gray>Sell product at the Shop to rank up.");
        set(RANK, Items.icon("ui_crown", "<gold>Rank: " + rank.colored(), rl));

        double limit = Math.max(1, plugin.getConfig().getDouble("effects.green-out-at", 100));
        int high = (int) Math.round(Math.min(1, plugin.effects().high(player) / limit) * 100);
        List<String> sl = new ArrayList<>();
        sl.add("<gray>High: <white>" + high + "% " + Text.bar(high / 100.0, 10, "green", "dark_gray"));
        Map<EffectType, Integer> active = plugin.effects().active(player);
        if (active.isEmpty()) {
            sl.add("<dark_gray>No effects right now.");
        }
        for (Map.Entry<EffectType, Integer> e : active.entrySet()) {
            sl.add(" " + e.getKey().colored() + " <gray>" + Text.time(e.getValue()));
        }
        if (plugin.effects().pending(player) > 0) {
            sl.add("<gray>Something is still kicking in...");
        }
        sl.add("");
        sl.add("<dark_gray>At 100% you green out.");
        set(STATUS, Items.icon(active.isEmpty() ? "effect_euphoria" : active.keySet().iterator().next().icon(),
                "<light_purple>You: <white>" + high + "% high", sl));

        ItemType hot = plugin.market().hot();
        if (hot != null) {
            set(HOT, Items.glint(Items.icon(hot.model(), "<gold>Hot: <white>" + hot.display(),
                    "<gray>Sells for <gold>+" + Math.round((plugin.getConfig().getDouble("market.hot-item-bonus", 1.5) - 1) * 100)
                            + "%</gold> at the Shop", "<gray>for another " + plugin.market().hotMinutesLeft() + " min."), true));
        } else {
            set(HOT, Items.icon("ui_fire", "<gray>Nothing is hot right now."));
        }

        List<String> nl = new ArrayList<>();
        if (next == null) {
            nl.add("<gold>You've unlocked everything!");
        } else {
            nl.add("<gray>At " + next.colored() + "<gray>:");
            for (LabRecipe r : LabRecipe.values()) {
                if (Math.min(r.rank(), ranks.all().size()) == next.level()) {
                    nl.add(" <white>• " + r.output().display());
                }
            }
            nl.add(" <green>• +" + Math.round(next.bonus() * 100) + "% on every sale");
        }
        set(NEXT, Items.icon("ui_lock", "<aqua>Next unlock", nl));

        List<Market.Order> orders = plugin.market().orders();
        int n = Math.min(orders.size(), ORDER_SLOTS.length);
        int[][] layouts = {{}, {2}, {1, 3}, {1, 2, 3}, {0, 1, 3, 4}, {0, 1, 2, 3, 4}};
        orderSlots = new int[n];
        for (int i = 0; i < n; i++) {
            orderSlots[i] = ORDER_SLOTS[layouts[n][i]];
            Market.Order o = orders.get(i);
            set(orderSlots[i], orderIcon(o));
        }
        shownOrders = List.copyOf(orders.subList(0, n));

        set(PACK, Items.icon("ui_info", "<aqua>Texture pack",
                plugin.pack().hasPack(player) ? "<green>Loaded ✔" : "<red>Not loaded",
                "<gray>Click to download it again."));
        if (player.hasPermission("kushcraft.admin")) {
            set(ADMIN, Items.icon("ui_crown", "<red>Admin: give items", "<gray>Get any KushCraft item.",
                    "<dark_gray>Only ops see this."));
        }
    }

    private ItemStack orderIcon(Market.Order o) {
        KushCraft plugin = KushCraft.get();
        int have = dev.kushcraft.util.InventoryUtil.count(player, it -> Items.type(it) == o.type());
        long mins = Math.max(0, (o.expires() - System.currentTimeMillis()) / 60_000L);
        ItemStack it = CatalogIcons.sample(o.type());
        it.setAmount(Math.max(1, Math.min(99, o.amount())));
        boolean ok = have >= o.amount();
        it.editMeta(m -> {
            m.itemName(Text.mm("<gold>Order: <white>" + o.amount() + "x " + o.type().display()));
            m.lore(Text.lines(List.of(
                    "<gray>Pays: <gold>" + plugin.economy().format(o.reward()),
                    "<gray>You have: " + (ok ? "<green>" : "<red>") + have + "/" + o.amount(),
                    "<gray>Ends in " + mins + " min.",
                    "",
                    ok ? "<green><bold>Click to hand it in!" : "<dark_gray>The first player to hand it in wins.")));
        });
        return Items.glint(it, ok);
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        KushCraft plugin = KushCraft.get();
        for (int i = 0; i < orderSlots.length; i++) {
            if (orderSlots[i] == slot && i < shownOrders.size()) {
                Market.Order o = shownOrders.get(i);
                if (plugin.market().complete(player, o)) {
                    successSound();
                } else {
                    failSound();
                }
                render();
                return;
            }
        }
        if (slot == PACK) {
            clickSound();
            player.closeInventory();
            plugin.pack().send(player);
            player.sendMessage(Text.msg("<gray>Texture pack sent - accept the prompt."));
        } else if (slot == ADMIN && player.hasPermission("kushcraft.admin")) {
            openChild(new GiveMenu(player));
        }
    }

    @Override
    public void tick() {
        render();
    }
}
