package dev.smpsuite.command;

import dev.smpsuite.Msg;
import dev.smpsuite.SMPSuite;
import dev.smpsuite.data.PlayerStore;
import dev.smpsuite.gui.SkillsMenu;
import dev.smpsuite.skill.Skill;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /skills [player], /skills top [skill]. */
public final class SkillsCommand implements CommandExecutor, TabCompleter {

    private final SMPSuite plugin;

    public SkillsCommand(SMPSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("top")) {
            Skill s = args.length >= 2 ? Skill.parse(args[1]) : null;
            top(sender, s);
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only (or /skills top).");
            return true;
        }
        if (args.length >= 1) {
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (op == null || !plugin.store().known(op.getUniqueId())) {
                Msg.send(p, "<red>No skills known for " + Msg.escape(args[0]) + ".");
                return true;
            }
            new SkillsMenu(plugin, p, plugin.store().get(op.getUniqueId())).open();
            return true;
        }
        new SkillsMenu(plugin, p, plugin.store().get(p)).open();
        return true;
    }

    private void top(CommandSender to, Skill s) {
        List<Map.Entry<UUID, PlayerStore.Summary>> list = new ArrayList<>(plugin.store().index().entrySet());
        list.sort(Comparator.comparingInt((Map.Entry<UUID, PlayerStore.Summary> e) ->
                s == null ? e.getValue().total() : e.getValue().levels()[s.ordinal()]).reversed());
        to.sendMessage(Msg.mm("<dark_aqua><bold>Top " + (s == null ? "total level" : s.display())));
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            PlayerStore.Summary sum = list.get(i).getValue();
            int v = s == null ? sum.total() : sum.levels()[s.ordinal()];
            to.sendMessage(Msg.mm("<gray>" + (i + 1) + ". <white>" + Msg.escape(sum.name()) + " <dark_gray>- <aqua>" + v));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("top");
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            for (Skill s : Skill.values()) {
                out.add(s.id());
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
