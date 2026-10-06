package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.Items;
import dev.kushcraft.machine.Machine;
import dev.kushcraft.util.Text;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.Locale;

/**
 * A Drug Lab page: Cook, Roll, Dry and Mix tabs along the top and the
 * upgrade button in the corner. Right-clicking a lab opens Cook. The active
 * tab is drawn into each page's background (tools/gui.py lab_page()).
 */
public abstract class LabTabMenu extends Menu {

    public enum Tab {
        COOK("Cook", "tab_cook"),
        ROLL("Roll", "tab_roll"),
        DRY("Dry", "tab_dry"),
        MIX("Mix", "tab_mix");

        private final String display;
        private final String icon;

        Tab(String display, String icon) {
            this.display = display;
            this.icon = icon;
        }

        public String display() {
            return display;
        }

        public String icon() {
            return icon;
        }
    }

    static final int ROWS = 5;
    static final int UPGRADE = 8;

    protected final Machine machine;
    protected final Tab tab;

    protected LabTabMenu(Player player, Machine machine, Tab tab) {
        super(player, ROWS, tab.name().toLowerCase(Locale.ROOT), "Drug Lab · " + tab.display(), false);
        this.machine = machine;
        this.tab = tab;
    }

    /** Opens a page of this lab. */
    public static void open(Player p, Machine m, Tab tab) {
        switch (tab) {
            case COOK -> new LabMenu(p, m).open();
            case ROLL -> new RollerMenu(p, m).open();
            case DRY -> new DryMenu(p, m).open();
            case MIX -> MixerMenu.openFor(p, m);
        }
    }

    @Override
    public final void render() {
        inv.clear();
        for (Tab t : Tab.values()) {
            set(t.ordinal(), Items.glint(Items.icon(t.icon, (t == tab ? "<green>" : "<gray>") + t.display, status(t)),
                    t != tab && ready(t)));
        }
        set(UPGRADE, upgradeIcon());
        page();
    }

    /** One line under a tab: what's going on there. */
    private List<String> status(Tab t) {
        return switch (t) {
            case COOK -> machine.busy() ? List.of(machine.jobDone() ? "<green>Batch ready!"
                    : "<yellow>Cooking: " + Math.round(machine.jobProgress() * 100) + "%") : List.of();
            case DRY -> machine.racksDry() > 0 ? List.of("<green>" + machine.racksDry() + " racks dry!")
                    : machine.racksInUse() > 0 ? List.of("<yellow>Drying: " + machine.racksInUse() + "/" + Machine.RACKS)
                    : List.of();
            default -> List.of();
        };
    }

    /** Something to collect on that tab. */
    private boolean ready(Tab t) {
        return (t == Tab.COOK && machine.busy() && machine.jobDone()) || (t == Tab.DRY && machine.racksDry() > 0);
    }

    private org.bukkit.inventory.ItemStack upgradeIcon() {
        List<Double> costs = costs();
        int level = machine.level();
        int max = costs.size() + 1;
        String stars = "<gold>" + "★".repeat(level) + "<dark_gray>" + "★".repeat(Math.max(0, max - level));
        if (level >= max) {
            return Items.icon("ui_upgrade", "<gold>Lab level " + level + " (max)", stars);
        }
        return Items.icon("ui_upgrade", "<gold>Upgrade lab: " + KushCraft.get().economy().format(costs.get(level - 1)),
                stars, "<gray>15% faster, +8% bonus items");
    }

    private static List<Double> costs() {
        return KushCraft.get().getConfig().getDoubleList("lab.upgrade-costs");
    }

    private void upgrade() {
        List<Double> costs = costs();
        int level = machine.level();
        if (level - 1 >= costs.size()) {
            failSound();
            return;
        }
        double cost = costs.get(level - 1);
        if (!KushCraft.get().economy().withdraw(player, cost)) {
            player.sendActionBar(Text.mm("<red>Upgrading costs " + KushCraft.get().economy().format(cost)));
            failSound();
            return;
        }
        machine.level(level + 1);
        KushCraft.get().machines().markDirty();
        KushCraft.get().awards().labLevel(player, level + 1, costs.size() + 1);
        player.playSound(player.getLocation(), "minecraft:block.anvil.use", SoundCategory.BLOCKS, 0.7f, 1.2f);
        player.sendActionBar(Text.mm("<gold>Drug Lab is now level " + (level + 1)));
        render();
    }

    /** Draws rows 1-4. */
    protected abstract void page();

    @Override
    public final void click(int slot, ClickType click) {
        if (slot < Tab.values().length) {
            Tab t = Tab.values()[slot];
            if (t != tab) {
                clickSound();
                open(player, machine, t);
            }
            return;
        }
        if (slot == UPGRADE) {
            upgrade();
            return;
        }
        if (slot >= 9) {
            clickPage(slot, click);
        }
    }

    protected void clickPage(int slot, ClickType click) {
    }

    protected static int at(int row, int col) {
        return row * 9 + col;
    }

    /** Cook time multiplier (see Cooking). */
    public static double timeFactor(Machine m) {
        return dev.kushcraft.lab.Cooking.timeFactor(m);
    }

    /** Chance of one extra item per batch (see Cooking). */
    public static double bonusChance(Machine m) {
        return dev.kushcraft.lab.Cooking.bonusChance(m);
    }
}
