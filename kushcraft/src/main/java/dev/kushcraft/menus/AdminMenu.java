package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.Items;
import dev.kushcraft.machines.Machine;
import dev.kushcraft.machines.MachineType;
import dev.kushcraft.plants.Plant;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/**
 * The admin panel (the red button in the /kush top bar, or /kush admin).
 * One row per topic, its icon in column 0: players &amp; items, the world
 * around you, the market, the server. Layout: tools/gui.py admin().
 */
public final class AdminMenu extends Menu {

    static final int ROWS = 6;
    static final int BACK = 45;
    // players & items
    static final int GIVE = 10;
    static final int STRAINS = 11;
    static final int PLAYERS = 12;
    static final int WORKERS = 13;
    static final int CARTELS = 14;
    // around you
    static final int GROW = 19;
    static final int FINISH = 20;
    static final int SOBER = 21;
    static final int KIT = 22;
    // market
    static final int HOT = 28;
    static final int PRICES = 29;
    static final int CONTRACTS = 30;
    static final int BOOM = 31;
    static final int SHIPMENTS = 32;
    // server
    static final int RELOAD = 37;
    static final int PACK = 38;
    static final int TOGGLE_WORKERS = 39;
    static final int STATS = 40;

    private static final int NEAR = 16;

    public AdminMenu(Player player) {
        super(player, ROWS, "admin", "Admin Panel", false);
    }

    private static KushCraft plugin() {
        return KushCraft.get();
    }

    @Override
    public void render() {
        inv.clear();
        backButton(BACK);
        KushCraft plugin = plugin();
        set(9, Items.icon("ui_members", "<white><bold>Players & items"));
        set(GIVE, Items.icon("ui_crown", "<white>Give items", "<gray>Any KushCraft item."));
        set(STRAINS, Items.icon("seed_pack", "<white>Strains", "<gray>Seeds or buds of any strain,", "<gray>bred ones too."));
        set(PLAYERS, Items.icon("ui_wallet", "<white>Players", "<gray>Money, sales, starter kit, effects..."));
        set(WORKERS, Items.icon("ui_workers", "<white>All workers", "<gray>" + plugin.workers().all().size() + " hired"));
        set(CARTELS, Items.icon("tab_cartel", "<white>Cartels", "<gray>" + plugin.cartels().all().size()
                + " cartels: levels, bank, disband"));

        set(18, Items.icon("award_farmer", "<white><bold>Around you", "<gray>Within " + NEAR + " blocks."));
        set(GROW, Items.icon("bud_fresh", "<green>Grow plants", "<gray>Every plant around you is ripe now."));
        set(FINISH, Items.icon("machine_lab_station", "<green>Finish Drug Labs",
                "<gray>Cooking and drying around you is done now."));
        set(SOBER, Items.icon("effect_green_out", "<green>Sober up", "<gray>Clears your effects."));
        set(KIT, Items.icon("grower_guide", "<green>Starter kit", "<gray>Gives you the new-player kit", "<gray>(config: new-players)."));

        var market = plugin.market();
        set(27, Items.icon("ui_market", "<white><bold>Market"));
        set(HOT, Items.icon("ui_sell", "<gold>New hot item", "<gray>Now: " + (market.hot() == null ? "none" : market.hot().display())));
        set(PRICES, Items.icon("cash", "<gold>Reset prices", "<gray>Every product back to 100%."));
        set(CONTRACTS, Items.icon("award_order", "<gold>New contracts"));
        double boost = market.boost();
        set(BOOM, Items.glint(Items.icon("award_money_bag", boost > 1 ? "<light_purple>Boom on: +" + Math.round((boost - 1) * 100)
                        + "% <gray>(" + market.boostMinutesLeft() + " min)" : "<light_purple>Start a market boom",
                List.of("<gray>Click: everything sells for +50% for 30 min.", "<gray>Shift-click: stop it.")), boost > 1));
        set(SHIPMENTS, Items.icon("ui_shipment", "<gold>New cartel shipments", "<gray>Every cartel gets a new one."));

        set(36, Items.icon("ui_admin", "<white><bold>Server"));
        set(RELOAD, Items.icon("ui_upgrade", "<aqua>Reload config", "<gray>Same as /kush reload."));
        set(PACK, Items.icon("ui_info", "<aqua>Resend the pack", "<gray>To everyone online."));
        boolean on = plugin.workers().enabled();
        set(TOGGLE_WORKERS, Items.icon(on ? "ui_play" : "ui_pause", on ? "<aqua>Workers: <green>on" : "<aqua>Workers: <red>off",
                "<gray>Click to turn them " + (on ? "off" : "on") + "."));
        set(STATS, Items.icon("ui_info", "<aqua>Server stats",
                "<gray>Plants: <white>" + plugin.plants().all().size(),
                "<gray>Machines: <white>" + plugin.machines().all().size(),
                "<gray>Workers: <white>" + plugin.workers().all().size(),
                "<gray>Cartels: <white>" + plugin.cartels().all().size(),
                "<gray>Strains: <white>" + plugin.strains().all().size(),
                "<gray>Online: <white>" + Bukkit.getOnlinePlayers().size()));
    }

    @Override
    public void click(int slot, ClickType click) {
        if (!player.hasPermission("kushcraft.admin")) {
            player.closeInventory();
            return;
        }
        KushCraft plugin = plugin();
        switch (slot) {
            case GIVE -> openChild(new GiveMenu(player));
            case STRAINS -> openChild(new AdminStrainsMenu(player));
            case PLAYERS -> openChild(new AdminPlayersMenu(player));
            case WORKERS -> openChild(new MyWorkersMenu(player, true));
            case CARTELS -> openChild(new AdminCartelsMenu(player));
            case GROW -> done("Grew " + growNear() + " plants.");
            case FINISH -> done("Finished " + finishNear() + " Drug Labs.");
            case SOBER -> {
                plugin.effects().clear(player);
                done("Sober again.");
            }
            case KIT -> done("Gave " + dev.kushcraft.listeners.PlayerListener.starterKit(player) + " starter items.");
            case HOT -> {
                plugin.market().rerollHot();
                done("Hot item: " + (plugin.market().hot() == null ? "none" : plugin.market().hot().display()));
            }
            case PRICES -> {
                plugin.market().resetPrices();
                done("Prices are back to normal.");
            }
            case CONTRACTS -> {
                plugin.market().newOrders();
                done("New contracts are up.");
            }
            case BOOM -> {
                if (click.isShiftClick()) {
                    plugin.market().startBoost(1, 0);
                    done("The market boom is over.");
                } else {
                    plugin.market().startBoost(1.5, 30);
                    Bukkit.broadcast(Text.msg("<light_purple><bold>Market boom!</bold></light_purple> <gray>Everything sells for"
                            + " <green>+50%</green> for the next 30 minutes."));
                    done("Market boom started.");
                }
            }
            case SHIPMENTS -> {
                plugin.cartels().newShipments();
                done("Every cartel has a new shipment.");
            }
            case RELOAD -> {
                plugin.reload();
                done("Config reloaded.");
            }
            case PACK -> {
                Bukkit.getOnlinePlayers().forEach(o -> plugin.pack().resend(o));
                done("Pack sent to everyone online.");
            }
            case TOGGLE_WORKERS -> {
                plugin.getConfig().set("workers.enabled", !plugin.workers().enabled());
                plugin.saveConfig();
                done("Workers are " + (plugin.workers().enabled() ? "on" : "off") + ".");
            }
            default -> {
                return;
            }
        }
    }

    private void done(String msg) {
        player.sendActionBar(Text.mm("<green>" + msg));
        player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", SoundCategory.MASTER, 0.6f, 1.1f);
        render();
    }

    private int growNear() {
        Location me = player.getLocation();
        int n = 0;
        for (Plant p : plugin().plants().all()) {
            Location c = p.key().center();
            if (c != null && c.getWorld().equals(me.getWorld()) && c.distanceSquared(me) <= NEAR * NEAR && !p.mature()) {
                p.growth(100);
                plugin().plants().refresh(p);
                n++;
            }
        }
        plugin().plants().markDirty();
        return n;
    }

    private int finishNear() {
        Location me = player.getLocation();
        int n = 0;
        for (Machine m : plugin().machines().all()) {
            Location c = m.key().center();
            if (m.type() == MachineType.LAB_STATION && c != null && c.getWorld().equals(me.getWorld())
                    && c.distanceSquared(me) <= NEAR * NEAR) {
                m.finishNow();
                n++;
            }
        }
        return n;
    }
}
