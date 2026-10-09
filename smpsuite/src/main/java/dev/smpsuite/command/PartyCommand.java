package dev.smpsuite.command;

import dev.smpsuite.Msg;
import dev.smpsuite.SMPSuite;
import dev.smpsuite.gui.PartyMenu;
import dev.smpsuite.team.Team;
import dev.smpsuite.team.Teams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /party (teams) and /tc (team chat). */
public final class PartyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("create", "invite", "accept", "deny", "join", "leave", "kick",
            "promote", "disband", "color", "tag", "rename", "share", "open", "chat", "info", "list", "top", "help");

    private final SMPSuite plugin;

    public PartyCommand(SMPSuite plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Teams teams = plugin.teams();
        if (!teams.enabled()) {
            Msg.send(p, "<red>Teams are turned off on this server.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("tc")) {
            if (teams.of(p) == null) {
                Msg.send(p, "<red>You're not in a team.");
            } else if (args.length == 0) {
                teams.setChat(p, !teams.chatOn(p));
                Msg.send(p, teams.chatOn(p) ? "<green>Team chat on: what you type goes to your team."
                        : "<gray>Team chat off: back to everyone.");
            } else {
                teams.teamChat(p, Component.text(String.join(" ", args)));
            }
            return true;
        }
        if (args.length == 0) {
            new PartyMenu(plugin, p).open();
            return true;
        }
        Team t = teams.of(p);
        boolean lead = t != null && t.leader().equals(p.getUniqueId());
        String sub = args[0].toLowerCase(Locale.ROOT);
        String err = null;
        switch (sub) {
            case "create" -> {
                if (args.length < 2) {
                    Msg.send(p, "<gray>/party create <name> [tag] [colour]");
                    return true;
                }
                // the name may have spaces: tag and colour are recognised at the end
                List<String> words = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
                NamedTextColor color = words.size() > 1 ? Teams.parseColor(words.get(words.size() - 1)) : null;
                if (color != null) {
                    words.remove(words.size() - 1);
                }
                String tag = null;
                if (words.size() > 1 && words.get(words.size() - 1).length() <= 4
                        && words.get(words.size() - 1).equals(words.get(words.size() - 1).toUpperCase(Locale.ROOT))) {
                    tag = words.remove(words.size() - 1);
                }
                err = teams.create(p, String.join(" ", words), tag, color);
                if (err == null) {
                    Team made = teams.of(p);
                    Msg.send(p, "Team " + made.coloredTag() + " " + made.colored() + " made! <gray>/party invite <player>");
                }
            }
            case "invite" -> {
                Player to = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (to == null || to == p) {
                    err = "Who? /party invite <online player>";
                } else if ((err = teams.inviteTo(p, to)) == null) {
                    Msg.send(p, "Invited " + Msg.escape(to.getName()) + ".");
                    to.sendMessage(Msg.mm(Msg.PREFIX + Msg.escape(p.getName()) + " invited you to " + t.coloredTag() + " "
                            + t.colored() + ". ").append(Msg.mm("<green><bold>[Join]")
                            .clickEvent(ClickEvent.runCommand("/party accept"))).append(Component.text(" "))
                            .append(Msg.mm("<gray>[No thanks]").clickEvent(ClickEvent.runCommand("/party deny"))));
                }
            }
            case "accept" -> {
                Teams.Invite inv = teams.invite(p.getUniqueId());
                Team to = inv == null ? null : find(inv.team());
                if (to == null) {
                    err = "You have no invite (they last 5 minutes).";
                } else if ((err = teams.join(p, to)) == null) {
                    Msg.send(p, "You joined " + to.colored() + "!");
                }
            }
            case "deny" -> {
                Teams.Invite inv = teams.invite(p.getUniqueId());
                if (inv != null) {
                    teams.quit(p.getUniqueId());
                    Player from = Bukkit.getPlayer(inv.from());
                    if (from != null) {
                        Msg.send(from, "<gray>" + Msg.escape(p.getName()) + " said no thanks.");
                    }
                }
                Msg.send(p, "<gray>Invite declined.");
            }
            case "join" -> {
                Team to = args.length >= 2 ? teams.byName(String.join(" ", Arrays.asList(args).subList(1, args.length))) : null;
                if (to == null) {
                    err = "No team by that name. /party list";
                } else if (!to.open() && !isInvited(p, to)) {
                    err = to.name() + " is invite only.";
                } else if ((err = teams.join(p, to)) == null) {
                    Msg.send(p, "You joined " + to.colored() + "!");
                }
            }
            case "leave" -> {
                if (t == null) {
                    err = "You're not in a team.";
                } else {
                    teams.broadcast(t, "<yellow>" + Msg.escape(p.getName()) + " left the team.");
                    teams.remove(p.getUniqueId());
                }
            }
            case "kick" -> {
                UUID who = args.length >= 2 && t != null ? member(t, args[1]) : null;
                if (!lead) {
                    err = "Only the leader can kick.";
                } else if (who == null || who.equals(p.getUniqueId())) {
                    err = "That's not someone else in your team.";
                } else {
                    teams.broadcast(t, "<yellow>" + Msg.escape(teams.nameOf(who)) + " was removed from the team.");
                    teams.remove(who);
                }
            }
            case "promote" -> {
                UUID who = args.length >= 2 && t != null ? member(t, args[1]) : null;
                if (!lead) {
                    err = "Only the leader can do that.";
                } else if (who == null) {
                    err = "That's not someone in your team.";
                } else {
                    teams.promote(t, who);
                    teams.broadcast(t, "<yellow>" + Msg.escape(teams.nameOf(who)) + " leads the team now.");
                }
            }
            case "disband" -> {
                if (!lead) {
                    err = "Only the leader can disband the team.";
                } else if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    Msg.send(p, "<yellow>Really disband " + t.colored() + "? <gray>/party disband confirm");
                } else {
                    teams.broadcast(t, "<red>The team was disbanded.");
                    teams.disband(t);
                }
            }
            case "color", "colour" -> {
                NamedTextColor c = args.length >= 2 ? Teams.parseColor(args[1]) : null;
                if (!lead) {
                    err = "Only the leader can do that.";
                } else if (c == null) {
                    err = "Colours: " + String.join(", ", NamedTextColor.NAMES.keys());
                } else {
                    teams.setColor(t, c);
                    teams.broadcast(t, "New colour!");
                }
            }
            case "tag" -> {
                if (!lead) {
                    err = "Only the leader can do that.";
                } else if (args.length < 2 || (err = teams.validTag(args[1].toUpperCase(Locale.ROOT))) == null) {
                    if (args.length < 2) {
                        err = "/party tag <TAG>";
                    } else {
                        teams.setTag(t, args[1]);
                        teams.broadcast(t, "New tag: " + t.coloredTag());
                    }
                }
            }
            case "rename" -> {
                String name = args.length >= 2 ? String.join(" ", Arrays.asList(args).subList(1, args.length)) : null;
                if (!lead) {
                    err = "Only the leader can do that.";
                } else if ((err = teams.validName(name)) == null) {
                    teams.rename(t, name);
                    teams.broadcast(t, "The team is called " + t.colored() + " now.");
                }
            }
            case "share" -> {
                if (!lead) {
                    err = "Only the leader can do that.";
                } else {
                    teams.setShareXp(t, !t.shareXp());
                    teams.broadcast(t, "Shared XP is " + (t.shareXp() ? "<green>on" : "<red>off") + "<gray>.");
                }
            }
            case "open" -> {
                if (!lead) {
                    err = "Only the leader can do that.";
                } else {
                    teams.setOpen(t, !t.open());
                    teams.broadcast(t, t.open() ? "Anyone can /party join now." : "Invite only now.");
                }
            }
            case "chat" -> {
                if (t == null) {
                    err = "You're not in a team.";
                } else {
                    teams.setChat(p, !teams.chatOn(p));
                    Msg.send(p, teams.chatOn(p) ? "<green>Team chat on." : "<gray>Team chat off.");
                }
            }
            case "info" -> {
                Team which = args.length >= 2 ? teams.byName(String.join(" ", Arrays.asList(args).subList(1, args.length))) : t;
                if (which == null) {
                    err = "No such team.";
                } else {
                    info(p, which);
                }
            }
            case "list", "top" -> {
                List<Team> list = teams.top();
                Msg.send(p, "<aqua>Teams by total skill level:");
                for (int i = 0; i < Math.min(10, list.size()); i++) {
                    Team o = list.get(i);
                    p.sendMessage(Msg.mm("<gray>" + (i + 1) + ". " + o.coloredTag() + " " + o.colored() + " <dark_gray>- <white>"
                            + teams.totalLevel(o) + " <dark_gray>(" + o.members().size() + " members" + (o.open() ? ", open" : "")
                            + ")"));
                }
                if (list.isEmpty()) {
                    p.sendMessage(Msg.mm("<gray>No teams yet. /party create <name>"));
                }
            }
            default -> help(p);
        }
        if (err != null) {
            Msg.send(p, "<red>" + err);
        }
        return true;
    }

    private boolean isInvited(Player p, Team t) {
        Teams.Invite inv = plugin.teams().invite(p.getUniqueId());
        return inv != null && inv.team().equals(t.id());
    }

    private Team find(UUID id) {
        for (Team t : plugin.teams().all()) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    private UUID member(Team t, String name) {
        for (UUID u : t.members()) {
            if (plugin.teams().nameOf(u).equalsIgnoreCase(name)) {
                return u;
            }
        }
        return null;
    }

    private void info(Player p, Team t) {
        Teams teams = plugin.teams();
        Msg.send(p, t.coloredTag() + " " + t.colored() + " <gray>- team level <white>" + teams.totalLevel(t));
        List<String> names = new ArrayList<>();
        for (UUID u : t.members()) {
            names.add((Bukkit.getPlayer(u) != null ? "<green>" : "<gray>") + (t.leader().equals(u) ? "★" : "")
                    + Msg.escape(teams.nameOf(u)));
        }
        p.sendMessage(Msg.mm("<gray>Members: " + String.join("<dark_gray>, ", names)));
        p.sendMessage(Msg.mm("<gray>Shared XP " + (t.shareXp() ? "<green>on" : "<red>off") + "<gray> · "
                + (t.open() ? "open to join" : "invite only")));
    }

    private void help(Player p) {
        Msg.send(p, "<aqua>Teams</aqua> <gray>- opt-in groups for playing together. No PvP stuff, just friends.");
        for (String line : List.of(
                "/party <dark_gray>- your team menu",
                "/party create <name> [TAG] [colour]",
                "/party invite <player> <dark_gray>/</dark_gray> accept <dark_gray>/</dark_gray> deny",
                "/party join <team> <dark_gray>(open teams)</dark_gray> <dark_gray>/</dark_gray> leave",
                "/party kick <player> <dark_gray>/</dark_gray> promote <player> <dark_gray>/</dark_gray> disband",
                "/party color <colour> <dark_gray>/</dark_gray> tag <TAG> <dark_gray>/</dark_gray> rename <name>",
                "/party share <dark_gray>(shared XP)</dark_gray> <dark_gray>/</dark_gray> open <dark_gray>/</dark_gray> chat",
                "/party info [team] <dark_gray>/</dark_gray> list",
                "/tc <message> <dark_gray>- team chat</dark_gray>",
                "/vc team <dark_gray>- team voice (Simple Voice Chat)")) {
            p.sendMessage(Msg.mm("<white>" + line));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("tc")) {
            return out;
        }
        if (args.length == 1) {
            out.addAll(SUBS);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "invite", "kick", "promote" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                case "color", "colour" -> out.addAll(NamedTextColor.NAMES.keys());
                case "join", "info" -> plugin.teams().all().forEach(t -> out.add(t.name()));
                default -> {
                }
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
