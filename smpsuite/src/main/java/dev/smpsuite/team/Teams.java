package dev.smpsuite.team;

import dev.smpsuite.Msg;
import dev.smpsuite.SMPSuite;
import dev.smpsuite.data.PlayerStore;
import dev.smpsuite.skill.Skill;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Teams (parties): opt-in groups with a colour and [TAG] on name tags and in
 * the tab list, team chat, a little shared XP and team stats. Teammates can't
 * hurt each other. Nothing about them is PvP: no wars, no claims, no factions.
 */
public final class Teams implements Listener {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_ \\-]+");
    private static final Pattern TAG = Pattern.compile("[A-Za-z0-9]+");

    /** A pending invite. */
    public record Invite(UUID team, UUID from, long expires) {
    }

    private final SMPSuite plugin;
    private final File file;
    private final Map<UUID, Team> teams = new ConcurrentHashMap<>();
    private final Map<UUID, Team> byMember = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    private final Set<UUID> chatOn = ConcurrentHashMap.newKeySet();

    public Teams(SMPSuite plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("teams.enabled", true);
    }

    public int maxMembers() {
        return Math.max(2, plugin.getConfig().getInt("teams.max-members", 8));
    }

    // ------------------------------------------------------------------
    // storage
    // ------------------------------------------------------------------

    public void load() {
        teams.clear();
        byMember.clear();
        if (file.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection sec = y.getConfigurationSection("teams");
            if (sec != null) {
                for (String k : sec.getKeys(false)) {
                    ConfigurationSection s = sec.getConfigurationSection(k);
                    try {
                        Team t = new Team(UUID.fromString(k));
                        t.name = s.getString("name", "Team");
                        t.tag = s.getString("tag", "T");
                        NamedTextColor c = NamedTextColor.NAMES.value(s.getString("color", "aqua"));
                        t.color = c == null ? NamedTextColor.AQUA : c;
                        t.leader = UUID.fromString(s.getString("leader", ""));
                        for (String m : s.getStringList("members")) {
                            t.members.add(UUID.fromString(m));
                        }
                        t.members.add(t.leader);
                        t.shareXp = s.getBoolean("share-xp", true);
                        t.open = s.getBoolean("open", false);
                        t.created = s.getLong("created");
                        add(t);
                    } catch (RuntimeException e) {
                        plugin.getLogger().warning("teams.yml: skipped a broken team " + k);
                    }
                }
            }
        }
        cleanBoards();
        for (Team t : teams.values()) {
            sync(t);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Team t : teams.values()) {
            String k = "teams." + t.id;
            y.set(k + ".name", t.name);
            y.set(k + ".tag", t.tag);
            y.set(k + ".color", t.colorName());
            y.set(k + ".leader", t.leader.toString());
            List<String> m = new ArrayList<>();
            for (UUID u : t.members) {
                m.add(u.toString());
            }
            y.set(k + ".members", m);
            y.set(k + ".share-xp", t.shareXp);
            y.set(k + ".open", t.open);
            y.set(k + ".created", t.created);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save teams.yml", e);
        }
    }

    private void add(Team t) {
        teams.put(t.id, t);
        for (UUID m : t.members) {
            byMember.put(m, t);
        }
    }

    // ------------------------------------------------------------------
    // looking up
    // ------------------------------------------------------------------

    public Collection<Team> all() {
        return Collections.unmodifiableCollection(teams.values());
    }

    public Team of(UUID player) {
        return byMember.get(player);
    }

    public Team of(Player p) {
        return of(p.getUniqueId());
    }

    public Team byName(String name) {
        for (Team t : teams.values()) {
            if (t.name.equalsIgnoreCase(name) || t.tag.equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }

    public boolean sameTeam(Player a, Player b) {
        return sameTeam(a.getUniqueId(), b.getUniqueId());
    }

    public boolean sameTeam(UUID a, UUID b) {
        Team t = of(a);
        return t != null && t == of(b);
    }

    /** Online teammates (not the player) within r blocks in the same world. */
    public List<Player> teammatesNear(Player p, double r) {
        List<Player> out = new ArrayList<>();
        Team t = of(p);
        if (t == null) {
            return out;
        }
        for (UUID u : t.members) {
            Player m = Bukkit.getPlayer(u);
            if (m != null && m != p && m.getWorld().equals(p.getWorld())
                    && m.getLocation().distanceSquared(p.getLocation()) <= r * r) {
                out.add(m);
            }
        }
        return out;
    }

    public List<Player> online(Team t) {
        List<Player> out = new ArrayList<>();
        for (UUID u : t.members) {
            Player m = Bukkit.getPlayer(u);
            if (m != null) {
                out.add(m);
            }
        }
        return out;
    }

    public String nameOf(UUID u) {
        PlayerStore.Summary s = plugin.store().index().get(u);
        if (s != null && !s.name().isEmpty()) {
            return s.name();
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
        return op.getName() == null ? u.toString().substring(0, 8) : op.getName();
    }

    /** Sum of every member's skill levels. */
    public int totalLevel(Team t) {
        int n = 0;
        for (UUID u : t.members) {
            PlayerStore.Summary s = plugin.store().index().get(u);
            if (s != null) {
                n += s.total();
            }
        }
        return n;
    }

    /** Teams by total level, best first. */
    public List<Team> top() {
        List<Team> list = new ArrayList<>(teams.values());
        list.sort(Comparator.comparingInt(this::totalLevel).reversed());
        return list;
    }

    public Invite invite(UUID player) {
        Invite i = invites.get(player);
        if (i != null && (i.expires() < System.currentTimeMillis() || !teams.containsKey(i.team()))) {
            invites.remove(player);
            return null;
        }
        return i;
    }

    // ------------------------------------------------------------------
    // changing teams (each returns an error message, or null when it worked)
    // ------------------------------------------------------------------

    public String validName(String name) {
        int max = Math.max(3, plugin.getConfig().getInt("teams.name-length", 16));
        if (name == null || name.length() < 3 || name.length() > max || !NAME.matcher(name).matches()) {
            return "A team name is 3-" + max + " letters, numbers, spaces, - or _.";
        }
        Team other = byName(name);
        return other != null ? "There's already a team called " + other.name + "." : null;
    }

    public String validTag(String tag) {
        int max = Math.max(1, plugin.getConfig().getInt("teams.tag-length", 4));
        if (tag == null || tag.isEmpty() || tag.length() > max || !TAG.matcher(tag).matches()) {
            return "A tag is 1-" + max + " letters or numbers.";
        }
        for (Team t : teams.values()) {
            if (t.tag.equalsIgnoreCase(tag)) {
                return "Team " + t.name + " already uses the tag " + t.tag + ".";
            }
        }
        return null;
    }

    public static NamedTextColor parseColor(String s) {
        return s == null ? null : NamedTextColor.NAMES.value(s.toLowerCase(Locale.ROOT));
    }

    public String create(Player leader, String name, String tag, NamedTextColor color) {
        return create(leader.getUniqueId(), name, tag, color);
    }

    public String create(UUID leader, String name, String tag, NamedTextColor color) {
        if (!enabled()) {
            return "Teams are turned off on this server.";
        }
        if (of(leader) != null) {
            return "Leave your team first.";
        }
        String err = validName(name);
        if (err != null) {
            return err;
        }
        if (tag == null) {
            tag = defaultTag(name);
        }
        tag = tag.toUpperCase(Locale.ROOT);
        err = validTag(tag);
        if (err != null) {
            return err;
        }
        Team t = new Team(UUID.randomUUID());
        t.name = name;
        t.tag = tag;
        t.color = color == null ? randomColor() : color;
        t.leader = leader;
        t.members.add(leader);
        t.created = System.currentTimeMillis();
        add(t);
        sync(t);
        save();
        plugin.voice().teamChanged(t);
        return null;
    }

    private String defaultTag(String name) {
        String letters = name.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        int max = Math.max(1, plugin.getConfig().getInt("teams.tag-length", 4));
        String base = letters.isEmpty() ? "T" : letters.substring(0, Math.min(max, letters.length()));
        String tag = base;
        for (int i = 2; validTag(tag) != null && i < 100; i++) {
            tag = base.substring(0, Math.min(base.length(), max - 1)) + (i % 10);
        }
        return tag;
    }

    private static NamedTextColor randomColor() {
        NamedTextColor[] nice = {NamedTextColor.AQUA, NamedTextColor.GREEN, NamedTextColor.GOLD, NamedTextColor.LIGHT_PURPLE,
                NamedTextColor.YELLOW, NamedTextColor.BLUE, NamedTextColor.RED, NamedTextColor.DARK_AQUA};
        return nice[java.util.concurrent.ThreadLocalRandom.current().nextInt(nice.length)];
    }

    public String inviteTo(Player from, Player to) {
        Team t = of(from);
        if (t == null) {
            return "You're not in a team. /party create <name>";
        }
        if (of(to) != null) {
            return to.getName() + " is already in a team.";
        }
        if (t.members.size() >= maxMembers()) {
            return "Your team is full (" + maxMembers() + ").";
        }
        invites.put(to.getUniqueId(), new Invite(t.id, from.getUniqueId(), System.currentTimeMillis() + 300_000L));
        return null;
    }

    public String join(Player p, Team t) {
        return join(p.getUniqueId(), t);
    }

    public String join(UUID p, Team t) {
        if (of(p) != null) {
            return "Leave your team first.";
        }
        if (t.members.size() >= maxMembers()) {
            return t.name + " is full.";
        }
        invites.remove(p);
        t.members.add(p);
        byMember.put(p, t);
        sync(t);
        save();
        plugin.voice().teamChanged(t);
        broadcast(t, "<green>" + Msg.escape(nameOf(p)) + " joined the team.");
        return null;
    }

    /** Leaves (or is kicked from) a team; the last one out closes it, a leaving leader hands over. */
    public void remove(UUID player) {
        Team t = of(player);
        if (t == null) {
            return;
        }
        t.members.remove(player);
        byMember.remove(player);
        chatOn.remove(player);
        String name = nameOf(player);
        org.bukkit.scoreboard.Team board = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(t.board());
        if (board != null) {
            board.removeEntry(name);
        }
        plugin.voice().left(player);
        if (t.members.isEmpty()) {
            disband(t);
            return;
        }
        if (t.leader.equals(player)) {
            t.leader = t.members.iterator().next();
            broadcast(t, "<yellow>" + Msg.escape(nameOf(t.leader)) + " leads the team now.");
        }
        save();
    }

    public void disband(Team t) {
        teams.remove(t.id);
        for (UUID m : t.members) {
            byMember.remove(m);
            chatOn.remove(m);
        }
        org.bukkit.scoreboard.Team board = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(t.board());
        if (board != null) {
            board.unregister();
        }
        invites.values().removeIf(i -> i.team().equals(t.id));
        plugin.voice().teamDisbanded(t);
        save();
    }

    public void promote(Team t, UUID member) {
        t.leader = member;
        save();
    }

    public void setColor(Team t, NamedTextColor c) {
        t.color = c;
        sync(t);
        save();
    }

    public void setTag(Team t, String tag) {
        t.tag = tag.toUpperCase(Locale.ROOT);
        sync(t);
        save();
    }

    public void rename(Team t, String name) {
        t.name = name;
        sync(t);
        save();
        plugin.voice().teamChanged(t);
    }

    public void setShareXp(Team t, boolean on) {
        t.shareXp = on;
        save();
    }

    public void setOpen(Team t, boolean on) {
        t.open = on;
        save();
    }

    public void broadcast(Team t, String msg) {
        for (Player m : online(t)) {
            m.sendMessage(Msg.mm("<" + t.colorName() + ">[" + t.tag + "]</" + t.colorName() + "> <gray>" + msg));
        }
    }

    // ------------------------------------------------------------------
    // shared XP
    // ------------------------------------------------------------------

    /** Teammates nearby get a share of skill XP on top (nobody loses any). */
    public void shareXp(Player p, Skill s, double xp) {
        Team t = of(p);
        double share = plugin.getConfig().getDouble("teams.shared-xp", 0.10);
        if (t == null || !t.shareXp || share <= 0 || !enabled()) {
            return;
        }
        double range = plugin.getConfig().getDouble("teams.shared-xp-range", 48);
        for (Player m : teammatesNear(p, range)) {
            plugin.skills().addXp(m, s, xp * share, true);
        }
    }

    // ------------------------------------------------------------------
    // name tags
    // ------------------------------------------------------------------

    private boolean tags() {
        return plugin.getConfig().getBoolean("teams.name-tags", true);
    }

    /** Removes old SMPSuite scoreboard teams (teams that no longer exist). */
    private void cleanBoards() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (org.bukkit.scoreboard.Team b : new ArrayList<>(sb.getTeams())) {
            if (b.getName().startsWith("smp_")) {
                b.unregister();
            }
        }
    }

    /** Puts a team's colour and tag on its members' name tags. */
    public void sync(Team t) {
        if (!tags() || !enabled()) {
            return;
        }
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team b = sb.getTeam(t.board());
        if (b == null) {
            b = sb.registerNewTeam(t.board());
        }
        b.prefix(Msg.mm("<" + t.colorName() + ">[" + t.tag + "]</" + t.colorName() + "> "));
        b.color(t.color);
        b.setAllowFriendlyFire(false);
        b.setCanSeeFriendlyInvisibles(true);
        for (String e : new ArrayList<>(b.getEntries())) {
            boolean member = false;
            for (UUID u : t.members) {
                if (nameOf(u).equals(e)) {
                    member = true;
                    break;
                }
            }
            if (!member) {
                b.removeEntry(e);
            }
        }
        for (UUID u : t.members) {
            Player p = Bukkit.getPlayer(u);
            String name = p != null ? p.getName() : nameOf(u);
            if (!b.hasEntry(name)) {
                b.addEntry(name);
            }
        }
    }

    /** A member joined the server (maybe with a new name). */
    public void joined(Player p) {
        Team t = of(p);
        if (t != null) {
            sync(t);
        }
    }

    // ------------------------------------------------------------------
    // team chat
    // ------------------------------------------------------------------

    public boolean chatOn(Player p) {
        return chatOn.contains(p.getUniqueId());
    }

    public void setChat(Player p, boolean on) {
        if (on) {
            chatOn.add(p.getUniqueId());
        } else {
            chatOn.remove(p.getUniqueId());
        }
    }

    public void teamChat(Player p, Component message) {
        Team t = of(p);
        if (t == null) {
            return;
        }
        Component line = Msg.mm("<" + t.colorName() + ">[" + t.tag + "]</" + t.colorName() + "> <white>"
                + Msg.escape(p.getName()) + "</white> <dark_gray>»</dark_gray> ").append(message.colorIfAbsent(NamedTextColor.GRAY));
        for (UUID u : t.members) {
            Player m = Bukkit.getPlayer(u);
            if (m != null) {
                m.sendMessage(line);
            }
        }
        plugin.getLogger().info("[Team " + t.name + "] " + p.getName() + ": "
                + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (chatOn.contains(p.getUniqueId()) && of(p) != null) {
            e.setCancelled(true);
            teamChat(p, e.message());
        }
    }

    public void quit(UUID id) {
        invites.remove(id);
    }
}
