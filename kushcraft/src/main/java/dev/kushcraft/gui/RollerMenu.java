package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.StrainStock;
import dev.kushcraft.util.Text;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Rolling Table: pick a bud, roll joints or blunts. Layout matches tools/gui.py roller(). */
public final class RollerMenu extends Menu {

    private static final int BUD = 10;
    private static final int JOINT = 12;
    private static final int BLUNT = 14;
    private static final int ALL = 16;
    private static final int INFO = 26;

    private String pickStrain;
    private int pickQuality;

    public RollerMenu(Player player) {
        super(player, 3, "roller", "Rolling Table");
    }

    private StrainStock.Group selected(int needed) {
        return StrainStock.pick(player, ItemType.BUD_DRIED, needed, pickStrain, pickQuality);
    }

    @Override
    public void render() {
        inv.clear();
        StrainStock.Group g = selected(1);
        if (g != null && (pickStrain == null || !g.is(pickStrain, pickQuality))) {
            pickStrain = g.strain().id();
            pickQuality = g.quality();
        }
        if (g == null) {
            set(BUD, Items.icon("bud_dried", "<gray>No dried buds",
                    "<gray>Dry fresh buds on a <yellow>Drying Rack</yellow>,",
                    "<gray>then click one in your inventory."));
        } else {
            ItemStack show = Items.amount(Items.strainItem(ItemType.BUD_DRIED, g.strain(), g.quality(), 1), g.count());
            set(BUD, Items.glint(show, true));
        }
        int papers = InventoryUtil.count(player, it -> Items.type(it) == ItemType.ROLLING_PAPERS);
        int wraps = InventoryUtil.count(player, it -> Items.type(it) == ItemType.BLUNT_WRAP);
        int buds = g == null ? 0 : g.count();
        Strain s = g == null ? KushCraft.get().strains().all().iterator().next() : g.strain();
        int q = g == null ? 3 : g.quality();

        ItemStack joint = Items.strainItem(ItemType.JOINT, s, q, 1);
        joint.editMeta(m -> m.lore(Text.lines(List.of(
                "<gray>Needs <white>1 Dried Bud</white> + <white>1 Rolling Papers",
                "<gray>You have: <white>" + buds + "</white> buds, <white>" + papers + "</white> papers",
                "<gray>" + Items.JOINT_HITS + " hits per joint",
                "",
                "<green>Click: roll 1   <yellow>Shift-click: roll 8"))));
        set(JOINT, joint);

        ItemStack blunt = Items.strainItem(ItemType.BLUNT, s, q, 1);
        blunt.editMeta(m -> m.lore(Text.lines(List.of(
                "<gray>Needs <white>2 Dried Bud</white> + <white>1 Blunt Wrap",
                "<gray>You have: <white>" + buds + "</white> buds, <white>" + wraps + "</white> wraps",
                "<gray>" + Items.BLUNT_HITS + " stronger hits per blunt",
                "",
                "<green>Click: roll 1   <yellow>Shift-click: roll 8"))));
        set(BLUNT, blunt);

        int canAll = Math.min(buds, papers);
        set(ALL, Items.icon("rolling_papers", "<green>Roll all <white>(" + canAll + " joints)",
                "<gray>Rolls every bud of the selected strain",
                "<gray>you have papers for."));
        set(INFO, Items.icon("ui_info", "<aqua>Rolling Table",
                "<gray>Click a <green>Dried Bud</green> in your inventory",
                "<gray>to pick which strain to roll.",
                "",
                "<gray>Rolling Papers: 3 paper + sugar cane",
                "<gray>Blunt Wrap: paper + cocoa + dried kelp",
                "<gray>(crafting table) or buy them from a Dealer."));
    }

    @Override
    public void click(int slot, ClickType click) {
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
