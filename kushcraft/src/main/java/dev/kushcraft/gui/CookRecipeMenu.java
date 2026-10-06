package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.Items;
import dev.kushcraft.lab.LabRecipe;
import dev.kushcraft.util.Text;
import dev.kushcraft.worker.Worker;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Pick what a Cook makes: every Drug Lab recipe, click one. */
public final class CookRecipeMenu extends ListMenu {

    private final Worker worker;

    public CookRecipeMenu(Player player, Worker worker) {
        super(player, "What should " + worker.name() + " cook?");
        this.worker = worker;
    }

    @Override
    protected List<ItemStack> entries() {
        List<ItemStack> out = new ArrayList<>();
        LabRecipe now = worker.recipe();
        for (LabRecipe r : LabRecipe.values()) {
            ItemStack it = CatalogIcons.sample(r.output());
            it.setAmount(r.amount());
            List<String> lore = new ArrayList<>();
            for (LabRecipe.Ingredient ing : r.ingredients()) {
                lore.add("<white>" + ing.amount() + " " + ing.name() + " <dark_gray>(" + ing.where() + ")");
            }
            lore.add("<dark_gray>" + r.seconds() + "s a batch, up to 4 batches at once");
            lore.add(r == now ? "<green>Cooking this now" : "<gray>Click: cook this");
            it.editMeta(m -> {
                m.itemName(Text.mm((r == now ? "<green>" : "<white>") + r.output().display() + " <gray>x" + r.amount()));
                m.lore(Text.lines(lore));
            });
            out.add(Items.glint(it, r == now));
        }
        return out;
    }

    @Override
    protected ItemStack header() {
        return Items.icon("tab_cook", "<aqua>Pick a drug",
                "<gray>" + Text.escape(worker.name()) + " cooks it at your Drug Lab,",
                "<gray>using what is in their satchel.");
    }

    @Override
    protected void clickEntry(int index, ClickType click) {
        if (KushCraft.get().workers().get(worker.id()) == null) {
            player.closeInventory();
            return;
        }
        LabRecipe r = LabRecipe.values()[index];
        KushCraft.get().workers().setRecipe(worker, r);
        player.sendActionBar(Text.mm("<aqua>" + Text.escape(worker.name()) + " now cooks <white>" + r.output().display()));
        successSound();
        new WorkerMenu(player, worker).open();
    }
}
