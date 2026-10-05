package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.Items;
import dev.kushcraft.shop.Economy;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** Richest players. */
public final class LeaderboardMenu extends ListMenu {

    public LeaderboardMenu(Player player) {
        super(player, "Top Dealers");
    }

    @Override
    protected List<ItemStack> entries() {
        List<ItemStack> out = new ArrayList<>();
        int rank = 1;
        for (Economy.Rich r : KushCraft.get().economy().top(PER_PAGE * 3)) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(r.id()));
            String col = rank == 1 ? "gold" : rank == 2 ? "white" : rank == 3 ? "#cd7f32" : "gray";
            meta.itemName(Text.mm("<" + col + ">#" + rank + " <white>" + Text.escape(r.name())
                    + (r.id().equals(player.getUniqueId()) ? " <green>(you)" : "")));
            meta.lore(Text.lines(List.of("<gray>Balance: <gold>" + KushCraft.get().economy().format(r.balance()))));
            head.setItemMeta(meta);
            out.add(head);
            rank++;
        }
        return out;
    }

    @Override
    protected ItemStack header() {
        return Items.icon("ui_trophy", "<gold>Top Dealers",
                "<gray>Your balance: <gold>" + KushCraft.get().economy().format(KushCraft.get().economy().balance(player)),
                "<gray>Sell product and fill orders to climb!");
    }
}
