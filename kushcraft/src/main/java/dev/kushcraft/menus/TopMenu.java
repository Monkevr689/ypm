package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.cartels.Cartel;
import dev.kushcraft.cartels.Cartels;
import dev.kushcraft.items.Items;
import dev.kushcraft.economy.Economy;
import dev.kushcraft.ranks.DealerTitles;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cartel > Top Dealers: the players who sold the most hold the titles. A
 * podium for #1-#3, #4-#10 below it and your own place at the bottom. The
 * button in the corner flips to the best cartels. Layout matches
 * tools/gui.py top().
 */
public final class TopMenu extends TabMenu {

    /** Slots for places 1, 2, 3, 4..10. */
    static final int[] PLACES = {at(1, 4), at(2, 2), at(2, 6),
            at(4, 1), at(4, 2), at(4, 3), at(4, 4), at(4, 5), at(4, 6), at(4, 7)};
    static final int YOU = at(5, 4);
    static final int SWITCH = at(5, 8);

    private boolean cartelMode;

    public TopMenu(Player player) {
        super(player, Tab.CARTEL, "top", "Top Dealers");
    }

    @Override
    protected boolean subPage() {
        return true;
    }

    @Override
    protected void page() {
        if (cartelMode) {
            cartels();
        } else {
            dealers();
        }
        boolean on = KushCraft.get().cartels().enabled();
        if (on) {
            set(SWITCH, cartelMode ? Items.icon("award_crown", "<gold>Top Dealers", "<dark_gray>Click: show players")
                    : Items.tintedIcon("cartel_banner", 0xE83A3A, "<gold>Top Cartels", List.of("<dark_gray>Click: show cartels")));
        }
    }

    private void dealers() {
        KushCraft plugin = KushCraft.get();
        DealerTitles titles = plugin.titles();
        List<Economy.Rich> board = titles.leaderboard();
        for (int i = 0; i < PLACES.length; i++) {
            int place = i + 1;
            DealerTitles.Rank r = titles.forPlace(place);
            if (i < board.size()) {
                Economy.Rich e = board.get(i);
                set(PLACES[i], head(e.id(), "<white>#" + place + " " + Text.escape(e.name()),
                        List.of(r.colored() + bonus(r), "<gold>" + money(e.balance()) + " <dark_gray>sold")));
            } else {
                set(PLACES[i], Items.icon("ui_lock", "<dark_gray>#" + place + " - nobody yet",
                        r != titles.everyone() ? r.colored() + bonus(r) : "<dark_gray>Sell to take it!"));
            }
        }
        int mine = titles.place(player);
        List<String> lore = new ArrayList<>();
        lore.add(titles.of(player).colored() + bonus(titles.of(player)));
        lore.add("<gold>" + money(plugin.economy().sales(player)) + " <dark_gray>sold");
        if (mine > 1) {
            Economy.Rich ahead = board.get(mine - 2);
            lore.add("<gray>Sell " + money(ahead.balance() - plugin.economy().sales(player) + 0.01)
                    + " to pass " + Text.escape(ahead.name()));
        } else if (mine == 0) {
            lore.add("<gray>Sell product to get on the board.");
        }
        set(YOU, head(player.getUniqueId(), "<green>You" + (mine > 0 ? " <white>#" + mine : ""), lore));
    }

    private void cartels() {
        Cartels cs = KushCraft.get().cartels();
        List<Cartel> board = cs.top();
        for (int i = 0; i < PLACES.length; i++) {
            if (i < board.size()) {
                Cartel c = board.get(i);
                set(PLACES[i], Items.tintedIcon("cartel_banner", c.color(), "<white>#" + (i + 1) + " " + c.colored(),
                        List.of("<gold>" + cs.tier(c).name() + " <dark_gray>· <gray>" + c.members().size() + " members",
                                "<gold>" + money(c.sales()) + " <dark_gray>sold")));
            } else {
                set(PLACES[i], Items.icon("ui_lock", "<dark_gray>#" + (i + 1) + " - no cartel yet"));
            }
        }
        Cartel mine = cs.of(player);
        set(YOU, mine == null ? Items.icon("ui_members", "<gray>You're not in a cartel", "<dark_gray>Start one in the Cartel tab.")
                : Items.tintedIcon("cartel_banner", mine.color(), "<green>Your cartel <white>#" + cs.place(mine),
                List.of(mine.colored(), "<gold>" + money(mine.sales()) + " <dark_gray>sold")));
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        if (slot == SWITCH && KushCraft.get().cartels().enabled()) {
            cartelMode = !cartelMode;
            clickSound();
            render();
        }
    }

    private static String bonus(DealerTitles.Rank r) {
        return r.bonus() > 0 ? " <green>+" + Math.round(r.bonus() * 100) + "%" : "";
    }

    private static ItemStack head(UUID id, String name, List<String> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        it.editMeta(SkullMeta.class, m -> {
            m.setOwningPlayer(Bukkit.getOfflinePlayer(id));
            m.itemName(Text.mm(name));
            m.lore(Text.lines(lore));
        });
        return it;
    }
}
