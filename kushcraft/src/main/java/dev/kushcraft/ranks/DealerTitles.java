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
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("dealer-titles");
        int last = 0;
        if (sec != null) {
            for (Map<?, ?> m : sec.getMapList("titles")) {
                int top = m.get("top") instanceof Number n ? n.intValue() : 0;
                if (top <= last) {
                     plugin.getLogger().warning("dealer-titles.titles: '" + m.get("name") + "' must have a bigger top than the one before it - skipped.");
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

    /** Sorts the whole leaderboard again (start-up, admin changes, a reset). */
    public void refresh() {
        board = new java.util.ArrayList<>(plugin.economy().topSales());
        places.clear();
        for (int i = 0; i < board.size(); i++) {
            places.put(board.get(i).id(), i + 1);
        }
    }

    private static final java.util.Comparator<Economy.Rich> ORDER = (a, b) -> a.balance() != b.balance()
            ? Double.compare(b.balance(), a.balance()) : a.name().compareToIgnoreCase(b.name());

    /**
     * One seller's total changed: they move to their new place (a binary search and a shift, not
     * a sort of everyone - sales happen many times a second on a big server).
     */
    private void moved(UUID id) {
        var r = plugin.players().get(id);
        if (r == null || r.sales() <= 0) {
            return;
        }
        Integer old = places.get(id);
        if (old != null) {
            board.remove(old - 1);
        }
        Economy.Rich now = new Economy.Rich(id, r.name() == null ? "?" : r.name(), r.sales() / 100.0);
        int at = java.util.Collections.binarySearch(board, now, ORDER);
        at = at < 0 ? -at - 1 : at;
        board.add(at, now);
        int from = old == null ? at : Math.min(old - 1, at);
        int to = old == null ? board.size() - 1 : Math.max(old - 1, at);
        for (int i = from; i <= to; i++) {
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
        // only the titled players can lose a title to this sale: remember theirs
        Map<UUID, Rank> before = new HashMap<>();
        int titled = titles.isEmpty() ? 0 : titles.get(titles.size() - 1).top();
        for (int i = 0; i < Math.min(titled + 1, board.size()); i++) {
            UUID id = board.get(i).id();
            before.put(id, forPlace(i + 1));
        }
        int placeBefore = place(p);
        plugin.economy().addSales(p, money);
        plugin.cartels().sold(p.getUniqueId(), money);
        moved(p.getUniqueId());
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
        before.forEach((id, b) -> {
            Player o = Bukkit.getPlayer(id);
            if (o != null && o != p && b != everyone && of(o) != b) {
                o.sendMessage(Text.msg("<gray>" + Text.escape(p.getName()) + " sold more than you - you're now "
                        + of(o).colored() + " <gray>(#" + place(o) + ")."));
            }
        });
    }

    /** A sale of an offline player's Runner: their place moves (no messages). */
    public void soldOffline(UUID id, double money) {
        if (money <= 0) {
            return;
        }
        var o = Bukkit.getOfflinePlayer(id);
        plugin.economy().addSales(o, money);
        plugin.cartels().sold(id, money);
        moved(id);
    }

    /** The tab list shows the ladder rank and the cartel (RankLadder). */
    public void showInTab(Player p) {
        plugin.ranks().showInTab(p);
    }
}
