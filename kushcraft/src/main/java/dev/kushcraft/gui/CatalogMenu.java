package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.catalog.Catalog;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.recipe.RecipeBook;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The product & drug catalog: every item, how to get it, what it does and
 * what it sells for. In admin "give" mode clicking an item gives it to you.
 */
public final class CatalogMenu extends ListMenu {

    private final boolean give;
    private Catalog.Category filter;
    private List<Catalog.Entry> current = List.of();

    public CatalogMenu(Player player, boolean give) {
        super(player, give ? "Give Items (admin)" : "Drug Catalog");
        this.give = give;
    }

    static ItemStack sample(ItemType t) {
        return t.strainBound() ? Items.strainItem(t, KushCraft.get().strains().getOrDefault(null), 3, 1) : Items.create(t);
    }

    @Override
    protected List<ItemStack> entries() {
        current = Catalog.entries(filter);
        List<ItemStack> out = new ArrayList<>();
        KushCraft plugin = KushCraft.get();
        for (Catalog.Entry e : current) {
            ItemStack it = sample(e.type());
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>" + e.category().display());
            lore.add("");
            lore.add("<yellow>How to get:");
            for (String h : e.howTo()) {
                lore.add(" <gray>" + h);
            }
            if (e.effects() != null) {
                lore.add("<yellow>Effects: <white>" + e.effects());
            } else if (e.type().strainBound() && e.type() != ItemType.SEED_PACK && e.type() != ItemType.BUD_FRESH) {
                lore.add("<yellow>Effects: <white>depends on the strain");
            }
            double base = plugin.shop().basePrice(e.type());
            if (base > 0) {
                lore.add("<yellow>Sells for: <gold>" + plugin.economy().format(base * plugin.market().multiplier(e.type()))
                        + " <dark_gray>(market " + plugin.market().trend(e.type()) + "<dark_gray>)");
            }
            if (give) {
                lore.add("");
                lore.add("<green>Click: get 1  <yellow>Shift-click: get a stack");
            } else if (!RecipeBook.making(e.type()).isEmpty()) {
                lore.add("");
                lore.add("<green>Click to see the recipe");
            }
            it.editMeta(m -> m.lore(Text.lines(lore)));
            out.add(it);
        }
        return out;
    }

    @Override
    protected ItemStack header() {
        return Items.icon("ui_catalog", "<green>" + (give ? "Give Items" : "Drug Catalog"),
                "<gray>Showing: <white>" + (filter == null ? "Everything" : filter.display()),
                "<gray>Use the button bottom-right to filter.");
    }

    @Override
    protected ItemStack action() {
        Catalog.Category next = nextFilter();
        String icon = next == null ? "ui_catalog" : next.icon();
        return Items.icon(icon, "<yellow>Show: <white>" + (next == null ? "Everything" : next.display()),
                "<gray>Click to change the category.");
    }

    private Catalog.Category nextFilter() {
        Catalog.Category[] all = Catalog.Category.values();
        if (filter == null) {
            return all[0];
        }
        return filter.ordinal() + 1 < all.length ? all[filter.ordinal() + 1] : null;
    }

    @Override
    protected void clickAction(ClickType click) {
        filter = nextFilter();
        page = 0;
        clickSound();
        render();
    }

    @Override
    protected void clickEntry(int index, ClickType click) {
        if (index >= current.size()) {
            return;
        }
        if (!give) {
            List<RecipeBook.Entry> recipes = RecipeBook.making(current.get(index).type());
            if (!recipes.isEmpty()) {
                openChild(new RecipeViewMenu(player, recipes, 0));
            }
            return;
        }
        if (!player.hasPermission("kushcraft.admin")) {
            return;
        }
        ItemStack it = sample(current.get(index).type());
        it.setAmount(click.isShiftClick() ? it.getMaxStackSize() : 1);
        InventoryUtil.give(player, it);
        successSound();
    }
}
