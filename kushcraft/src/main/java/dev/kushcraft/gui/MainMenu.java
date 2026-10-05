package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.guide.Guide;
import dev.kushcraft.item.Items;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * /kush - the main menu. Everything a player needs, no other commands.
 * Layout matches tools/gui.py main_menu().
 */
public final class MainMenu extends Menu {

    private static final int WALLET = 0;
    private static final int ADMIN = 8;
    private static final int GUIDE = 10;
    private static final int MARKET = 12;
    private static final int CATALOG = 14;
    private static final int STRAINS = 16;
    private static final int ORDERS = 28;
    private static final int TOP = 30;
    private static final int STATUS = 32;
    private static final int PACK = 34;

    public MainMenu(Player player) {
        super(player, 5, "main", "KushCraft");
    }

    @Override
    public void render() {
        inv.clear();
        KushCraft plugin = KushCraft.get();
        set(WALLET, Items.icon("ui_wallet", "<gold>Balance: <white>" + plugin.economy().format(plugin.economy().balance(player)),
                "<gray>Earn money at the Market and", "<gray>by filling Daily Orders."));
        if (player.hasPermission("kushcraft.admin")) {
            set(ADMIN, Items.icon("ui_crown", "<red>Admin: give items",
                    "<gray>Get any KushCraft item for free.",
                    "<dark_gray>Only ops see this."));
        }
        set(GUIDE, Items.icon("grower_guide", "<green><bold>Guide",
                "<gray>How to grow, cook, roll and sell.",
                "<gray>Start here!"));
        boolean anywhere = plugin.getConfig().getBoolean("market.anywhere", true);
        set(MARKET, Items.icon("cash", "<green><bold>Market",
                "<gray>Buy seeds, supplies and blocks.",
                "<gray>Sell your product.",
                anywhere ? "" : "<yellow>Only at a Dealer Stand on this server."));
        set(CATALOG, Items.icon("ui_catalog", "<green><bold>Drug Catalog",
                "<gray>Every product: how to make it,",
                "<gray>what it does and what it's worth."));
        set(STRAINS, Items.icon("ui_dna", "<green><bold>Strains",
                "<gray>All strains, mix your own and",
                "<gray>give them a <white>name</white>."));
        set(ORDERS, Items.icon("ui_orders", "<gold><bold>Daily Orders",
                "<gray>Hand in batches for bonus cash.",
                "<gray>" + plugin.market().orders().size() + " open right now."));
        set(TOP, Items.icon("ui_trophy", "<gold><bold>Top Dealers",
                "<gray>Who has the most money?"));
        double limit = Math.max(1, plugin.getConfig().getDouble("effects.green-out-at", 100));
        set(STATUS, Items.icon("effect_euphoria", "<light_purple><bold>Your Status",
                "<gray>High: <white>" + (int) Math.round(Math.min(1, plugin.effects().high(player) / limit) * 100) + "%",
                "<gray>Active effects: <white>" + plugin.effects().active(player).size()));
        set(PACK, Items.icon("ui_info", "<aqua><bold>Texture Pack",
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
                player.openBook(Guide.book());
            }
            case MARKET -> {
                if (!plugin.getConfig().getBoolean("market.anywhere", true) && !player.hasPermission("kushcraft.admin")) {
                    player.sendActionBar(Text.mm("<yellow>Find a <green>Dealer Stand</green> to use the market."));
                    failSound();
                    return;
                }
                openChild(new DealerMenu(player));
            }
            case CATALOG -> openChild(new CatalogMenu(player, false));
            case STRAINS -> openChild(new StrainsMenu(player));
            case ORDERS -> openChild(new OrdersMenu(player));
            case TOP -> openChild(new LeaderboardMenu(player));
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
