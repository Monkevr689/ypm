package dev.kushcraft.commands;

import dev.kushcraft.KushCraft;
import dev.kushcraft.economy.Economy;
import dev.kushcraft.menus.InfoMenu;
import dev.kushcraft.menus.RanksMenu;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The short commands players use every day:
 * /menu (the server guide), /rankup, /balance, /pay and /baltop.
 *
 * KushCraft owns the money, so it also owns /balance, /pay and /baltop. If
 * EssentialsX is installed its economy commands must be switched off (its
 * config.yml disabled-commands) - on start-up KushCraft logs which plugin
 * really answers each of these names, and how to fix it if it isn't us.
 */
public final class ServerCommands implements CommandExecutor, TabCompleter {

    private static final List<String> OURS = List.of("menu", "rankup", "balance", "bal", "money", "pay", "baltop",
            "balancetop");

    private final KushCraft plugin;

    public ServerCommands(KushCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "menu" -> {
                if (sender instanceof Player p) {
                    InfoMenu.openMain(p);
                } else {
                    sender.sendMessage(Text.msg("<gray>The server guide is a menu: players type /menu."));
                }
            }
            case "rankup" -> {
                if (sender instanceof Player p) {
                    new RanksMenu(p).open();
                } else {
                    sender.sendMessage(Text.msg("<gray>Players rank up with /rankup. Admins: /kush rank <player> <n>."));
                }
            }
            case "balance" -> balance(sender, args);
            case "pay" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Text.msg("<red>Only players can send money."));
                } else if (args.length < 2) {
                    p.sendMessage(Text.msg("<red>/pay <player> <amount>"));
                } else {
                    KushCommand.pay(p, Bukkit.getPlayerExact(args[0]), args[1]);
                }
            }
            case "baltop" -> baltop(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void balance(CommandSender sender, String[] args) {
        Economy eco = plugin.economy();
        if (args.length >= 1 && sender.hasPermission("kushcraft.admin")) {
            var o = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (o == null || !eco.known(o.getUniqueId())) {
                sender.sendMessage(Text.msg("<red>Unknown player."));
                return;
            }
            sender.sendMessage(Text.msg("<gray>" + Text.escape(String.valueOf(o.getName())) + ": <gold>"
                    + eco.format(eco.balance(o))));
            return;
        }
        if (sender instanceof Player p) {
            p.sendMessage(Text.msg("<gray>Balance: <gold>" + eco.format(eco.balance(p))));
        }
    }

    private void baltop(CommandSender sender, String[] args) {
        int page = 0;
        if (args.length >= 1) {
            try {
                page = Math.max(0, Integer.parseInt(args[0]) - 1);
            } catch (NumberFormatException ignored) {
                // page 1
            }
        }
        List<Economy.Rich> top = plugin.economy().top(10 * (page + 1));
        sender.sendMessage(Text.msg("<gold>Richest players <gray>(page " + (page + 1) + ")"));
        for (int i = page * 10; i < top.size(); i++) {
            Economy.Rich r = top.get(i);
            sender.sendMessage(Text.mm(" <white>#" + (i + 1) + " " + Text.escape(r.name()) + " <gold>"
                    + plugin.economy().format(r.balance())));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String n = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1 && (n.equals("pay") || (n.equals("balance") && sender.hasPermission("kushcraft.admin")))) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            String last = args[0].toLowerCase(Locale.ROOT);
            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        }
        return out;
    }

    /**
     * Start-up check: which plugin answers /balance, /pay, /baltop, /menu and /rankup. Another
     * plugin owning one of them means players would see two different moneys (or menus).
     */
    public static void reportCollisions(KushCraft plugin) {
        List<String> others = new ArrayList<>();
        for (String label : OURS) {
            Command c = Bukkit.getCommandMap().getCommand(label);
            if (c instanceof PluginCommand pc && pc.getPlugin() != plugin) {
                others.add("/" + label + " -> " + pc.getPlugin().getName());
            } else if (c != null && !(c instanceof PluginCommand)) {
                others.add("/" + label + " -> " + c.getClass().getSimpleName());
            }
        }
        boolean essentials = Bukkit.getPluginManager().getPlugin("Essentials") != null;
        if (others.isEmpty()) {
            plugin.getLogger().info("Commands: /menu, /rankup, /balance, /pay and /baltop are KushCraft's"
                    + (essentials ? " (EssentialsX is installed and doesn't take them)." : "."));
            return;
        }
        plugin.getLogger().warning("Commands owned by another plugin: " + String.join(", ", others)
                + ". KushCraft owns the money, so its /balance, /pay and /baltop should win."
                + (essentials ? " In EssentialsX's config.yml add balance, bal, money, pay, baltop, balancetop and eco"
                + " to disabled-commands and restart. (Players can always use /kush balance and /kush pay.)" : ""));
    }
}
