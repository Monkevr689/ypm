package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.cartels.Cartel;
import dev.kushcraft.cartels.Cartels;
import dev.kushcraft.items.Items;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cartel > Members: everyone in your cartel (the boss can shift-click to
 * kick) and the Invite button, which lists the online players without a
 * cartel - click one to invite them.
 */
public final class CartelMembersMenu extends ListMenu {

    private final boolean inviting;
    private List<UUID> shown = List.of();

    public CartelMembersMenu(Player player) {
        this(player, false);
    }

    private CartelMembersMenu(Player player, boolean inviting) {
        super(player, inviting ? "Invite to your cartel" : "Cartel members");
        this.inviting = inviting;
    }

    private Cartel cartel() {
        return KushCraft.get().cartels().of(player);
    }

    @Override
    protected List<ItemStack> entries() {
        Cartel c = cartel();
        List<ItemStack> out = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        if (c == null) {
            shown = ids;
            return out;
        }
        if (inviting) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (o != player && KushCraft.get().cartels().of(o) == null) {
                    ids.add(o.getUniqueId());
                    out.add(head(o, "<white>" + Text.escape(o.getName()), List.of("<green>Click: invite")));
                }
            }
        } else {
            boolean boss = c.isLeader(player.getUniqueId());
            for (UUID id : c.members()) {
                OfflinePlayer o = Bukkit.getOfflinePlayer(id);
                List<String> lore = new ArrayList<>();
                lore.add(KushCraft.get().titles().label(o));
                lore.add("<gold>" + KushCraft.get().economy().format(KushCraft.get().economy().sales(o))
                        + " <dark_gray>sold");
                if (boss && !c.isLeader(id)) {
                    lore.add("<dark_gray>Shift-click: kick");
                }
                ids.add(id);
                out.add(head(o, (c.isLeader(id) ? "<gold>★ " : "<white>") + Text.escape(Cartels.name(id)), lore));
            }
        }
        shown = ids;
        return out;
    }

    private static ItemStack head(OfflinePlayer p, String name, List<String> lore) {
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
        Cartel c = cartel();
        return c == null ? Items.icon("ui_members", "<gray>No cartel")
                : Items.tintedIcon("cartel_banner", c.color(), c.colored(), List.of(inviting
                ? "<gray>Players without a cartel" : "<gray>" + c.members().size() + "/"
                + KushCraft.get().cartels().tier(c).members() + " members"));
    }

    @Override
    protected ItemStack action() {
        return inviting ? null : Items.icon("ui_members", "<green>Invite players", "<dark_gray>Online players only.");
    }

    @Override
    protected void clickAction(ClickType click) {
        openChild(new CartelMembersMenu(player, true));
    }

    @Override
    protected void clickEntry(int index, ClickType click) {
        if (index >= shown.size()) {
            return;
        }
        UUID id = shown.get(index);
        String error;
        if (inviting) {
            error = KushCraft.get().cartels().invite(player, Bukkit.getOfflinePlayer(id));
            if (error == null) {
                player.sendActionBar(Text.mm("<green>Invited " + Text.escape(Cartels.name(id))));
            }
        } else if (click.isShiftClick()) {
            error = KushCraft.get().cartels().kick(player, id);
        } else {
            return;
        }
        if (error != null) {
            player.sendActionBar(Text.mm("<red>" + error));
            failSound();
        } else {
            successSound();
        }
        render();
    }
}
