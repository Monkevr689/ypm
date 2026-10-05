package dev.kushcraft.gui;

import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * A chest menu. Nothing can be put into or taken out of it: every click is
 * cancelled and turned into a button press. Clicking items in your own
 * inventory below is used to pick / sell them.
 */
public abstract class Menu implements InventoryHolder {

    protected final Player player;
    protected final Inventory inv;

    protected Menu(Player player, int rows, String gui, String title) {
        this.player = player;
        this.inv = Bukkit.createInventory(this, rows * 9, GuiFont.title(player, gui, title));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open() {
        render();
        player.openInventory(inv);
        MenuListener.opened(this);
    }

    public Player player() {
        return player;
    }

    /** Draw every slot. */
    public abstract void render();

    /** A slot of the menu was clicked. */
    public void click(int slot, ClickType click) {
    }

    /** An item in the player's own inventory was clicked while the menu is open. */
    public void clickOwn(int slot, ItemStack item, ClickType click) {
    }

    /** Called every second while open. */
    public void tick() {
    }

    public void closed() {
    }

    protected void set(int slot, ItemStack item) {
        inv.setItem(slot, item);
    }

    protected void set(int row, int col, ItemStack item) {
        inv.setItem(row * 9 + col, item);
    }

    protected void clickSound() {
        player.playSound(player.getLocation(), "minecraft:ui.button.click", SoundCategory.MASTER, 0.5f, 1.2f);
    }

    protected void failSound() {
        player.playSound(player.getLocation(), "minecraft:block.note_block.bass", SoundCategory.MASTER, 0.7f, 0.6f);
    }

    protected void successSound() {
        player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", SoundCategory.MASTER, 0.6f, 1.1f);
    }
}
