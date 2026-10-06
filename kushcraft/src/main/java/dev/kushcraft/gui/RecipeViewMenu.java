package dev.kushcraft.gui;

import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.recipe.RecipeBook;
import dev.kushcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One recipe shown like a crafting table: ingredients in a 3x3 grid, the
 * result on the right and where it's made. Arrows flip through the list.
 * Layout matches tools/gui.py recipe().
 */
public final class RecipeViewMenu extends Menu {

    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int RESULT = 24;
    private static final int STATION = 8;
    private static final int BACK = 36;
    private static final int PREV = 39;
    private static final int NEXT = 41;

    private final List<RecipeBook.Entry> list;
    private int index;

    public RecipeViewMenu(Player player, List<RecipeBook.Entry> list, int index) {
        super(player, 5, "recipe", "Recipe");
        this.list = list;
        this.index = index;
    }

    @Override
    public void render() {
        inv.clear();
        backButton(BACK);
        RecipeBook.Entry e = list.get(index);
        for (int i = 0; i < 9; i++) {
            if (e.grid()[i] != null) {
                set(GRID[i], e.grid()[i].clone());
            }
        }
        ItemStack result = RecipeBook.sample(e.result(), e.amount());
        result.editMeta(m -> {
            m.itemName(Text.mm("<green>" + e.title() + (e.amount() > 1 ? " <gray>x" + e.amount() : "")));
            List<String> notes = new ArrayList<>();
            for (String n : e.notes()) {
                notes.add("<gray>" + n);
            }
            m.lore(Text.lines(notes));
        });
        set(RESULT, result);
        ItemStack station = e.kind() == RecipeBook.Kind.CRAFTING ? new ItemStack(Material.CRAFTING_TABLE)
                : Items.create(ItemType.LAB_STATION);
        station.editMeta(m -> {
            m.itemName(Text.mm("<yellow>" + e.kind().station()));
            m.lore(List.of());
        });
        set(STATION, station);
        if (list.size() > 1) {
            set(PREV, Items.icon("ui_back", "<gray>Previous"));
            set(NEXT, Items.icon("ui_arrow", "<gray>Next " + (index + 1) + "/" + list.size()));
        }
    }

    @Override
    public void click(int slot, ClickType click) {
        if (slot == PREV || slot == NEXT) {
            index = Math.floorMod(index + (slot == NEXT ? 1 : -1), list.size());
            clickSound();
            render();
        }
    }
}
