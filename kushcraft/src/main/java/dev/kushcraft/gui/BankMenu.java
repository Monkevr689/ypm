package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.Items;
import dev.kushcraft.shop.Economy;
import dev.kushcraft.shop.Ranks;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Bank: your balance, send money, every rank and the top dealers.
 * Layout matches tools/gui.py bank().
 */
public final class BankMenu extends TabMenu {

    private static final int BALANCE = at(1, 1);
    private static final int SEND = at(1, 4);
    private static final int RANK = at(1, 7);
    private static final int FIRST_TOP = at(4, 0);
    private static final int TOP = 18;

    public BankMenu(Player player) {
        super(player, Tab.BANK);
    }

    @Override
    protected void page() {
        KushCraft plugin = KushCraft.get();
        Economy eco = plugin.economy();
        set(BALANCE, Items.icon("ui_wallet", "<gold>Balance: <white>" + money(eco.balance(player)),
                eco.usingVault() ? "<dark_gray>Vault economy" : "<dark_gray>KushCraft wallet",
                "<gray>Sold so far: <gold>" + money(eco.sales(player))));
        set(SEND, Items.icon("ui_pay", "<gold><bold>Send money",
                "<gray>Pick an online player and", "<gray>type the amount.",
                "", "<dark_gray>or /kush pay \\<player> \\<amount>"));
        Ranks ranks = plugin.ranks();
        Ranks.Rank mine = ranks.of(player);
        List<String> rl = new ArrayList<>();
        for (Ranks.Rank r : ranks.all()) {
            rl.add((r.level() == mine.level() ? "<white>▶ " : "<dark_gray>  ") + r.colored() + " <gray>" + money(r.sales())
                    + " sold <green>+" + Math.round(r.bonus() * 100) + "%");
        }
        rl.add("");
        rl.add("<dark_gray>Ranks unlock Drug Lab recipes and");
        rl.add("<dark_gray>pay a bonus on every sale.");
        set(RANK, Items.icon("ui_crown", "<gold>Dealer ranks", rl));
        int place = 1;
        for (Economy.Rich r : eco.topSales(TOP)) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(r.id()));
            String col = place == 1 ? "gold" : place == 2 ? "white" : place == 3 ? "#cd7f32" : "gray";
            meta.itemName(Text.mm("<" + col + ">#" + place + " <white>" + Text.escape(r.name())
                    + (r.id().equals(player.getUniqueId()) ? " <green>(you)" : "")));
            meta.lore(Text.lines(List.of("<gray>Sold: <gold>" + money(r.balance()),
                    "<gray>Rank: " + ranks.of(r.balance()).colored())));
            head.setItemMeta(meta);
            set(FIRST_TOP + place - 1, head);
            place++;
        }
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        if (slot == SEND) {
            openChild(new PayMenu(player));
        }
    }
}
