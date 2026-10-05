package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.lab.LabRecipe;
import dev.kushcraft.machine.Machine;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.StrainStock;
import dev.kushcraft.util.Text;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Lab Station: pick a recipe, wait, collect. Layout matches tools/gui.py lab(). */
public final class LabMenu extends Menu {

    private static final int STATUS = 8;
    private static final int BACK = 36;
    private static final int OUTPUT = 40;
    private static final int INFO = 44;
    private static final int[] RECIPES = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
    private static final int[] PROGRESS = {28, 29, 30, 31, 32, 33, 34};

    private final Machine machine;
    private String pickStrain;
    private int pickQuality;

    public LabMenu(org.bukkit.entity.Player player, Machine machine) {
        super(player, 5, "lab", "Drug Lab - Cook");
        this.machine = machine;
    }

    @Override
    public void render() {
        inv.clear();
        backButton(BACK);
        LabRecipe[] recipes = LabRecipe.values();
        for (int i = 0; i < RECIPES.length && i < recipes.length; i++) {
            set(RECIPES[i], recipeIcon(recipes[i]));
        }
        LabRecipe job = machine.busy() ? LabRecipe.parse(machine.job()) : null;
        double progress = machine.jobProgress();
        int filled = machine.busy() ? (int) Math.floor(progress * PROGRESS.length + 1e-6) : 0;
        for (int i = 0; i < PROGRESS.length; i++) {
            boolean on = i < filled;
            set(PROGRESS[i], Items.icon(on ? "progress_full" : "progress_empty",
                    machine.busy() ? "<green>" + (int) Math.round(progress * 100) + "%" : "<gray>Idle"));
        }
        // status
        if (job == null) {
            set(STATUS, Items.icon("lab_solvent", "<aqua>Lab is idle", "<gray>Click a recipe to start cooking."));
        } else if (machine.jobDone()) {
            set(STATUS, Items.glint(Items.icon("lab_solvent", "<green>Batch ready!", "<gray>Collect it below."), true));
        } else {
            int left = (int) Math.ceil((machine.jobEnd() - System.currentTimeMillis()) / 1000.0);
            set(STATUS, Items.icon("lab_solvent", "<yellow>Cooking " + job.output().display(),
                    "<gray>" + Text.bar(progress, 10, "green", "dark_gray") + " <white>" + Text.time(left)));
        }
        // output
        if (machine.busy() && machine.output() != null) {
            ItemStack out = machine.output().clone();
            ItemMeta meta = out.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Text.mm(""));
            lore.add(Text.mm(machine.jobDone() ? "<green><bold>Click to collect!" : "<gray>Not ready yet..."));
            meta.lore(lore);
            out.setItemMeta(meta);
            set(OUTPUT, machine.jobDone() ? Items.glint(out, true) : out);
        }
        // help
        List<String> help = new ArrayList<>(List.of(
                "<gray>1. Have the ingredients in your inventory",
                "<gray>2. Click a recipe to start the batch",
                "<gray>3. Come back when it's done and collect",
                "",
                "<gray>Recipes with <green>Dried Bud</green> or <green>Hash</green> use the",
                "<gray>one you clicked in your inventory (or the first).",
                "",
                "<gray>Lab level <white>" + machine.level() + "</white>: <green>" + Math.round((1 - Math.pow(0.85,
                        machine.level() - 1)) * 100) + "%</green> faster, <green>" + Math.round(bonusChance(machine) * 100)
                        + "%</green> bonus chance",
                "<gray>Locked recipes unlock with your dealer rank."));
        if (pickStrain != null) {
            Strain s = KushCraft.get().strains().get(pickStrain);
            if (s != null) {
                help.add("<gray>Selected: " + s.colored() + " " + Text.stars(pickQuality));
            }
        }
        help.add("");
        help.add("<dark_gray>Lab Solvent & Catalyst: crafting table or Market");
        set(INFO, Items.icon("ui_info", "<aqua>How the lab works", help));
    }

    private ItemStack recipeIcon(LabRecipe r) {
        ItemStack icon;
        StrainStock.Group g = null;
        for (LabRecipe.Ingredient ing : r.ingredients()) {
            if (ing.strainSource()) {
                g = StrainStock.pick(player, ing.custom(), ing.amount(), pickStrain, pickQuality);
            }
        }
        if (r.output().strainBound()) {
            Strain s = g != null ? g.strain() : KushCraft.get().strains().all().iterator().next();
            icon = Items.strainItem(r.output(), s, g != null ? g.quality() : 3, r.amount());
        } else {
            icon = Items.create(r.output(), r.amount());
        }
        ItemMeta meta = icon.getItemMeta();
        meta.itemName(Text.mm("<white>" + r.output().display() + " <gray>x" + r.amount()));
        List<String> lore = new ArrayList<>();
        int secs = (int) Math.round(r.seconds() * timeFactor(machine));
        lore.add("<gray>Time: <white>" + Text.time(secs));
        lore.add("<gray>Needs:");
        boolean all = true;
        for (LabRecipe.Ingredient ing : r.ingredients()) {
            int have;
            String extra = "";
            if (ing.strainSource()) {
                have = g == null ? maxGroup(ing.custom()) : g.count();
                if (g != null) {
                    extra = " <dark_gray>(" + Text.plain(Text.mm(g.strain().colored())) + ")";
                }
            } else {
                have = InventoryUtil.count(player, ing::matches);
            }
            boolean ok = have >= ing.amount();
            all &= ok;
            lore.add((ok ? " <green>✔ " : " <red>✘ ") + "<white>" + ing.amount() + "x " + ing.name()
                    + extra + " <dark_gray>(" + have + ")");
        }
        lore.add("");
        KushCraft plugin = KushCraft.get();
        if (!plugin.ranks().canCook(player, r)) {
            var need = plugin.ranks().level(r.rank());
            lore.add("<red>✘ Locked - needs rank " + need.colored());
            lore.add("<dark_gray>Sell " + plugin.economy().format(need.sales()) + " of product to unlock.");
        } else if (machine.busy()) {
            lore.add("<red>The lab is busy.");
        } else {
            lore.add(all ? "<green><bold>Click to cook!" : "<red>Missing ingredients.");
        }
        meta.lore(Text.lines(lore));
        icon.setItemMeta(meta);
        if (!KushCraft.get().ranks().canCook(player, r)) {
            ItemStack locked = Items.icon("ui_lock", "<red>" + r.output().display() + " <gray>(locked)", List.of());
            locked.editMeta(m -> m.lore(meta.lore()));
            return locked;
        }
        return icon;
    }

    /** Cook time multiplier: config x 0.85 per upgrade level. */
    public static double timeFactor(Machine m) {
        return Math.max(0.01, KushCraft.get().getConfig().getDouble("lab.time-multiplier", 1.0))
                * Math.pow(0.85, Math.max(0, m.level() - 1));
    }

    /** Chance of one extra item per batch: 8% per upgrade level. */
    public static double bonusChance(Machine m) {
        return 0.08 * Math.max(0, m.level() - 1);
    }

    private int maxGroup(ItemType type) {
        int max = 0;
        for (StrainStock.Group g : StrainStock.groups(player, type)) {
            max = Math.max(max, g.count());
        }
        return max;
    }

    @Override
    public void click(int slot, ClickType click) {
        if (slot == OUTPUT) {
            collect();
            return;
        }
        for (int i = 0; i < RECIPES.length; i++) {
            if (RECIPES[i] == slot && i < LabRecipe.values().length) {
                start(LabRecipe.values()[i]);
                return;
            }
        }
    }

    private void collect() {
        if (!machine.busy() || !machine.jobDone() || machine.output() == null) {
            failSound();
            return;
        }
        InventoryUtil.give(player, machine.output().clone());
        machine.clearJob();
        KushCraft.get().machines().markDirty();
        player.playSound(player.getLocation(), "minecraft:entity.item.pickup", org.bukkit.SoundCategory.PLAYERS, 0.8f, 1f);
        render();
    }

    private void start(LabRecipe r) {
        if (!KushCraft.get().ranks().canCook(player, r)) {
            var need = KushCraft.get().ranks().level(r.rank());
            player.sendActionBar(Text.mm("<red>" + r.output().display() + " needs rank " + need.colored()
                    + "<red>. Sell more product to rank up!"));
            failSound();
            return;
        }
        if (machine.busy()) {
            player.sendActionBar(Text.mm(machine.jobDone() ? "<yellow>Collect the finished batch first."
                    : "<red>The lab is already cooking something."));
            failSound();
            return;
        }
        StrainStock.Group g = null;
        for (LabRecipe.Ingredient ing : r.ingredients()) {
            if (ing.strainSource()) {
                g = StrainStock.pick(player, ing.custom(), ing.amount(), pickStrain, pickQuality);
                if (g == null) {
                    missing(ing);
                    return;
                }
            } else if (InventoryUtil.count(player, ing::matches) < ing.amount()) {
                missing(ing);
                return;
            }
        }
        for (LabRecipe.Ingredient ing : r.ingredients()) {
            if (ing.strainSource()) {
                StrainStock.take(player, ing.custom(), g, ing.amount());
            } else {
                InventoryUtil.remove(player, ing::matches, ing.amount());
            }
        }
        ItemStack result = r.output().strainBound() && g != null
                ? Items.strainItem(r.output(), g.strain(), g.quality(), r.amount())
                : Items.create(r.output(), r.amount());
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < bonusChance(machine)
                && result.getAmount() < result.getMaxStackSize()) {
            result.setAmount(result.getAmount() + 1);
            player.sendMessage(Text.msg("<green>Lab bonus: <white>+1 " + r.output().display()));
        }
        machine.startJob(r.name(), (long) (r.seconds() * 1000L * timeFactor(machine)), result);
        KushCraft.get().machines().markDirty();
        successSound();
        player.playSound(player.getLocation(), "minecraft:block.brewing_stand.brew", org.bukkit.SoundCategory.BLOCKS, 1f, 1f);
        render();
    }

    private void missing(LabRecipe.Ingredient ing) {
        player.sendActionBar(Text.mm("<red>You need " + ing.amount() + "x " + ing.name()
                + (ing.strainSource() ? " (of one strain)" : "") + "."));
        failSound();
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        ItemType t = Items.type(item);
        if ((t == ItemType.BUD_DRIED || t == ItemType.HASH) && Items.strain(item) != null) {
            pickStrain = Items.strain(item).id();
            pickQuality = Items.quality(item);
            clickSound();
            render();
        }
    }

    @Override
    public void tick() {
        render();
    }
}
