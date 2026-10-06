package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.shop.Economy;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin: online players first, then everyone with money or sales. Click one to manage them. */
public final class AdminPlayersMenu extends ListMenu {

    private List<UUID> shown = List.of();

    public AdminPlayersMenu(Player player) {
        super(player, "Players (admin)");
    }

    @Override
    protected List<ItemStack> entries() {
        KushCraft plugin = KushCraft.get();
        Map<UUID, Boolean> ids = new LinkedHashMap<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            ids.put(o.getUniqueId(), true);
        }
        for (Economy.Rich r : plugin.economy().top(200)) {
            ids.putIfAbsent(r.id(), false);
        }
        for (Economy.Rich r : plugin.economy().topSales()) {
            ids.putIfAbsent(r.id(), false);
        }
        List<ItemStack> out = new ArrayList<>();
        List<UUID> list = new ArrayList<>();
        for (Map.Entry<UUID, Boolean> e : ids.entrySet()) {
            OfflinePlayer o = Bukkit.getOfflinePlayer(e.getKey());
            list.add(e.getKey());
            out.add(head(o, (e.getValue() ? "<green>● " : "<gray>") + Text.escape(String.valueOf(o.getName())), List.of(
                    "<gold>" + plugin.economy().format(plugin.economy().balance(o)) + " <dark_gray>money",
                    "<gold>" + plugin.economy().format(plugin.economy().sales(o)) + " <dark_gray>sold · "
                            + plugin.ranks().label(o),
                    "<gray>" + plugin.workers().of(o.getUniqueId()).size() + " workers · "
                            + plugin.awards().count(o) + " awards",
                    "<dark_gray>Click to manage")));
        }
        shown = list;
        return out;
    }

    static ItemStack head(OfflinePlayer p, String name, List<String> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        it.editMeta(SkullMeta.class, m -> {
            m.setOwningPlayer(p);
            m.itemName(Text.mm(name));
            m.lore(Text.lines(lore));
        });
        return it;
    }

    @Override
    protected ItemStack header() {
        return dev.kushcraft.item.Items.icon("ui_wallet", "<gold>Players", "<gray>" + shown.size() + " players");
    }

    @Override
    protected void clickEntry(int index, ClickType click) {
        if (index < shown.size()) {
            openChild(new AdminPlayerMenu(player, Bukkit.getOfflinePlayer(shown.get(index))));
        }
    }
}
