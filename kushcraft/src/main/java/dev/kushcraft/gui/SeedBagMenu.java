package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.Items;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import dev.kushcraft.worker.Worker;
import dev.kushcraft.worker.Workers;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * A Farmhand's seed backpack: 45 slots of seeds (thousands of them) that
 * never fill the satchel. Click seeds to take them, click seeds in your own
 * inventory to put them in; Plant now sends the Farmhand off to plant every
 * empty farmland they can walk to. Layout: tools/gui.py seeds_page().
 */
public final class SeedBagMenu extends Menu {

    static final int ROWS = 6;
    static final int BACK = 45;
    static final int PLANT = 47;
    static final int TAKE_ALL = 49;
    static final int INFO = 53;

    private final Worker worker;

    public SeedBagMenu(Player player, Worker worker) {
        super(player, ROWS, "seeds", "Seed backpack <dark_gray>· <white>" + Text.escape(worker.name()));
        this.worker = worker;
    }

    private static Workers ws() {
        return KushCraft.get().workers();
    }

    @Override
    public void render() {
        inv.clear();
        ItemStack[] items = worker.seedBag().getStorageContents();
        for (int i = 0; i < Worker.SEED_BAG && i < items.length; i++) {
            if (items[i] != null && !items[i].getType().isAir()) {
                set(i, items[i].clone());
            }
        }
        backButton(BACK);
        int seeds = worker.seedCount();
        int empty = ws().emptyFarmland(worker);
        set(PLANT, Items.glint(Items.icon("ui_play", seeds > 0 && empty > 0 ? "<green><bold>Plant now" : "<gray>Plant now",
                "<gray>Empty farmland and Planters they can", "<gray>walk to: <white>" + empty,
                seeds == 0 ? "<yellow>No seeds: give them some (click yours)" : "<gray>They plant before anything else.",
                "<dark_gray>Hoe more land near them for more plants."), seeds > 0 && empty > 0));
        set(TAKE_ALL, Items.glint(Items.icon("ui_take", seeds > 0 ? "<green><bold>Take all</bold> <gray>(" + seeds + " seeds)"
                : "<gray>The backpack is empty", "<dark_gray>Click a stack to take just that."), seeds > 0));
        set(INFO, Items.icon("seed_pack", "<green>Seed backpack <gray>(" + worker.seedSlots() + "/" + Worker.SEED_BAG
                        + " slots)",
                "<gray>Harvested and bought seeds go in here,",
                "<gray>never in the satchel - <white>" + seeds + "<gray> seeds now.",
                "<gray>They plant them on empty farmland first,",
                "<gray>keep 32 of a kind and give spare ones",
                "<gray>to other Farmhands or a Cook mixing.",
                "<gray>Full? Common seeds past 64 of a kind",
                "<gray>turn into fertilizer (Rare ones never).",
                "<dark_gray>Click seeds in your inventory to add them."));
    }

    @Override
    public void click(int slot, ClickType click) {
        if (ws().get(worker.id()) == null) {
            player.closeInventory();
            return;
        }
        if (slot >= 0 && slot < Worker.SEED_BAG) {
            ItemStack it = worker.seedBag().getItem(slot);
            if (it == null || it.getType().isAir()) {
                return;
            }
            InventoryUtil.give(player, it);
            worker.seedBag().setItem(slot, null);
            ws().markDirty();
            player.playSound(player.getLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 0.6f, 1.2f);
        } else if (slot == TAKE_ALL) {
            int n = 0;
            ItemStack[] items = worker.seedBag().getStorageContents();
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null && !items[i].getType().isAir()) {
                    n += items[i].getAmount();
                    InventoryUtil.give(player, items[i]);
                    worker.seedBag().setItem(i, null);
                }
            }
            if (n == 0) {
                failSound();
                return;
            }
            ws().markDirty();
            player.playSound(player.getLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 0.8f, 1f);
        } else if (slot == PLANT) {
            if (worker.seedCount() == 0 || ws().emptyFarmland(worker) == 0) {
                player.sendActionBar(Text.mm(worker.seedCount() == 0 ? "<yellow>Give them seeds first."
                        : "<yellow>No empty farmland near them: hoe some ground first."));
                failSound();
                return;
            }
            ws().hurry(worker);
            successSound();
            player.sendActionBar(Text.mm("<green>" + Text.escape(worker.name()) + " is off to plant."));
        }
        render();
    }

    /** Clicking seeds in your own inventory puts them in the backpack. */
    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (dev.kushcraft.plant.PlantManager.kindOf(Items.type(item)) == null) {
            player.sendActionBar(Text.mm("<gray>Only seeds go in the seed backpack."));
            return;
        }
        ItemStack inSlot = player.getInventory().getItem(slot);
        if (inSlot == null || !inSlot.isSimilar(item)) {
            return;
        }
        List<ItemStack> left = ws().stash(worker, List.of(inSlot.clone()));
        player.getInventory().setItem(slot, left.isEmpty() ? null : left.get(0));
        if (!left.isEmpty() && left.get(0).getAmount() == inSlot.getAmount()) {
            player.sendActionBar(Text.mm("<red>The backpack is full."));
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
