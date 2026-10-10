package dev.kushcraft.listeners;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.menus.InfoMenu;
import dev.kushcraft.storage.PlayerRecord;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * What happens when someone joins: the inventory wipe of an "everything"
 * reset catches up (players who were offline during the reset), the starter
 * kit (once a season) and the welcome menu (once a season, menus.yml).
 */
public final class Onboarding {

    private final KushCraft plugin;

    public Onboarding(KushCraft plugin) {
        this.plugin = plugin;
    }

    public void join(Player p) {
        PlayerRecord r = plugin.economy().account(p.getUniqueId());
        if (r.season() < plugin.inventoryWipeSeason()) {
            wipe(p);
        }
        if (r.season() != plugin.season()) {
            r.season(plugin.season());
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> welcome(p), 40L);
    }

    /** After a reset, for everyone online (their rows were just reset). */
    public void afterReset(Player p) {
        plugin.economy().join(p);
        plugin.ranks().showInTab(p);
        join(p);
    }

    private void welcome(Player p) {
        if (!p.isOnline()) {
            return;
        }
        PlayerRecord r = plugin.economy().account(p.getUniqueId());
        if (!r.has(PlayerRecord.GOT_KIT)) {
            r.set(PlayerRecord.GOT_KIT, true);
            if (plugin.getConfig().getBoolean("new-players.give-guide", true)) {
                InventoryUtil.give(p, Items.create(ItemType.GROWER_GUIDE));
            }
            int kit = PlayerListener.starterKit(p);
            if (kit > 0) {
                p.sendMessage(Text.msg("<green>You got a starter kit!</green> <gray>Type <white>/menu</white> any time "
                        + "for how the server works, <white>/kush</white> for the shop and your lab."));
            }
        }
        if (!r.has(PlayerRecord.ONBOARDED) && plugin.menuTexts().openOnFirstJoin()) {
            r.set(PlayerRecord.ONBOARDED, true);
            InfoMenu.openMain(p);
        }
    }

    /** Season reset "everything": inventory, ender chest and XP are gone. */
    public void wipe(Player p) {
        p.closeInventory();
        p.getInventory().clear();
        p.getEnderChest().clear();
        p.setItemOnCursor(null);
        p.setLevel(0);
        p.setExp(0);
        p.setTotalExperience(0);
        plugin.effects().clear(p);
        PlayerRecord r = plugin.economy().account(p.getUniqueId());
        r.season(plugin.season());
        // saved before the database notes the wipe: a crash in between just wipes again (harmless)
        plugin.persistence().took(p);
        p.sendMessage(Text.msg("<gold>New season:</gold> <gray>your inventory, ender chest and XP were reset."));
    }
}
