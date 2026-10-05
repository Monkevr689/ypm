package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.jobs.Jobs;
import dev.kushcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Jobs: pick a job to see what it pays (as items you recognise) and what
 * you earned this hour. Layout matches tools/gui.py jobs().
 */
public final class JobsMenu extends TabMenu {

    private static final int[] JOB_SLOTS = {at(1, 0), at(1, 2), at(1, 4), at(1, 6), at(1, 8)};
    private static final int FIRST_RATE = at(3, 0);
    private static final int RATES = 18;
    private static final int PREV = at(5, 0);
    private static final int NEXT = at(5, 1);
    private static final int EARNED = at(5, 4);
    private static final int INFO = at(5, 8);

    private Jobs.Job selected = Jobs.Job.MINER;
    private int page;

    public JobsMenu(Player player) {
        super(player, Tab.JOBS);
    }

    static ItemStack jobIcon(Jobs.Job j) {
        return switch (j) {
            case MINER -> new ItemStack(Material.IRON_PICKAXE);
            case FARMER -> new ItemStack(Material.IRON_HOE);
            case WOODCUTTER -> new ItemStack(Material.IRON_AXE);
            case HUNTER -> new ItemStack(Material.IRON_SWORD);
            case GROWER -> Items.icon("bud_fresh", "");
        };
    }

    /** An item that shows what this pay-table line is about. */
    private static ItemStack rateIcon(Jobs.Job j, String key) {
        String k = key.toLowerCase(Locale.ROOT);
        Material m = switch (j) {
            case WOODCUTTER -> Material.OAK_LOG;
            case HUNTER -> Material.matchMaterial(k + "_spawn_egg");
            case FARMER -> switch (k) {
                case "carrots" -> Material.CARROT;
                case "potatoes" -> Material.POTATO;
                case "beetroots" -> Material.BEETROOT;
                case "cocoa" -> Material.COCOA_BEANS;
                case "torchflower_crop" -> Material.TORCHFLOWER;
                default -> Material.matchMaterial(k);
            };
            case MINER -> Material.matchMaterial(k);
            case GROWER -> null;
        };
        if (j == Jobs.Job.GROWER) {
            ItemType t = switch (k) {
                case "coca" -> ItemType.COCA_LEAVES;
                case "poppy" -> ItemType.POPPY_POD;
                case "mushroom" -> ItemType.MAGIC_MUSHROOM;
                case "peyote" -> ItemType.PEYOTE_BUTTON;
                default -> ItemType.BUD_FRESH;
            };
            return CatalogIcons.sample(t);
        }
        return m != null && m.isItem() ? new ItemStack(m) : new ItemStack(Material.PAPER);
    }

    @Override
    protected void page() {
        KushCraft plugin = KushCraft.get();
        Jobs jobs = plugin.jobs();
        Jobs.Earnings e = jobs.earnings(player);
        Jobs.Job[] all = Jobs.Job.values();
        for (int i = 0; i < all.length && i < JOB_SLOTS.length; i++) {
            Jobs.Job j = all[i];
            boolean on = j == selected;
            ItemStack it = jobIcon(j);
            it.editMeta(m -> {
                m.itemName(Text.mm((on ? "<green>▶ " : "<yellow>") + "<bold>" + j.display()));
                m.lore(Text.lines(List.of("<gray>" + j.description(), "",
                        "<gray>Earned this hour: <gold>" + money(e.of(j)),
                        on ? "<green>Pay table below" : "<yellow>Click to see what it pays")));
                m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                m.setEnchantmentGlintOverride(on ? Boolean.TRUE : null);
            });
            set(JOB_SLOTS[i], it);
        }
        List<Map.Entry<String, Double>> rates = new ArrayList<>(jobs.rates(selected).entrySet());
        rates.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        int pages = Math.max(1, (rates.size() + RATES - 1) / RATES);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < RATES; i++) {
            int idx = page * RATES + i;
            if (idx >= rates.size()) {
                break;
            }
            Map.Entry<String, Double> r = rates.get(idx);
            ItemStack it = rateIcon(selected, r.getKey());
            String label = selected == Jobs.Job.WOODCUTTER && r.getKey().equals("logs") ? "Any log"
                    : Text.titleCase(r.getKey()) + (selected == Jobs.Job.GROWER ? " harvest" : "");
            double pay = jobs.rate(selected, r.getKey());
            it.editMeta(m -> {
                m.itemName(Text.mm("<white>" + label));
                m.lore(Text.lines(List.of("<gray>Pays <gold>" + money(pay) + "</gold> each")));
                m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            });
            set(FIRST_RATE + i, it);
        }
        if (pages > 1) {
            set(PREV, Items.icon("ui_back", "<gray>Previous", "<dark_gray>Page " + (page + 1) + "/" + pages));
            set(NEXT, Items.icon("ui_arrow", "<gray>Next", "<dark_gray>Page " + (page + 1) + "/" + pages));
        }
        double cap = jobs.cap();
        set(EARNED, Items.icon("ui_wallet", "<gold>This hour: <white>" + money(e.total()) + " <gray>/ " + money(cap),
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

    @Override
    protected void clickPage(int slot, ClickType click) {
        for (int i = 0; i < JOB_SLOTS.length; i++) {
            if (JOB_SLOTS[i] == slot && i < Jobs.Job.values().length) {
                selected = Jobs.Job.values()[i];
                page = 0;
                clickSound();
                render();
                return;
            }
        }
        if (slot == PREV || slot == NEXT) {
            page += slot == NEXT ? 1 : -1;
            clickSound();
            render();
        }
    }
}
