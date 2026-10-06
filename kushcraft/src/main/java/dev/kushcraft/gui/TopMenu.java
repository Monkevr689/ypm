package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.Items;
import dev.kushcraft.shop.Economy;
import dev.kushcraft.shop.Ranks;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Top Dealers: the players who sold the most hold the titles. A podium for
 * #1-#3, #4-#10 below it and your own place at the bottom.
 * Layout matches tools/gui.py top().
 */
public final class TopMenu extends TabMenu {

    /** Slots for places 1, 2, 3, 4..10. */
    static final int[] PLACES = {at(1, 4), at(2, 2), at(2, 6),
            at(4, 1), at(4, 2), at(4, 3), at(4, 4), at(4, 5), at(4, 6), at(4, 7)};
    static final int YOU = at(5, 4);

    public TopMenu(Player player) {
        super(player, Tab.TOP);
    }

    @Override
    protected void page() {
        KushCraft plugin = KushCraft.get();
        Ranks ranks = plugin.ranks();
        List<Economy.Rich> board = ranks.leaderboard();
        for (int i = 0; i < PLACES.length; i++) {
            int place = i + 1;
            Ranks.Rank r = ranks.forPlace(place);
            if (i < board.size()) {
                Economy.Rich e = board.get(i);
                set(PLACES[i], head(e.id(), "<white>#" + place + " " + Text.escape(e.name()),
                        List.of(r.colored() + bonus(r), "<gold>" + money(e.balance()) + " <dark_gray>sold")));
            } else {
                set(PLACES[i], Items.icon("ui_lock", "<dark_gray>#" + place + " - nobody yet",
                        r != ranks.everyone() ? r.colored() + bonus(r) : "<dark_gray>Sell to take it!"));
            }
        }
        int mine = ranks.place(player);
        List<String> lore = new ArrayList<>();
        lore.add(ranks.of(player).colored() + bonus(ranks.of(player)));
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

    private static String bonus(Ranks.Rank r) {
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
