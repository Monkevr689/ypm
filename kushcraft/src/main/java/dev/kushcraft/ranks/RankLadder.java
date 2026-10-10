package dev.kushcraft.ranks;

import dev.kushcraft.KushCraft;
import dev.kushcraft.cartels.Cartel;
import dev.kushcraft.economy.Tx;
import dev.kushcraft.storage.PlayerRecord;
import dev.kushcraft.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The rank ladder (config.yml ranks.ladder). Everyone starts at rank 1. Each
 * next rank costs money AND needs time: enough active playtime this season,
 * and enough real time since the last rank-up - so even the richest player
 * climbs at the pace of the clock. Ranks give worker slots.
 *
 * A rank-up is one step on the server thread: it checks the rank the player
 * clicked from (a double click can't rank up twice), the cooldowns, the
 * playtime, the wait and the money, then takes the money and raises the rank
 * together. Both land in the database in the same transaction (Persistence).
 */
public final class RankLadder {

    /** One rung: costs and waits are for reaching THIS rank from the one below. */
    public record Rank(int number, String name, String color, double cost, double playtimeHours, double waitHours,
                       int workers) {

        public String colored() {
            return "<" + color + ">" + name + "</" + color + ">";
        }
    }

    /** Where a player stands on the way to their next rank. */
    public record Progress(Rank current, Rank next, double money, long playtimeSeconds, long waitedMillis,
                           boolean moneyOk, boolean playtimeOk, boolean waitOk, long waitLeftMillis,
                           long playtimeLeftSeconds) {

        public boolean ready() {
            return next != null && moneyOk && playtimeOk && waitOk;
        }
    }

    private final KushCraft plugin;
    private final List<Rank> ranks = new ArrayList<>();

    public RankLadder(KushCraft plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // config
    // ------------------------------------------------------------------

    public void load() {
        ranks.clear();
        int n = 1;
        for (Map<?, ?> m : plugin.getConfig().getMapList("ranks.ladder")) {
            String name = String.valueOf(m.get("name"));
            double cost = n == 1 ? 0 : num(m.get("cost"));
            ranks.add(new Rank(n, name, m.get("color") == null ? "gray" : String.valueOf(m.get("color")), cost,
                    n == 1 ? 0 : num(m.get("playtime-hours")), n == 1 ? 0 : num(m.get("wait-hours")),
                    (int) Math.max(0, num(m.get("workers")))));
            n++;
        }
        if (ranks.isEmpty()) {
            ranks.add(new Rank(1, "Nobody", "gray", 0, 0, 0, 1));
            plugin.getLogger().warning("ranks.ladder is empty: everyone is rank 1 with 1 worker.");
        }
        for (int i = 1; i < ranks.size(); i++) {
            if (ranks.get(i).cost() < ranks.get(i - 1).cost()) {
                plugin.getLogger().warning("ranks.ladder: " + ranks.get(i).name() + " costs less than the rank below it.");
            }
        }
    }

    private static double num(Object o) {
        return o instanceof Number x ? Math.max(0, x.doubleValue()) : 0;
    }

    public List<Rank> all() {
        return List.copyOf(ranks);
    }

    public Rank get(int number) {
        return ranks.get(Math.max(1, Math.min(ranks.size(), number)) - 1);
    }

    public int top() {
        return ranks.size();
    }

    /** ranks.min-gap-minutes: at least this long between two rank-ups, whatever the rank says. */
    public long minGapMillis() {
        return (long) (Math.max(0, plugin.getConfig().getDouble("ranks.min-gap-minutes", 30)) * 60_000L);
    }

    // ------------------------------------------------------------------
    // players
    // ------------------------------------------------------------------

    public Rank of(UUID id) {
        PlayerRecord r = plugin.players().get(id);
        return get(r == null ? 1 : r.rank());
    }

    /** Worker slots the player's rank gives. */
    public int workerSlots(UUID id) {
        return of(id).workers();
    }

    public Progress progress(UUID id) {
        PlayerRecord r = plugin.economy().account(id);
        Rank cur = get(r.rank());
        Rank next = r.rank() >= ranks.size() ? null : ranks.get(r.rank());
        double money = plugin.economy().balance(id);
        long now = System.currentTimeMillis();
        long waited = Math.max(0, now - r.rankedAt());
        if (next == null) {
            return new Progress(cur, null, money, r.playtime(), waited, true, true, true, 0, 0);
        }
        long needWait = Math.max((long) (next.waitHours() * 3_600_000L), minGapMillis());
        long needPlay = (long) (next.playtimeHours() * 3600);
        return new Progress(cur, next, money, r.playtime(), waited, money >= next.cost(), r.playtime() >= needPlay,
                waited >= needWait, Math.max(0, needWait - waited), Math.max(0, needPlay - r.playtime()));
    }

    /**
     * Ranks the player up one step from {@code fromRank} (the rank shown when they clicked).
     * Returns an error for the player, or null when it worked.
     */
    public String rankUp(Player p, int fromRank) {
        return rankUp(p.getUniqueId(), fromRank, p);
    }

    /** The rank-up itself; p (may be null) gets the title and the tab list update. */
    public String rankUp(UUID id, int fromRank, Player p) {
        PlayerRecord r = plugin.economy().account(id);
        long now = System.currentTimeMillis();
        if (now - r.lastRankTry < 2_000L) {
            return "Slow down - one rank-up at a time.";
        }
        r.lastRankTry = now;
        if (r.rank() != fromRank) {
            return "Your rank already changed - open the menu again.";
        }
        Progress pr = progress(id);
        if (pr.next() == null) {
            return "You're already at the top: " + pr.current().name() + ".";
        }
        if (!pr.playtimeOk()) {
            return "You need " + Text.duration(pr.playtimeLeftSeconds() * 1000L) + " more active playtime.";
        }
        if (!pr.waitOk()) {
            return "You can rank up again in " + Text.duration(pr.waitLeftMillis()) + ".";
        }
        Rank next = pr.next();
        if (!plugin.economy().withdraw(id, next.cost(), Tx.RANK_UP, null,
                pr.current().name() + " -> " + next.name() + " (rank " + next.number() + ")")) {
            return next.name() + " costs " + plugin.economy().format(next.cost()) + " (you have "
                    + plugin.economy().format(pr.money()) + ").";
        }
        r.rank(next.number(), now);
        plugin.workers().slotsChanged(id);
        if (p != null) {
            celebrate(p, next);
        }
        return null;
    }

    private void celebrate(Player p, Rank next) {
        p.showTitle(Title.title(Text.mm(next.colored()), Text.mm("<white>Rank " + next.number() + " <gray>· "
                + next.workers() + " worker" + (next.workers() == 1 ? "" : "s"))));
        p.playSound(p.getLocation(), "minecraft:ui.toast.challenge_complete", SoundCategory.MASTER, 0.8f, 1f);
        if (plugin.getConfig().getBoolean("ranks.announce", true)) {
            Bukkit.broadcast(Text.msg("<white>" + Text.escape(p.getName()) + " <gray>ranked up to " + next.colored()
                    + "<gray>!"));
        }
        showInTab(p);
    }

    /** Admin: set a rank (logged). Starts the wait for the next one now. */
    public void set(UUID id, int rank, String by) {
        PlayerRecord r = plugin.economy().account(id);
        int to = Math.max(1, Math.min(ranks.size(), rank));
        plugin.economy().ledger().log(id, Tx.ADMIN, 0, r.balance(), by, "rank set " + r.rank() + " -> " + to);
        r.rank(to, System.currentTimeMillis());
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            showInTab(p);
        }
        plugin.workers().slotsChanged(id);
    }

    /** "[Rank] Name · Cartel" in the tab list (ranks.tab-list). */
    public void showInTab(Player p) {
        if (!plugin.getConfig().getBoolean("ranks.tab-list", true)) {
            return;
        }
        Rank r = of(p.getUniqueId());
        Cartel c = plugin.cartels().enabled() ? plugin.cartels().of(p) : null;
        p.playerListName(Text.mm("<" + r.color() + ">[" + r.name() + "]</" + r.color() + "> <white>"
                + Text.escape(p.getName()) + "</white>" + (c == null ? "" : " <dark_gray>·</dark_gray> " + c.colored())));
    }
}
