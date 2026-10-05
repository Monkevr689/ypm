package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.catalog.Catalog;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.lab.LabRecipe;
import dev.kushcraft.recipe.RecipeBook;
import dev.kushcraft.shop.Ranks;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Drugs & recipes: every product by category - what it does, what it's
 * worth, which rank cooks it - and a click shows its recipe.
 * Layout matches tools/gui.py drugs().
 */
public final class DrugsMenu extends TabMenu {

    private static final int FIRST = 9;
    private static final int PER_PAGE = 36;
    private static final int FIRST_FILTER = at(5, 0);
    private static final int PREV = at(5, 7);
    private static final int NEXT = at(5, 8);

    private Catalog.Category filter;
    private int page;
    private List<Catalog.Entry> current = List.of();

    public DrugsMenu(Player player) {
        super(player, Tab.DRUGS);
    }

    @Override
    protected void page() {
        KushCraft plugin = KushCraft.get();
        current = Catalog.entries(filter);
        int pages = Math.max(1, (current.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= current.size()) {
                break;
            }
            set(FIRST + i, entry(current.get(idx), plugin));
        }
        // filters: everything + each category
        set(FIRST_FILTER, filterIcon(null));
        Catalog.Category[] cats = Catalog.Category.values();
        for (int i = 0; i < cats.length && i < 6; i++) {
            set(FIRST_FILTER + 1 + i, filterIcon(cats[i]));
        }
        if (pages > 1) {
            set(PREV, Items.icon("ui_back", "<gray>Previous page", "<dark_gray>Page " + (page + 1) + "/" + pages));
            set(NEXT, Items.icon("ui_arrow", "<gray>Next page", "<dark_gray>Page " + (page + 1) + "/" + pages));
        }
    }

    private ItemStack filterIcon(Catalog.Category c) {
        boolean on = c == filter;
        int count = Catalog.entries(c).size();
        return Items.glint(Items.icon(c == null ? "ui_catalog" : c.icon(),
                (on ? "<green>▶ " : "<yellow>") + (c == null ? "Everything" : c.display()),
                "<gray>" + count + " items", on ? "<green>Showing" : "<gray>Click to show"), on);
    }

    private ItemStack entry(Catalog.Entry e, KushCraft plugin) {
        ItemStack it = CatalogIcons.sample(e.type());
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>" + e.category().display());
        if (e.effects() != null) {
            lore.add("<yellow>Effects: <white>" + e.effects());
        } else if (e.type().strainBound() && e.type() != ItemType.SEED_PACK && e.type() != ItemType.BUD_FRESH) {
            lore.add("<yellow>Effects: <white>from the strain");
        }
        double base = plugin.shop().basePrice(e.type());
        if (base > 0) {
            lore.add("<yellow>Sells for: <gold>" + money(base * plugin.market().multiplier(e.type())
                    * plugin.ranks().multiplier(player)) + " <dark_gray>each (" + plugin.market().trend(e.type()) + "<dark_gray>)");
        }
        lore.add("<yellow>How: <gray>" + String.join(" ", e.howTo()));
        LabRecipe cook = cookRecipe(e.type());
        if (cook != null) {
            Ranks.Rank need = plugin.ranks().level(cook.rank());
            boolean ok = plugin.ranks().canCook(player, cook);
            lore.add(ok ? "<green>✔ You can cook this" : "<red>✘ Needs rank " + plain(need.colored())
                    + " <dark_gray>(" + money(need.sales()) + " sold)");
        }
        if (!RecipeBook.making(e.type()).isEmpty()) {
            lore.add("");
            lore.add("<green>Click to see the recipe");
        }
        it.editMeta(m -> m.lore(Text.lines(lore)));
        return it;
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
        if (slot >= FIRST && slot < FIRST + PER_PAGE) {
            int idx = page * PER_PAGE + (slot - FIRST);
            if (idx < current.size()) {
                List<RecipeBook.Entry> recipes = RecipeBook.making(current.get(idx).type());
                if (!recipes.isEmpty()) {
                    openChild(new RecipeViewMenu(player, recipes, 0));
                }
            }
            return;
        }
        if (slot == PREV || slot == NEXT) {
            page += slot == NEXT ? 1 : -1;
            clickSound();
            render();
            return;
        }
        int f = slot - FIRST_FILTER;
        if (f >= 0 && f <= 6) {
            Catalog.Category[] cats = Catalog.Category.values();
            filter = f == 0 ? null : f - 1 < cats.length ? cats[f - 1] : filter;
            page = 0;
            clickSound();
            render();
        }
    }
}
