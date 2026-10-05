package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.shop.Market;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Daily orders: hand in a batch for bonus cash. Layout: tools/gui.py orders(). */
public final class OrdersMenu extends Menu {

    private static final int[] SLOTS = {11, 13, 15};
    private static final int BACK = 18;
    private static final int INFO = 26;

    public OrdersMenu(Player player) {
        super(player, 3, "orders", "Daily Orders");
    }

    @Override
    public void render() {
        inv.clear();
        backButton(BACK);
        Market m = KushCraft.get().market();
        List<Market.Order> orders = m.orders();
        for (int i = 0; i < SLOTS.length; i++) {
            if (i >= orders.size()) {
                set(SLOTS[i], Items.icon("ui_orders", "<gray>No order", "<gray>New orders appear every few minutes."));
                continue;
            }
            Market.Order o = orders.get(i);
            ItemType t = o.type();
            int have = InventoryUtil.count(player, it -> Items.type(it) == t);
            int minutes = (int) Math.max(0, (o.expires() - System.currentTimeMillis()) / 60_000L);
            ItemStack icon = t.strainBound()
                    ? Items.strainItem(t, KushCraft.get().strains().getOrDefault(null), 3, 1) : Items.create(t);
            icon = Items.amount(icon, o.amount());
            boolean ok = have >= o.amount();
            icon.editMeta(meta -> {
                meta.itemName(Text.mm("<gold>Order: <white>" + o.amount() + "x " + t.display()));
                meta.lore(Text.lines(List.of(
                        "<gray>Reward: <gold>" + KushCraft.get().economy().format(o.reward()),
                        "<gray>You have: " + (ok ? "<green>" : "<red>") + have + "/" + o.amount(),
                        t.strainBound() ? "<dark_gray>Any strain and quality counts." : "",
                        "<gray>Expires in " + minutes + " min",
                        "",
                        ok ? "<green><bold>Click to hand in!" : "<red>Not enough yet.")));
            });
            set(SLOTS[i], ok ? Items.glint(icon, true) : icon);
        }
        set(INFO, Items.icon("ui_info", "<aqua>Daily Orders",
                "<gray>Buyers want big batches and pay",
                "<gray>about <gold>75% more</gold> than the market.",
                "<gray>First player to hand one in gets it,",
                "<gray>then a new order shows up."));
    }

    @Override
    public void click(int slot, ClickType click) {
        List<Market.Order> orders = KushCraft.get().market().orders();
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot && i < orders.size()) {
                if (KushCraft.get().market().complete(player, orders.get(i))) {
                    player.playSound(player.getLocation(), "minecraft:ui.toast.challenge_complete", SoundCategory.MASTER,
                            0.6f, 1.3f);
                } else {
                    failSound();
                }
                render();
                return;
            }
        }
    }

    @Override
    public void tick() {
        render();
    }
}
