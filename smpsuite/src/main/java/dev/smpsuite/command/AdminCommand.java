package dev.smpsuite.command;

import dev.smpsuite.Msg;
import dev.smpsuite.SMPSuite;
import dev.smpsuite.data.PlayerData;
import dev.smpsuite.gem.GemType;
import dev.smpsuite.skill.Skill;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /smp: admin tools. */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final SMPSuite plugin;

    public AdminCommand(SMPSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("help") && !sender.hasPermission("smpsuite.admin")) {
            Msg.send(sender, "<red>Admins only.");
            return true;
        }
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
        switch (sub) {
            case "reload" -> {
                plugin.reload();
                Msg.send(sender, "Config reloaded.");
            }
            case "selftest" -> {
                if (sender instanceof Player) {
                    Msg.send(sender, "<red>Run this from the console on a test world.");
                    return true;
                }
                List<String> fails = new SelfTest(plugin).run();
                Msg.send(sender, fails.isEmpty() ? "<green>Self test passed." : "<red>Self test failed: " + fails);
            }
            case "level", "xp" -> {
                Skill s = args.length >= 3 ? Skill.parse(args[2]) : null;
                if (target == null || s == null || args.length < 4) {
                    Msg.send(sender, "<gray>/smp " + sub + " <player> <skill> <" + (sub.equals("xp") ? "amount" : "level") + ">");
                    return true;
                }
                double n = num(args[3]);
                if (sub.equals("level")) {
                    plugin.skills().setLevel(target, s, (int) n);
                } else {
                    plugin.skills().addXp(target, s, n, true);
                }
                Msg.send(sender, Msg.escape(target.getName()) + "'s " + s.display() + ": level "
                        + plugin.store().get(target).level(s));
            }
            case "gem" -> {
                GemType t = args.length >= 3 ? GemType.parse(args[2]) : null;
                if (target == null || t == null) {
                    Msg.send(sender, "<gray>/smp gem <player> <astra|fire|flux|life|puff|speed|strength|wealth>");
                    return true;
                }
                PlayerData d = plugin.store().get(target);
                if (d.gem == null) {
                    d.energy = Math.min(plugin.gems().maxEnergy(), plugin.getConfig().getInt("gems.start-energy", 5));
                }
                plugin.gems().setGem(target, t);
                Msg.send(sender, Msg.escape(target.getName()) + " has the " + t.colored() + " gem now.");
            }
            case "energy" -> {
                if (target == null || args.length < 3) {
                    Msg.send(sender, "<gray>/smp energy <player> <0-" + plugin.gems().maxEnergy() + ">");
                    return true;
                }
                plugin.gems().setEnergy(target, (int) num(args[2]));
                Msg.send(sender, Msg.escape(target.getName()) + "'s gem energy: " + plugin.store().get(target).energy);
            }
            case "cooldowns" -> {
                if (target == null) {
                    Msg.send(sender, "<gray>/smp cooldowns <player>");
                    return true;
                }
                PlayerData d = plugin.store().get(target);
                d.cooldowns.clear();
                d.dirty = true;
                Msg.send(sender, "Cleared " + Msg.escape(target.getName()) + "'s cooldowns.");
            }
            case "info" -> {
                Msg.send(sender, "Jobs pay: <white>" + plugin.money().mode() + "</white> · voice: <white>"
                        + (plugin.voice().ready() ? "ready" : plugin.voice().problem()) + "</white> · teams: <white>"
                        + plugin.teams().all().size() + "</white> · pack: <white>"
                        + (plugin.pack().enabled() ? plugin.pack().url() : "off"));
            }
            default -> {
                Msg.send(sender, "<aqua>SMPSuite admin</aqua>");
                for (String l : List.of("/smp info", "/smp reload", "/smp level <player> <skill> <level>",
                        "/smp xp <player> <skill> <amount>", "/smp gem <player> <gem>", "/smp energy <player> <n>",
                        "/smp cooldowns <player>", "/smp selftest <dark_gray>(console)")) {
                    sender.sendMessage(Msg.mm("<white>" + l));
                }
            }
        }
        return true;
    }

    private static double num(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("info", "reload", "level", "xp", "gem", "energy", "cooldowns", "selftest"));
        } else if (args.length == 2) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("level") || args[0].equalsIgnoreCase("xp"))) {
            for (Skill s : Skill.values()) {
                out.add(s.id());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("gem")) {
            for (GemType t : GemType.values()) {
                out.add(t.id());
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
