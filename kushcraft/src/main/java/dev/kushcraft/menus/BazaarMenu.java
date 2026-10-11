package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.economy.Economy;
import dev.kushcraft.items.Items;
import dev.kushcraft.trade.Bazaar;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Bazaar: everything other players have for sale (newest first) or your own listings. Click a
 * listing twice to buy it (the first click asks you to confirm). To sell, press "List an item", click
 * an item in your inventory and type the price.
 */
public final class BazaarMenu extends ListMenu {

    private static final int MODE = 51;
    private static final int HELP = 52;

    private boolean mine;
    private boolean listing;
    private List<Long> shown = List.of();
    private long pendingBuy;
    private long pendingUntil;

    public BazaarMenu(Player player) {
        super(player, "Bazaar");
    }

    public BazaarMenu(Player player, boolean mine) {
        this(player);
        this.mine = mine;
    }

    private static KushCraft plugin() {
        return KushCraft.get();
    }

    private String money(long cents) {
        return plugin().economy().format(Economy.dollars(cents));
    }

    @Override
    protected List<ItemStack> entries() {
        Bazaar bz = plugin().bazaar();
        List<Bazaar.Listing> list = mine ? bz.of(player.getUniqueId()) : bz.active();
        List<ItemStack> out = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Bazaar.Listing l : list) {
            ItemStack it = l.item();
            List<Component> lore = it.lore() == null ? new ArrayList<>() : new ArrayList<>(it.lore());
            lore.add(Component.empty());
            int n = Math.max(1, it.getAmount());
            lore.add(Text.mm("<gold>" + money(l.price()) + (n > 1 ? " <dark_gray>(" + money(l.price() / n) + " each)" : "")));
            boolean yours = l.seller().equals(player.getUniqueId());
            lore.add(Text.mm("<gray>Seller: <white>" + Text.escape(l.sellerName())));
            if (l.active()) {
                lore.add(Text.mm("<dark_gray>Runs out in " + Text.duration(Math.max(0, l.expires() - now))));
                lore.add(Text.mm(yours ? "<yellow>Your listing - click to take it back"
                        : (pendingBuy == l.id() && now < pendingUntil ? "<green><bold>Click again to buy" : "<yellow>Click to buy")));
            } else {
                lore.add(Text.mm("<red>Ran out - click to take it back"));
            }
            it.lore(lore);
            out.add(it);
            ids.add(l.id());
        }
        shown = ids;
        return out;
    }

    @Override
    protected ItemStack header() {
        Bazaar bz = plugin().bazaar();
        int have = bz.activeCount(player.getUniqueId());
        int max = bz.maxListings(plugin().ranks().of(player.getUniqueId()).number());
        return Items.icon("ui_market", mine ? "<gold>My listings" : "<gold>Bazaar", "<gray>" + bz.count() + " listings in all",
                "<gray>Yours: <white>" + have + "/" + max, "<gray>Sale tax: <white>" + Math.round(bz.saleTax() * 100) + "%");
    }

    @Override
    protected ItemStack action() {
        return Items.glint(Items.icon("ui_plus", "<green>List an item", listing
                ? List.of("<yellow>Now click an item in your inventory.", "<gray>Right-click: just one of them.")
                : List.of("<gray>Sell something to other players.", "<dark_gray>Click, then pick an item")), listing);
    }

    @Override
    public void render() {
        super.render();
        set(MODE, Items.icon(mine ? "ui_market" : "ui_members", mine ? "<gold>Showing: your listings" : "<gold>Showing: everything",
                "<dark_gray>Click to switch"));
        set(HELP, Items.icon("ui_info", "<white>How it works", "<gray>Items you list are kept safe here.",
                "<gray>When someone buys, you get the money",
                "<gray>(minus the tax) even if you're offline.", "<gray>Unsold items come back after "
                        + plugin().getConfig().getInt("bazaar.listing-days", 3) + " days."));
    }

    @Override
    public void click(int slot, ClickType click) {
        if (slot == MODE) {
            mine = !mine;
            page = 0;
            clickSound();
            render();
            return;
        }
        super.click(slot, click);
    }

    @Override
    protected void clickAction(ClickType click) {
        listing = !listing;
        clickSound();
        if (listing) {
            player.sendActionBar(Text.mm("<yellow>Click an item in your inventory to list it."));
        }
        render();
    }

    @Override
    protected void clickEntry(int index, ClickType click) {
        if (index >= shown.size()) {
            return;
        }
        KushCraft plugin = plugin();
        long id = shown.get(index);
        Bazaar bz = plugin.bazaar();
        Bazaar.Listing l = bz.get(id);
        if (l == null) {
            failSound();
            render();
            return;
        }
        if (!plugin.rates().allow(player.getUniqueId(), "bazaar-click", 300L)) {
            return;
        }
        if (l.seller().equals(player.getUniqueId())) {
            var r = bz.takeBack(player.getUniqueId(), id, it -> Bazaar.fits(player, it));
            if (!r.ok()) {
                failSound();
                player.sendActionBar(Text.mm("<red>" + r.error()));
            } else {
                InventoryUtil.give(player, r.value());
                successSound();
                player.sendActionBar(Text.mm("<green>Took back " + Text.escape(Bazaar.describe(r.value()))));
            }
            render();
            return;
        }
        long now = System.currentTimeMillis();
        if (pendingBuy != id || now > pendingUntil) {
            pendingBuy = id;
            pendingUntil = now + 6_000L;
            clickSound();
            player.sendActionBar(Text.mm("<yellow>Click again within 6 seconds to buy for <gold>" + money(l.price())));
            render();
            return;
        }
        pendingBuy = 0;
        var r = bz.buy(player.getUniqueId(), player.getName(), id, it -> Bazaar.fits(player, it));
        if (!r.ok()) {
            failSound();
            player.sendActionBar(Text.mm("<red>" + r.error()));
        } else {
            InventoryUtil.give(player, r.value().item());
            successSound();
            player.sendMessage(Text.msg("<green>Bought</green> <white>" + Text.escape(Bazaar.describe(r.value().item()))
                    + "</white> <gray>for <gold>" + money(r.value().price())));
        }
        render();
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (!listing || item == null || item.getType().isAir()) {
            return;
        }
        KushCraft plugin = plugin();
        if (!plugin.bazaar().enabled()) {
            return;
        }
        int n = click.isRightClick() ? 1 : item.getAmount();
        ItemStack chosen = item.clone();
        chosen.setAmount(n);
        double dealer = plugin.shop().sellPrice(item) * n;
        String hint = dealer > 0 ? " <dark_gray>(the dealer pays about " + plugin.economy().format(dealer) + ")" : "";
        ChatInput.ask(player, "<gold>Price for <white>" + Text.escape(Bazaar.describe(chosen)) + "</white>?" + hint
                        + " <gray>Type e.g. <white>5000</white>, <white>1.5k</white> or <white>2m</white>.",
                text -> finish(slot, chosen, text),
                () -> new BazaarMenu(player, true).open());
    }

    private void finish(int slot, ItemStack chosen, String text) {
        KushCraft plugin = plugin();
        long price = Bazaar.parsePrice(text);
        if (price < 0) {
            player.sendMessage(Text.msg("<red>That's not a price."));
            new BazaarMenu(player, true).open();
            return;
        }
        if (!plugin.rates().allow(player.getUniqueId(), "bazaar-list", 1_000L)) {
            player.sendMessage(Text.msg("<red>Slow down a little."));
            return;
        }
        ItemStack now = player.getInventory().getItem(slot);
        if (now == null || !now.isSimilar(chosen) || now.getAmount() < chosen.getAmount()) {
            player.sendMessage(Text.msg("<red>That item moved, so nothing was listed."));
            new BazaarMenu(player, true).open();
            return;
        }
        var r = plugin.bazaar().list(player.getUniqueId(), player.getName(), plugin.ranks().of(player.getUniqueId()).number(),
                chosen, price);
        if (!r.ok()) {
            player.sendMessage(Text.msg("<red>" + r.error()));
            new BazaarMenu(player, true).open();
            return;
        }
        if (now.getAmount() > chosen.getAmount()) {
            now.setAmount(now.getAmount() - chosen.getAmount());
            player.getInventory().setItem(slot, now);
        } else {
            player.getInventory().setItem(slot, null);
        }
        plugin.persistence().took(player);
        player.sendMessage(Text.msg("<green>Listed</green> <white>" + Text.escape(Bazaar.describe(chosen)) + "</white> <gray>for <gold>"
                + money(price)));
        new BazaarMenu(player, true).open();
    }
}
