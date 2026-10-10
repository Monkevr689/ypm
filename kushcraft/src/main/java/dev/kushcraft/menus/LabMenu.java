package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.lab.Cooking;
import dev.kushcraft.lab.LabRecipe;
import dev.kushcraft.machines.Machine;
import dev.kushcraft.strains.Strain;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.StrainStock;
import dev.kushcraft.util.Text;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Drug Lab > Cook: every recipe (green glow = you have everything), a
 * progress bar and the finished batch. Ingredients come straight from your
 * inventory. Layout matches tools/gui.py cook().
 */
public final class LabMenu extends LabTabMenu {

    static final int FIRST = 9;
    static final int SLOTS = 27;
    static final int[] PROGRESS = {at(4, 0), at(4, 1), at(4, 2), at(4, 3), at(4, 4), at(4, 5), at(4, 6)};
    static final int OUTPUT = at(4, 8);
    static final int MAX_BATCHES = Cooking.MAX_BATCHES;

    private String pickStrain;
    private int pickQuality;

    public LabMenu(Player player, Machine machine) {
        super(player, machine, Tab.COOK);
    }

    @Override
    protected void page() {
        LabRecipe[] recipes = LabRecipe.values();
        for (int i = 0; i < SLOTS && i < recipes.length; i++) {
            set(FIRST + i, recipeIcon(recipes[i]));
        }
        double progress = machine.jobProgress();
        int filled = machine.busy() ? (int) Math.floor(progress * PROGRESS.length + 1e-6) : 0;
        String label = !machine.busy() ? "<gray>Click a recipe to cook"
                : machine.jobDone() ? "<green>Done!"
                : "<yellow>" + Text.time((int) Math.ceil((machine.jobEnd() - System.currentTimeMillis()) / 1000.0)) + " left";
        for (int i = 0; i < PROGRESS.length; i++) {
            set(PROGRESS[i], Items.icon(i < filled ? "progress_full" : "progress_empty", label));
        }
        if (machine.busy() && machine.output() != null) {
            ItemStack out = machine.output().clone();
            ItemMeta meta = out.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(0, Text.mm(machine.jobDone() ? "<green><bold>Click to collect!" : "<gray>Cooking..."));
            meta.lore(lore);
            out.setItemMeta(meta);
            set(OUTPUT, Items.glint(out, machine.jobDone()));
        }
    }

    private ItemStack recipeIcon(LabRecipe r) {
        StrainStock.Group g = null;
        for (LabRecipe.Ingredient ing : r.ingredients()) {
            if (ing.strainSource()) {
                g = StrainStock.pick(player, ing.custom(), ing.amount(), pickStrain, pickQuality);
            }
        }
        ItemStack icon;
        if (r.output().strainBound()) {
            Strain s = g != null ? g.strain() : KushCraft.get().strains().getOrDefault(null);
            icon = Items.strainItem(r.output(), s, g != null ? g.quality() : 3, r.amount());
        } else {
            icon = Items.create(r.output(), r.amount());
        }
        List<String> lore = new ArrayList<>();
        boolean all = true;
        for (LabRecipe.Ingredient ing : r.ingredients()) {
            int have = ing.strainSource() ? (g == null ? maxGroup(ing.custom()) : g.count())
                    : InventoryUtil.count(player, ing::matches);
            boolean ok = have >= ing.amount();
            all &= ok;
            lore.add((ok ? "<green>✔ " : "<red>✘ ") + "<white>" + ing.amount() + " " + ing.name()
                    + (ok ? "" : " <dark_gray>(" + ing.where() + ")"));
        }
        lore.add("<dark_gray>⌚ " + Text.time((int) Math.round(r.seconds() * timeFactor(machine)))
                + " · Shift: up to " + MAX_BATCHES + " batches");
        boolean ready = all && !machine.busy();
        ItemMeta meta = icon.getItemMeta();
        meta.itemName(Text.mm((ready ? "<green>" : "<white>") + r.output().display() + " <gray>x" + r.amount()));
        meta.lore(Text.lines(lore));
        icon.setItemMeta(meta);
        return Items.glint(icon, ready);
    }

    private int maxGroup(ItemType type) {
        int max = 0;
        for (StrainStock.Group g : StrainStock.groups(player, type)) {
            max = Math.max(max, g.count());
        }
        return max;
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        if (slot == OUTPUT) {
            collect();
            return;
        }
        int i = slot - FIRST;
        if (i >= 0 && i < SLOTS && i < LabRecipe.values().length) {
            start(LabRecipe.values()[i], click.isShiftClick() ? MAX_BATCHES : 1);
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
        player.playSound(player.getLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 0.8f, 1f);
        render();
    }

    private void start(LabRecipe r, int wanted) {
        if (machine.busy()) {
            if (machine.jobDone()) {
                collect(); // one click: collect the old batch, then start the new one
                if (machine.busy()) {
                    return;
                }
            } else {
                player.sendActionBar(Text.mm("<red>The lab is still cooking."));
                failSound();
                return;
            }
        }
        Cooking.Result res = Cooking.start(player.getInventory(), machine, r, wanted, pickStrain, pickQuality,
                left -> InventoryUtil.give(player, left));
        if (!res.ok()) {
            player.sendActionBar(Text.mm("<red>" + res.error()));
            failSound();
            return;
        }
        if (res.bonus() > 0) {
            player.sendActionBar(Text.mm("<green>Lab bonus: +" + res.bonus() + " " + r.output().display()));
        } else if (res.batches() > 1) {
            player.sendActionBar(Text.mm("<green>Cooking " + res.batches() + " batches at once."));
        }
        KushCraft.get().awards().cooked(player, r);
        successSound();
        player.playSound(player.getLocation(), "minecraft:block.brewing_stand.brew", SoundCategory.BLOCKS, 1f, 1f);
        render();
    }

    /** Clicking a strain item below picks which strain the weed recipes use. */
    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        ItemType t = Items.type(item);
        if (t != null && t.strainBound() && Items.strain(item) != null) {
            pickStrain = Items.strain(item).id();
            pickQuality = Items.quality(item);
            clickSound();
            render();
        }
    }

    @Override
    public void tick() {
        if (machine.busy()) {
            render();
        }
    }
}
