package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.award.Award;
import dev.kushcraft.award.Awards;
import dev.kushcraft.item.Items;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Awards: every achievement, grey until you unlock it. They're also in the
 * KushCraft tab of the advancements screen. Layout matches tools/gui.py awards().
 */
public final class AwardsMenu extends TabMenu {

    static final int FIRST = 9;
    /** Rows 1-5: up to 45 awards. */
    static final int SLOTS = 45;

    public AwardsMenu(Player player) {
        super(player, Tab.AWARDS);
    }

    @Override
    protected void page() {
        Awards awards = KushCraft.get().awards();
        Award[] all = Award.values();
        double earned = 0;
        for (int i = 0; i < all.length && i < SLOTS; i++) {
            Award a = all[i];
            boolean done = awards.has(player, a);
            if (done) {
                earned += a.reward();
            }
            List<String> lore = new ArrayList<>();
            if (done || !a.secret()) {
                lore.add("<gray>" + a.description());
            }
            String progress = done ? null : awards.progress(player, a);
            if (progress != null) {
                lore.add("<white>" + progress);
            }
            lore.add(done ? "<green>✔ <gold>" + money(a.reward()) : "<gold>" + money(a.reward()));
            String name = done ? "<" + a.color() + ">" + a.title()
                    : "<gray>" + (a.secret() ? "???" : a.title());
            set(FIRST + i, Items.icon(done ? a.icon() : a.icon() + "_locked", name, lore));
        }
        // the summary sits on this page's own tab
        int count = awards.count(player);
        set(Tab.AWARDS.ordinal(), Items.icon("tab_awards", "<green>Awards <gold>" + count + "/" + all.length,
                "<gold>" + money(earned) + " <dark_gray>earned",
                "<dark_gray>Also in the advancements screen (L)."));
    }
}
