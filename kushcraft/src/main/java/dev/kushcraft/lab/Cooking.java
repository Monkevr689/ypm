package dev.kushcraft.lab;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.Items;
import dev.kushcraft.machines.Machine;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.StrainStock;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Starting a batch at a Drug Lab: takes the ingredients from an inventory
 * (a player's, or a Cook's satchel), hands back buckets and bottles, and
 * starts the lab. Used by Drug Lab &gt; Cook and by hired Cooks.
 */
public final class Cooking {

    /** Shift-click (and a Cook) cooks up to this many batches in one go; each takes the full time. */
    public static final int MAX_BATCHES = 4;

    /** error = what's missing (null when it started). */
    public record Result(String error, int batches, int bonus, ItemStack output) {
        public boolean ok() {
            return error == null;
        }
    }

    private Cooking() {
    }

    /** Cook time multiplier: config x 0.85 per upgrade level, minus the owner's cartel bonus. */
    public static double timeFactor(Machine m) {
        return Math.max(0.01, KushCraft.get().getConfig().getDouble("lab.time-multiplier", 1.0))
                * Math.pow(0.85, Math.max(0, m.level() - 1))
                * (1 - KushCraft.get().cartels().labBonus(m.owner()));
    }

    /** Chance of one extra item per batch: 8% per upgrade level. */
    public static double bonusChance(Machine m) {
        return 0.08 * Math.max(0, m.level() - 1);
    }

    /** The first ingredient missing from inv for one batch, or null when everything is there. */
    public static LabRecipe.Ingredient missing(Inventory inv, LabRecipe r, String preferStrain, int preferQuality) {
        for (LabRecipe.Ingredient ing : r.ingredients()) {
            if (ing.strainSource()) {
                if (StrainStock.pick(inv, ing.custom(), ing.amount(), preferStrain, preferQuality) == null) {
                    return ing;
                }
            } else if (InventoryUtil.count(inv, ing::matches) < ing.amount()) {
                return ing;
            }
        }
        return null;
    }

    /**
     * Takes the ingredients for up to wanted batches (as many as inv has, and
     * one stack of output at most) and starts the lab. Leftover containers
     * (bucket, bottles) go to giveBack.
     */
    public static Result start(Inventory inv, Machine machine, LabRecipe r, int wanted, String preferStrain,
                               int preferQuality, Consumer<ItemStack> giveBack) {
        if (machine.busy()) {
            return new Result("The lab is busy.", 0, 0, null);
        }
        LabRecipe.Ingredient miss = missing(inv, r, preferStrain, preferQuality);
        if (miss != null) {
            return new Result("You need " + miss.amount() + " " + miss.name() + (miss.strainSource() ? " (one strain)" : ""),
                    0, 0, null);
        }
        StrainStock.Group g = null;
        for (LabRecipe.Ingredient ing : r.ingredients()) {
            if (ing.strainSource()) {
                g = StrainStock.pick(inv, ing.custom(), ing.amount(), preferStrain, preferQuality);
            }
        }
        int batches = Math.max(1, Math.min(wanted, r.output().maxStack() / Math.max(1, r.amount())));
        for (LabRecipe.Ingredient ing : r.ingredients()) {
            int have = ing.strainSource() ? g.count() : InventoryUtil.count(inv, ing::matches);
            batches = Math.max(1, Math.min(batches, have / ing.amount()));
        }
        for (LabRecipe.Ingredient ing : r.ingredients()) {
            int n = ing.amount() * batches;
            if (ing.strainSource()) {
                StrainStock.take(inv, ing.custom(), g, n);
            } else {
                InventoryUtil.remove(inv, ing::matches, n);
                if (ing.remainder() != null) {
                    giveBack.accept(new ItemStack(ing.remainder(), n));
                }
            }
        }
        int bonus = 0;
        for (int b = 0; b < batches; b++) {
            if (ThreadLocalRandom.current().nextDouble() < bonusChance(machine)) {
                bonus++;
            }
        }
        int amount = Math.min(r.output().maxStack(), r.amount() * batches + bonus);
        ItemStack result = r.output().strainBound() && g != null
                ? Items.strainItem(r.output(), g.strain(), g.quality(), amount)
                : r.output().strainBound()
                ? Items.strainItem(r.output(), KushCraft.get().strains().getOrDefault(null), 3, amount)
                : Items.create(r.output(), amount);
        machine.startJob(r.name(), (long) (r.seconds() * 1000L * batches * timeFactor(machine)), result);
        KushCraft.get().machines().markDirty();
        return new Result(null, batches, bonus, result);
    }
}
