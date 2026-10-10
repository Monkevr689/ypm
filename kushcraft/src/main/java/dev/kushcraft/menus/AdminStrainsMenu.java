package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.strains.Strain;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Admin: every strain (bred ones too). Click: seeds, right-click: 16 top-quality dried buds. */
public final class AdminStrainsMenu extends ListMenu {

    private List<Strain> shown = List.of();

    public AdminStrainsMenu(Player player) {
        super(player, "Strains (admin)");
    }

    @Override
    protected List<ItemStack> entries() {
        List<Strain> list = new ArrayList<>(KushCraft.get().strains().all());
        list.sort((a, b) -> a.rarity() != b.rarity() ? a.rarity().compareTo(b.rarity()) : a.name().compareToIgnoreCase(b.name()));
        List<ItemStack> out = new ArrayList<>();
        for (Strain s : list) {
            ItemStack it = Items.strainItem(ItemType.BUD_DRIED, s, 5, 1);
            it.editMeta(m -> {
                List<net.kyori.adventure.text.Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
                lore.add(Text.mm("<dark_gray>Click: 1 seed · Shift: 16 seeds"));
                lore.add(Text.mm("<dark_gray>Right-click: 16 dried buds ★5"));
                m.lore(lore);
            });
            out.add(it);
        }
        shown = list;
        return out;
    }

    @Override
    protected ItemStack header() {
        return Items.icon("seed_pack", "<green>Strains", "<gray>" + shown.size() + " strains");
    }

    @Override
    protected void clickEntry(int index, ClickType click) {
        if (!player.hasPermission("kushcraft.admin") || index >= shown.size()) {
            return;
        }
        Strain s = shown.get(index);
        if (click.isRightClick()) {
            InventoryUtil.give(player, Items.strainItem(ItemType.BUD_DRIED, s, 5, 16));
        } else {
            InventoryUtil.give(player, Items.strainItem(ItemType.SEED_PACK, s, 3, click.isShiftClick() ? 16 : 1));
        }
        successSound();
    }
}
