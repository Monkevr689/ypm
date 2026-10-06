package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.Items;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The /kush menu: five tabs along the top (Shop, Drugs, Trade, Top, Awards)
 * and your money in the corner. Rows 1-5 belong to the page. The active tab
 * is drawn into each page's background (tools/gui.py tab_page()).
 */
public abstract class TabMenu extends Menu {

    public enum Tab {
        SHOP("Shop", "tab_shop", ShopMenu::new),
        DRUGS("Drugs", "tab_drugs", DrugsMenu::new),
        TRADE("Trade", "tab_trade", TradeMenu::new),
        TOP("Top Dealers", "tab_top", TopMenu::new),
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

    static final int WALLET = 8;
    private static final Map<UUID, Tab> LAST = new HashMap<>();

    protected final Tab tab;

    protected TabMenu(Player player, Tab tab) {
        super(player, 6, tab.name().toLowerCase(Locale.ROOT), "KushCraft · " + tab.display(), false);
        this.tab = tab;
        LAST.put(player.getUniqueId(), tab);
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
        set(WALLET, Items.icon("ui_wallet", "<gold>" + money(plugin.economy().balance(player)),
                plugin.ranks().label(player)));
        page();
    }

    /** Draws rows 1-5. */
    protected abstract void page();

    @Override
    public final void click(int slot, ClickType click) {
        if (slot < Tab.values().length) {
            Tab t = Tab.values()[slot];
            if (t != tab) {
                clickSound();
                t.open(player);
            }
            return;
        }
        if (slot >= 9) {
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
