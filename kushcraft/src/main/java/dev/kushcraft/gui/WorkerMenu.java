package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.lab.LabRecipe;
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
 * linked chests, where they work, chests around them (a Supplier: the money
 * they leave you), their job (a Cook's drug), their satchel (click an item
 * to take it, click your own seeds / buds / ingredients to give them), and
 * buttons to rename, pause, train and dismiss them. Layout: tools/gui.py worker().
 */
public final class WorkerMenu extends Menu {

    static final int ROWS = 5;
    static final int INFO = 0;
    static final int LINK = 1;
    static final int SHOW = 2;
    static final int OPTION = 3;
    static final int JOB = 4;
    static final int RENAME = 5;
    static final int PAUSE = 6;
    static final int UPGRADE = 7;
    static final int DISMISS = 8;
    static final int FIRST = 9;
    static final int BACK = 36;
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
        if (t == WorkerType.RUNNER) {
            info.add("<gray>Keeps " + Math.round(ws.runnerCut() * 100) + "% of sales <dark_gray>· " + worker.jobs()
                    + " trips, " + money(worker.wages()) + " kept");
        } else if (t == WorkerType.SUPPLIER) {
            info.add("<gray>" + money(ws.wage(t)) + " per delivery <dark_gray>· spent " + money(worker.spent())
                    + " on supplies");
        } else {
            info.add("<gray>" + money(ws.wage(t)) + " per job <dark_gray>· " + worker.jobs() + " jobs, "
                    + money(worker.wages()) + " paid");
        }
        if (worker.wants() != null) {
            info.add("<yellow>Missing: " + worker.wants());
        }
        set(INFO, Items.icon(t.item().model(), t.color() + Text.escape(worker.name()) + " <gray>the " + t.display(), info));
        set(LINK, linkIcon());
        set(SHOW, Items.icon("ui_show", "<aqua>Show where they work",
                "<gray>For 10 seconds: a ring around their area,",
                "<green>green<gray> on chests they use, <red>red<gray> on",
                "<gray>linked chests behind walls."));
        set(OPTION, optionIcon());
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
        backButton(BACK);
        int carried = worker.carried();
        set(TAKE_ALL, Items.glint(Items.icon("ui_take", carried > 0 ? "<green><bold>Take all</bold> <gray>(" + carried + ")"
                : "<gray>Satchel is empty", "<dark_gray>Click an item to take just that."), carried > 0));
    }

    private ItemStack linkIcon() {
        Workers ws = workers();
        List<String> lore = new ArrayList<>();
        int linked = worker.links().size();
        int blocked = ws.blocked(worker).size();
        lore.add("<white>" + linked + "/" + Worker.MAX_LINKS + " linked" + (blocked > 0 && worker.type() != WorkerType.RUNNER
                && worker.type() != WorkerType.SUPPLIER ? " <red>(" + blocked + " behind walls)" : ""));
        lore.add(switch (worker.type()) {
            case RUNNER -> "<gray>They sell everything in them.";
            case SUPPLIER -> "<gray>Nothing to link: they buy what's missing.";
            default -> "<gray>They take ingredients from them and";
        });
        if (worker.type() != WorkerType.RUNNER && worker.type() != WorkerType.SUPPLIER) {
            lore.add("<gray>put their work in them.");
            lore.add("<dark_gray>Behind a wall? A Runner carries it.");
        }
        lore.add("<dark_gray>Click, then right-click chests.");
        return Items.amount(Items.icon("ui_link", "<green>Link chests", lore), Math.max(1, linked));
    }

    private ItemStack optionIcon() {
        Workers ws = workers();
        if (worker.type() == WorkerType.SUPPLIER) {
            return Items.icon("ui_reserve", "<gold>Keep in your wallet: " + money(worker.reserve()),
                    "<gray>They never spend below this.", "<dark_gray>Click: more · Right-click: less");
        }
        if (worker.type() == WorkerType.RUNNER) {
            return Items.icon("ui_nearby", "<gray>Chests around them", "<dark_gray>Runners only use linked chests",
                    "<dark_gray>and the one by their spot.");
        }
        int around = ws.chests(worker).size();
        return Items.glint(Items.icon("ui_nearby", worker.nearby() ? "<green>Chests around them: ON"
                        : "<red>Chests around them: OFF",
                "<gray>ON: they also use your chests they", "<gray>can walk to (" + around + " now).",
                "<gray>OFF: only linked chests and the one", "<gray>right by their spot.",
                "<dark_gray>Click to switch"), worker.nearby());
    }

    private org.bukkit.inventory.ItemStack jobIcon() {
        WorkerType t = worker.type();
        List<String> lore = new ArrayList<>();
        if (t == WorkerType.COOK) {
            ItemType made = worker.product();
            LabRecipe r = worker.recipe();
            if (worker.autoPick()) {
                lore.add("<gray>They make the best drug they have");
                lore.add("<gray>everything for" + (made != null ? " - now: <white>" + made.display() : "."));
                lore.add("<dark_gray>Click: pick one drug instead");
                lore.addAll(crewLines());
                ItemStack icon = Items.icon("ui_auto", "<aqua>Making: <white>whatever pays best", lore);
                return Items.glint(icon, true);
            }
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
                lore.add("<gray>They fetch it from your other workers");
                lore.add("<gray>and chests, or click yours below.");
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
                    "<gray>plants a seed from the harvest again.",
                    "<gray>Give them seeds and fertilizer to plant",
                    "<gray>empty farmland. Spare seeds become",
                    "<gray>fertilizer. Dryers and Cooks take the",
                    "<gray>harvest from them."));
            case DRYER -> lore.addAll(List.of(
                    "<gray>Put them near your Drug Lab. They take",
                    "<gray>fresh buds from your Farmhands, hang",
                    "<gray>them on the racks and take them off dry.",
                    "<gray>Your Cooks and Runners take the dry buds."));
            case RUNNER -> lore.addAll(List.of(
                    "<gray>Brings your workers what they're",
                    "<gray>missing from anywhere in the crew -",
                    "<gray>through walls, the back way. Picks up",
                    "<gray>finished product nobody needs and sells",
                    "<gray>it, at your Dealer Stand if one is near."));
            case SUPPLIER -> {
                lore.addAll(List.of(
                        "<gray>Buys what your Cooks and Farmhands",
                        "<gray>run low on (Trade items, Lab Solvent,",
                        "<gray>papers, fertilizer, water) with your",
                        "<gray>money and brings it to them."));
                List<String> buying = new ArrayList<>();
                for (Worker o : workers().crew(worker)) {
                    for (Workers.Buy b : workers().needs(o)) {
                        if (buying.size() < 4) {
                            buying.add("<white>" + b.amount() + " " + b.name() + " <dark_gray>for " + Text.escape(o.name())
                                    + " <gold>" + money(b.cost()));
                        }
                    }
                }
                if (!buying.isEmpty()) {
                    lore.add("<yellow>Next:");
                    lore.addAll(buying);
                }
            }
            default -> {
            }
        }
        if (t != WorkerType.SUPPLIER) {
            lore.add("<white>Chest by their spot: <gray>they put their work in it.");
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
        int chests = ws.chests(worker).size();
        if (chests > 0) {
            out.add("<green>Uses " + chests + " chest" + (chests > 1 ? "s" : ""));
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
            case LINK -> {
                if (worker.type() == WorkerType.SUPPLIER) {
                    failSound();
                    return;
                }
                player.closeInventory();
                ws.startLinking(player, worker);
                return;
            }
            case SHOW -> {
                player.closeInventory();
                ws.show(player, worker);
                player.sendActionBar(Text.mm("<aqua>Look around: green = chests they use, red = behind walls."));
                return;
            }
            case OPTION -> {
                if (worker.type() == WorkerType.SUPPLIER) {
                    double[] r = Workers.RESERVES;
                    int i = 0;
                    while (i < r.length && r[i] < worker.reserve() - 0.01) {
                        i++;
                    }
                    i = click.isRightClick() ? Math.max(0, i - 1) : Math.min(r.length - 1, i + (i < r.length
                            && Math.abs(r[i] - worker.reserve()) < 0.01 ? 1 : 0));
                    ws.setReserve(worker, r[i]);
                    clickSound();
                } else if (worker.type() != WorkerType.RUNNER) {
                    ws.setNearby(worker, !worker.nearby());
                    clickSound();
                }
            }
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
        boolean wanted = worker.type() == WorkerType.RUNNER ? Selling.sellable(item)
                : worker.type() == WorkerType.COOK && worker.autoPick() ? LabRecipe.anyNeeds(item)
                : workers().uses(worker, item);
        if (!wanted) {
            player.sendActionBar(Text.mm("<gray>" + Text.escape(worker.name()) + " only takes " + switch (worker.type()) {
                case FARMHAND -> "seeds and fertilizer.";
                case DRYER -> "fresh buds.";
                case COOK -> worker.product() == null ? "ingredients - pick a drug first." : "what their recipe needs.";
                case RUNNER -> "product to sell.";
                case SUPPLIER -> "nothing - they buy things themselves.";
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

    @Override
    public void tick() {
        render();
    }
}
