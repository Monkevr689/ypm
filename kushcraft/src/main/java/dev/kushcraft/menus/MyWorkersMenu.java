package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.Items;
import dev.kushcraft.util.Text;
import dev.kushcraft.workers.Worker;
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
            lore.add("<white>" + (w.knockedOut() ? "<red>Knocked out" : w.paused() ? "<red>Paused"
                    : KushCraft.get().workers().overLimit(w) ? "<red>Over your rank's worker limit"
                    : w.isLoaded() ? w.status() : "<dark_gray>Working slowly while nobody's around"));
            if (w.needs() != null) {
                lore.add("<yellow>Needs: <white>" + w.needs() + " <dark_gray>(Supply, or a chest near them)");
            }
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
                        : "<gray>" + ws.of(player.getUniqueId()).size() + "/" + ws.limit(player.getUniqueId())
                        + " worker slots <dark_gray>(" + KushCraft.get().ranks().of(player.getUniqueId()).name()
                        + ", /rankup for more)");
    }

    @Override
    protected ItemStack action() {
        if (everyone) {
            return null;
        }
        return Items.icon("ui_take", "<green><bold>Collect everything",
                "<gray>Takes what all your workers made",
                "<gray>(not what they need) into your bag.");
    }

    @Override
    protected void clickAction(ClickType click) {
        if (everyone) {
            return;
        }
        int n = KushCraft.get().workers().collectAll(player);
        if (n <= 0) {
            player.sendActionBar(Text.mm("<gray>Nothing to collect" + (player.getInventory().firstEmpty() < 0
                    ? " - your inventory is full." : " yet.")));
            failSound();
            return;
        }
        player.playSound(player.getLocation(), "minecraft:entity.item.pickup", org.bukkit.SoundCategory.PLAYERS, 0.8f, 1f);
        player.sendActionBar(Text.mm("<green>Collected " + n + " items from your workers."));
        render();
    }

    private static final int SUPPLY = 52;

    @Override
    public void render() {
        super.render();
        if (!everyone) {
            set(SUPPLY, Items.icon("ui_wallet", "<aqua><bold>Supply your crew",
                    "<gray>Hands every worker what they use",
                    "<gray>from your inventory: seeds and",
                    "<gray>fertilizer, fresh buds, a Cook's",
                    "<gray>ingredients. (Runners get nothing.)"));
        }
    }

    @Override
    public void click(int slot, ClickType click) {
        if (slot == SUPPLY && !everyone) {
            int n = KushCraft.get().workers().supply(player);
            if (n <= 0) {
                player.sendActionBar(Text.mm("<gray>Nothing in your inventory your workers use."));
                failSound();
            } else {
                successSound();
                player.sendActionBar(Text.mm("<green>Handed " + n + " items to your workers."));
            }
            render();
            return;
        }
        super.click(slot, click);
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
