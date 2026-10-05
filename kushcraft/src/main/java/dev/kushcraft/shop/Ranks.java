package dev.kushcraft.shop;

import dev.kushcraft.KushCraft;
import dev.kushcraft.lab.LabRecipe;
import dev.kushcraft.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dealer ranks: the more product you sell, the higher your rank. Ranks
 * unlock Drug Lab recipes and pay a bonus on every sale. Defined in
 * config.yml (ranks:), level 1 is everyone's starting rank.
 */
public final class Ranks {

    public record Rank(int level, String name, String color, double sales, double bonus) {

        public String colored() {
            return "<" + color + ">" + name + "</" + color + ">";
        }
    }

    private final KushCraft plugin;
    private final List<Rank> ranks = new ArrayList<>();

    public Ranks(KushCraft plugin) {
        this.plugin = plugin;
    }

    public void load() {
        ranks.clear();
        int level = 1;
        double last = -1;
        for (Map<?, ?> m : plugin.getConfig().getMapList("ranks")) {
            double sales = m.get("sales") instanceof Number n ? n.doubleValue() : 0;
            if (level == 1) {
                sales = 0;
            }
            if (sales <= last) {
                plugin.getLogger().warning("ranks: '" + m.get("name") + "' needs more sales than the rank before it - skipped.");
                continue;
            }
            last = sales;
            double bonus = m.get("bonus") instanceof Number n ? n.doubleValue() : 0;
            String color = m.get("color") == null ? "white" : String.valueOf(m.get("color"));
            ranks.add(new Rank(level++, String.valueOf(m.get("name")), color, sales, Math.max(0, bonus)));
        }
        if (ranks.isEmpty()) {
            ranks.add(new Rank(1, "Dealer", "white", 0, 0));
        }
    }

    public List<Rank> all() {
        return ranks;
    }

    public Rank of(double sales) {
        Rank r = ranks.get(0);
        for (Rank x : ranks) {
            if (sales >= x.sales()) {
                r = x;
            }
        }
        return r;
    }

    public Rank of(OfflinePlayer p) {
        return of(plugin.economy().sales(p));
    }

    /** The rank after this one, or null at the top. */
    public Rank next(Rank r) {
        return r.level() < ranks.size() ? ranks.get(r.level()) : null;
    }

    /** Rank with this level (clamped to the ranks that exist). */
    public Rank level(int level) {
        return ranks.get(Math.max(1, Math.min(ranks.size(), level)) - 1);
    }

    public boolean canCook(Player p, LabRecipe r) {
        return p.hasPermission("kushcraft.admin") || of(p).level() >= Math.min(r.rank(), ranks.size());
    }

    /** Sale price multiplier for this player's rank (1.10 = +10%). */
    public double multiplier(Player p) {
        return 1 + of(p).bonus();
    }

    /** Records product sold at the Market (or an order) and announces a rank-up. */
    public void sold(Player p, double money) {
        if (money <= 0) {
            return;
        }
        Rank before = of(p);
        plugin.economy().addSales(p, money);
        Rank after = of(p);
        if (after.level() > before.level()) {
            p.showTitle(Title.title(Text.mm("<gold><bold>RANK UP!"), Text.mm("<white>You are now a " + after.colored())));
            p.playSound(p.getLocation(), "minecraft:ui.toast.challenge_complete", SoundCategory.MASTER, 0.8f, 1f);
            Bukkit.broadcast(Text.msg("<white>" + Text.escape(p.getName()) + " <gray>is now a " + after.colored()
                    + "<gray>!" + (after.bonus() > 0 ? " <dark_gray>(+" + Math.round(after.bonus() * 100) + "% on sales)" : "")));
            for (LabRecipe r : LabRecipe.values()) {
                if (Math.min(r.rank(), ranks.size()) == after.level()) {
                    p.sendMessage(Text.msg("<green>Unlocked: <white>" + r.output().display() + " <gray>(Drug Lab > Cook)"));
                }
            }
        }
    }
}
