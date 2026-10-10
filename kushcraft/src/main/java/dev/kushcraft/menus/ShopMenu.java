package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.economy.Shop;
import dev.kushcraft.strains.Strain;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop: seeds of every strain (cheapest first) and the other seeds in rows
 * 1-4, 36 a page, the Mythic seeds on pages of their own at the end; the
 * bottom row has the page arrows,
 * Gear &amp; Workers, Sell all (or click product below to sell it) and the
 * market news. Layout: tools/gui.py shop().
 */
public final class ShopMenu extends TabMenu {

    static final int FIRST_SEED = 9;
    static final int SEEDS = 36;
    static final int GEAR = at(5, 1);
    static final int SELL_ALL = at(5, 4);
    static final int MARKET = at(5, 7);
    static final int PREV = at(5, 0);
    static final int NEXT = at(5, 8);

    private final boolean atDealer;
    private int ticks;
    private int page;

    public ShopMenu(Player player) {
        this(player, false);
    }

    /** atDealer: opened from a Dealer Stand (needed when market.anywhere is false). */
    public ShopMenu(Player player, boolean atDealer) {
        super(player, Tab.SHOP);
        this.atDealer = atDealer;
    }

    private boolean allowed() {
        return atDealer || player.hasPermission("kushcraft.admin")
                || KushCraft.get().getConfig().getBoolean("market.anywhere", true);
    }

    private static Shop shop() {
        return KushCraft.get().shop();
    }

    @Override
    protected void page() {
        KushCraft plugin = KushCraft.get();
        if (!allowed()) {
            set(at(2, 4), Items.icon("machine_dealer", "<yellow>Find a Dealer Stand",
                    "<gray>This server only trades at a Dealer Stand."));
            return;
        }
        double bal = plugin.economy().balance(player);
        List<Shop.BuyEntry> seeds = laidOut();
        page = Math.min(page, pages() - 1);
        for (int i = 0; i < SEEDS && page * SEEDS + i < seeds.size(); i++) {
            Shop.BuyEntry e = seeds.get(page * SEEDS + i);
            if (e != null) {
                set(FIRST_SEED + i, entryIcon(e, bal));
            }
        }
        if (page > 0) {
            set(PREV, Items.icon("ui_arrow", "<gray>Page " + page + "/" + pages()));
        }
        if (page < pages() - 1) {
            boolean mythicNext = (page + 1) * SEEDS < seeds.size() && isMythic(seeds.get((page + 1) * SEEDS));
            set(NEXT, Items.icon("ui_arrow", mythicNext
                            ? "<light_purple><bold>Mythic seeds</bold> <gray>(page " + (page + 2) + "/" + pages() + ")"
                            : "<white>More seeds <gray>(page " + (page + 2) + "/" + pages() + ")",
                    mythicNext ? "<gray>Glowing, sparkling plants that sell for 4x." : "<dark_gray>Click"));
        }
        set(GEAR, Items.icon("ui_gear", "<aqua><bold>Gear & Workers",
                "<gray>Papers, solvent, lamps, the Drug Lab...", "<gray>and workers who farm for you."));
        set(MARKET, marketIcon());
        double value = Selling.allValue(player);
        List<String> sell = new ArrayList<>();
        sell.add("<gray>Or click product below to sell it.");
        ItemType hot = plugin.market().hot();
        if (hot != null) {
            sell.add("<gold>Hot: " + hot.display() + " +" + Math.round(
                    (plugin.getConfig().getDouble("market.hot-item-bonus", 1.5) - 1) * 100) + "%");
        }
        double bonus = shop().bonus(player) - 1;
        if (bonus > 0.001) {
            sell.add("<green>Your bonus: +" + Math.round(bonus * 100) + "%");
        }
        set(SELL_ALL, Items.glint(Items.icon("ui_sell", value > 0 ? "<green><bold>Sell all</bold> <gold>" + money(value)
                : "<gray>Sell all", sell), value > 0));
    }

    /** Hot item, contracts and what is flooded: click for the Market page. */
    private ItemStack marketIcon() {
        KushCraft plugin = KushCraft.get();
        List<String> lore = new ArrayList<>();
        ItemType hot = plugin.market().hot();
        if (hot != null) {
            lore.add("<gold>Hot: " + hot.display() + " " + plugin.market().trend(hot));
        }
        double boom = plugin.market().boost();
        if (boom > 1.001) {
            lore.add("<light_purple>Market boom: +" + Math.round((boom - 1) * 100) + "% <dark_gray>("
                    + plugin.market().boostMinutesLeft() + " min)");
        }
        lore.add("<gray>" + plugin.market().orders().size() + " contracts open");
        lore.add("<dark_gray>Click: contracts & flooded products");
        return Items.icon("ui_market", "<yellow>Market", lore);
    }

    static ItemStack entryIcon(Shop.BuyEntry e, double bal) {
        ItemStack show = shop().create(e);
        ItemMeta meta = show.getItemMeta();
        List<String> lore = new ArrayList<>();
        Strain s = e.strain() == null ? null : KushCraft.get().strains().get(e.strain());
        if (s != null) {
            lore.add(s.rarity().colored() + " <dark_gray>·</dark_gray> <white>" + s.potency() + "% THC"
                    + " <dark_gray>·</dark_gray> " + s.type().colored());
            lore.add(Items.climateLine(s));
        } else if (!e.type().lore().isEmpty()) {
            lore.add(e.type().lore().get(0));
        }
        String price = e.price() <= 0 ? "FREE" : money(e.price()) + (e.amount() > 1 ? " for " + e.amount() : "");
        lore.add((bal >= e.price() ? "<gold>" : "<red>") + price + " <dark_gray>· Shift: buy 5");
        meta.lore(Text.lines(lore));
        show.setItemMeta(meta);
        return show;
    }

    private static boolean isMythic(Shop.BuyEntry e) {
        Strain s = e == null || e.strain() == null ? null : KushCraft.get().strains().get(e.strain());
        return s != null && s.rarity().animated();
    }

    /** The seeds page by page: the Mythic ones (last in the list) start on a page of their own (gaps are null). */
    private static List<Shop.BuyEntry> laidOut() {
        List<Shop.BuyEntry> out = new ArrayList<>();
        for (Shop.BuyEntry e : shop().seeds()) {
            if (isMythic(e) && !out.isEmpty() && !isMythic(out.get(out.size() - 1))) {
                while (out.size() % SEEDS != 0) {
                    out.add(null);
                }
            }
            out.add(e);
        }
        return out;
    }

    private static int pages() {
        return Math.max(1, (laidOut().size() + SEEDS - 1) / SEEDS);
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        if (!allowed()) {
            return;
        }
        if ((slot == PREV && page > 0) || (slot == NEXT && page < pages() - 1)) {
            page += slot == NEXT ? 1 : -1;
            clickSound();
            render();
            return;
        }
        if (slot == SELL_ALL) {
            if (Selling.all(player)) {
                render();
            } else {
                failSound();
            }
            return;
        }
        if (slot == GEAR) {
            clickSound();
            new GearMenu(player).open();
            return;
        }
        if (slot == MARKET) {
            openChild(new MarketMenu(player));
            return;
        }
        int idx = slot - FIRST_SEED;
        List<Shop.BuyEntry> seeds = laidOut();
        if (idx >= 0 && idx < SEEDS && page * SEEDS + idx < seeds.size() && seeds.get(page * SEEDS + idx) != null) {
            buy(player, seeds.get(page * SEEDS + idx), click.isShiftClick() ? 5 : 1);
            render();
        }
    }

    /** Buys an entry {@code times} times (as long as the money lasts). */
    static void buy(Player player, Shop.BuyEntry e, int times) {
        KushCraft plugin = KushCraft.get();
        int bought = 0;
        for (int i = 0; i < times; i++) {
            if (!plugin.economy().withdraw(player, e.price())) {
                break;
            }
            InventoryUtil.give(player, plugin.shop().create(e));
            bought++;
        }
        if (bought == 0) {
            player.sendActionBar(Text.mm("<red>You need " + money(e.price())));
            player.playSound(player.getLocation(), "minecraft:block.note_block.bass", SoundCategory.MASTER, 0.7f, 0.6f);
        } else {
            player.playSound(player.getLocation(), "minecraft:entity.villager.yes", SoundCategory.PLAYERS, 0.7f, 1.1f);
            player.sendActionBar(Text.mm("<green>Bought " + (bought * e.amount()) + "x " + e.type().display()
                    + " <gray>for <gold>" + money(bought * e.price())));
        }
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (!allowed()) {
            return;
        }
        if (Selling.clicked(player, slot, item, click)) {
            render();
        } else if (Items.isCustom(item)) {
            player.sendActionBar(Text.mm("<gray>The Shop doesn't buy that."));
        }
    }

    @Override
    public void tick() {
        // the hot item and prices change while the menu is open
        if (++ticks % 15 == 0) {
            render();
        }
    }
}
