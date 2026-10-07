package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.award.Award;
import dev.kushcraft.award.Awards;
import dev.kushcraft.item.Items;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Awards: every achievement, grey until you unlock it, 36 to a page (rows
 * 1-4; the page arrows and your count on row 5). They're also in the
 * KushCraft tab of the advancements screen. Layout matches tools/gui.py awards().
 */
public final class AwardsMenu extends TabMenu {

    static final int FIRST = 9;
    /** Rows 1-4: up to 36 awards. */
    static final int SLOTS = 36;
    static final int COUNT = at(5, 4);

    private int page;

    public AwardsMenu(Player player) {
        super(player, Tab.AWARDS);
    }

    private static int pages() {
        return (Award.values().length + SLOTS - 1) / SLOTS;
    }

    @Override
    protected void page() {
        Awards awards = KushCraft.get().awards();
        Award[] all = Award.values();
        double earned = 0;
        for (Award a : all) {
            if (awards.has(player, a)) {
                earned += a.reward();
            }
        }
        for (int i = 0; i < SLOTS && page * SLOTS + i < all.length; i++) {
            Award a = all[page * SLOTS + i];
            boolean done = awards.has(player, a);
            List<String> lore = new ArrayList<>();
            if (done || !a.secret()) {
                lore.add("<gray>" + a.description());
            }
            String progress = done ? null : awards.progress(player, a);
            if (progress != null) {
                lore.add("<white>" + progress);
            }
            if (a.reward() > 0) {
                lore.add(done ? "<green>✔ <gold>" + money(a.reward()) : "<gold>" + money(a.reward()));
            } else if (done) {
                lore.add("<green>✔ Unlocked");
            }
            String name = done ? "<" + a.color() + ">" + a.title()
                    : "<gray>" + (a.secret() ? "???" : a.title());
            set(FIRST + i, Items.icon(done ? a.icon() : a.icon() + "_locked", name, lore));
        }
        int count = awards.count(player);
        set(COUNT, Items.icon("tab_awards", "<green>Awards <gold>" + count + "/" + all.length,
                earned > 0 ? "<gold>" + money(earned) + " <dark_gray>earned" : "<gray>Achievements for bragging rights.",
                "<dark_gray>Also in the advancements screen (L)."));
        arrows(page, pages());
    }

    @Override
    protected void clickPage(int slot, org.bukkit.event.inventory.ClickType click) {
        int next = turn(slot, page, pages());
        if (next != page) {
            page = next;
            clickSound();
            render();
        }
    }
}
