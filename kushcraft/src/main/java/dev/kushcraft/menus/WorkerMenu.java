package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.lab.LabRecipe;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import dev.kushcraft.workers.Worker;
import dev.kushcraft.workers.WorkerType;
import dev.kushcraft.workers.Workers;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A worker's menu (right-click them): who they are and what they're doing,
 * their job (a Cook's drug), their satchel in two pages (click an item to
 * take it, click your own seeds / buds / ingredients to give them), buttons
 * to rename, pause, train and dismiss them, and the bottom bar: satchel
 * pages, auto-buy (a Runner: sell now), take all and the chests they use.
 * Layout: tools/gui.py worker().
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
    /** Satchel slots on one page (the satchel has two). */
    static final int PAGE = 27;
    static final int PREV = 36;
    static final int OPTION = 38;
    static final int TAKE_ALL = 40;
    static final int CHESTS = 42;
    static final int NEXT = 44;

    private final Worker worker;
    private boolean confirmDismiss;
    private int page;

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
        if (t == WorkerType.RUNNER) {
            info.add("<gray>Keeps " + Math.round(ws.runnerCut() * 100) + "% of sales <dark_gray>· " + worker.jobs()
                    + " trips, " + money(worker.wages()) + " kept");
        } else {
            info.add("<gray>" + money(ws.wage(t)) + " per job <dark_gray>· " + worker.jobs() + " jobs, "
                    + money(worker.wages()) + " paid");
        }
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
        for (int i = 0; i < PAGE && page * PAGE + i < items.length; i++) {
            ItemStack it = items[page * PAGE + i];
            if (it != null && !it.getType().isAir()) {
                set(FIRST + i, it.clone());
            }
        }
        int pages = (Worker.SATCHEL + PAGE - 1) / PAGE;
        int used = Worker.SATCHEL - worker.freeSlots();
        if (page > 0) {
            set(PREV, Items.icon("ui_arrow", "<white>Satchel page " + page + "/" + pages));
        }
        if (page < pages - 1) {
            set(NEXT, Items.icon("ui_arrow", "<white>Satchel page " + (page + 2) + "/" + pages,
                    "<gray>" + used + "/" + Worker.SATCHEL + " slots used"));
        }
        int carried = worker.carried();
        set(TAKE_ALL, Items.glint(Items.icon("ui_take", carried > 0 ? "<green><bold>Take all</bold> <gray>(" + carried + ")"
                : "<gray>Satchel is empty", "<gray>" + used + "/" + Worker.SATCHEL + " slots used",
                "<dark_gray>Click an item to take just that."), carried > 0));
        set(OPTION, optionIcon());
        set(CHESTS, chestsIcon());
    }

    /** A Runner: sell now. Everyone else: auto-buy (your setting for all your workers). */
    private ItemStack optionIcon() {
        Workers ws = workers();
        if (worker.type() == WorkerType.RUNNER) {
            double value = ws.carriedValue(worker);
            return Items.glint(Items.icon("ui_sell", value > 0 ? "<gold><bold>Sell now</bold> <gray>(" + money(value) + ")"
                            : "<gray>Nothing to sell",
                    "<gray>They sell whatever they get right away;",
                    "<gray>this sells what they carry this second.",
                    "<dark_gray>Keeps " + Math.round(ws.runnerCut() * 100) + "% of every sale."), value > 0);
        }
        boolean on = ws.autoBuy(worker.owner());
        List<String> lore = new ArrayList<>();
        lore.add(on ? "<gray>When they can't find seeds, fertilizer or" : "<gray>Switched off: they only use what's in");
        lore.add(on ? "<gray>an ingredient, they buy it with your money." : "<gray>your chests and satchels.");
        var next = on ? ws.nextBuy(worker) : null;
        if (next != null) {
            lore.add("<yellow>Next: <white>" + next.amount() + " " + next.name() + " <gold>" + money(next.cost()));
        }
        if (!ws.autoBuyAllowed()) {
            lore.add("<red>Turned off on this server.");
        }
        lore.add("<dark_gray>Click to switch (all your workers)");
        return Items.glint(Items.icon("ui_wallet", on ? "<green>Auto-buy: ON" : "<yellow>Auto-buy: OFF", lore), on);
    }

    private ItemStack chestsIcon() {
        Workers ws = workers();
        int n = ws.chests(worker).size();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + n + " of your chests within " + ws.chestRadius(worker) + " blocks of them.");
        lore.add(switch (worker.type()) {
            case FARMHAND -> "<gray>They put the harvest in them and take seeds";
            case DRYER -> "<gray>They take fresh buds from them and put";
            case COOK -> "<gray>They take ingredients from any of them";
            case RUNNER -> "<gray>They sell the product in them, bring";
        });
        lore.add(switch (worker.type()) {
            case FARMHAND -> "<gray>and fertilizer from them.";
            case DRYER -> "<gray>the dried ones in them.";
            case COOK -> "<gray>(several in one trip) and put drugs in them.";
            case RUNNER -> "<gray>workers what they're missing and store the rest.";
        });
        lore.add("<dark_gray>Chests other players placed are left alone.");
        return Items.icon("ui_nearby", n > 0 ? "<aqua>Chests: " + n : "<yellow>No chests near them", lore);
    }

    private org.bukkit.inventory.ItemStack jobIcon() {
        WorkerType t = worker.type();
        List<String> lore = new ArrayList<>();
        if (t == WorkerType.COOK) {
            ItemType made = worker.product();
            LabRecipe r = worker.recipe();
            if (made == null) {
                lore.add("<yellow>Click to pick the drug they make.");
            } else {
                lore.add("<gray>Needs, per " + (r != null ? "batch:" : "roll:"));
                if (r != null) {
                    for (LabRecipe.Ingredient ing : r.ingredients()) {
                        lore.add("<white>" + ing.amount() + " " + ing.name());
                    }
                } else {
                    lore.add("<white>" + (made == ItemType.JOINT ? "1 Dried Bud + 1 Rolling Papers" : "2 Dried Bud + 1 Blunt Wrap"));
                }
                lore.add("<gray>They take it from any of your chests");
                lore.add("<gray>around them (or buy it), or click yours.");
                lore.add("<dark_gray>Click: make something else");
            }
            lore.addAll(crewLines());
            ItemStack icon = made == null ? Items.icon("tab_cook", "<aqua>Making: <white>nothing yet", lore)
                    : CatalogIcons.sample(made);
            if (made != null) {
                icon.editMeta(m -> {
                    m.itemName(Text.mm("<aqua>Making: <white>" + made.display()));
                    m.lore(Text.lines(lore));
                });
            }
            return Items.glint(icon, made == null);
        }
        switch (t) {
            case FARMHAND -> lore.addAll(List.of(
                    "<gray>Picks your ripe plants near them and",
                    "<gray>plants them again, plants seeds on empty",
                    "<gray>farmland (" + workers().emptyFarmland(worker) + " near them) and fertilizes.",
                    "<gray>The harvest goes in your chests; seeds",
                    "<gray>and fertilizer come from them or their",
                    "<gray>satchel (Supply your crew in /kush workers)."));
            case DRYER -> lore.addAll(List.of(
                    "<gray>Put them near your Drug Lab. They take",
                    "<gray>fresh buds from your Farmhands and chests,",
                    "<gray>hang them on the racks and take them off",
                    "<gray>dry. The dried buds go in a chest; your",
                    "<gray>Cooks use them, your Runners sell them."));
            case RUNNER -> lore.addAll(List.of(
                    "<gray>Sells everything your workers make the",
                    "<gray>moment they get it: from their satchels",
                    "<gray>and from any of your chests around (what",
                    "<gray>nobody uses). Brings workers what they're",
                    "<gray>missing from your chests and puts what",
                    "<gray>doesn't sell in a chest. Money goes to you."));
            default -> {
            }
        }
        lore.addAll(crewLines());
        return Items.icon("ui_guide", "<aqua>How they work", lore);
    }

    /** "Crew: 2 Farmhands, 1 Dryer" - the workers this one works together with. */
    private List<String> crewLines() {
        Workers ws = workers();
        java.util.Map<WorkerType, Integer> count = new java.util.EnumMap<>(WorkerType.class);
        for (Worker o : ws.crew(worker)) {
            count.merge(o.type(), 1, Integer::sum);
        }
        List<String> out = new ArrayList<>();
        if (count.isEmpty()) {
            out.add("<dark_gray>No other workers of yours within " + ws.chainRadius() + " blocks.");
        } else {
            List<String> parts = new ArrayList<>();
            count.forEach((type, n) -> parts.add(n + " " + type.display() + (n > 1 ? "s" : "")));
            out.add("<green>Works with: <white>" + String.join(", ", parts));
        }
        return out;
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
            case PREV, NEXT -> {
                int pages = (Worker.SATCHEL + PAGE - 1) / PAGE;
                int next = Math.max(0, Math.min(pages - 1, page + (slot == NEXT ? 1 : -1)));
                if (next != page) {
                    page = next;
                    clickSound();
                }
            }
            case OPTION -> {
                if (worker.type() == WorkerType.RUNNER) {
                    if (ws.sellAll(worker) > 0) {
                        successSound();
                    } else {
                        failSound();
                    }
                } else if (ws.autoBuyAllowed()) {
                    ws.setAutoBuy(worker.owner(), !ws.autoBuy(worker.owner()));
                    clickSound();
                } else {
                    failSound();
                }
            }
            case CHESTS -> clickSound();
            default -> {
                int i = slot - FIRST;
                if (i >= 0 && i < PAGE) {
                    take(page * PAGE + i);
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
        workers().touch(worker, true);
        player.playSound(player.getLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 0.8f, 1f);
    }

    private void take(int i) {
        ItemStack it = worker.satchel().getItem(i);
        if (it == null || it.getType().isAir()) {
            return;
        }
        InventoryUtil.give(player, it);
        worker.satchel().setItem(i, null);
        workers().touch(worker, true);
        player.playSound(player.getLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    /** Clicking your own seeds / fertilizer / fresh buds hands them over. */
    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        boolean wanted = worker.type() == WorkerType.RUNNER ? Selling.sellable(item) : workers().uses(worker, item);
        if (!wanted) {
            player.sendActionBar(Text.mm("<gray>" + Text.escape(worker.name()) + " only takes " + switch (worker.type()) {
                case FARMHAND -> "seeds and fertilizer.";
                case DRYER -> "fresh buds.";
                case COOK -> worker.product() == null ? "ingredients - pick a drug first." : "what their recipe needs.";
                case RUNNER -> "product to sell.";
            }));
            return;
        }
        ItemStack inSlot = player.getInventory().getItem(slot);
        if (inSlot == null || !inSlot.isSimilar(item)) {
            return;
        }
        List<ItemStack> left = workers().stash(worker, List.of(inSlot.clone()));
        player.getInventory().setItem(slot, left.isEmpty() ? null : left.get(0));
        KushCraft.get().persistence().took(player); // into a satchel in the database: their inventory is saved first
        if (!left.isEmpty() && left.get(0).getAmount() == inSlot.getAmount()) {
            player.sendActionBar(Text.mm("<red>The satchel is full."));
            failSound();
            return;
        }
        player.playSound(player.getLocation(), "minecraft:item.bundle.insert", SoundCategory.PLAYERS, 0.8f, 1f);
        if (worker.type() == WorkerType.RUNNER) {
            workers().sellAll(worker); // a Runner sells what they get right away
        } else {
            workers().hurry(worker);
        }
        render();
    }

    @Override
    public void tick() {
        render();
    }
}
