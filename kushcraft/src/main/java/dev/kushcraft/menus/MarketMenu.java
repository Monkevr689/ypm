package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.economy.Market;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop > Market: the daily contracts (hand in a batch for bonus cash) and
 * which products sell badly right now because everyone is selling them.
 */
public final class MarketMenu extends ListMenu {

    private List<Market.Order> orders = List.of();

    public MarketMenu(Player player) {
        super(player, "Market");
    }

    @Override
    protected List<ItemStack> entries() {
        KushCraft plugin = KushCraft.get();
        boolean inCartel = plugin.cartels().enabled() && plugin.cartels().of(player) != null;
        List<ItemStack> out = new ArrayList<>();
        orders = List.copyOf(plugin.market().orders());
        for (Market.Order o : orders) {
            int have = InventoryUtil.count(player, it -> Items.type(it) == o.type());
            long mins = Math.max(0, (o.expires() - System.currentTimeMillis()) / 60_000L);
            ItemStack it = CatalogIcons.sample(o.type());
            it.setAmount(Math.max(1, Math.min(99, o.amount())));
            boolean ok = have >= o.amount();
            it.editMeta(m -> {
                m.itemName(Text.mm("<yellow>Contract: <white>" + o.amount() + "x " + o.type().display()));
                m.lore(Text.lines(List.of(
                        "<gold>Pays " + TabMenu.money(o.reward()) + (inCartel ? " <dark_gray>+ a cut for your cartel" : ""),
                        (ok ? "<green>" : "<gray>") + "You have " + have + "/" + o.amount() + " <dark_gray>· " + mins + " min left",
                        ok ? "<green>Click: hand it in" : "<dark_gray>Bring the whole batch.")));
            });
            out.add(Items.glint(it, ok));
        }
        for (ItemType t : plugin.market().flooded()) {
            ItemStack it = CatalogIcons.sample(t);
            it.editMeta(m -> {
                m.itemName(Text.mm("<red>Flooded: <white>" + t.display()));
                m.lore(Text.lines(List.of("<gray>Price now: " + plugin.market().trend(t),
                        "<dark_gray>Everyone sold it lately. It climbs back", "<dark_gray>over time - sell something else.")));
            });
            out.add(it);
        }
        return out;
    }

    @Override
    protected ItemStack header() {
        KushCraft plugin = KushCraft.get();
        List<String> lore = new ArrayList<>();
        ItemType hot = plugin.market().hot();
        if (hot != null) {
            lore.add("<gold>Hot: " + hot.display() + " +" + Math.round(
                    (plugin.getConfig().getDouble("market.hot-item-bonus", 1.5) - 1) * 100) + "% <dark_gray>("
                    + plugin.market().hotMinutesLeft() + " min)");
        }
        double boom = plugin.market().boost();
        if (boom > 1.001) {
            lore.add("<light_purple>Market boom: everything +" + Math.round((boom - 1) * 100) + "%");
        }
        lore.add("<gray>Contracts: hand in a batch for extra cash.");
        lore.add("<dark_gray>Selling lots of one thing drops its price.");
        return Items.icon("ui_market", "<yellow>Market", lore);
    }

    @Override
    protected void clickEntry(int index, ClickType click) {
        if (index >= orders.size()) {
            return;
        }
        if (KushCraft.get().market().complete(player, orders.get(index))) {
            successSound();
        } else {
            failSound();
        }
        render();
    }

    @Override
    public void tick() {
        render();
    }
}
