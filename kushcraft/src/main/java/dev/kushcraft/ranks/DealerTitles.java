package dev.kushcraft.ranks;

import dev.kushcraft.KushCraft;
import dev.kushcraft.awards.Award;
import dev.kushcraft.util.Text;
import net.kyori.adventure.title.Title;
import dev.kushcraft.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dealer ranks go to whoever sells the most: the #1 seller on the server is
 * the Cartel Boss, #2 the Kingpin and so on (config.yml ranks.titles).
 * Everyone else is a Street Seller. Titles pay a bonus on every sale, show
 * in the tab list and change hands the moment someone sells more.
 */
public final class DealerTitles {

    /** top = the lowest leaderboard place that still gets this title (0 = everyone else). */
    public record Rank(int top, String name, String color, double bonus) {

        public String colored() {
            return "<" + color + ">" + name + "</" + color + ">";
        }
    }

    private final KushCraft plugin;
    private final List<Rank> titles = new ArrayList<>();
    private Rank everyone = new Rank(0, "Street Seller", "gray", 0);
    private List<Economy.Rich> board = List.of();
    private final Map<UUID, Integer> places = new HashMap<>();

    public DealerTitles(KushCraft plugin) {
        this.plugin = plugin;
    }

    public void load() {
        titles.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("ranks");
        int last = 0;
        if (sec != null) {
            for (Map<?, ?> m : sec.getMapList("titles")) {
                int top = m.get("top") instanceof Number n ? n.intValue() : 0;
                if (top <= last) {
                    plugin.getLogger().warning("ranks.titles: '" + m.get("name") + "' must have a bigger top than the one before it - skipped.");
                    continue;
                }
                last = top;
                titles.add(new Rank(top, String.valueOf(m.get("name")), color(m.get("color")), bonus(m.get("bonus"))));
            }
            ConfigurationSection e = sec.getConfigurationSection("everyone");
            if (e != null) {
                everyone = new Rank(0, e.getString("name", "Street Seller"), e.getString("color", "gray"),
                        Math.max(0, e.getDouble("bonus", 0)));
            }
        }
        refresh();
    }

    private static String color(Object o) {
        return o == null ? "white" : String.valueOf(o);
    }

    private static double bonus(Object o) {
        return o instanceof Number n ? Math.max(0, n.doubleValue()) : 0;
    }

    /** Re-sorts the leaderboard (after every sale). */
    public void refresh() {
        board = plugin.economy().topSales();
        places.clear();
        for (int i = 0; i < board.size(); i++) {
            places.put(board.get(i).id(), i + 1);
        }
    }

    public List<Rank> titles() {
        return titles;
    }

    public Rank everyone() {
        return everyone;
    }

    /** Best sellers first. */
    public List<Economy.Rich> leaderboard() {
        return board;
    }

    /** Leaderboard place (1 = sold the most), 0 = hasn't sold anything yet. */
    public int place(OfflinePlayer p) {
        return place(p.getUniqueId());
    }

    public int place(UUID id) {
        return places.getOrDefault(id, 0);
    }

    /** The title for a leaderboard place. */
    public Rank forPlace(int place) {
        if (place > 0) {
            for (Rank r : titles) {
                if (place <= r.top()) {
                    return r;
                }
            }
        }
        return everyone;
    }

    public Rank of(OfflinePlayer p) {
        return forPlace(place(p));
    }

    /** Sale price multiplier for this player's title (1.10 = +10%). */
    public double multiplier(Player p) {
        return 1 + of(p).bonus();
    }

    /** "#3 The Plug" */
    public String label(OfflinePlayer p) {
        int place = place(p);
        return (place > 0 ? "<white>#" + place + " " : "") + of(p).colored();
    }

    /** Records product sold (Shop sales and orders) and moves titles around. */
    public void sold(Player p, double money) {
        if (money <= 0) {
            return;
        }
        Map<UUID, Rank> before = new HashMap<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            before.put(o.getUniqueId(), of(o));
        }
        int placeBefore = place(p);
        plugin.economy().addSales(p, money);
        plugin.cartels().sold(p.getUniqueId(), money);
        refresh();
        plugin.awards().sales(p, plugin.economy().sales(p));
        Rank now = of(p);
        Rank was = before.getOrDefault(p.getUniqueId(), everyone);
        int place = place(p);
        if (place == 1) {
            plugin.awards().grant(p, Award.TOP_1);
        }
        if (now != was && now != everyone && (was == everyone || now.top() < was.top())) {
            p.showTitle(Title.title(Text.mm("<gold><bold>#" + place + "</bold>"), Text.mm("<white>You are now " + now.colored())));
            p.playSound(p.getLocation(), "minecraft:ui.toast.challenge_complete", SoundCategory.MASTER, 0.8f, 1f);
            Bukkit.broadcast(Text.msg("<white>" + Text.escape(p.getName()) + " <gray>is now " + now.colored()
                    + " <gray>(#" + place + " seller)"));
        } else if (placeBefore != place && place > 0) {
            p.sendActionBar(Text.mm("<gray>You're now <white>#" + place + "</white> on the leaderboard."));
        }
        for (Player o : Bukkit.getOnlinePlayers()) {
            Rank b = before.get(o.getUniqueId());
            if (o != p && b != null && b != everyone && of(o) != b) {
                o.sendMessage(Text.msg("<gray>" + Text.escape(p.getName()) + " sold more than you - you're now "
                        + of(o).colored() + " <gray>(#" + place(o) + ")."));
            }
            showInTab(o);
        }
    }

    /** Puts the title in front of the name (and the cartel after it) in the tab list (ranks.tab-list). */
    public void showInTab(Player p) {
        if (!plugin.getConfig().getBoolean("ranks.tab-list", true)) {
            return;
        }
        Rank r = of(p);
        dev.kushcraft.cartels.Cartel c = plugin.cartels().enabled() ? plugin.cartels().of(p) : null;
        if (r == everyone && c == null) {
            p.playerListName(null);
            return;
        }
        p.playerListName(Text.mm((r == everyone ? "" : "<" + r.color() + ">[" + r.name() + "]</" + r.color() + "> ")
                + "<white>" + Text.escape(p.getName()) + "</white>"
                + (c == null ? "" : " <dark_gray>·</dark_gray> " + c.colored())));
    }
}
