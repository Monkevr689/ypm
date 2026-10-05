package dev.kushcraft.gui;

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
    private static final int INFO = 26;

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
                "<gray>Hash, moon rocks, brownies, shroom tea,",
                "<gray>cocaine, heroin, LSD and meth."));
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
                "<gray>Cross two seeds, pick effects,",
                "<gray>colour and a <white>name</white> - your own strain!"));
        set(INFO, Items.icon("ui_info", "<aqua>Drug Lab",
                "<gray>Everything is made here.",
                "<gray>Ingredients come from your inventory.",
                "",
                "<gray>Punch the lab to pick it up."));
    }

    @Override
    public void click(int slot, ClickType click) {
        switch (slot) {
            case COOK -> openChild(new LabMenu(player, machine));
            case ROLL -> openChild(new RollerMenu(player));
            case DRY -> openChild(new DryMenu(player, machine));
            case MIX -> {
                if (!player.hasPermission("kushcraft.strainmaker")) {
                    player.sendActionBar(Text.mm("<red>You are not allowed to create strains."));
                    failSound();
                    return;
                }
                openChild(new StrainMakerMenu(player));
            }
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
