package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.machine.Machine;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.StrainStock;
import dev.kushcraft.util.Text;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Drug Lab > Roll: pick a bud, roll joints or blunts. Layout matches tools/gui.py roll(). */
public final class RollerMenu extends LabTabMenu {

    static final int BUD = at(2, 1);
    static final int JOINT = at(2, 3);
    static final int BLUNT = at(2, 5);
    static final int ALL = at(2, 7);

    private String pickStrain;
    private int pickQuality;

    public RollerMenu(Player player, Machine machine) {
        super(player, machine, Tab.ROLL);
    }

    private StrainStock.Group selected(int needed) {
        return StrainStock.pick(player, ItemType.BUD_DRIED, needed, pickStrain, pickQuality);
    }

    @Override
    protected void page() {
        StrainStock.Group g = selected(1);
        if (g != null && (pickStrain == null || !g.is(pickStrain, pickQuality))) {
            pickStrain = g.strain().id();
            pickQuality = g.quality();
        }
        if (g == null) {
            set(BUD, Items.icon("bud_dried", "<gray>No dried buds", "<dark_gray>Dry fresh buds first (Dry tab)."));
        } else {
            ItemStack show = Items.amount(Items.strainItem(ItemType.BUD_DRIED, g.strain(), g.quality(), 1), g.count());
            show.editMeta(m -> m.lore(Text.lines(List.of("<dark_gray>Click a bud below to switch."))));
            set(BUD, show);
        }
        int papers = InventoryUtil.count(player, it -> Items.type(it) == ItemType.ROLLING_PAPERS);
        int wraps = InventoryUtil.count(player, it -> Items.type(it) == ItemType.BLUNT_WRAP);
        int buds = g == null ? 0 : g.count();
        Strain s = g == null ? KushCraft.get().strains().getOrDefault(null) : g.strain();
        int q = g == null ? 3 : g.quality();
        set(JOINT, rollIcon(ItemType.JOINT, s, q, buds >= 1 && papers >= 1,
                "1 Dried Bud + 1 Rolling Papers", papers + " papers"));
        set(BLUNT, rollIcon(ItemType.BLUNT, s, q, buds >= 2 && wraps >= 1,
                "2 Dried Bud + 1 Blunt Wrap", wraps + " wraps"));
        int canAll = Math.min(buds, papers);
        set(ALL, Items.glint(Items.icon("rolling_papers", canAll > 0 ? "<green>Roll all <white>(" + canAll + ")"
                : "<gray>Roll all", "<dark_gray>Every bud you have papers for."), canAll > 0));
    }

    private ItemStack rollIcon(ItemType type, Strain s, int q, boolean ok, String needs, String have) {
        ItemStack it = Items.strainItem(type, s, q, 1);
        it.editMeta(m -> {
            m.itemName(Text.mm((ok ? "<green>" : "<white>") + "Roll a " + type.display()));
            m.lore(Text.lines(List.of((ok ? "<green>✔ " : "<red>✘ ") + "<white>" + needs,
                    "<dark_gray>You have " + have + " · Shift: roll 8")));
        });
        return Items.glint(it, ok);
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        int times = click.isShiftClick() ? 8 : 1;
        if (slot == JOINT) {
            roll(ItemType.JOINT, 1, ItemType.ROLLING_PAPERS, times);
        } else if (slot == BLUNT) {
            roll(ItemType.BLUNT, 2, ItemType.BLUNT_WRAP, times);
        } else if (slot == ALL) {
            roll(ItemType.JOINT, 1, ItemType.ROLLING_PAPERS, 9999);
        }
    }

    private void roll(ItemType product, int budsEach, ItemType wrap, int times) {
        StrainStock.Group g = selected(budsEach);
        if (g == null) {
            player.sendActionBar(Text.mm("<red>You need " + budsEach + " Dried Bud of one strain."));
            failSound();
            return;
        }
        int wraps = InventoryUtil.count(player, it -> Items.type(it) == wrap);
        int n = Math.min(times, Math.min(g.count() / budsEach, wraps));
        if (n <= 0) {
            player.sendActionBar(Text.mm("<red>You need " + wrap.display() + "."));
            failSound();
            return;
        }
        StrainStock.take(player, ItemType.BUD_DRIED, g, n * budsEach);
        InventoryUtil.remove(player, it -> Items.type(it) == wrap, n);
        InventoryUtil.give(player, Items.strainItem(product, g.strain(), g.quality(), n));
        KushCraft.get().awards().rolled(player, product);
        player.playSound(player.getLocation(), "minecraft:item.book.page_turn", SoundCategory.PLAYERS, 1f, 1.3f);
        player.sendActionBar(Text.mm("<green>Rolled " + n + "x " + g.strain().colored() + " <white>" + product.display()));
        render();
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (Items.type(item) == ItemType.BUD_DRIED && Items.strain(item) != null) {
            pickStrain = Items.strain(item).id();
            pickQuality = Items.quality(item);
            clickSound();
            render();
        }
    }
}
