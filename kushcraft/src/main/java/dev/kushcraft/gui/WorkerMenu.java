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
 * A worker's menu (right-click them): who they are, who they work with
 * (no chests: worker to worker), where they work, an option (a Dryer: sell
 * what it dries; a Cook mixing strains: what they keep; a Supplier: what
 * they spent; else how full the satchel is), their job (a Cook's drug),
 * rename / pause / train / dismiss; their satchel (click an item to take it,
 * click your own seeds / buds / ingredients to give them); and a bottom bar:
 * back, their own button (a Farmhand's seed backpack, a Runner's sell now, a
 * Dryer's racks, a Cook's shopping list, a Supplier's next buys), take all,
 * what they're doing right now and the handbook. Layout: tools/gui.py worker().
 */
public final class WorkerMenu extends Menu {

    static final int ROWS = 5;
    static final int INFO = 0;
    static final int CHAIN = 1;
    static final int SHOW = 2;
    static final int OPTION = 3;
    static final int JOB = 4;
    static final int RENAME = 5;
    static final int PAUSE = 6;
    static final int UPGRADE = 7;
    static final int DISMISS = 8;
    static final int FIRST = 9;
    static final int BACK = 36;
    static final int EXTRA = 38;
    static final int TAKE_ALL = 40;
    static final int NOW = 42;
    static final int HELP = 44;

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
        if (worker.problem() != null && !worker.paused()) {
            info.add("<gold>⚠ Stuck - they need you (see above).");
        }
        set(INFO, Items.icon(t.item().model(), t.color() + Text.escape(worker.name()) + " <gray>the " + t.display(), info));
        set(CHAIN, chainIcon());
        set(SHOW, Items.icon("ui_show", "<aqua>Show where they work",
                "<gray>For 10 seconds: a ring around their area",
                "<gray>and <green>green<gray> sparks over the workers they", "<gray>work with."));
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
        set(EXTRA, extraIcon());
        int carried = worker.carried();
        set(TAKE_ALL, Items.glint(Items.icon("ui_take", carried > 0 ? "<green><bold>Take all</bold> <gray>(" + carried + ")"
                : "<gray>Satchel is empty", "<dark_gray>Click an item to take just that."), carried > 0));
        set(NOW, nowIcon());
        set(HELP, Items.icon("ui_guide", "<aqua>Handbook: workers", "<gray>How the work chain works, what each",
                "<gray>worker does and what they need.", "<dark_gray>Click to open"));
    }

    /** Right now: their status in big letters, what's wrong and what they're missing. */
    private ItemStack nowIcon() {
        boolean stuck = worker.problem() != null && !worker.paused();
        List<String> lore = new ArrayList<>();
        if (stuck) {
            lore.add("<gold>⚠ Stuck for a while - they need you.");
        }
        if (worker.wants() != null) {
            lore.add("<yellow>Missing: <white>" + worker.wants());
        }
        lore.add("<gray>" + worker.jobs() + " jobs done since hired.");
        String status = worker.paused() ? "<red>Paused" : worker.isLoaded() ? worker.status()
                : "<dark_gray>Asleep (nobody nearby)";
        return Items.glint(Items.icon(stuck ? "ui_info" : "ui_auto", "<white>Now: " + status, lore), stuck);
    }

    /** The bottom-bar button of each kind of worker. */
    private ItemStack extraIcon() {
        Workers ws = workers();
        switch (worker.type()) {
            case FARMHAND -> {
                int seeds = worker.seedCount();
                return Items.glint(Items.icon("seed_pack", "<green><bold>Seed backpack</bold> <gray>(" + seeds + " seeds)",
                        "<gray>" + worker.seedSlots() + "/" + Worker.SEED_BAG + " slots · thousands of seeds fit.",
                        "<gray>Seeds never fill the satchel: they go",
                        "<gray>in here and get planted on empty",
                        "<gray>farmland (" + ws.emptyFarmland(worker) + " near them) before anything else.",
                        "<dark_gray>Click to open"), seeds > 0);
            }
            case RUNNER -> {
                double value = ws.carriedValue(worker);
                return Items.glint(Items.icon("ui_sell", value > 0 ? "<gold><bold>Sell now</bold> <gray>(" + money(value) + ")"
                                : "<gray>Nothing to sell",
                        "<gray>They sell whatever nobody needs the",
                        "<gray>moment they get it - this sells what",
                        "<gray>they carry right now (never seeds).",
                        "<dark_gray>Keeps " + Math.round(ws.runnerCut() * 100) + "% of every sale."), value > 0);
            }
            case DRYER -> {
                int[] r = ws.racks(worker);
                if (r == null) {
                    return Items.icon("ui_rack", "<red>No Drug Lab", "<gray>Put a Drug Lab within " + ws.radius(worker),
                            "<gray>blocks of them (they walk there).");
                }
                return Items.icon("ui_rack", "<gold>Racks: <white>" + r[0] + "/" + r[2] + " <gray>in use, <green>" + r[1]
                                + " dry", "<gray>They hang fresh buds and take them",
                        "<gray>off dry. A Runner brings the buds and",
                        "<gray>sells the dried ones" + (worker.sells() ? " (all of them)." : "."),
                        "<dark_gray>Click: show where they work");
            }
            case COOK -> {
                List<String> lore = new ArrayList<>();
                List<Workers.Buy> buys = ws.needs(worker);
                if (worker.wants() != null) {
                    lore.add("<yellow>Missing now: <white>" + worker.wants());
                }
                for (Workers.Buy b : buys.subList(0, Math.min(5, buys.size()))) {
                    lore.add("<white>" + b.amount() + " " + b.name() + " <dark_gray>(" + money(b.cost()) + ")");
                }
                if (lore.isEmpty()) {
                    lore.add("<green>They have what they need.");
                }
                lore.add("<dark_gray>A Supplier buys it, a Runner brings it.");
                return Items.icon("ui_supply", "<aqua>Shopping list", lore);
            }
            default -> {
                List<String> lore = new ArrayList<>();
                for (Worker o : ws.crew(worker)) {
                    for (Workers.Buy b : ws.needs(o)) {
                        if (lore.size() < 6) {
                            lore.add("<white>" + b.amount() + " " + b.name() + " <dark_gray>for " + Text.escape(o.name())
                                    + " <gold>" + money(b.cost()));
                        }
                    }
                }
                if (lore.isEmpty()) {
                    lore.add("<green>Everyone has what they need.");
                }
                lore.add("<gray>Spent so far: <gold>" + money(worker.spent()));
                return Items.icon("ui_supply", "<yellow>Next buys", lore);
            }
        }
    }

    /** Who they get things from and pass them to: no chests, worker to worker. */
    private ItemStack chainIcon() {
        List<String> lore = new ArrayList<>(switch (worker.type()) {
            case FARMHAND -> List.of("<gray>Their harvest goes to your <light_purple>Runner<gray>,",
                    "<gray>who takes fresh buds to the <gold>Dryer<gray> and",
                    "<gray>sells the rest. Seeds stay in their",
                    "<gray>backpack and get planted.");
            case DRYER -> List.of("<gray>Fresh buds come from your Farmhands",
                    "<gray>(a <light_purple>Runner<gray> brings them). Dried buds go",
                    "<gray>to a <aqua>Cook<gray> who uses them, or the Runner",
                    "<gray>sells them.");
            case COOK -> List.of("<gray>Ingredients come from your other workers,",
                    "<gray>a <light_purple>Runner<gray> and the <gold>Supplier<gray>. What they",
                    "<gray>make goes to the next Cook or the Runner,",
                    "<gray>who sells it.");
            case RUNNER -> List.of("<gray>Empties your workers' satchels: the",
                    "<gray>harvest to the Dryer, buds to Cooks, seeds",
                    "<gray>to Farmhands - and sells whatever else",
                    "<gray>the moment they get it (never seeds).");
            case SUPPLIER -> List.of("<gray>Buys whatever your Cooks and Farmhands",
                    "<gray>are short of with your money - seeds for",
                    "<gray>empty farmland, fertilizer, ingredients.",
                    "<gray>No budget, as long as you can pay.");
        });
        lore.addAll(crewLines());
        lore.add("<dark_gray>Click: show who they work with");
        return Items.icon("ui_workers", "<green>Who they work with", lore);
    }

    private ItemStack optionIcon() {
        Workers ws = workers();
        if (worker.type() == WorkerType.SUPPLIER) {
            return Items.icon("ui_wallet", "<gold>Spent " + money(worker.spent()) + " on supplies",
                    "<gray>No budget: they buy anything your", "<gray>workers are short of while you have",
                    "<gray>the money. Pause them to stop.");
        }
        if (worker.type() == WorkerType.DRYER) {
            boolean on = worker.sells();
            return Items.glint(Items.icon("ui_sell", on ? "<green>Sell what I dry: ON" : "<yellow>Sell what I dry: OFF",
                    on ? "<gray>Runners take every dried bud I make" : "<gray>Cooks get the dried buds first;",
                    on ? "<gray>and sell it (Cooks don't get any)." : "<gray>Runners sell what's left.",
                    "<dark_gray>Click to switch"), on);
        }
        if (worker.type() == WorkerType.COOK && worker.mixes()) {
            return Items.glint(Items.icon("ui_dna", "<light_purple>Keep: " + worker.keepRarity().colored()
                            + " <light_purple>and better",
                    "<gray>New strains rarer than this get a name",
                    "<gray>and seeds; the rest are thrown away.",
                    "<gray>You've bred <white>" + KushCraft.get().strains().countCreatedBy(worker.owner())
                            + "/" + KushCraft.get().getConfig().getInt("strain-maker.max-per-player", 25)
                            + "<gray> strains.",
                    "<dark_gray>Click: rarer · Right-click: less rare"), true);
        }
        int free = worker.freeSlots();
        boolean tight = free < 6;
        return Items.glint(Items.icon("ui_take", (tight ? "<red>" : "<white>") + "Satchel: " + (Worker.SATCHEL - free) + "/"
                        + Worker.SATCHEL + " slots " + WorkersMenu.bar(Worker.SATCHEL - free, Worker.SATCHEL),
                tight ? "<gray>Nearly full: the Runner empties it first" : "<gray>A Runner takes what they make and",
                tight ? "<gray>and sells what nobody can take in time." : "<gray>passes it on or sells it.",
                "<dark_gray>Take things out below."), tight);
    }

    private org.bukkit.inventory.ItemStack jobIcon() {
        WorkerType t = worker.type();
        List<String> lore = new ArrayList<>();
        if (t == WorkerType.COOK) {
            ItemType made = worker.product();
            LabRecipe r = worker.recipe();
            if (worker.mixes()) {
                lore.add("<gray>At your Drug Lab they cross the two best");
                lore.add("<gray>strains they have seeds of (" + money(KushCraft.get().getConfig()
                        .getDouble("strain-maker.cost", 1500)) + " a mix).");
                lore.add("<gray>Seeds come from your Farmhands, a Runner");
                lore.add("<gray>or the Supplier; new seeds go to your");
                lore.add("<gray>Farmhands to plant.");
                lore.add("<dark_gray>Click: make something else");
                lore.addAll(crewLines());
                return Items.glint(Items.icon("tab_mix", "<light_purple>Making: <white>new strains", lore), true);
            }
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
                lore.add("<gray>Your other workers, a Runner and the");
                lore.add("<gray>Supplier bring it - or click yours below.");
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
                    "<gray>Plants every empty farmland near them",
                    "<gray>from their seed backpack first, then picks",
                    "<gray>your ripe plants (and plants them again)",
                    "<gray>and fertilizes the growing ones.",
                    "<gray>A Runner takes the harvest from them."));
            case DRYER -> lore.addAll(List.of(
                    "<gray>Put them near your Drug Lab. They take",
                    "<gray>fresh buds from your Farmhands, hang",
                    "<gray>them on the racks and take them off dry.",
                    "<gray>Your Cooks and Runners take the dry buds."));
            case RUNNER -> lore.addAll(List.of(
                    "<gray>Brings your workers what they're",
                    "<gray>missing from anywhere in the crew -",
                    "<gray>through walls, the back way. Never",
                    "<gray>gives anyone more than they have room",
                    "<gray>for, and sells the rest on the spot."));
            case SUPPLIER -> {
                lore.addAll(List.of(
                        "<gray>Buys what your Cooks and Farmhands",
                        "<gray>run low on (seeds for empty farmland,",
                        "<gray>fertilizer, Trade items, Lab Solvent,",
                        "<gray>papers, water) with your money."));
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
            case CHAIN, SHOW -> {
                player.closeInventory();
                ws.show(player, worker);
                player.sendActionBar(Text.mm("<aqua>Look around: green sparks = the workers they work with."));
                return;
            }
            case OPTION -> {
                if (worker.type() == WorkerType.COOK && worker.mixes()) {
                    ws.cycleKeep(worker, click.isRightClick());
                    clickSound();
                } else if (worker.type() == WorkerType.DRYER) {
                    ws.setSell(worker, !worker.sells());
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
            case EXTRA -> {
                switch (worker.type()) {
                    case FARMHAND -> {
                        openChild(new SeedBagMenu(player, worker));
                        return;
                    }
                    case RUNNER -> {
                        double got = ws.sellAll(worker);
                        if (got <= 0) {
                            failSound();
                        } else {
                            successSound();
                        }
                    }
                    case DRYER -> {
                        player.closeInventory();
                        ws.show(player, worker);
                        return;
                    }
                    case COOK -> {
                        openChild(new CookRecipeMenu(player, worker));
                        return;
                    }
                    default -> clickSound();
                }
            }
            case HELP -> {
                clickSound();
                player.closeInventory();
                player.openBook(dev.kushcraft.guide.Guide.book(player));
                player.sendActionBar(Text.mm("<gray>Workers are in the contents of the handbook."));
                return;
            }
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
                case FARMHAND -> "seeds (into the backpack) and fertilizer.";
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
        if (worker.type() == WorkerType.RUNNER) {
            workers().sellAll(worker); // a Runner sells what they get right away
        } else {
            workers().hurry(worker); // they get to work with it right away
        }
        render();
    }

    @Override
    public void tick() {
        render();
    }
}
