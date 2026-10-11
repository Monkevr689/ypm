package dev.kushcraft.menus;

import dev.kushcraft.items.Items;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * A paged list with the generic "list" background: header (0,4), 36 entries
 * (rows 1-4), back (5,0), prev/page/next (5,3-5) and one action button (5,8).
 */
public abstract class ListMenu extends Menu {

    protected static final int HEADER = 4;
    protected static final int FIRST = 9;
    protected static final int PER_PAGE = 36;
    protected static final int BACK = 45;
    protected static final int PREV = 48;
    protected static final int PAGE = 49;
    protected static final int NEXT = 50;
    protected static final int ACTION = 53;

    protected int page;
    private List<ItemStack> shown = List.of();

    protected ListMenu(Player player, String title) {
        super(player, 6, "list", title);
    }

    /** All entries (already filtered). */
    protected abstract List<ItemStack> entries();

    protected abstract ItemStack header();

    /** Optional button at (5,8). */
    protected ItemStack action() {
        return null;
    }

    protected void clickEntry(int index, ClickType click) {
    }

    protected void clickAction(ClickType click) {
    }

    @Override
    public void render() {
        inv.clear();
        backButton(BACK);
        shown = entries();
        int pages = Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        set(HEADER, header());
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= shown.size()) {
                break;
            }
            set(FIRST + i, shown.get(idx));
        }
        if (pages > 1) {
            set(PREV, Items.icon("ui_back", "<gray>Previous page"));
            set(NEXT, Items.icon("ui_arrow", "<gray>Next page"));
        }
        set(PAGE, Items.amount(Items.icon("ui_info", "<gray>Page " + (page + 1) + "/" + pages), page + 1));
        ItemStack a = action();
        if (a != null) {
            set(ACTION, a);
        }
    }

    @Override
    public void click(int slot, ClickType click) {
        if (slot == PREV) {
            page--;
            clickSound();
            render();
        } else if (slot == NEXT) {
            page++;
            clickSound();
            render();
        } else if (slot == ACTION) {
            clickAction(click);
        } else if (slot >= FIRST && slot < FIRST + PER_PAGE) {
            int idx = page * PER_PAGE + (slot - FIRST);
            if (idx < shown.size()) {
                clickEntry(idx, click);
            }
        }
    }
}
