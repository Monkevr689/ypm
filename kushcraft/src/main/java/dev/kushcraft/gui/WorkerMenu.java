package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.lab.LabRecipe;
import dev.kushcraft.plant.PlantManager;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import dev.kushcraft.worker.Worker;
import dev.kushcraft.worker.WorkerType;
import dev.kushcraft.worker.Workers;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A worker's menu (right-click them): who they are and what they're doing,
 * their job (a Cook's drug), their satchel (click an item to take it, click
 * your own seeds / buds / ingredients to give them), and buttons to rename,
 * pause, train and dismiss them. Layout: tools/gui.py worker().
 */
public final class WorkerMenu extends Menu {

    static final int ROWS = 5;
    static final int INFO = 0;
    static final int JOB = 4;
    static final int RENAME = 5;
    static final int PAUSE = 6;
    static final int UPGRADE = 7;
    static final int DISMISS = 8;
    static final int FIRST = 9;
    static final int TAKE_ALL = 40;

    private final Worker worker;
    private boolean confirmDismiss;

    public WorkerMenu(Player player, Worker worker) {
        super(player, ROWS, "worker", worker.type().display() + " " + worker.name(), false);
        this.worker = worker;
    }

    private static Workers workers() {
        return KushCraft.get().workers();
    }

    private static String money(double v) {
        return KushCraft.get().economy().format(v);
    }

    @Override
    public void render() {
        inv.clear();
        Workers ws = workers();
        WorkerType t = worker.type();
        List<String> info = new ArrayList<>();
        info.add("<white>" + (worker.paused() ? "<red>Paused" : worker.status()));
        info.add("<gold>Level " + worker.level() + " <dark_gray>· <gray>works " + ws.radius(worker) + " blocks around");
        info.add("<gray>" + money(ws.wage(t)) + " per job <dark_gray>· " + worker.jobs() + " jobs, "
                + money(worker.wages()) + " paid");
        set(INFO, Items.icon(t.item().model(), t.color() + Text.escape(worker.name()) + " <gray>the " + t.display(), info));
        set(JOB, jobIcon());
        set(RENAME, Items.icon("ui_rename", "<white>Rename", "<dark_gray>Type a new name in chat."));
        set(PAUSE, worker.paused() ? Items.icon("ui_play", "<green>Back to work")
                : Items.icon("ui_pause", "<yellow>Take a break", "<dark_gray>They stop working until you click again."));
        List<Double> costs = ws.upgradeCosts();
        if (worker.level() - 1 < costs.size()) {
            int next = worker.level() + 1;
            set(UPGRADE, Items.icon("ui_upgrade", "<gold>Train: " + money(costs.get(worker.level() - 1)),
                    "<gray>Level " + next + ": works " + radiusAt(next) + " blocks around, rests less,",
                    "<gray>walks faster."));
        } else {
            set(UPGRADE, Items.icon("ui_upgrade", "<gold>Fully trained", "<gray>Level " + worker.level() + " (max)"));
        }
        set(DISMISS, Items.icon("ui_dismiss", confirmDismiss ? "<red><bold>Click again to dismiss" : "<red>Dismiss",
                "<gray>You get their contract and satchel back."));
        ItemStack[] items = worker.satchel().getStorageContents();
        for (int i = 0; i < Worker.SATCHEL; i++) {
            if (items[i] != null && !items[i].getType().isAir()) {
                set(FIRST + i, items[i].clone());
            }
        }
        int carried = worker.carried();
        set(TAKE_ALL, Items.glint(Items.icon("ui_take", carried > 0 ? "<green><bold>Take all</bold> <gray>(" + carried + ")"
                : "<gray>Satchel is empty", "<dark_gray>Click an item to take just that."), carried > 0));
    }

    private org.bukkit.inventory.ItemStack jobIcon() {
        WorkerType t = worker.type();
        if (t == WorkerType.COOK) {
            LabRecipe r = worker.recipe();
            List<String> lore = new ArrayList<>();
            if (r == null) {
                lore.add("<yellow>Click to pick the drug they cook.");
            } else {
                lore.add("<gray>Put in their satchel, per batch:");
                for (LabRecipe.Ingredient ing : r.ingredients()) {
                    lore.add("<white>" + ing.amount() + " " + ing.name());
                }
                lore.add("<dark_gray>Click: cook something else");
            }
            ItemStack icon = r == null ? Items.icon("tab_cook", "<aqua>Cooking: <white>nothing yet", lore)
                    : CatalogIcons.sample(r.output());
            if (r != null) {
                icon.editMeta(m -> {
                    m.itemName(Text.mm("<aqua>Cooking: <white>" + r.output().display()));
                    m.lore(Text.lines(lore));
                });
            }
            return Items.glint(icon, r == null);
        }
        return Items.icon("ui_guide", "<aqua>How they work", t == WorkerType.FARMHAND ? List.of(
                "<gray>Picks your ripe plants near them and",
                "<gray>plants a seed from the harvest again.",
                "<gray>Click seeds or fertilizer below to give",
                "<gray>them some: they plant empty farmland.")
                : List.of("<gray>Put them near your Drug Lab.",
                "<gray>They hang fresh buds on its racks and",
                "<gray>take them off dry. They fetch fresh",
                "<gray>buds from your Farmhands, or click yours."));
    }

    private int radiusAt(int level) {
        List<Double> r = KushCraft.get().getConfig().getDoubleList("workers.radius");
        return r.isEmpty() ? 6 : (int) (double) r.get(Math.min(r.size() - 1, level - 1));
    }

    @Override
    public void click(int slot, ClickType click) {
        Workers ws = workers();
        if (ws.get(worker.id()) == null) {
            player.closeInventory();
            return;
        }
        if (slot != DISMISS) {
            confirmDismiss = false;
        }
        switch (slot) {
            case JOB -> {
                if (worker.type() == WorkerType.COOK) {
                    openChild(new CookRecipeMenu(player, worker));
                    return;
                }
            }
            case RENAME -> {
                player.closeInventory();
                ChatInput.ask(player, "<green>New name for " + Text.escape(worker.name()) + "?</green> <gray>(up to 16 letters)",
                        name -> {
                            ws.rename(worker, name);
                            new WorkerMenu(player, worker).open();
                        }, () -> new WorkerMenu(player, worker).open());
                return;
            }
            case PAUSE -> {
                ws.pause(worker, !worker.paused());
                clickSound();
            }
            case UPGRADE -> {
                String error = ws.upgrade(worker, player);
                if (error != null) {
                    player.sendActionBar(Text.mm("<red>" + error));
                    failSound();
                } else {
                    player.playSound(player.getLocation(), "minecraft:entity.villager.celebrate", SoundCategory.NEUTRAL, 0.8f, 1.2f);
                    player.sendActionBar(Text.mm("<gold>" + Text.escape(worker.name()) + " is now level " + worker.level()));
                }
            }
            case DISMISS -> {
                if (!confirmDismiss) {
                    confirmDismiss = true;
                    failSound();
                    break;
                }
                ws.dismiss(worker, player);
                player.closeInventory();
                player.sendActionBar(Text.mm("<gray>" + Text.escape(worker.name()) + " packed up and left."));
                return;
            }
            case TAKE_ALL -> takeAll();
            default -> {
                int i = slot - FIRST;
                if (i >= 0 && i < Worker.SATCHEL) {
                    take(i);
                }
            }
        }
        render();
    }

    private void takeAll() {
        int n = 0;
        ItemStack[] items = worker.satchel().getStorageContents();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && !items[i].getType().isAir()) {
                n += items[i].getAmount();
                InventoryUtil.give(player, items[i]);
                worker.satchel().setItem(i, null);
            }
        }
        if (n == 0) {
            failSound();
            return;
        }
        workers().markDirty();
        player.playSound(player.getLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 0.8f, 1f);
    }

    private void take(int i) {
        ItemStack it = worker.satchel().getItem(i);
        if (it == null || it.getType().isAir()) {
            return;
        }
        InventoryUtil.give(player, it);
        worker.satchel().setItem(i, null);
        workers().markDirty();
        player.playSound(player.getLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    /** Clicking your own seeds / fertilizer / fresh buds hands them over. */
    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        ItemType t = Items.type(item);
        boolean wanted = switch (worker.type()) {
            case FARMHAND -> t == ItemType.FERTILIZER || PlantManager.kindOf(t) != null;
            case DRYER -> t == ItemType.BUD_FRESH;
            case COOK -> usedByCook(item);
        };
        if (!wanted) {
            player.sendActionBar(Text.mm("<gray>" + Text.escape(worker.name()) + " only takes " + switch (worker.type()) {
                case FARMHAND -> "seeds and fertilizer.";
                case DRYER -> "fresh buds.";
                case COOK -> worker.recipe() == null ? "ingredients - pick a drug first." : "what their recipe needs.";
            }));
            return;
        }
        ItemStack inSlot = player.getInventory().getItem(slot);
        if (inSlot == null || !inSlot.isSimilar(item)) {
            return;
        }
        List<ItemStack> left = workers().stash(worker, List.of(inSlot.clone()));
        player.getInventory().setItem(slot, left.isEmpty() ? null : left.get(0));
        if (!left.isEmpty() && left.get(0).getAmount() == inSlot.getAmount()) {
            player.sendActionBar(Text.mm("<red>The satchel is full."));
            failSound();
            return;
        }
        player.playSound(player.getLocation(), "minecraft:item.bundle.insert", SoundCategory.PLAYERS, 0.8f, 1f);
        render();
    }

    /** A Cook takes the ingredients of their recipe. */
    private boolean usedByCook(ItemStack item) {
        LabRecipe r = worker.recipe();
        if (r == null) {
            return false;
        }
        for (LabRecipe.Ingredient ing : r.ingredients()) {
            if (ing.matches(item)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        render();
    }
}
