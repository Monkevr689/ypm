package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.award.Starter;
import dev.kushcraft.guide.Guide;
import dev.kushcraft.item.Items;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Getting Started: the seven first steps along a path (2,1..7), the done
 * ones ticked and the next one glowing, a summary (1,4), the handbook
 * (4,3) and menu tips (4,5). Layout: tools/gui.py guide().
 */
public final class StarterMenu extends Menu {

    static final int ROWS = 5;
    static final int SUMMARY = 13;
    static final int[] STEPS = {19, 20, 21, 22, 23, 24, 25};
    static final int BACK = 36;
    static final int BOOK = 39;
    static final int TIPS = 41;

    public StarterMenu(Player player) {
        super(player, ROWS, "guide", "Getting Started", false);
    }

    /** The guide button on every /kush page: your next step. */
    static ItemStack button(Player p) {
        Starter next = Starter.next(p);
        if (next == null) {
            return Items.icon("ui_guide", "<aqua>Guide", "<gray>You know the basics!", "<dark_gray>Click: steps and the handbook");
        }
        List<String> lore = new ArrayList<>();
        for (String l : next.how()) {
            lore.add("<gray>" + l);
        }
        lore.add((next.reward() > 0 ? "<gold>+" + KushCraft.get().economy().format(next.reward()) + " <dark_gray>· " : "<dark_gray>")
                + "step " + (next.ordinal() + 1) + "/" + Starter.values().length);
        lore.add("<dark_gray>Click: all steps");
        return Items.glint(Items.icon("ui_guide", "<aqua><bold>Next: " + next.title(), lore), true);
    }

    @Override
    public void render() {
        inv.clear();
        backButton(BACK);
        Starter next = Starter.next(player);
        int done = Starter.doneCount(player);
        set(SUMMARY, Items.icon("ui_guide", "<aqua><bold>Getting started <white>" + done + "/" + Starter.values().length,
                next == null ? List.of("<green>All done! <gray>Now breed strains,", "<gray>start a cartel and get rich.")
                        : List.of("<gray>Do these in order to learn", "<gray>the basics. <white>Next: " + next.title())));
        for (Starter s : Starter.values()) {
            boolean ok = s.done(player);
            List<String> lore = new ArrayList<>();
            for (String l : s.how()) {
                lore.add("<gray>" + l);
            }
            if (ok) {
                lore.add("<green>✔ Done");
            } else if (s.reward() > 0) {
                lore.add("<gold>+" + KushCraft.get().economy().format(s.reward()));
            }
            String name = (ok ? "<green>✔ " : s == next ? "<aqua><bold>" : "<white>") + (s.ordinal() + 1) + ". " + s.title();
            ItemStack icon = Items.icon(ok || s == next ? s.icon() : "ui_lock", name, lore);
            set(STEPS[s.ordinal()], Items.glint(icon, s == next));
        }
        set(BOOK, Items.icon("grower_guide", "<green>Handbook", "<gray>Everything explained, with pictures."));
        set(TIPS, Items.icon("ui_info", "<white>Tips",
                "<gray>Open this menu: <white>/kush</white>, <white>Shift+F</white>,",
                "<gray>or the KushCraft Menu book.",
                "<gray>Right-click a plant to see how it grows;",
                "<gray>sneak + right-click for the details."));
    }

    @Override
    public void click(int slot, ClickType click) {
        if (slot == BOOK) {
            player.closeInventory();
            player.openBook(Guide.book(player));
        }
    }
}
