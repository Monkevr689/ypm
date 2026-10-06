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

import java.util.List;

/**
 * Drug Lab > Dry: five racks. Click fresh buds in your inventory to hang
 * them (shift-click hangs all of them), they're dry in about 30 seconds.
 * Clicking a rack collects every dry one. Layout: tools/gui.py dry().
 */
public final class DryMenu extends LabTabMenu {

    static final int[] RACKS = {at(2, 2), at(2, 3), at(2, 4), at(2, 5), at(2, 6)};
    static final int[] GAUGES = {at(3, 2), at(3, 3), at(3, 4), at(3, 5), at(3, 6)};
    /** Steps of the gauge_N icons under each rack. */
    static final int GAUGE_STEPS = 8;
    private static final int CAPACITY = 64;

    public DryMenu(Player player, Machine machine) {
        super(player, machine, Tab.DRY);
    }

    private static Strain strain(Machine.Rack r) {
        return KushCraft.get().strains().getOrDefault(r.strain());
    }

    @Override
    protected void page() {
        for (int i = 0; i < Machine.RACKS; i++) {
            Machine.Rack r = machine.rack(i);
            if (r == null) {
                set(RACKS[i], Items.icon("ui_rack", "<gray>Empty rack", "<dark_gray>Click fresh buds below."));
                set(GAUGES[i], Items.icon("gauge_0", "<dark_gray>Empty"));
                continue;
            }
            boolean dry = r.dry();
            ItemStack buds = Items.amount(Items.strainItem(dry ? ItemType.BUD_DRIED : ItemType.BUD_FRESH, strain(r),
                    r.quality(), 1), r.amount());
            buds.editMeta(m -> m.lore(Text.lines(List.of(dry ? "<green><bold>Dry! Click to collect"
                    : "<yellow>Drying... " + Text.time(r.secondsLeft())))));
            set(RACKS[i], Items.glint(buds, dry));
            int step = dry ? GAUGE_STEPS : (int) Math.floor(r.progress() * GAUGE_STEPS);
            set(GAUGES[i], Items.icon("gauge_" + step, dry ? "<green>Done!" : "<yellow>" + Text.time(r.secondsLeft())));
        }
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        for (int i = 0; i < Machine.RACKS; i++) {
            if (slot == RACKS[i] || slot == GAUGES[i]) {
                collect();
                return;
            }
        }
    }

    /** Collects every dry rack. */
    private void collect() {
        int total = 0;
        for (int i = 0; i < Machine.RACKS; i++) {
            Machine.Rack r = machine.rack(i);
            if (r != null && r.dry()) {
                InventoryUtil.give(player, Items.strainItem(ItemType.BUD_DRIED, strain(r), r.quality(), r.amount()));
                total += r.amount();
                machine.emptyRack(i);
            }
        }
        if (total == 0) {
            player.sendActionBar(Text.mm(machine.racksInUse() > 0 ? "<yellow>Still drying..."
                    : "<gray>Click fresh buds in your inventory to hang them."));
            failSound();
            return;
        }
        KushCraft.get().machines().markDirty();
        KushCraft.get().awards().dried(player);
        player.sendActionBar(Text.mm("<green>Collected " + total + " dried buds"));
        player.playSound(player.getLocation(), "minecraft:entity.item.pickup", SoundCategory.PLAYERS, 0.8f, 1f);
        render();
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (Items.type(item) != ItemType.BUD_FRESH || Items.strain(item) == null) {
            return;
        }
        int hung = 0;
        if (click.isShiftClick()) {
            ItemStack[] inv = player.getInventory().getStorageContents();
            for (int i = 0; i < inv.length; i++) {
                if (Items.type(inv[i]) == ItemType.BUD_FRESH && Items.strain(inv[i]) != null) {
                    hung += hang(i, inv[i]);
                }
            }
        } else {
            hung = hang(slot, item);
        }
        if (hung == 0) {
            player.sendActionBar(Text.mm("<red>All " + Machine.RACKS + " racks are busy."));
            failSound();
            return;
        }
        KushCraft.get().machines().markDirty();
        if (machine.racksInUse() == Machine.RACKS) {
            KushCraft.get().awards().fullRacks(player);
        }
        player.sendActionBar(Text.mm("<green>Hung " + hung + " buds <gray>- dry in "
                + Text.time(KushCraft.get().machines().dryingSeconds())));
        player.playSound(player.getLocation(), "minecraft:block.azalea_leaves.place", SoundCategory.BLOCKS, 1f, 1f);
        render();
    }

    /** Hangs the stack in inventory slot {@code slot} on a free rack (or one with the same buds). */
    private int hang(int slot, ItemStack item) {
        ItemStack inSlot = player.getInventory().getItem(slot);
        if (inSlot == null || !inSlot.isSimilar(item)) {
            return 0;
        }
        Strain s = Items.strain(inSlot);
        int q = Items.quality(inSlot);
        long now = System.currentTimeMillis();
        long done = now + KushCraft.get().machines().dryingSeconds() * 1000L;
        int target = -1;
        for (int i = 0; i < Machine.RACKS && target < 0; i++) {
            Machine.Rack r = machine.rack(i);
            if (r != null && !r.dry() && r.strain().equals(s.id()) && r.quality() == q && r.amount() < CAPACITY) {
                target = i; // top up a rack with the same buds (its timer restarts)
            }
        }
        for (int i = 0; i < Machine.RACKS && target < 0; i++) {
            if (machine.rack(i) == null) {
                target = i;
            }
        }
        if (target < 0) {
            return 0;
        }
        Machine.Rack r = machine.rack(target);
        int have = r == null ? 0 : r.amount();
        int add = Math.min(CAPACITY - have, inSlot.getAmount());
        inSlot.setAmount(inSlot.getAmount() - add);
        player.getInventory().setItem(slot, inSlot.getAmount() <= 0 ? null : inSlot);
        machine.rack(target, new Machine.Rack(s.id(), q, have + add, now, done));
        if (inSlot.getAmount() > 0) {
            return add + hang(slot, inSlot);
        }
        return add;
    }

    @Override
    public void tick() {
        if (machine.racksInUse() > 0) {
            render();
        }
    }
}
