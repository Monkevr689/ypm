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
    private static final int PAGE = 40;
    private static final int NEXT = 41;
    private static final int INFO = 44;

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
        result.editMeta(m -> m.itemName(Text.mm("<green>" + e.title() + (e.amount() > 1 ? " <gray>x" + e.amount() : ""))));
        set(RESULT, result);
        ItemStack station = e.kind() == RecipeBook.Kind.CRAFTING ? new ItemStack(Material.CRAFTING_TABLE)
                : Items.create(ItemType.LAB_STATION);
        station.editMeta(m -> {
            m.itemName(Text.mm("<yellow>" + e.kind().station()));
            m.lore(Text.lines(List.of(e.kind() == RecipeBook.Kind.CRAFTING
                    ? "<gray>Use any crafting table." : "<gray>Place a <green>Drug Lab</green> and right-click it.")));
        });
        set(STATION, station);
        List<String> info = new ArrayList<>();
        info.add("<gray>Made at: <white>" + e.kind().station());
        for (String n : e.notes()) {
            info.add("<gray>" + n);
        }
        set(INFO, Items.icon("ui_info", "<aqua>" + e.title(), info));
        if (list.size() > 1) {
            set(PREV, Items.icon("ui_back", "<gray>Previous recipe"));
            set(NEXT, Items.icon("ui_arrow", "<gray>Next recipe"));
        }
        set(PAGE, Items.amount(Items.icon("ui_recipes", "<gray>Recipe " + (index + 1) + "/" + list.size()), index + 1));
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
