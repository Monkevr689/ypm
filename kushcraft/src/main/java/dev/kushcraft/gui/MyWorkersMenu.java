package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.Items;
import dev.kushcraft.util.Text;
import dev.kushcraft.worker.Worker;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Your workers (Shop > Gear &amp; Workers): where they are and what they're
 * doing. Click one to open their menu from anywhere. Admins see everyone's.
 */
public final class MyWorkersMenu extends ListMenu {

    private final boolean everyone;
    private List<Worker> shown = List.of();

    public MyWorkersMenu(Player player) {
        this(player, false);
    }

    public MyWorkersMenu(Player player, boolean everyone) {
        super(player, everyone ? "All workers (admin)" : "Your workers");
        this.everyone = everyone;
    }

    @Override
    protected List<ItemStack> entries() {
        List<Worker> list = everyone ? new ArrayList<>(KushCraft.get().workers().all())
                : KushCraft.get().workers().of(player.getUniqueId());
        List<ItemStack> out = new ArrayList<>();
        for (Worker w : list) {
            Location h = w.home();
            List<String> lore = new ArrayList<>();
            lore.add("<gold>Level " + w.level() + " <dark_gray>· <gray>" + w.carried() + " items in the satchel");
            lore.add("<white>" + (w.paused() ? "<red>Paused" : w.isLoaded() ? w.status() : "<dark_gray>Asleep (nobody nearby)"));
            if (h != null) {
                lore.add("<dark_gray>" + w.worldName() + " " + h.getBlockX() + ", " + h.getBlockY() + ", " + h.getBlockZ());
            }
            if (everyone) {
                UUID o = w.owner();
                lore.add("<dark_gray>Owner: " + Text.escape(String.valueOf(org.bukkit.Bukkit.getOfflinePlayer(o).getName())));
            }
            lore.add("<dark_gray>Click: open their menu");
            out.add(Items.icon(w.type().item().model(), w.type().color() + Text.escape(w.name())
                    + " <gray>the " + w.type().display(), lore));
        }
        shown = list;
        return out;
    }

    @Override
    protected ItemStack header() {
        var ws = KushCraft.get().workers();
        return Items.icon("ui_workers", "<green>" + (everyone ? "All workers" : "Your workers"),
                everyone ? "<gray>" + ws.all().size() + " hired on the server"
                        : "<gray>" + ws.of(player.getUniqueId()).size() + "/" + ws.maxPerPlayer() + " hired");
    }

    @Override
    protected void clickEntry(int index, ClickType click) {
        if (index < shown.size() && KushCraft.get().workers().get(shown.get(index).id()) != null) {
            openChild(new WorkerMenu(player, shown.get(index)));
        }
    }

    @Override
    public void tick() {
        render();
    }
}
