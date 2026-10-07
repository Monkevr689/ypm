package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.lab.LabRecipe;
import dev.kushcraft.util.Text;
import dev.kushcraft.worker.Worker;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Pick what a Cook makes: roll joints or blunts, or any Drug Lab recipe. Click one. */
public final class CookRecipeMenu extends ListMenu {

    /** The jobs in the order shown: auto, rolling, then every lab recipe. */
    private static List<String> jobs() {
        List<String> out = new ArrayList<>(List.of(Worker.AUTO, Worker.ROLL_JOINT, Worker.ROLL_BLUNT));
        for (LabRecipe r : LabRecipe.values()) {
            out.add(r.name());
        }
        return out;
    }

    private final Worker worker;

    public CookRecipeMenu(Player player, Worker worker) {
        super(player, "What should " + worker.name() + " make?");
        this.worker = worker;
    }

    @Override
    protected List<ItemStack> entries() {
        List<ItemStack> out = new ArrayList<>();
        for (String job : jobs()) {
            boolean now = job.equals(currentJob());
            if (job.equals(Worker.AUTO)) {
                out.add(Items.glint(Items.icon("ui_auto", (now ? "<green>" : "<white>") + "Auto: whatever pays best",
                        "<gray>They make the most valuable drug they",
                        "<gray>have everything for, and switch when",
                        "<gray>ingredients run out. Pairs well with",
                        "<gray>a Supplier and a Runner.",
                        now ? "<green>Doing this now" : "<gray>Click: let them pick"), now));
                continue;
            }
            LabRecipe r = LabRecipe.parse(job);
            ItemType made = r != null ? r.output() : job.equals(Worker.ROLL_JOINT) ? ItemType.JOINT : ItemType.BLUNT;
            ItemStack it = CatalogIcons.sample(made);
            List<String> lore = new ArrayList<>();
            if (r != null) {
                it.setAmount(r.amount());
                for (LabRecipe.Ingredient ing : r.ingredients()) {
                    lore.add("<white>" + ing.amount() + " " + ing.name() + " <dark_gray>(" + ing.where() + ")");
                }
                lore.add("<dark_gray>" + r.seconds() + "s a batch at your Drug Lab, up to 4 at once");
            } else {
                lore.add("<white>" + (made == ItemType.JOINT ? "1 Dried Bud + 1 Rolling Papers" : "2 Dried Bud + 1 Blunt Wrap")
                        + " <dark_gray>each");
                lore.add("<dark_gray>Rolled where they stand, no lab needed");
            }
            lore.add(now ? "<green>Making this now" : "<gray>Click: make this");
            String verb = r != null ? "" : "Roll ";
            it.editMeta(m -> {
                m.itemName(Text.mm((now ? "<green>" : "<white>") + verb + made.display() + (r != null ? " <gray>x" + r.amount() : "s")));
                m.lore(Text.lines(lore));
            });
            out.add(Items.glint(it, now));
        }
        return out;
    }

    private String currentJob() {
        if (worker.autoPick()) {
            return Worker.AUTO;
        }
        LabRecipe r = worker.recipe();
        if (r != null) {
            return r.name();
        }
        ItemType roll = worker.rolls();
        return roll == null ? "" : roll == ItemType.JOINT ? Worker.ROLL_JOINT : Worker.ROLL_BLUNT;
    }

    @Override
    protected ItemStack header() {
        return Items.icon("tab_cook", "<aqua>Pick a drug",
                "<gray>" + Text.escape(worker.name()) + " makes it batch after batch,",
                "<gray>fetching the ingredients from chests and",
                "<gray>workers they can walk to.");
    }

    @Override
    protected void clickEntry(int index, ClickType click) {
        if (KushCraft.get().workers().get(worker.id()) == null) {
            player.closeInventory();
            return;
        }
        List<String> jobs = jobs();
        if (index >= jobs.size()) {
            return;
        }
        KushCraft.get().workers().setJob(worker, jobs.get(index));
        player.sendActionBar(Text.mm("<aqua>" + Text.escape(worker.name()) + " now makes <white>"
                + (worker.autoPick() ? "whatever pays best" : worker.product().display())));
        successSound();
        new WorkerMenu(player, worker).open();
    }
}
