package dev.smpsuite.gui;

import dev.smpsuite.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** A simple chest menu: items are drawn by render(), clicks go to click(). */
public abstract class Menu implements InventoryHolder {

    protected final Player player;
    private final Inventory inv;

    protected Menu(Player player, int rows, String title) {
        this.player = player;
        this.inv = Bukkit.createInventory(this, rows * 9, Msg.mm(title));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open() {
        render();
        player.openInventory(inv);
    }

    public void render() {
        inv.clear();
        draw();
    }

    protected abstract void draw();

    /** A click in the top inventory. */
    public abstract void click(int slot, ClickType type);

    /** Free slots (Pockets) let items move; menus don't. */
    public boolean storage() {
        return false;
    }

    public void closed() {
    }

    protected void set(int slot, ItemStack it) {
        inv.setItem(slot, it);
    }

    public static ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(Msg.mm(name));
            meta.lore(Msg.lines(lore));
            meta.addItemFlags(ItemFlag.values());
        });
        return it;
    }

    public static ItemStack icon(Material m, String name, String... lore) {
        return icon(m, name, List.of(lore));
    }

    protected void fill(Material pane) {
        ItemStack glass = icon(pane, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, glass);
            }
        }
    }
}
