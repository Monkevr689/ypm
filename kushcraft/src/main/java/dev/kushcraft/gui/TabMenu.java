package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.guide.Guide;
import dev.kushcraft.item.Items;
import dev.kushcraft.shop.Ranks;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.function.Function;

/**
 * A main page with the tab bar on top: Home, Drugs, Shop, Breed, Jobs,
 * Trade and Bank are always one click away, plus the handbook and your
 * wallet. The page itself uses rows 1-5. The tab labels and the highlighted
 * tab are drawn into each page's background (tools/gui.py tab_page()).
 */
public abstract class TabMenu extends Menu {

    public enum Tab {
        HOME("Home", "ui_home", HomeMenu::new),
        DRUGS("Drugs & Recipes", "ui_catalog", DrugsMenu::new),
        SHOP("Shop", "cash", ShopMenu::new),
        BREED("Breed Strains", "ui_dna", BreedMenu::new),
        JOBS("Jobs", "ui_jobs", JobsMenu::new),
        TRADE("Trade Resources", "ui_exchange", TradeMenu::new),
        BANK("Bank", "ui_bank", BankMenu::new);

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

        public void open(Player p) {
            page.apply(p).open();
        }
    }

    static final int BOOK = 7;
    static final int WALLET = 8;

    protected final Tab tab;

    protected TabMenu(Player player, Tab tab) {
        super(player, 6, tab.name().toLowerCase(java.util.Locale.ROOT), "KushCraft - " + tab.display(), false);
        this.tab = tab;
    }

    @Override
    public final void render() {
        inv.clear();
        KushCraft plugin = KushCraft.get();
        for (Tab t : Tab.values()) {
            boolean on = t == tab;
            set(t.ordinal(), Items.glint(Items.icon(t.icon, (on ? "<green>▶ " : "<yellow>") + t.display,
                    on ? "<gray>You are here." : "<gray>Click to open."), on));
        }
        set(BOOK, Items.icon("grower_guide", "<yellow>Handbook",
                "<gray>How everything works,",
                "<gray>with a picture of every recipe."));
        Ranks.Rank rank = plugin.ranks().of(player);
        set(WALLET, Items.icon("ui_wallet", "<gold>" + plugin.economy().format(plugin.economy().balance(player)),
                "<gray>Rank: " + rank.colored(),
                "<gray>Click for the Bank."));
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
        if (slot == BOOK) {
            clickSound();
            player.closeInventory();
            player.openBook(Guide.book(player));
            return;
        }
        if (slot == WALLET) {
            if (tab != Tab.BANK) {
                clickSound();
                Tab.BANK.open(player);
            }
            return;
        }
        clickPage(slot, click);
    }

    protected void clickPage(int slot, ClickType click) {
    }

    /** Row/column of the page area (row 1-5). */
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
