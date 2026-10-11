package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.ranks.RankLadder;
import dev.kushcraft.util.Text;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The welcome / help menu (/menu, /kush help, and on a player's first join
 * each season). Pages: main, economy, ranks, rules, community - all text and
 * icons from menus.yml. It only shows information: there is no button here
 * that buys, ranks up or skips anything (rank-ups are /rankup, with every
 * check).
 */
public final class InfoMenu extends Menu {

    private final String page;
    private final Map<Integer, MenuTexts.Entry> bySlot = new HashMap<>();

    public InfoMenu(Player player, String page) {
        super(player, 6, "list", KushCraft.get().menuTexts().title(page));
        this.page = page;
    }

    public static void openMain(Player p) {
        new InfoMenu(p, "main").open();
    }

    private MenuTexts texts() {
        return KushCraft.get().menuTexts();
    }

    @Override
    public void render() {
        inv.clear();
        bySlot.clear();
        MenuTexts t = texts();
        for (MenuTexts.Entry e : t.entries(page)) {
            if (e.slot() >= 0 && e.slot() < inv.getSize()) {
                set(e.slot(), t.icon(player, e.icon(), e.name(), e.lore()));
                bySlot.put(e.slot(), e);
            }
        }
        if (page.equals("main")) {
            progress();
        }
        if (page.equals("ranks")) {
            ladder();
        }
        if (!page.equals("main")) {
            parent(new InfoMenu(player, "main"));
        }
        backButton(45);
    }

    /** The one-glance progress card on the main page (menus.yml pages.main.progress). */
    private void progress() {
        MenuTexts t = texts();
        int slot = Integer.parseInt(t.text("pages.main.progress.slot", "4"));
        Material icon = Material.matchMaterial(t.text("pages.main.progress.icon", "player_head"));
        List<String> lore = new ArrayList<>(t.lines("pages.main.progress.lore"));
        RankLadder.Progress pr = KushCraft.get().ranks().progress(player.getUniqueId());
        if (pr.next() != null) {
            lore.addAll(t.lines(pr.ready() ? "pages.main.progress.ready" : "pages.main.progress.not-ready"));
        } else {
            lore.addAll(t.lines("pages.main.progress.top"));
        }
        set(slot, t.icon(player, icon == null ? Material.PLAYER_HEAD : icon,
                t.text("pages.main.progress.name", "<gold>Your progress"), lore));
    }

    /** Every rank of the ladder, read from config.yml (so it's always the real numbers). */
    private void ladder() {
        MenuTexts t = texts();
        int first = Integer.parseInt(t.text("pages.ranks.ladder.first-slot", "18"));
        Material icon = Material.matchMaterial(t.text("pages.ranks.ladder.icon", "paper"));
        Material reached = Material.matchMaterial(t.text("pages.ranks.ladder.reached-icon", "lime_dye"));
        int mine = KushCraft.get().ranks().of(player.getUniqueId()).number();
        int slot = first;
        for (RankLadder.Rank r : KushCraft.get().ranks().all()) {
            if (slot >= 45) {
                break;
            }
            while (bySlot.containsKey(slot) && slot < 45) {
                slot++;
            }
            List<String> lore = new ArrayList<>();
            if (r.number() > 1) {
                lore.add("<gray>Costs <gold>" + KushCraft.get().economy().format(r.cost()));
                lore.add("<gray>Needs <white>" + hours(r.playtimeHours()) + "</white> active playtime");
                lore.add("<gray>and <white>" + hours(Math.max(r.waitHours(), KushCraft.get().ranks().minGapMillis() / 3_600_000.0))
                        + "</white> since your last rank-up");
            } else {
                lore.add("<gray>Where everyone starts");
            }
            lore.add("<aqua>" + r.workers() + " worker slot" + (r.workers() == 1 ? "" : "s"));
            if (r.number() == mine) {
                lore.add("<green>◀ You are here");
            }
            Material m = r.number() <= mine ? reached : icon;
            set(slot++, t.icon(player, m == null ? Material.PAPER : m, "<white>" + r.number() + ". " + r.colored(), lore));
        }
    }

    private static String hours(double h) {
        if (h <= 0) {
            return "no";
        }
        return h >= 48 ? Text.number(Math.round(h / 24 * 10) / 10.0) + " days" : Text.number(h) + " hours";
    }

    @Override
    public void click(int slot, ClickType click) {
        MenuTexts.Entry e = bySlot.get(slot);
        if (e == null) {
            return;
        }
        if (e.page() != null) {
            clickSound();
            new InfoMenu(player, e.page()).open();
            return;
        }
        if (e.link() != null) {
            sendLink(e);
        }
    }

    private void sendLink(MenuTexts.Entry e) {
        MenuTexts t = texts();
        List<String> urls = new ArrayList<>();
        if (e.link().equals("vote")) {
            urls.addAll(t.voteLinks());
        } else {
            String u = t.link(e.link());
            if (u != null) {
                urls.add(u);
            }
        }
        urls.removeIf(u -> u == null || u.isBlank());
        if (urls.isEmpty()) {
            player.sendActionBar(Text.mm("<gray>That link hasn't been set up yet."));
            failSound();
            return;
        }
        clickSound();
        player.closeInventory();
        player.sendMessage(Text.msg(t.fill(player, e.name()) + " <gray>(click to open):"));
        for (String u : urls) {
            player.sendMessage(Text.mm(" <aqua><u>" + Text.escape(u) + "</u>").clickEvent(ClickEvent.openUrl(u)));
        }
    }
}
