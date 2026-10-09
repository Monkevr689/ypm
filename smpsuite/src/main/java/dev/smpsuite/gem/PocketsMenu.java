package dev.smpsuite.gem;

import dev.smpsuite.SMPSuite;
import dev.smpsuite.data.PlayerData;
import dev.smpsuite.gui.Menu;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Wealth gem's Pockets: 9 extra slots that stay with you (saved with your data). */
public final class PocketsMenu extends Menu {

    private final SMPSuite plugin;

    public PocketsMenu(SMPSuite plugin, Player player) {
        super(player, 1, "<#3AD86A>Pockets</#3AD86A> <dark_gray>(Wealth gem)");
        this.plugin = plugin;
    }

    @Override
    protected void draw() {
        PlayerData d = plugin.store().get(player);
        for (int i = 0; i < d.pockets.length; i++) {
            set(i, d.pockets[i]);
        }
    }

    @Override
    public void click(int slot, ClickType type) {
    }

    @Override
    public boolean storage() {
        return true;
    }

    @Override
    public void closed() {
        PlayerData d = plugin.store().get(player);
        for (int i = 0; i < d.pockets.length; i++) {
            ItemStack it = getInventory().getItem(i);
            d.pockets[i] = it == null || it.getType().isAir() ? null : it.clone();
        }
        d.dirty = true;
        plugin.store().save(d);
    }
}
