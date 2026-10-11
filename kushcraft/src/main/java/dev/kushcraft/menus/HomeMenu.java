package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.guide.Guide;
import dev.kushcraft.items.Items;
import dev.kushcraft.storage.PlayerRecord;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/**
 * The simple home menu: what /kush, Shift+F and the Handbook open. A dozen
 * big buttons in two rows, nothing else. Everything else is one click away
 * (the shop and the other tabs have their own tab bar; the wallet in their
 * top corner brings you back here). Layout: tools/gui.py home().
 */
public final class HomeMenu extends Menu {

    static final int NEXT_STEP = 0;
    static final int YOU = 4;
    static final int ADMIN = 8;
    static final int SELL = 10;
    static final int SHOP = 12;
    static final int DRUGS = 14;
    static final int WORKERS = 16;
    static final int RANK = 28;
    static final int BAZAAR = 30;
    static final int TRADE = 32;
    static final int CARTEL = 34;
    static final int CLOSE = 36;
    static final int HANDBOOK = 38;
    static final int AWARDS = 40;
    static final int HELP = 42;

    public HomeMenu(Player player) {
        super(player, 5, "home", "Home", false);
    }

    /** Opens the home menu. */
    public static void open(Player p) {
        new HomeMenu(p).open();
    }

    @Override
    public void render() {
        inv.clear();
        KushCraft plugin = KushCraft.get();
        backButton(CLOSE);
        PlayerRecord r = plugin.economy().account(player.getUniqueId());
        var rank = plugin.ranks().of(player.getUniqueId());
        dev.kushcraft.cartels.Cartel cartel = plugin.cartels().enabled() ? plugin.cartels().of(player) : null;
        List<String> you = new java.util.ArrayList<>();
        you.add("<gold>" + plugin.economy().format(plugin.economy().balance(player)));
        you.add("<gray>Rank: " + rank.colored() + " <dark_gray>(" + rank.number() + "/" + plugin.ranks().top() + ")");
        you.add("<gray>Playtime: <white>" + Text.duration(r.playtime() * 1000L));
        if (cartel != null) {
            you.add("<gray>Cartel: " + cartel.colored());
        }
        set(YOU, AdminPlayersMenu.head(player, "<white>" + Text.escape(player.getName()), you));
        set(NEXT_STEP, StarterMenu.button(player));
        if (player.hasPermission("kushcraft.admin")) {
            set(ADMIN, Items.icon("ui_admin", "<red>Admin panel", "<dark_gray>Only admins see this button."));
        }

        double worth = Selling.allValue(player);
        set(SELL, worth > 0
                ? Items.glint(Items.icon("ui_sell", "<green>Sell all", "<gray>Everything you carry that sells:",
                "<gold>" + plugin.economy().format(worth), "<dark_gray>Click to sell"), true)
                : Items.icon("ui_sell", "<green>Sell all", "<gray>Nothing to sell right now.",
                "<dark_gray>Grow, dry and cook first."));
        set(SHOP, Items.icon("tab_shop", "<yellow>Shop", "<gray>Seeds, gear and workers,", "<gray>and the market contracts."));
        set(DRUGS, Items.icon("tab_drugs", "<green>Drugs", "<gray>Every product and how to make it."));
        int workers = plugin.workers().of(player.getUniqueId()).size();
        set(WORKERS, Items.icon("ui_workers", "<gold>My workers", "<gray>" + workers + " of " + plugin.workers().limit(player.getUniqueId())
                + " slots used", "<dark_gray>Supply, collect, manage"));
        set(RANK, Items.icon("ui_crown", "<light_purple>Rank up", "<gray>You are " + rank.colored(),
                "<dark_gray>What the next rank needs"));
        set(BAZAAR, Items.icon("ui_market", "<gold>Bazaar", "<gray>Buy from other players", "<gray>or sell them your goods."));
        set(TRADE, Items.icon("ui_exchange", "<aqua>Trade with a player", "<gray>Swap items and money safely", "<gray>with someone online."));
        set(CARTEL, Items.icon("tab_cartel", "<red>Cartel", cartel == null ? "<gray>Start or join a crew." : "<gray>" + Text.escape(cartel.name()),
                "<dark_gray>Bank, shipments, top dealers"));
        set(HANDBOOK, Items.icon("grower_guide", "<white>Handbook", "<gray>The written guide with every", "<gray>recipe picture."));
        set(AWARDS, Items.icon("tab_awards", "<light_purple>Awards", "<gray>Things to unlock."));
        set(HELP, Items.icon("ui_info", "<aqua>Server guide", "<gray>Economy, ranks, rules, community."));
    }

    @Override
    public void click(int slot, ClickType click) {
        KushCraft plugin = KushCraft.get();
        switch (slot) {
            case NEXT_STEP -> openChild(new StarterMenu(player));
            case ADMIN -> {
                if (player.hasPermission("kushcraft.admin")) {
                    openChild(new AdminMenu(player));
                }
            }
            case SELL -> {
                if (Selling.all(player)) {
                    successSound();
                } else {
                    failSound();
                }
                render();
            }
            case SHOP -> {
                clickSound();
                TabMenu.Tab.SHOP.open(player);
            }
            case DRUGS -> {
                clickSound();
                TabMenu.Tab.DRUGS.open(player);
            }
            case WORKERS -> openChild(new MyWorkersMenu(player));
            case RANK -> openChild(new RanksMenu(player));
            case BAZAAR -> openChild(new BazaarMenu(player));
            case TRADE -> {
                if (plugin.trades().enabled()) {
                    openChild(new TradePickMenu(player));
                } else {
                    failSound();
                    player.sendActionBar(Text.mm("<red>Player trading is turned off on this server."));
                }
            }
            case CARTEL -> {
                clickSound();
                TabMenu.Tab.CARTEL.open(player);
            }
            case HANDBOOK -> {
                clickSound();
                player.closeInventory();
                player.openBook(Guide.book(player));
            }
            case AWARDS -> {
                clickSound();
                TabMenu.Tab.AWARDS.open(player);
            }
            case HELP -> {
                clickSound();
                InfoMenu.openMain(player);
            }
            default -> {
            }
        }
    }

    @Override
    public void tick() {
        render();
    }
}
