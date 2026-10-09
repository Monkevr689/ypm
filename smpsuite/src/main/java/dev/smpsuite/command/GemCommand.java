package dev.smpsuite.command;

import dev.smpsuite.Msg;
import dev.smpsuite.SMPSuite;
import dev.smpsuite.data.PlayerData;
import dev.smpsuite.gem.GemType;
import dev.smpsuite.gem.PocketsMenu;
import dev.smpsuite.gui.GemMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** /gem, /gem use primary|secondary, /gem recover, /gem reroll, /gem pockets. */
public final class GemCommand implements CommandExecutor, TabCompleter {

    private final SMPSuite plugin;

    public GemCommand(SMPSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        PlayerData d = plugin.store().get(p);
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> new GemMenu(plugin, p).open();
            case "use" -> plugin.gems().use(p, args.length >= 2 && args[1].toLowerCase(Locale.ROOT).startsWith("s"));
            case "recover" -> {
                if (d.gem == null) {
                    Msg.send(p, "<red>You don't have a gem.");
                } else if (plugin.gems().carries(p)) {
                    Msg.send(p, "<gray>You already have your gem.");
                } else {
                    plugin.gems().giveNew(p);
                    Msg.send(p, "Here's your gem again. <gray>Older copies no longer work.");
                }
            }
            case "reroll" -> {
                int cost = plugin.getConfig().getInt("gems.reroll-cost", 5);
                if (d.gem == null) {
                    Msg.send(p, "<red>You don't have a gem.");
                } else if (d.energy < cost) {
                    Msg.send(p, "<red>A reroll costs " + cost + "⚡ energy <gray>(you have " + d.energy + ").");
                } else if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    Msg.send(p, "<yellow>Swap your " + d.gem.colored() + " gem for a random other one for " + cost
                            + "⚡? <gray>Type <white>/gem reroll confirm");
                } else {
                    GemType[] all = GemType.values();
                    GemType next;
                    do {
                        next = all[ThreadLocalRandom.current().nextInt(all.length)];
                    } while (next == d.gem);
                    d.energy -= cost;
                    plugin.gems().setGem(p, next);
                    Msg.send(p, "Your new gem: " + next.colored() + "!");
                }
            }
            case "pockets" -> {
                if (d.gem == GemType.WEALTH && plugin.gems().active(p) == GemType.WEALTH) {
                    new PocketsMenu(plugin, p).open();
                } else {
                    Msg.send(p, "<gray>Pockets need the Wealth gem in your off hand.");
                }
            }
            default -> Msg.send(p, "<gray>/gem <dark_gray>|</dark_gray> /gem use primary|secondary <dark_gray>|</dark_gray> "
                    + "/gem recover <dark_gray>|</dark_gray> /gem reroll <dark_gray>|</dark_gray> /gem pockets");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("use", "recover", "reroll", "pockets"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("use")) {
            out.addAll(List.of("primary", "secondary"));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(last));
        return out;
    }
}
