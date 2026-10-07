package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.Items;
import dev.kushcraft.shop.Shop;
import dev.kushcraft.util.Text;
import dev.kushcraft.worker.Worker;
import dev.kushcraft.worker.WorkerType;
import dev.kushcraft.worker.Workers;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Workers tab: five contracts to hire on row 1, your workers on rows
 * 2-4 (27 a page, click one for their menu - from anywhere), and row 5:
 * pages, collect everything, pause / restart everyone and how the work
 * chain works. Layout: tools/gui.py workers_page().
 */
public final class WorkersMenu extends TabMenu {

    static final int[] HIRE = {at(1, 0), at(1, 2), at(1, 4), at(1, 6), at(1, 8)};
    static final int FIRST = at(2, 0);
    static final int SLOTS = 27;
    static final int COLLECT = at(5, 2);
    static final int PAUSE = at(5, 4);
    static final int HELP = at(5, 6);

    private int page;
    private List<Worker> shown = List.of();
    private int ticks;

    public WorkersMenu(Player player) {
        super(player, Tab.WORKERS);
    }

    private static Workers ws() {
        return KushCraft.get().workers();
    }

    private List<Worker> mine() {
        return ws().of(player.getUniqueId());
    }

    private int pages() {
        return Math.max(1, (mine().size() + SLOTS - 1) / SLOTS);
    }

    @Override
    protected void page() {
        KushCraft plugin = KushCraft.get();
        Workers ws = ws();
        double bal = plugin.economy().balance(player);
        List<Shop.BuyEntry> hires = plugin.shop().hires();
        for (WorkerType t : WorkerType.values()) {
            Shop.BuyEntry e = null;
            for (Shop.BuyEntry h : hires) {
                if (h.type() == t.item()) {
                    e = h;
                }
            }
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + t.job());
            lore.add("<gray>" + t.tip());
            lore.add(switch (t) {
                case RUNNER -> "<gray>Pay: <gold>" + Math.round(ws.runnerCut() * 100) + "% <gray>of what they sell.";
                case SUPPLIER -> "<gray>Wage: <gold>" + money(ws.wage(t)) + " <gray>a delivery + what they buy.";
                default -> "<gray>Wage: <gold>" + money(ws.wage(t)) + " <gray>a job, from your wallet.";
            });
            if (e == null) {
                lore.add("<red>Not for hire on this server.");
            } else {
                lore.add((bal >= e.price() ? "<gold>" : "<red>") + money(e.price())
                        + " <dark_gray>· then right-click the ground");
            }
            set(HIRE[t.ordinal()], Items.icon(t.item().model(), t.color() + "<bold>Hire a " + t.display(), lore));
        }
        List<Worker> list = mine();
        page = Math.min(page, pages() - 1);
        shown = list.subList(Math.min(list.size(), page * SLOTS), Math.min(list.size(), (page + 1) * SLOTS));
        for (int i = 0; i < shown.size(); i++) {
            set(FIRST + i, icon(shown.get(i)));
        }
        if (list.isEmpty()) {
            set(at(3, 4), Items.icon("ui_workers", "<gray>No workers yet",
                    "<gray>Hire one above, then right-click the", "<gray>ground where they should work."));
        }
        arrows(page, pages());
        set(COLLECT, Items.glint(Items.icon("ui_take", "<green><bold>Collect everything",
                "<gray>Takes what all your workers made", "<gray>(not what they need) into your bag."), !list.isEmpty()));
        boolean anyWorking = list.stream().anyMatch(w -> !w.paused());
        set(PAUSE, anyWorking ? Items.icon("ui_pause", "<yellow>Everyone: take a break",
                "<gray>Pauses all " + list.size() + " of your workers.")
                : Items.icon("ui_play", "<green>Everyone: back to work", "<gray>Starts all your workers again."));
        set(HELP, Items.icon("ui_guide", "<aqua>How the work chain works",
                "<gray>No chests: work goes hand to hand.",
                "<green>Farmhand<gray> > <light_purple>Runner<gray> > <gold>Dryer<gray> > <aqua>Cook",
                "<gray>or the <light_purple>Runner<gray>, who sells it on the spot.",
                "<yellow>Suppliers<gray> buy whatever anyone is missing.",
                "<gold>⚠<gray> = stuck: they tell you what they need.",
                "<dark_gray>Click: open the handbook"));
    }

    private ItemStack icon(Worker w) {
        Location h = w.home();
        List<String> lore = new ArrayList<>();
        lore.add("<gold>Level " + w.level() + " <dark_gray>· <gray>" + (Worker.SATCHEL - w.freeSlots()) + "/" + Worker.SATCHEL
                + " satchel slots");
        lore.add("<white>" + (w.paused() ? "<red>Paused" : w.isLoaded() ? w.status() : "<dark_gray>Asleep (nobody nearby)"));
        if (w.problem() != null && !w.paused()) {
            lore.add("<gold>⚠ Stuck - they need you");
        }
        if (w.wants() != null) {
            lore.add("<yellow>Missing: " + w.wants());
        }
        if (h != null) {
            lore.add("<dark_gray>" + w.worldName() + " " + h.getBlockX() + ", " + h.getBlockY() + ", " + h.getBlockZ());
        }
        lore.add("<dark_gray>Click: their menu · Shift-click: pause / go");
        boolean stuck = w.problem() != null && !w.paused();
        ItemStack it = Items.icon(w.type().item().model(), (stuck ? "<gold>⚠ " : "") + w.type().color()
                + Text.escape(w.name()) + " <gray>the " + w.type().display(), lore);
        return Items.glint(it, stuck || (w.wants() != null && !w.paused()));
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        int next = turn(slot, page, pages());
        if (next != page) {
            page = next;
            clickSound();
            render();
            return;
        }
        Workers ws = ws();
        for (WorkerType t : WorkerType.values()) {
            if (slot == HIRE[t.ordinal()]) {
                hire(t);
                return;
            }
        }
        if (slot == COLLECT) {
            collect();
            return;
        }
        if (slot == PAUSE) {
            boolean anyWorking = mine().stream().anyMatch(w -> !w.paused());
            int n = ws.pauseAll(player.getUniqueId(), anyWorking);
            player.sendActionBar(Text.mm(n == 0 ? "<gray>No workers to " + (anyWorking ? "pause." : "start.")
                    : anyWorking ? "<yellow>" + n + " workers are taking a break." : "<green>" + n + " workers are back at work."));
            clickSound();
            render();
            return;
        }
        if (slot == HELP) {
            clickSound();
            player.openBook(dev.kushcraft.guide.Guide.book(player));
            player.sendActionBar(Text.mm("<gray>Workers are in the contents of the handbook."));
            return;
        }
        int i = slot - FIRST;
        if (i >= 0 && i < shown.size() && ws.get(shown.get(i).id()) != null) {
            Worker w = shown.get(i);
            if (click.isShiftClick()) {
                ws.pause(w, !w.paused());
                clickSound();
                render();
                return;
            }
            openChild(new WorkerMenu(player, w));
        }
    }

    private void hire(WorkerType t) {
        KushCraft plugin = KushCraft.get();
        if (!ws().enabled()) {
            player.sendActionBar(Text.mm("<red>Workers are turned off on this server."));
            failSound();
            return;
        }
        for (Shop.BuyEntry e : plugin.shop().hires()) {
            if (e.type() == t.item()) {
                ShopMenu.buy(player, e, 1);
                render();
                return;
            }
        }
        failSound();
    }

    private void collect() {
        int n = ws().collectAll(player);
        if (n <= 0) {
            player.sendActionBar(Text.mm("<gray>Nothing to collect" + (player.getInventory().firstEmpty() < 0
                    ? " - your inventory is full." : " yet.")));
            failSound();
            return;
        }
        player.playSound(player.getLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 0.8f, 1f);
        player.sendActionBar(Text.mm("<green>Collected " + n + " items from your workers."));
        render();
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (Selling.clicked(player, slot, item, click)) {
            render();
        }
    }

    @Override
    public void tick() {
        if (++ticks % 2 == 0) {
            render();
        }
    }
}
