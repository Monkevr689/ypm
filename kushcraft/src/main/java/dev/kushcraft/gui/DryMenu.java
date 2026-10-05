package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.machine.Machine;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * Drug Lab drying shelf: click fresh buds in your inventory to hang them,
 * collect dried buds when the timer is done. Layout: tools/gui.py dry().
 */
public final class DryMenu extends Menu {

    private static final int INPUT = 10;
    private static final int[] PROGRESS = {12, 13, 14};
    private static final int OUTPUT = 16;
    private static final int BACK = 18;
    private static final int INFO = 26;
    private static final int CAPACITY = 64;

    private final Machine machine;

    public DryMenu(Player player, Machine machine) {
        super(player, 3, "dry", "Drug Lab - Dry");
        this.machine = machine;
    }

    @Override
    public void render() {
        inv.clear();
        backButton(BACK);
        int amount = machine.rackAmount();
        Strain s = amount > 0 ? KushCraft.get().strains().get(machine.rackStrain()) : null;
        if (amount > 0 && s == null) {
            s = KushCraft.get().strains().getOrDefault(null);
        }
        if (amount > 0 && !machine.rackDry()) {
            ItemStack in = Items.amount(Items.strainItem(ItemType.BUD_FRESH, s, machine.rackQuality(), 1), amount);
            in.editMeta(m -> m.lore(Text.lines(java.util.List.of("<yellow>Drying...", "<gray>Click more of the same",
                    "<gray>fresh buds to add them."))));
            set(INPUT, in);
        } else {
            set(INPUT, Items.icon("bud_fresh", "<gray>Click <green>Fresh Buds</green> in your inventory",
                    "<gray>to hang them up (max " + CAPACITY + ")."));
        }
        double progress = 0;
        int left = 0;
        if (amount > 0) {
            long minutes = Math.max(0, KushCraft.get().getConfig().getLong("drying.minutes", 3));
            long total = Math.max(1, minutes * 60_000L);
            long remaining = Math.max(0, machine.rackDone() - System.currentTimeMillis());
            progress = 1 - remaining / (double) total;
            left = (int) Math.ceil(remaining / 1000.0);
        }
        int filled = amount > 0 ? (int) Math.floor(Math.max(0, Math.min(1, progress)) * PROGRESS.length + 1e-6) : 0;
        for (int i = 0; i < PROGRESS.length; i++) {
            set(PROGRESS[i], Items.icon(i < filled ? "progress_full" : "progress_empty",
                    amount == 0 ? "<gray>Empty" : machine.rackDry() ? "<green>Done!" : "<yellow>" + Text.time(left) + " left"));
        }
        if (amount > 0 && machine.rackDry()) {
            ItemStack out = Items.amount(Items.strainItem(ItemType.BUD_DRIED, s, machine.rackQuality(), 1), amount);
            out.editMeta(m -> m.lore(Text.lines(java.util.List.of("<green><bold>Click to collect!"))));
            set(OUTPUT, Items.glint(out, true));
        }
        long minutes = KushCraft.get().getConfig().getLong("drying.minutes", 3);
        set(INFO, Items.icon("ui_info", "<aqua>Drying",
                "<gray>Fresh buds need " + minutes + " min to dry.",
                "<gray>Dried buds can be rolled, smoked,",
                "<gray>cooked and sell for much more.",
                "",
                "<gray>One strain at a time."));
    }

    @Override
    public void click(int slot, ClickType click) {
        if (slot != OUTPUT || machine.rackAmount() <= 0 || !machine.rackDry()) {
            return;
        }
        Strain s = KushCraft.get().strains().getOrDefault(machine.rackStrain());
        InventoryUtil.give(player, Items.strainItem(ItemType.BUD_DRIED, s, machine.rackQuality(), machine.rackAmount()));
        player.sendActionBar(Text.mm("<green>Collected " + machine.rackAmount() + "x dried " + s.colored()));
        machine.emptyRack();
        KushCraft.get().machines().markDirty();
        player.playSound(player.getLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 0.8f, 1f);
        render();
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (Items.type(item) != ItemType.BUD_FRESH || Items.strain(item) == null) {
            return;
        }
        Strain s = Items.strain(item);
        int q = Items.quality(item);
        if (machine.rackAmount() > 0 && machine.rackDry()) {
            player.sendActionBar(Text.mm("<yellow>Collect the dried buds first."));
            failSound();
            return;
        }
        if (machine.rackAmount() > 0 && (!s.id().equals(machine.rackStrain()) || q != machine.rackQuality())) {
            player.sendActionBar(Text.mm("<red>Already drying a different strain - wait until it's done."));
            failSound();
            return;
        }
        int add = Math.min(CAPACITY - machine.rackAmount(), item.getAmount());
        if (add <= 0) {
            player.sendActionBar(Text.mm("<red>The shelf is full (" + CAPACITY + " buds)."));
            failSound();
            return;
        }
        ItemStack inSlot = player.getInventory().getItem(slot);
        if (inSlot == null || !inSlot.isSimilar(item)) {
            return;
        }
        inSlot.setAmount(inSlot.getAmount() - add);
        player.getInventory().setItem(slot, inSlot.getAmount() <= 0 ? null : inSlot);
        long minutes = Math.max(0, KushCraft.get().getConfig().getLong("drying.minutes", 3));
        // adding more buds restarts the timer for the whole batch
        machine.fillRack(s.id(), q, machine.rackAmount() + add, System.currentTimeMillis() + minutes * 60_000L);
        KushCraft.get().machines().markDirty();
        player.playSound(player.getLocation(), "minecraft:block.azalea_leaves.place", SoundCategory.BLOCKS, 1f, 1f);
        render();
    }

    @Override
    public void tick() {
        if (machine.rackAmount() > 0) {
            render();
        }
    }
}
