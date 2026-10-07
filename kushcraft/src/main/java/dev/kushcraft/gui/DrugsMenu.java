package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.catalog.Catalog;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.lab.LabRecipe;
import dev.kushcraft.recipe.RecipeBook;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Drugs: one row per kind (weed, psychedelics, uppers, downers, gear). Each
 * item shows what it sells for and does; click it for the recipe picture.
 * Layout matches tools/gui.py drugs().
 */
public final class DrugsMenu extends TabMenu {

    /** The categories shown, top to bottom (rows 1-5). */
    static final List<Catalog.Category> ROWS = List.of(Catalog.Category.WEED, Catalog.Category.PSYCH,
            Catalog.Category.UPPERS, Catalog.Category.DOWNERS, Catalog.Category.GEAR);

    public DrugsMenu(Player player) {
        super(player, Tab.DRUGS);
    }

    /** Items of a row, at most 9. */
    static List<Catalog.Entry> row(int i) {
        List<Catalog.Entry> e = Catalog.entries(ROWS.get(i));
        return e.size() > 9 ? e.subList(0, 9) : e;
    }

    @Override
    protected void page() {
        KushCraft plugin = KushCraft.get();
        for (int r = 0; r < ROWS.size(); r++) {
            List<Catalog.Entry> entries = row(r);
            for (int c = 0; c < entries.size(); c++) {
                set(at(r + 1, c), entry(entries.get(c), plugin));
            }
        }
    }

    private ItemStack entry(Catalog.Entry e, KushCraft plugin) {
        ItemStack it = CatalogIcons.sample(e.type());
        List<String> lore = new ArrayList<>();
        double base = plugin.shop().basePrice(e.type());
        boolean hot = plugin.market().isHot(e.type());
        if (base > 0) {
            double now = base * plugin.market().multiplier(e.type()) * plugin.ranks().multiplier(player);
            lore.add("<gold>" + money(now) + (hot ? " <red>HOT" : ""));
        }
        if (e.effects() != null) {
            lore.add("<gray>" + e.effects());
        }
        if (!RecipeBook.making(e.type()).isEmpty()) {
            lore.add("<dark_gray>Click: recipe");
        } else {
            lore.add("<dark_gray>" + String.join(" ", e.howTo()));
        }
        it.editMeta(m -> m.lore(Text.lines(lore)));
        return Items.glint(it, hot);
    }

    static LabRecipe cookRecipe(ItemType t) {
        for (LabRecipe r : LabRecipe.values()) {
            if (r.output() == t) {
                return r;
            }
        }
        return null;
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        int r = slot / 9 - 1;
        int c = slot % 9;
        if (r < 0 || r >= ROWS.size()) {
            return;
        }
        List<Catalog.Entry> entries = row(r);
        if (c < entries.size()) {
            List<RecipeBook.Entry> recipes = RecipeBook.making(entries.get(c).type());
            if (!recipes.isEmpty()) {
                openChild(new RecipeViewMenu(player, recipes, 0));
            }
        }
    }
}
