package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.guide.Guide;
import dev.kushcraft.item.Items;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * /kush, Shift+F or the KushCraft Menu book - everything in one place, in
 * four colour-coded rows: LEARN (guide, recipes, catalog), TRADE (market,
 * exchange, orders), EARN (jobs, send money, top 10) and YOU (strains,
 * status, texture pack). Layout matches tools/gui.py main_menu().
 */
public final class MainMenu extends Menu {

    private static final int INFO = 0;
    private static final int WALLET = 4;
    private static final int ADMIN = 8;
    private static final int GUIDE = 9;
    private static final int RECIPES = 12;
    private static final int CATALOG = 15;
    private static final int MARKET = 18;
    private static final int EXCHANGE = 21;
    private static final int ORDERS = 24;
    private static final int JOBS = 27;
    private static final int PAY = 30;
    private static final int TOP = 33;
    private static final int STRAINS = 36;
    private static final int STATUS = 39;
    private static final int PACK = 42;
    private static final int CLOSE = 45;

    public MainMenu(Player player) {
        super(player, 6, "main", "KushCraft");
    }

    @Override
    public void render() {
        inv.clear();
        backButton(CLOSE);
        KushCraft plugin = KushCraft.get();
        set(INFO, Items.icon("ui_info", "<aqua>How to use this menu",
                "<gray>Click a button to open it.",
                "<gray>Every page has a <white>Back</white> button.",
                "",
                "<gray>Open this menu any time with:",
                "<white> /kush</white><gray>, <white>Shift + F</white><gray> (sneak +",
                "<gray> swap hands) or the <green>KushCraft Menu</green> book."));
        set(WALLET, Items.icon("ui_wallet", "<gold>Balance: <white>" + plugin.economy().format(plugin.economy().balance(player)),
                "<gray>Earn money at the Market, the",
                "<gray>Exchange, with Jobs and Daily Orders."));
        if (player.hasPermission("kushcraft.admin")) {
            set(ADMIN, Items.icon("ui_crown", "<red>Admin: give items",
                    "<gray>Get any KushCraft item for free.",
                    "<dark_gray>Only ops see this."));
        }
        // LEARN
        set(GUIDE, Items.icon("grower_guide", "<green><bold>Guide",
                "<gray>The handbook: how to grow, cook,",
                "<gray>roll and sell - with recipe pictures."));
        set(RECIPES, Items.icon("ui_recipes", "<green><bold>Recipes",
                "<gray>Every crafting and Drug Lab recipe",
                "<gray>shown in a crafting grid."));
        set(CATALOG, Items.icon("ui_catalog", "<green><bold>Drug Catalog",
                "<gray>Every product: what it does",
                "<gray>and what it's worth."));
        // TRADE
        boolean anywhere = plugin.getConfig().getBoolean("market.anywhere", true);
        set(MARKET, Items.icon("cash", "<gold><bold>Market",
                "<gray>Buy seeds, supplies and blocks.",
                "<gray>Sell your drugs.",
                anywhere ? "" : "<yellow>Only at a Dealer Stand on this server."));
        set(EXCHANGE, Items.icon("ui_exchange", "<gold><bold>Exchange",
                "<gray>Trade money for ores, food, wood,",
                "<gray>blocks and more - at fair prices.",
                plugin.exchange().enabled() ? "" : "<red>Turned off on this server."));
        set(ORDERS, Items.icon("ui_orders", "<gold><bold>Daily Orders",
                "<gray>Hand in batches for bonus cash.",
                "<gray>" + plugin.market().orders().size() + " open right now."));
        // EARN
        set(JOBS, Items.icon("ui_jobs", "<#f0a040><bold>Jobs",
                "<gray>Get paid for mining, farming,",
                "<gray>chopping, hunting and growing.",
                "<gray>This hour: <gold>" + plugin.economy().format(plugin.jobs().earnings(player).total())));
        set(PAY, Items.icon("ui_pay", "<#f0a040><bold>Send Money",
                "<gray>Pay another player."));
        set(TOP, Items.icon("ui_trophy", "<#f0a040><bold>Top Dealers",
                "<gray>Who has the most money?"));
        // YOU
        set(STRAINS, Items.icon("ui_dna", "<#8aa0ff><bold>Strains",
                "<gray>All strains, mix your own and",
                "<gray>give them a <white>name</white>."));
        double limit = Math.max(1, plugin.getConfig().getDouble("effects.green-out-at", 100));
        set(STATUS, Items.icon("effect_euphoria", "<#8aa0ff><bold>Your Status",
                "<gray>High: <white>" + (int) Math.round(Math.min(1, plugin.effects().high(player) / limit) * 100) + "%",
                "<gray>Active effects: <white>" + plugin.effects().active(player).size()));
        set(PACK, Items.icon("ui_info", "<#8aa0ff><bold>Texture Pack",
                plugin.pack().hasPack(player) ? "<green>Loaded ✔" : "<red>Not loaded",
                "<gray>Click to download it again."));
    }

    @Override
    public void click(int slot, ClickType click) {
        KushCraft plugin = KushCraft.get();
        switch (slot) {
            case GUIDE -> {
                clickSound();
                player.closeInventory();
                player.openBook(Guide.book(player));
            }
            case RECIPES -> openChild(new RecipesMenu(player));
            case CATALOG -> openChild(new CatalogMenu(player, false));
            case MARKET -> {
                if (!plugin.getConfig().getBoolean("market.anywhere", true) && !player.hasPermission("kushcraft.admin")) {
                    player.sendActionBar(Text.mm("<yellow>Find a <green>Dealer Stand</green> to use the market."));
                    failSound();
                    return;
                }
                openChild(new DealerMenu(player));
            }
            case EXCHANGE -> {
                if (!plugin.exchange().enabled()) {
                    player.sendActionBar(Text.mm("<red>The exchange is turned off on this server."));
                    failSound();
                    return;
                }
                openChild(new ExchangeMenu(player));
            }
            case ORDERS -> openChild(new OrdersMenu(player));
            case JOBS -> openChild(new JobsMenu(player));
            case PAY -> openChild(new PayMenu(player));
            case TOP -> openChild(new LeaderboardMenu(player));
            case STRAINS -> openChild(new StrainsMenu(player));
            case STATUS -> openChild(new StatusMenu(player));
            case PACK -> {
                clickSound();
                player.closeInventory();
                plugin.pack().send(player);
                player.sendMessage(Text.msg("<gray>Texture pack sent - accept the prompt."));
            }
            case ADMIN -> {
                if (player.hasPermission("kushcraft.admin")) {
                    openChild(new CatalogMenu(player, true));
                }
            }
            default -> {
            }
        }
    }
}
