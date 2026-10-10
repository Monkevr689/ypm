package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.Items;
import dev.kushcraft.listeners.PlayerListener;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/** Admin: one player's money, sales and a few helpers. Uses the admin background. */
public final class AdminPlayerMenu extends Menu {

    static final int HEAD = 4;
    static final int[] MONEY = {10, 11, 12, 13, 14};
    static final double[] AMOUNTS = {100, 1000, 10000, -1000, 0};
    static final int SALES_UP = 19;
    static final int SALES_RESET = 20;
    static final int KIT = 28;
    static final int SOBER = 29;
    static final int TELEPORT = 30;
    static final int WORKERS = 31;
    static final int BACK = 45;

    private final OfflinePlayer target;

    public AdminPlayerMenu(Player player, OfflinePlayer target) {
        super(player, 6, "admin", "Admin · " + target.getName(), false);
        this.target = target;
    }

    @Override
    public void render() {
        inv.clear();
        backButton(BACK);
        KushCraft plugin = KushCraft.get();
        var eco = plugin.economy();
        set(HEAD, AdminPlayersMenu.head(target, "<white>" + Text.escape(String.valueOf(target.getName())), List.of(
                "<gold>" + eco.format(eco.balance(target)) + " <dark_gray>money",
                "<gold>" + eco.format(eco.sales(target)) + " <dark_gray>sold · " + plugin.titles().label(target))));
        set(9, Items.icon("ui_wallet", "<white><bold>Money"));
        for (int i = 0; i < MONEY.length; i++) {
            double a = AMOUNTS[i];
            set(MONEY[i], Items.icon(a > 0 ? "cash" : "ui_lock", a > 0 ? "<green>+" + eco.format(a)
                    : a < 0 ? "<red>-" + eco.format(-a) : "<red>Set to " + eco.format(0)));
        }
        set(18, Items.icon("award_crown", "<white><bold>Sales <gray>(Top Dealers)"));
        set(SALES_UP, Items.icon("award_cash_stack", "<green>+" + eco.format(10000) + " sales"));
        set(SALES_RESET, Items.icon("ui_lock", "<red>Reset sales to 0"));
        set(27, Items.icon("ui_admin", "<white><bold>Other"));
        boolean online = target.isOnline();
        set(KIT, Items.icon("grower_guide", online ? "<green>Give the starter kit" : "<dark_gray>Starter kit (offline)"));
        set(SOBER, Items.icon("effect_green_out", online ? "<green>Sober them up" : "<dark_gray>Sober up (offline)"));
        set(TELEPORT, Items.icon("ui_arrow", online ? "<aqua>Teleport to them" : "<dark_gray>Teleport (offline)"));
        set(WORKERS, Items.icon("ui_workers", "<aqua>Their workers <gray>(" + plugin.workers().of(target.getUniqueId()).size() + ")",
                "<gray>Dismissing one sends their stuff to you."));
    }

    @Override
    public void click(int slot, ClickType click) {
        if (!player.hasPermission("kushcraft.admin")) {
            player.closeInventory();
            return;
        }
        KushCraft plugin = KushCraft.get();
        var eco = plugin.economy();
        Player online = target.getPlayer();
        for (int i = 0; i < MONEY.length; i++) {
            if (slot == MONEY[i]) {
                double a = AMOUNTS[i];
                if (a > 0) {
                    eco.deposit(target.getUniqueId(), a, dev.kushcraft.economy.Tx.ADMIN, player.getName(), "admin panel");
                } else if (a < 0) {
                    eco.set(target, Math.max(0, eco.balance(target) + a), player.getName());
                } else {
                    eco.set(target, 0, player.getName());
                }
                ok();
                return;
            }
        }
        switch (slot) {
            case SALES_UP -> {
                eco.setSales(target, eco.sales(target) + 10000, player.getName());
                refreshRanks();
                ok();
            }
            case SALES_RESET -> {
                eco.setSales(target, 0, player.getName());
                refreshRanks();
                ok();
            }
            case KIT -> {
                if (online != null) {
                    PlayerListener.starterKit(online);
                    ok();
                }
            }
            case SOBER -> {
                if (online != null) {
                    plugin.effects().clear(online);
                    ok();
                }
            }
            case TELEPORT -> {
                if (online != null) {
                    player.teleport(online);
                    player.closeInventory();
                }
            }
            case WORKERS -> openChild(new MyWorkersMenu(player, true));
            default -> {
            }
        }
    }

    private void refreshRanks() {
        KushCraft plugin = KushCraft.get();
        plugin.titles().refresh();
        Bukkit.getOnlinePlayers().forEach(o -> plugin.titles().showInTab(o));
    }

    private void ok() {
        player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", SoundCategory.MASTER, 0.6f, 1.1f);
        render();
    }
}
