package dev.smpsuite.gui;

import dev.smpsuite.Msg;
import dev.smpsuite.SMPSuite;
import dev.smpsuite.data.PlayerStore;
import dev.smpsuite.team.Team;
import dev.smpsuite.team.Teams;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /party: your team's members and settings, or how to start or join one. */
public final class PartyMenu extends Menu {

    private final SMPSuite plugin;

    public PartyMenu(SMPSuite plugin, Player player) {
        super(player, 4, "<aqua>Your team");
        this.plugin = plugin;
    }

    @Override
    protected void draw() {
        Teams teams = plugin.teams();
        Team t = teams.of(player);
        if (t == null) {
            set(11, icon(Material.WRITABLE_BOOK, "<green>Start a team",
                    "<gray>/party create <name> [tag] [colour]",
                    "<gray>then /party invite <player>",
                    "<dark_gray>Teams are just for playing together:",
                    "<dark_gray>a colour and tag, team chat, shared XP.",
                    "<dark_gray>Teammates can't hurt each other."));
            Teams.Invite inv = teams.invite(player.getUniqueId());
            Team from = inv == null ? null : teams.all().stream().filter(x -> x.id().equals(inv.team())).findFirst().orElse(null);
            set(13, from == null ? icon(Material.PAPER, "<gray>No invites right now")
                    : icon(Material.LIME_DYE, "<green>Join " + from.colored(), "<gray>Invited by " + Msg.escape(teams.nameOf(inv.from())),
                    "<yellow>Click to accept"));
            List<String> open = new ArrayList<>();
            for (Team o : teams.all()) {
                if (o.open() && open.size() < 8) {
                    open.add(o.coloredTag() + " " + o.colored() + " <dark_gray>(" + o.members().size() + "/" + teams.maxMembers() + ")");
                }
            }
            if (open.isEmpty()) {
                open.add("<gray>None right now.");
            }
            open.add("<dark_gray>/party join <name>");
            set(15, icon(Material.OAK_DOOR, "<aqua>Open teams", open));
            fill(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
            return;
        }
        boolean lead = t.leader().equals(player.getUniqueId());
        set(4, icon(Material.WHITE_BANNER, t.coloredTag() + " " + t.colored(),
                "<gray>Members: <white>" + t.members().size() + "/" + teams.maxMembers(),
                "<gray>Team level: <white>" + teams.totalLevel(t),
                "<gray>Leader: <white>" + Msg.escape(teams.nameOf(t.leader()))));
        int slot = 9;
        for (UUID u : t.members()) {
            if (slot > 17) {
                break;
            }
            set(slot++, head(t, u));
        }
        set(29, icon(t.shareXp() ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE,
                t.shareXp() ? "<green>Shared XP: on" : "<gray>Shared XP: off",
                "<gray>Teammates within " + plugin.getConfig().getInt("teams.shared-xp-range", 48) + " blocks get "
                        + Msg.pct(plugin.getConfig().getDouble("teams.shared-xp", 0.1)),
                "<gray>of each other's skill XP, on top.",
                lead ? "<dark_gray>Click to switch" : "<dark_gray>The leader can switch it"));
        boolean chat = teams.chatOn(player);
        set(30, icon(chat ? Material.WRITABLE_BOOK : Material.BOOK, chat ? "<green>Team chat: on" : "<gray>Team chat: off",
                "<gray>On: everything you type goes to your team.", "<gray>Or use /tc <message>.", "<dark_gray>Click to switch"));
        set(31, icon(Material.NOTE_BLOCK, "<light_purple>Team voice",
                plugin.voice().ready() ? "<gray>Click to talk in your team's voice group" : "<gray>" + plugin.voice().problem(),
                "<dark_gray>/vc leave: back to proximity voice"));
        set(32, icon(t.open() ? Material.OAK_DOOR : Material.IRON_DOOR, t.open() ? "<green>Open team" : "<gray>Invite only",
                "<gray>Open: anyone can /party join.", lead ? "<dark_gray>Click to switch" : "<dark_gray>The leader can switch it"));
        set(35, icon(Material.BARRIER, "<red>Leave the team", "<dark_gray>Shift-click to leave"));
        fill(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
    }

    private ItemStack head(Team t, UUID u) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        Player online = Bukkit.getPlayer(u);
        PlayerStore.Summary s = plugin.store().index().get(u);
        it.editMeta(SkullMeta.class, m -> {
            m.setOwningPlayer(Bukkit.getOfflinePlayer(u));
            m.displayName(Msg.mm((t.leader().equals(u) ? "<gold>★ " : "<white>") + Msg.escape(plugin.teams().nameOf(u))));
            m.lore(Msg.lines(List.of(online != null ? "<green>Online" : "<dark_gray>Offline",
                    "<gray>Total level <white>" + (s == null ? 0 : s.total()))));
        });
        return it;
    }

    @Override
    public void click(int slot, ClickType type) {
        Teams teams = plugin.teams();
        Team t = teams.of(player);
        if (t == null) {
            Teams.Invite inv = teams.invite(player.getUniqueId());
            if (slot == 13 && inv != null) {
                teams.all().stream().filter(x -> x.id().equals(inv.team())).findFirst().ifPresent(team -> {
                    String err = teams.join(player, team);
                    if (err != null) {
                        Msg.send(player, "<red>" + err);
                    }
                });
                render();
            }
            return;
        }
        boolean lead = t.leader().equals(player.getUniqueId());
        switch (slot) {
            case 29 -> {
                if (lead) {
                    teams.setShareXp(t, !t.shareXp());
                }
            }
            case 30 -> teams.setChat(player, !teams.chatOn(player));
            case 31 -> {
                String err = plugin.voice().joinTeam(player);
                Msg.send(player, err == null ? "<green>You're in your team's voice group." : "<red>" + err);
            }
            case 32 -> {
                if (lead) {
                    teams.setOpen(t, !t.open());
                }
            }
            case 35 -> {
                if (type.isShiftClick()) {
                    teams.broadcast(t, "<yellow>" + Msg.escape(player.getName()) + " left the team.");
                    teams.remove(player.getUniqueId());
                    player.closeInventory();
                    return;
                }
            }
            default -> {
                return;
            }
        }
        render();
    }
}
