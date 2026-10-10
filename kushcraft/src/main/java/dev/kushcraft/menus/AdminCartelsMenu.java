package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.cartels.Cartel;
import dev.kushcraft.cartels.Cartels;
import dev.kushcraft.items.Items;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Admin: every cartel. Click: +1 level, right-click: +$10,000 bank, shift-click twice: disband. */
public final class AdminCartelsMenu extends ListMenu {

    private List<Cartel> shown = List.of();
    private Cartel confirm;

    public AdminCartelsMenu(Player player) {
        super(player, "Cartels (admin)");
    }

    @Override
    protected List<ItemStack> entries() {
        Cartels cs = KushCraft.get().cartels();
        List<Cartel> list = cs.top();
        List<ItemStack> out = new ArrayList<>();
        for (Cartel c : list) {
            out.add(Items.tintedIcon("cartel_banner", c.color(), c.colored(), List.of(
                    "<gold>" + cs.tier(c).name() + " <dark_gray>· <gray>" + c.members().size() + " members",
                    "<gold>" + KushCraft.get().economy().format(c.bank()) + " <dark_gray>in the bank",
                    "<dark_gray>Click: +1 level · Right-click: +$10,000 bank",
                    c == confirm ? "<red><bold>Shift-click again to disband" : "<dark_gray>Shift-click twice: disband")));
        }
        shown = list;
        return out;
    }

    @Override
    protected ItemStack header() {
        return Items.icon("tab_cartel", "<red>Cartels", "<gray>" + shown.size() + " cartels");
    }

    @Override
    protected void clickEntry(int index, ClickType click) {
        if (!player.hasPermission("kushcraft.admin") || index >= shown.size()) {
            return;
        }
        Cartels cs = KushCraft.get().cartels();
        Cartel c = shown.get(index);
        if (click.isShiftClick()) {
            if (confirm == c) {
                cs.disband(c);
                confirm = null;
                player.sendActionBar(Text.mm("<red>Disbanded " + c.name()));
            } else {
                confirm = c;
            }
        } else if (click.isRightClick()) {
            cs.addBank(c, 10000);
            confirm = null;
        } else {
            cs.level(c, c.level() + 1);
            confirm = null;
        }
        successSound();
        render();
    }
}
