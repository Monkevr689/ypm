package dev.kushcraft.gui;

import dev.kushcraft.item.Items;
import dev.kushcraft.recipe.RecipeBook;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every recipe: click one to see it in a crafting grid. */
public final class RecipesMenu extends ListMenu {

    private static final String[] FILTERS = {null, "Crafting", "Drug Lab"};

    private int filter;
    private List<RecipeBook.Entry> current = List.of();

    public RecipesMenu(Player player) {
        super(player, "Recipes");
    }

    private List<RecipeBook.Entry> filtered() {
        List<RecipeBook.Entry> out = new ArrayList<>();
        for (RecipeBook.Entry e : RecipeBook.all()) {
            if (FILTERS[filter] == null || e.kind().group().equals(FILTERS[filter])) {
                out.add(e);
            }
        }
        return out;
    }

    @Override
    protected List<ItemStack> entries() {
        current = filtered();
        List<ItemStack> out = new ArrayList<>();
        for (RecipeBook.Entry e : current) {
            ItemStack it = RecipeBook.sample(e.result(), e.amount());
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>" + e.kind().station());
            lore.add("");
            // shapeless crafting lists the same ingredient several times: count it once
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (ItemStack in : e.grid()) {
                if (in != null) {
                    counts.merge(RecipeBook.name(in), in.getAmount(), Integer::sum);
                }
            }
            counts.forEach((name, n) -> lore.add("<gray>• <white>" + n + "x</white> " + name));
            lore.add("");
            lore.add("<yellow>Click to see the recipe");
            it.editMeta(m -> {
                m.itemName(Text.mm("<green>" + e.title() + (e.amount() > 1 ? " <gray>x" + e.amount() : "")));
                m.lore(Text.lines(lore));
            });
            out.add(it);
        }
        return out;
    }

    @Override
    protected ItemStack header() {
        return Items.icon("ui_recipes", "<green>Recipes",
                "<gray>Showing: <white>" + (FILTERS[filter] == null ? "Everything" : FILTERS[filter]),
                "<gray>Crafting table recipes and",
                "<gray>everything the <green>Drug Lab</green> makes.",
                "",
                "<gray>Click a recipe to see it in a grid.");
    }

    @Override
    protected ItemStack action() {
        int next = (filter + 1) % FILTERS.length;
        return Items.icon(next == 2 ? "machine_lab_station" : next == 1 ? "ui_recipes" : "ui_catalog",
                "<yellow>Show: <white>" + (FILTERS[next] == null ? "Everything" : FILTERS[next]),
                "<gray>Click to change.");
    }

    @Override
    protected void clickAction(ClickType click) {
        filter = (filter + 1) % FILTERS.length;
        page = 0;
        clickSound();
        render();
    }

    @Override
    protected void clickEntry(int index, ClickType click) {
        if (index < current.size()) {
            openChild(new RecipeViewMenu(player, current, index));
        }
    }
}
