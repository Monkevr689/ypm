package dev.kushcraft.command;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effect.EffectType;
import dev.kushcraft.gui.DealerMenu;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /kush - players only need "guide", "pack" and "balance"; everything else
 * happens with items and machines. The rest is for admins.
 */
public final class KushCommand implements CommandExecutor, TabCompleter {

    private final KushCraft plugin;

    public KushCommand(KushCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("kushcraft.admin");
        switch (sub) {
            case "guide", "book" -> {
                if (sender instanceof Player p) {
                    p.openBook(Guide.book());
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
                    sender.sendMessage(Text.mm(" " + s.colored() + " <dark_gray>" + s.type().display() + " "
                            + s.potency() + "% <gray>" + eff + (s.isCustom() ? " <dark_gray>by " + Text.escape(s.creatorName()) : "")));
                }
            }
            case "shop" -> {
                if (!admin) {
                    noPerm(sender);
                } else if (sender instanceof Player p) {
                    new DealerMenu(p).open();
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
                sender.sendMessage(Text.msg("<green>KushCraft <gray>- most things are done with items & machines!"));
                sender.sendMessage(Text.mm(" <white>/kush guide <gray>- open the Grower's Handbook"));
                sender.sendMessage(Text.mm(" <white>/kush pack <gray>- re-download the texture pack"));
                sender.sendMessage(Text.mm(" <white>/kush balance <gray>- your money"));
                sender.sendMessage(Text.mm(" <white>/kush strains <gray>- list every strain"));
                if (admin) {
                    sender.sendMessage(Text.mm(" <red>/kush give <player> <item> [amount] [strain] [quality]"));
                    sender.sendMessage(Text.mm(" <red>/kush money <player> <amount> <gray>- set balance"));
                    sender.sendMessage(Text.mm(" <red>/kush shop <gray>- open the dealer anywhere"));
                    sender.sendMessage(Text.mm(" <red>/kush reload"));
                }
            }
        }
        return true;
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

    private static void noPerm(CommandSender s) {
        s.sendMessage(Text.msg("<red>You don't have permission."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("kushcraft.admin");
        if (args.length == 1) {
            out.addAll(List.of("guide", "pack", "balance", "strains"));
            if (admin) {
                out.addAll(List.of("give", "money", "shop", "reload"));
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
        } else if (admin && (args[0].equalsIgnoreCase("money") || args[0].equalsIgnoreCase("balance")) && args.length == 2) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
