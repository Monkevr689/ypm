package dev.kushcraft.command;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effect.EffectType;
import dev.kushcraft.gui.AwardsMenu;
import dev.kushcraft.gui.CartelMenu;
import dev.kushcraft.gui.DrugsMenu;
import dev.kushcraft.gui.GiveMenu;
import dev.kushcraft.gui.ShopMenu;
import dev.kushcraft.gui.TabMenu;
import dev.kushcraft.gui.TopMenu;
import dev.kushcraft.gui.TradeMenu;
import dev.kushcraft.guide.Guide;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /kush opens the menu (and hands out the menu book); the other sub
 * commands are shortcuts to menu pages. give/money/reload are for admins.
 */
public final class KushCommand implements CommandExecutor, TabCompleter {

    private final KushCraft plugin;

    public KushCommand(KushCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("kushcraft.admin");
        if (sub.equals("menu") && !(sender instanceof Player)) {
            sub = "help";
        }
        switch (sub) {
            case "menu" -> {
                Player p = (Player) sender;
                giveMenuBook(p);
                TabMenu.openMain(p);
            }
            case "guide", "book", "handbook" -> {
                if (sender instanceof Player p) {
                    p.openBook(Guide.book(p));
                }
            }
            case "recipes", "recipe", "drugs", "catalog" -> open(sender, DrugsMenu::new);
            case "market", "shop" -> open(sender, ShopMenu::new);
            case "exchange", "trade" -> {
                if (!plugin.exchange().enabled()) {
                    sender.sendMessage(Text.msg("<red>The exchange is turned off on this server."));
                } else {
                    open(sender, TradeMenu::new);
                }
            }
            case "top", "ranks", "leaderboard" -> open(sender, TopMenu::new);
            case "cartel", "gang", "c" -> cartel(sender, args);
            case "awards", "achievements" -> open(sender, AwardsMenu::new);
            case "items" -> {
                if (!admin) {
                    noPerm(sender);
                    return true;
                }
                open(sender, GiveMenu::new);
            }
            case "sales" -> {
                if (!admin) {
                    noPerm(sender);
                    return true;
                }
                if (args.length < 3 || Bukkit.getPlayerExact(args[1]) == null) {
                    sender.sendMessage(Text.msg("<red>/kush sales <player> <amount> <gray>- sets lifetime sales (rank)"));
                    return true;
                }
                try {
                    Player t = Bukkit.getPlayerExact(args[1]);
                    plugin.economy().setSales(t, Double.parseDouble(args[2]));
                    plugin.ranks().refresh();
                    Bukkit.getOnlinePlayers().forEach(o -> plugin.ranks().showInTab(o));
                    sender.sendMessage(Text.msg("<green>" + t.getName() + " is now " + plugin.ranks().label(t)));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Text.msg("<red>Not a number."));
                }
            }
            case "pay", "send" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Text.msg("<red>Only players can send money."));
                } else if (args.length < 3) {
                    p.sendMessage(Text.msg("<red>/kush pay <player> <amount>"));
                } else {
                    pay(p, Bukkit.getPlayerExact(args[1]), args[2]);
                }
            }
            case "pack" -> {
                if (sender instanceof Player p) {
                    plugin.pack().send(p);
                    p.sendMessage(Text.msg("<gray>Resource pack sent. <dark_gray>" + plugin.pack().url(p)));
                }
            }
            case "balance", "bal", "money" -> {
                if (args.length >= 3 && admin) {
                    Player t = Bukkit.getPlayerExact(args[1]);
                    if (t == null) {
                        sender.sendMessage(Text.msg("<red>Player not online."));
                        return true;
                    }
                    try {
                        double v = Double.parseDouble(args[2]);
                        plugin.economy().set(t, v);
                        sender.sendMessage(Text.msg("<green>Set " + t.getName() + "'s balance to " + plugin.economy().format(v)));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Text.msg("<red>Not a number."));
                    }
                    return true;
                }
                if (sender instanceof Player p) {
                    p.sendMessage(Text.msg("<gray>Balance: <gold>" + plugin.economy().format(plugin.economy().balance(p))));
                }
            }
            case "strains" -> {
                sender.sendMessage(Text.msg("<gray>Strains (" + plugin.strains().all().size() + "):"));
                for (Strain s : plugin.strains().all()) {
                    StringBuilder eff = new StringBuilder();
                    for (EffectType e : s.effects()) {
                        eff.append(eff.isEmpty() ? "" : ", ").append(e.display());
                    }
                    sender.sendMessage(Text.mm(" " + s.colored() + " <dark_gray>" + s.rarity().display() + " "
                            + s.type().display() + " " + s.potency() + "% " + s.climate().display() + " <gray>" + eff
                            + (s.isCustom() ? " <dark_gray>by " + Text.escape(s.creatorName()) : "")));
                }
            }
            case "give" -> {
                if (!admin) {
                    noPerm(sender);
                    return true;
                }
                give(sender, args);
            }
            case "selftest" -> {
                if (sender instanceof Player || !admin) {
                    sender.sendMessage(Text.msg("<red>Run this from the server console on a test world."));
                    return true;
                }
                List<String> fails = new SelfTest(plugin).run();
                sender.sendMessage(Text.msg(fails.isEmpty() ? "<green>Self test passed." : "<red>Self test failed: " + fails));
            }
            case "reload" -> {
                if (!admin) {
                    noPerm(sender);
                    return true;
                }
                plugin.reload();
                sender.sendMessage(Text.msg("<green>KushCraft reloaded (config, strains, shop, recipes)."));
            }
            default -> {
                sender.sendMessage(Text.msg("<green>KushCraft <gray>- type <white>/kush</white> (or press <white>Shift+F</white>)"
                        + " to open the menu!"));
                sender.sendMessage(Text.mm(" <white>/kush <gray>- the menu (everything is in there)"));
                sender.sendMessage(Text.mm(" <white>/kush shop|drugs|trade|cartel|top|awards <gray>- open a tab directly"));
                sender.sendMessage(Text.mm(" <white>/kush cartel invite|join|leave <gray>- cartels"));
                sender.sendMessage(Text.mm(" <white>/kush pay <player> <amount> <gray>- send money"));
                sender.sendMessage(Text.mm(" <white>/kush guide <gray>- the handbook"));
                sender.sendMessage(Text.mm(" <white>/kush pack <gray>- re-download the texture pack"));
                sender.sendMessage(Text.mm(" <white>/kush balance <gray>- your money"));
                if (admin) {
                    sender.sendMessage(Text.mm(" <red>/kush give <player> <item> [amount] [strain] [quality]"));
                    sender.sendMessage(Text.mm(" <red>/kush items <gray>- click any item to get it"));
                    sender.sendMessage(Text.mm(" <red>/kush money <player> <amount> <gray>- set balance"));
                    sender.sendMessage(Text.mm(" <red>/kush sales <player> <amount> <gray>- set lifetime sales (rank)"));
                    sender.sendMessage(Text.mm(" <red>/kush reload"));
                }
            }
        }
        return true;
    }

    /** /kush cartel [invite <player> | join <cartel> | leave] */
    private void cartel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Text.msg("<red>Only players can be in a cartel."));
            return;
        }
        if (!plugin.cartels().enabled()) {
            p.sendMessage(Text.msg("<red>Cartels are turned off on this server."));
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        String error;
        switch (action) {
            case "invite" -> {
                Player t = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
                error = t == null ? "/kush cartel invite <player> (online)" : plugin.cartels().invite(p, t);
                if (error == null) {
                    p.sendMessage(Text.msg("<green>Invited " + Text.escape(t.getName()) + "."));
                }
            }
            case "join" -> {
                dev.kushcraft.cartel.Cartel c = args.length >= 3
                        ? plugin.cartels().byName(String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)))
                        : null;
                error = c == null ? "No cartel with that name." : plugin.cartels().join(p, c);
            }
            case "leave" -> error = plugin.cartels().leave(p);
            default -> {
                new CartelMenu(p).open();
                return;
            }
        }
        if (error != null) {
            p.sendMessage(Text.msg("<red>" + error));
        }
    }

    private static void open(CommandSender sender, java.util.function.Function<Player, dev.kushcraft.gui.Menu> menu) {
        if (sender instanceof Player p) {
            menu.apply(p).open();
        } else {
            sender.sendMessage(Text.msg("<red>Only players can open menus."));
        }
    }

    /** /kush hands out the KushCraft Menu book once, so the menu is always one click away. */
    private void giveMenuBook(Player p) {
        if (!plugin.getConfig().getBoolean("menu.give-book-on-command", true)) {
            return;
        }
        for (ItemStack it : p.getInventory().getContents()) {
            if (Items.is(it, ItemType.GROWER_GUIDE)) {
                return;
            }
        }
        InventoryUtil.give(p, Items.create(ItemType.GROWER_GUIDE));
        p.sendMessage(Text.msg("<gray>Here's your <green>KushCraft Menu</green> book - right-click it any time."
                + " <dark_gray>(or Shift+F)"));
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Text.msg("<red>/kush give <player> <item> [amount] [strain] [quality]"));
            return;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        ItemType type = ItemType.parse(args[2]);
        if (t == null || type == null) {
            sender.sendMessage(Text.msg("<red>Unknown player or item."));
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64 * 36, Integer.parseInt(args[3])));
            } catch (NumberFormatException ignored) {
            }
        }
        if (type.strainBound()) {
            Strain s = args.length >= 5 ? plugin.strains().byName(args[4]) : plugin.strains().all().iterator().next();
            if (s == null) {
                sender.sendMessage(Text.msg("<red>Unknown strain."));
                return;
            }
            int q = 3;
            if (args.length >= 6) {
                try {
                    q = Integer.parseInt(args[5]);
                } catch (NumberFormatException ignored) {
                }
            }
            InventoryUtil.give(t, split(Items.strainItem(type, s, q, 1), amount));
        } else {
            InventoryUtil.give(t, split(Items.create(type), amount));
        }
        sender.sendMessage(Text.msg("<green>Gave " + amount + "x " + type.display() + " to " + t.getName()));
    }

    private static org.bukkit.inventory.ItemStack[] split(org.bukkit.inventory.ItemStack one, int amount) {
        int max = Math.max(1, one.getMaxStackSize());
        List<org.bukkit.inventory.ItemStack> out = new ArrayList<>();
        while (amount > 0) {
            int n = Math.min(max, amount);
            out.add(Items.amount(one, n));
            amount -= n;
        }
        return out.toArray(new org.bukkit.inventory.ItemStack[0]);
    }

    /** /kush pay */
    static boolean pay(Player from, Player to, String amountText) {
        KushCraft plugin = KushCraft.get();
        double amount;
        try {
            amount = Math.round(Double.parseDouble(amountText.replace("$", "").replace(",", "").trim()) * 100) / 100.0;
        } catch (NumberFormatException e) {
            from.sendMessage(Text.msg("<red>That's not a number."));
            return false;
        }
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            from.sendMessage(Text.msg("<red>The amount must be more than 0."));
            return false;
        }
        if (to == null || !to.isOnline() || to.equals(from)) {
            from.sendMessage(Text.msg("<red>That player isn't online."));
            return false;
        }
        if (!plugin.economy().withdraw(from, amount)) {
            from.sendMessage(Text.msg("<red>You only have " + plugin.economy().format(plugin.economy().balance(from)) + "."));
            return false;
        }
        plugin.economy().deposit(to, amount);
        from.sendMessage(Text.msg("<green>Sent <gold>" + plugin.economy().format(amount) + "</gold> to <white>"
                + Text.escape(to.getName())));
        to.sendMessage(Text.msg("<green>You got <gold>" + plugin.economy().format(amount) + "</gold> from <white>"
                + Text.escape(from.getName())));
        to.playSound(to.getLocation(), "minecraft:entity.experience_orb.pickup", 0.7f, 1.4f);
        return true;
    }

    private static void noPerm(CommandSender s) {
        s.sendMessage(Text.msg("<red>You don't have permission."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("kushcraft.admin");
        if (args.length == 1) {
            out.addAll(List.of("menu", "shop", "drugs", "trade", "cartel", "top", "awards", "pay", "guide", "pack",
                    "balance", "strains", "help"));
            if (admin) {
                out.addAll(List.of("give", "items", "money", "sales", "reload"));
            }
        } else if (admin && args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (args.length == 3) {
                for (ItemType t : ItemType.values()) {
                    out.add(t.id());
                }
            } else if (args.length == 4) {
                out.addAll(List.of("1", "16", "64"));
            } else if (args.length == 5) {
                plugin.strains().all().forEach(s -> out.add(s.id()));
            } else if (args.length == 6) {
                out.addAll(List.of("1", "2", "3", "4", "5"));
            }
        } else if (args[0].equalsIgnoreCase("cartel") && args.length == 2) {
            out.addAll(List.of("invite", "join", "leave"));
        } else if (args[0].equalsIgnoreCase("cartel") && args.length == 3 && args[1].equalsIgnoreCase("invite")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args[0].equalsIgnoreCase("cartel") && args.length == 3 && args[1].equalsIgnoreCase("join")) {
            plugin.cartels().all().forEach(c -> out.add(c.id()));
        } else if (args[0].equalsIgnoreCase("pay") && args.length == 2) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (admin && (args[0].equalsIgnoreCase("money") || args[0].equalsIgnoreCase("sales") || args[0].equalsIgnoreCase("balance")) && args.length == 2) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
