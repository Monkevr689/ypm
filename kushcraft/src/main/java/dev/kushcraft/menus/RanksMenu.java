package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.ranks.RankLadder;
import dev.kushcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/**
 * /rankup: your rank, what the next one needs (money, active playtime, time
 * since your last rank-up) and the rank-up button. The button goes through
 * RankLadder.rankUp, which checks everything again on the server.
 */
public final class RanksMenu extends Menu {

    private static final int HEADER = 4;
    private static final int MONEY = 20;
    private static final int PLAYTIME = 22;
    private static final int WAIT = 24;
    private static final int BUTTON = 40;
    private static final int LADDER = 53;

    /** The rank the menu showed: the click only works from it (no double rank-ups). */
    private int shownRank;

    public RanksMenu(Player player) {
        super(player, 6, "list", "Rank up");
    }

    private KushCraft plugin() {
        return KushCraft.get();
    }

    @Override
    public void render() {
        inv.clear();
        backButton(45);
        MenuTexts t = plugin().menuTexts();
        RankLadder.Progress pr = plugin().ranks().progress(player.getUniqueId());
        shownRank = pr.current().number();
        set(HEADER, t.icon(player, Material.NETHER_STAR, "<white>Rank " + shownRank + ": " + pr.current().colored(),
                List.of("<aqua>" + pr.current().workers() + " worker slot" + (pr.current().workers() == 1 ? "" : "s"),
                        "<gray>Active playtime this season: <white>" + Text.duration(pr.playtimeSeconds() * 1000L),
                        "<gray>Money: <gold>" + plugin().economy().format(pr.money()))));
        set(LADDER, t.icon(player, Material.LADDER, "<yellow>Every rank", List.of("<gray>Costs, times and worker slots")));
        RankLadder.Rank next = pr.next();
        if (next == null) {
            set(PLAYTIME, t.icon(player, Material.GOLD_BLOCK, "<gold>You're at the top!", List.of()));
            return;
        }
        set(MONEY, check(pr.moneyOk(), Material.GOLD_INGOT, "Money",
                plugin().economy().format(next.cost()) + " <dark_gray>(you have " + plugin().economy().format(pr.money()) + ")"));
        set(PLAYTIME, check(pr.playtimeOk(), Material.CLOCK, "Active playtime",
                Text.number(next.playtimeHours()) + "h this season" + (pr.playtimeOk() ? ""
                        : " <dark_gray>(" + Text.duration(pr.playtimeLeftSeconds() * 1000L) + " to go)")));
        set(WAIT, check(pr.waitOk(), Material.COMPASS, "Time since your last rank-up",
                pr.waitOk() ? "done" : Text.duration(pr.waitLeftMillis()) + " to go"));
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Next: <white>Rank " + next.number() + " " + next.colored());
        lore.add("<aqua>" + next.workers() + " worker slot" + (next.workers() == 1 ? "" : "s"));
        lore.add("");
        lore.add(pr.ready() ? "<green><bold>Click to rank up</bold> <gray>(" + plugin().economy().format(next.cost()) + ")"
                : "<red>Not yet - see the three checks above.");
        set(BUTTON, t.icon(player, pr.ready() ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                pr.ready() ? "<green><bold>Rank up</bold>" : "<red>Rank up", lore));
    }

    private org.bukkit.inventory.ItemStack check(boolean ok, Material icon, String what, String value) {
        return plugin().menuTexts().icon(player, icon, (ok ? "<green>✔ " : "<red>✘ ") + what,
                List.of("<white>" + value));
    }

    @Override
    public void click(int slot, ClickType click) {
        if (slot == LADDER) {
            openChild(new InfoMenu(player, "ranks"));
            return;
        }
        if (slot != BUTTON) {
            return;
        }
        String error = plugin().ranks().rankUp(player, shownRank);
        if (error != null) {
            player.sendActionBar(Text.mm("<red>" + error));
            failSound();
        } else {
            successSound();
        }
        render();
    }

    @Override
    public void tick() {
        render();
    }
}
