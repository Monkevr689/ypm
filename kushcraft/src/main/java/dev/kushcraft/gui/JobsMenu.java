package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.Items;
import dev.kushcraft.jobs.Jobs;
import dev.kushcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** What every job pays and what you earned this hour. Layout matches tools/gui.py jobs(). */
public final class JobsMenu extends Menu {

    private static final int[] SLOTS = {9, 11, 13, 15, 17};
    private static final int BACK = 27;
    private static final int EARNED = 31;
    private static final int INFO = 35;

    public JobsMenu(Player player) {
        super(player, 4, "jobs", "Jobs");
    }

    private static ItemStack icon(Jobs.Job j) {
        return switch (j) {
            case MINER -> new ItemStack(Material.IRON_PICKAXE);
            case FARMER -> new ItemStack(Material.IRON_HOE);
            case WOODCUTTER -> new ItemStack(Material.IRON_AXE);
            case HUNTER -> new ItemStack(Material.IRON_SWORD);
            case GROWER -> Items.icon("bud_fresh", "");
        };
    }

    @Override
    public void render() {
        inv.clear();
        backButton(BACK);
        KushCraft plugin = KushCraft.get();
        Jobs jobs = plugin.jobs();
        Jobs.Earnings e = jobs.earnings(player);
        Jobs.Job[] all = Jobs.Job.values();
        for (int i = 0; i < all.length; i++) {
            Jobs.Job j = all[i];
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + j.description());
            lore.add("");
            List<Map.Entry<String, Double>> rates = new ArrayList<>(jobs.rates(j).entrySet());
            rates.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            int shown = 0;
            for (Map.Entry<String, Double> r : rates) {
                if (shown++ >= 8) {
                    lore.add("<dark_gray>...and " + (rates.size() - 8) + " more");
                    break;
                }
                lore.add("<gray>" + label(j, r.getKey()) + ": <gold>" + plugin.economy().format(r.getValue()));
            }
            lore.add("");
            lore.add("<gray>Earned this hour: <gold>" + plugin.economy().format(e.of(j)));
            ItemStack it = icon(j);
            it.editMeta(m -> {
                m.itemName(Text.mm("<yellow><bold>" + j.display()));
                m.lore(Text.lines(lore));
                m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            });
            set(SLOTS[i], it);
        }
        double cap = jobs.cap();
        set(EARNED, Items.icon("ui_wallet", "<gold>This hour: <white>" + plugin.economy().format(e.total())
                        + " <gray>/ " + plugin.economy().format(cap),
                "<gray>" + Text.bar(Math.min(1, e.total() / Math.max(1, cap)), 20, "gold", "dark_gray"),
                "<gray>Resets in " + e.minutesLeft() + " min."));
        set(INFO, Items.icon("ui_info", "<aqua>How jobs work",
                "<gray>No need to join - just work and",
                "<gray>the money lands in your wallet.",
                "",
                "<gray>Blocks placed by players don't pay,",
                "<gray>crops must be fully grown and",
                "<gray>spawner mobs don't count.",
                jobs.enabled() ? "" : "<red>Jobs are turned off on this server."));
    }

    private static String label(Jobs.Job j, String key) {
        if (j == Jobs.Job.WOODCUTTER && key.equals("logs")) {
            return "Any log";
        }
        if (j == Jobs.Job.GROWER) {
            return Text.titleCase(key) + " harvest";
        }
        return Text.titleCase(key);
    }
}
