package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.Items;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Send money: click an online player, then type the amount in chat. */
public final class PayMenu extends ListMenu {

    private List<UUID> shown = List.of();

    public PayMenu(Player player) {
        super(player, "Send Money");
    }

    @Override
    protected List<ItemStack> entries() {
        List<ItemStack> out = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(player) || !player.canSee(p)) {
                continue;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(p);
            meta.itemName(Text.mm("<white>" + Text.escape(p.getName())));
            meta.lore(Text.lines(List.of("<yellow>Click to send money")));
            head.setItemMeta(meta);
            out.add(head);
            ids.add(p.getUniqueId());
        }
        shown = ids;
        return out;
    }

    @Override
    protected ItemStack header() {
        return Items.icon("ui_pay", "<gold>Send Money",
                "<gray>Your balance: <gold>" + KushCraft.get().economy().format(KushCraft.get().economy().balance(player)),
                "<gray>Click a player, then type the amount.",
                shown.isEmpty() ? "<dark_gray>Nobody else is online." : "");
    }

    @Override
    protected void clickEntry(int index, ClickType click) {
        if (index >= shown.size()) {
            return;
        }
        Player target = Bukkit.getPlayer(shown.get(index));
        if (target == null) {
            failSound();
            render();
            return;
        }
        clickSound();
        Menu back = this;
        ChatInput.ask(player, "<gray>How much do you want to send to <white>" + Text.escape(target.getName())
                        + "</white>? <dark_gray>(you have " + KushCraft.get().economy().format(
                        KushCraft.get().economy().balance(player)) + ")",
                text -> {
                    send(player, target, text);
                    back.open();
                }, back::open);
    }

    /** Shared by the menu and /kush pay. */
    public static boolean send(Player from, Player to, String amountText) {
        KushCraft plugin = KushCraft.get();
        double amount;
        try {
            amount = Math.round(Double.parseDouble(amountText.replace("$", "").replace(",", "").trim()) * 100) / 100.0;
        } catch (NumberFormatException e) {
            from.sendMessage(Text.msg("<red>That's not a number."));
            return false;
        }
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            from.sendMessage(Text.msg("<red>The amount must be more than 0."));
            return false;
        }
        if (to == null || !to.isOnline() || to.equals(from)) {
            from.sendMessage(Text.msg("<red>That player isn't online."));
            return false;
        }
        if (!plugin.economy().withdraw(from, amount)) {
            from.sendMessage(Text.msg("<red>You only have " + plugin.economy().format(plugin.economy().balance(from)) + "."));
            return false;
        }
        plugin.economy().deposit(to, amount);
        from.sendMessage(Text.msg("<green>Sent <gold>" + plugin.economy().format(amount) + "</gold> to <white>"
                + Text.escape(to.getName())));
        to.sendMessage(Text.msg("<green>You got <gold>" + plugin.economy().format(amount) + "</gold> from <white>"
                + Text.escape(from.getName())));
        to.playSound(to.getLocation(), "minecraft:entity.experience_orb.pickup", 0.7f, 1.4f);
        return true;
    }
}
