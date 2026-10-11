package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.Items;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Who do you want to trade with? Everyone online; click a head to send a request. */
public final class TradePickMenu extends ListMenu {

    private List<UUID> shown = List.of();

    public TradePickMenu(Player player) {
        super(player, "Trade with a player");
    }

    @Override
    protected List<ItemStack> entries() {
        List<ItemStack> out = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            ids.add(o.getUniqueId());
            var rank = KushCraft.get().ranks().of(o.getUniqueId());
            out.add(AdminPlayersMenu.head(o, "<white>" + Text.escape(o.getName()), List.of(
                    "<gray>Rank: " + rank.colored(), KushCraft.get().trades().of(o) != null
                            ? "<red>In another trade" : "<dark_gray>Click: ask to trade")));
        }
        shown = ids;
        return out;
    }

    @Override
    protected ItemStack header() {
        return Items.icon("ui_exchange", "<aqua>Trade with a player", "<gray>Swap items and money safely.",
                "<gray>Nothing changes hands until", "<gray>you both press Ready.");
    }

    @Override
    protected void clickEntry(int index, ClickType click) {
        if (index >= shown.size()) {
            return;
        }
        Player target = Bukkit.getPlayer(shown.get(index));
        String problem = KushCraft.get().trades().request(player, target);
        player.closeInventory();
        if (problem != null) {
            player.sendMessage(Text.msg("<red>" + problem));
        }
    }
}
