package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.Items;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The /kush menu: five tabs along the top (Shop, Drugs, Trade, Cartel,
 * Awards), then a free slot for the page (5), the admin panel (6, admins
 * only), the guide button (7: your next step) and your money (8). Rows 1-5
 * belong to the page. The active tab is drawn into each page's background
 * (tools/gui.py tab_page()). Top Dealers is a page inside the Cartel tab.
 */
public abstract class TabMenu extends Menu {

    public enum Tab {
        SHOP("Shop", "tab_shop", ShopMenu::new),
        DRUGS("Drugs", "tab_drugs", DrugsMenu::new),
        TRADE("Trade", "tab_trade", TradeMenu::new),
        CARTEL("Cartel", "tab_cartel", CartelMenu::new),
        AWARDS("Awards", "tab_awards", AwardsMenu::new);

        private final String display;
        private final String icon;
        private final Function<Player, TabMenu> page;

        Tab(String display, String icon, Function<Player, TabMenu> page) {
            this.display = display;
            this.icon = icon;
            this.page = page;
        }

        public String display() {
            return display;
        }

        public String icon() {
            return icon;
        }

        public void open(Player p) {
            page.apply(p).open();
        }
    }

    /** A button of the page itself (e.g. Awards: next page). */
    static final int EXTRA = 5;
    static final int ADMIN = 6;
    static final int GUIDE = 7;
    static final int WALLET = 8;
    private static final Map<UUID, Tab> LAST = new HashMap<>();

    protected final Tab tab;

    protected TabMenu(Player player, Tab tab) {
        this(player, tab, tab.name().toLowerCase(Locale.ROOT), tab.display());
    }

    /** A page inside a tab with its own background (gui) and title. */
    protected TabMenu(Player player, Tab tab, String gui, String title) {
        super(player, 6, gui, "KushCraft · " + title, false);
        this.tab = tab;
        LAST.put(player.getUniqueId(), tab);
    }

    /** True for pages inside a tab (clicking the tab goes back to its main page). */
    protected boolean subPage() {
        return false;
    }

    /** /kush, Shift+F and the menu book: opens the tab you used last. */
    public static void openMain(Player p) {
        LAST.getOrDefault(p.getUniqueId(), Tab.SHOP).open(p);
    }

    @Override
    public final void render() {
        inv.clear();
        KushCraft plugin = KushCraft.get();
        for (Tab t : Tab.values()) {
            set(t.ordinal(), Items.icon(t.icon, (t == tab ? "<green>" : "<gray>") + t.display));
        }
        dev.kushcraft.cartels.Cartel cartel = plugin.cartels().enabled() ? plugin.cartels().of(player) : null;
        set(WALLET, Items.icon("ui_wallet", "<gold>" + money(plugin.economy().balance(player)),
                cartel == null ? java.util.List.of(plugin.titles().label(player))
                        : java.util.List.of(plugin.titles().label(player), cartel.colored())));
        set(GUIDE, StarterMenu.button(player));
        if (player.hasPermission("kushcraft.admin")) {
            set(ADMIN, Items.icon("ui_admin", "<red>Admin panel", "<dark_gray>Only admins see this button."));
        }
        page();
    }

    /** Draws rows 1-5. */
    protected abstract void page();

    @Override
    public final void click(int slot, ClickType click) {
        if (slot < Tab.values().length) {
            Tab t = Tab.values()[slot];
            if (t != tab || subPage()) {
                clickSound();
                t.open(player);
            }
            return;
        }
        if (slot == GUIDE) {
            openChild(new StarterMenu(player));
            return;
        }
        if (slot == ADMIN && player.hasPermission("kushcraft.admin")) {
            openChild(new AdminMenu(player));
            return;
        }
        if (slot >= 9 || slot == EXTRA) {
            clickPage(slot, click);
        }
    }

    protected void clickPage(int slot, ClickType click) {
    }

    /** Slot of a page cell. */
    protected static int at(int row, int col) {
        return row * 9 + col;
    }

    /** Money shown with the configured symbol. */
    protected static String money(double v) {
        return KushCraft.get().economy().format(v);
    }

    protected static String plain(String mm) {
        return Text.plain(Text.mm(mm));
    }
}
