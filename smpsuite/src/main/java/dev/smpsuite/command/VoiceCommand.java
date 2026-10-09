package dev.smpsuite.command;

import dev.smpsuite.Msg;
import dev.smpsuite.SMPSuite;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /vc: voice chat channels and your team's group (Simple Voice Chat). */
public final class VoiceCommand implements CommandExecutor, TabCompleter {

    private final SMPSuite plugin;

    public VoiceCommand(SMPSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Voice chat: " + (plugin.voice().ready() ? "ready" : plugin.voice().problem()));
            return true;
        }
        var voice = plugin.voice();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String err;
        switch (sub) {
            case "join" -> {
                if (args.length < 2) {
                    Msg.send(p, "<gray>/vc join <channel> <dark_gray>- " + String.join(", ", voice.channelNames()));
                    return true;
                }
                err = voice.joinChannel(p, args[1]);
                Msg.send(p, err == null ? "<green>You're in the " + Msg.escape(args[1]) + " voice channel." : "<red>" + err);
            }
            case "team" -> {
                err = voice.joinTeam(p);
                Msg.send(p, err == null ? "<green>You're in your team's voice group." : "<red>" + err);
            }
            case "leave" -> {
                err = voice.leave(p);
                Msg.send(p, err == null ? "<gray>Back to proximity voice (people near you)." : "<red>" + err);
            }
            default -> {
                if (!voice.ready()) {
                    Msg.send(p, "<gray>" + voice.problem());
                    return true;
                }
                Msg.send(p, "<light_purple>Voice chat</light_purple> <gray>- talk to people near you, or join a group:");
                Msg.send(p, "<white>/vc join <channel></white> <gray>" + String.join(", ", voice.channelNames()));
                Msg.send(p, "<white>/vc team</white> <gray>your team <dark_gray>|</dark_gray> <white>/vc leave</white> "
                        + "<gray>back to proximity");
                if (!voice.connected(p)) {
                    Msg.send(p, "<yellow>Install the Simple Voice Chat mod to hear and talk.");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("join", "team", "leave"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            out.addAll(plugin.voice().channelNames());
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
