package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.Items;
import dev.kushcraft.lab.LabRecipe;
import dev.kushcraft.machine.Machine;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/** Drug Lab front page: Cook, Roll, Dry, Mix strains. Layout: tools/gui.py hub(). */
public final class LabHubMenu extends Menu {

    private static final int COOK = 10;
    private static final int ROLL = 12;
    private static final int DRY = 14;
    private static final int MIX = 16;
    private static final int BACK = 18;
    private static final int UPGRADE = 26;

    private final Machine machine;

    public LabHubMenu(Player player, Machine machine) {
        super(player, 3, "hub", "Drug Lab");
        this.machine = machine;
    }

    @Override
    public void render() {
        inv.clear();
        backButton(BACK);
        List<String> cook = new ArrayList<>(List.of(
                "<gray>18 recipes: weed edibles, psychedelics,",
                "<gray>uppers and downers. Ranks unlock more."));
        LabRecipe job = machine.busy() ? LabRecipe.parse(machine.job()) : null;
        if (job != null) {
            cook.add("");
            cook.add(machine.jobDone() ? "<green>Your " + job.output().display() + " is ready!"
                    : "<yellow>Cooking " + job.output().display() + ": " + (int) Math.round(machine.jobProgress() * 100) + "%");
        }
        set(COOK, Items.glint(Items.icon("lab_solvent", "<green><bold>Cook", cook), job != null && machine.jobDone()));
        set(ROLL, Items.icon("joint", "<green><bold>Roll",
                "<gray>Joints and blunts from your dried buds."));
        List<String> dry = new ArrayList<>(List.of("<gray>Turn fresh buds into dried buds."));
        if (machine.rackAmount() > 0) {
            dry.add("");
            dry.add(machine.rackDry() ? "<green>" + machine.rackAmount() + " buds are dry - collect them!"
                    : "<yellow>Drying " + machine.rackAmount() + " buds...");
        }
        set(DRY, Items.glint(Items.icon("bud_fresh", "<green><bold>Dry", dry), machine.rackDry()));
        set(MIX, Items.icon("ui_dna", "<green><bold>Mix Strains",
                "<gray>Cross two seeds - random effects,",
                "<gray>mutations and rarity. Name your strain!"));
        int level = machine.level();
        java.util.List<Double> costs = costs();
        List<String> up = new ArrayList<>();
        up.add("<gray>Level <white>" + level + "</white> of " + (costs.size() + 1));
        up.add("<gray>Cooking: <green>" + Math.round((1 - LabMenu.timeFactor(machine)
                / Math.max(0.01, KushCraft.get().getConfig().getDouble("lab.time-multiplier", 1.0))) * 100) + "% faster");
        up.add("<gray>Bonus item chance: <green>" + Math.round(LabMenu.bonusChance(machine) * 100) + "%");
        up.add("");
        if (level - 1 < costs.size()) {
            up.add("<gray>Next level: <green>15% faster, +8% bonus");
            up.add("<gray>Costs <gold>" + KushCraft.get().economy().format(costs.get(level - 1)));
            up.add("<yellow>Click to upgrade");
        } else {
            up.add("<gold>Fully upgraded!");
        }
        up.add("");
        up.add("<dark_gray>Punch the lab to pick it up -");
        up.add("<dark_gray>it keeps its level.");
        set(UPGRADE, Items.icon("ui_crown", "<gold><bold>Upgrade lab", up));
    }

    private static java.util.List<Double> costs() {
        return KushCraft.get().getConfig().getDoubleList("lab.upgrade-costs");
    }

    private void upgrade() {
        java.util.List<Double> costs = costs();
        int level = machine.level();
        if (level - 1 >= costs.size()) {
            player.sendActionBar(Text.mm("<gold>This lab is fully upgraded."));
            failSound();
            return;
        }
        double cost = costs.get(level - 1);
        if (!KushCraft.get().economy().withdraw(player, cost)) {
            player.sendActionBar(Text.mm("<red>Upgrading costs " + KushCraft.get().economy().format(cost) + "."));
            failSound();
            return;
        }
        machine.level(level + 1);
        KushCraft.get().machines().markDirty();
        player.playSound(player.getLocation(), "minecraft:block.anvil.use", org.bukkit.SoundCategory.BLOCKS, 0.7f, 1.2f);
        player.sendMessage(Text.msg("<green>Drug Lab upgraded to level " + (level + 1) + "!"));
        render();
    }

    @Override
    public void click(int slot, ClickType click) {
        switch (slot) {
            case COOK -> openChild(new LabMenu(player, machine));
            case ROLL -> openChild(new RollerMenu(player));
            case DRY -> openChild(new DryMenu(player, machine));
            case MIX -> MixerMenu.openFor(player, this);
            case UPGRADE -> upgrade();
            default -> {
            }
        }
    }

    @Override
    public void tick() {
        if (machine.busy() || machine.rackAmount() > 0) {
            render();
        }
    }
}
