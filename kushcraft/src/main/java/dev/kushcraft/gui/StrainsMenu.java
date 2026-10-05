package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effect.EffectType;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Every strain. Your own strains come first and can be renamed; mix a new one from here. */
public final class StrainsMenu extends ListMenu {

    private List<Strain> current = List.of();

    public StrainsMenu(Player player) {
        super(player, "Strains");
    }

    private boolean mine(Strain s) {
        return player.getUniqueId().equals(s.creator());
    }

    @Override
    protected List<ItemStack> entries() {
        List<Strain> list = new ArrayList<>();
        for (Strain s : KushCraft.get().strains().all()) {
            if (mine(s)) {
                list.add(s);
            }
        }
        for (Strain s : KushCraft.get().strains().all()) {
            if (!mine(s)) {
                list.add(s);
            }
        }
        current = list;
        boolean admin = player.hasPermission("kushcraft.admin");
        List<ItemStack> out = new ArrayList<>();
        for (Strain s : list) {
            List<String> lore = new ArrayList<>();
            lore.add(s.type().colored() + " <dark_gray>•</dark_gray> <gray>THC <white>" + s.potency() + "%");
            StringBuilder eff = new StringBuilder();
            for (EffectType e : s.effects()) {
                eff.append(eff.isEmpty() ? "" : "<dark_gray>, </dark_gray>").append(e.colored());
            }
            lore.add("<gray>Effects: " + eff);
            lore.add("<gray>Loves " + s.type().climate().colored() + " <gray>biomes");
            if (!s.wildBiomes().isEmpty()) {
                lore.add("<gray>Wild in: <white>" + Text.titleCase(String.join(", ", s.wildBiomes().subList(0,
                        Math.min(3, s.wildBiomes().size())))));
            }
            if (s.isCustom()) {
                lore.add("<dark_gray>Bred by " + Text.escape(String.valueOf(s.creatorName())));
            }
            if (mine(s) || admin) {
                lore.add("");
                lore.add("<yellow>Click to rename");
            }
            if (admin) {
                lore.add("<yellow>Shift-click: get 4 seeds");
            }
            out.add(Items.tintedIcon("seed_pack", s.color(), s.colored() + (mine(s) ? " <gold>★ yours" : ""), lore));
        }
        return out;
    }

    @Override
    protected ItemStack header() {
        int mine = 0;
        for (Strain s : KushCraft.get().strains().all()) {
            if (mine(s)) {
                mine++;
            }
        }
        return Items.icon("ui_dna", "<green>Strains <gray>(" + KushCraft.get().strains().all().size() + ")",
                "<gray>Your strains: <white>" + mine,
                "<gray>Mix two seeds to make a new one!");
    }

    @Override
    protected ItemStack action() {
        return Items.icon("ui_plus", "<green><bold>Mix a new strain",
                "<gray>Pick two seeds from your inventory,",
                "<gray>choose effects, colour and a <white>name</white>.");
    }

    @Override
    protected void clickAction(ClickType click) {
        if (!player.hasPermission("kushcraft.strainmaker")) {
            player.sendActionBar(Text.mm("<red>You are not allowed to create strains."));
            failSound();
            return;
        }
        if (!KushCraft.get().getConfig().getBoolean("strain-maker.anywhere", true)
                && !player.hasPermission("kushcraft.admin")) {
            player.sendActionBar(Text.mm("<yellow>Use a <green>Drug Lab</green> > Mix to make strains."));
            failSound();
            return;
        }
        openChild(new StrainMakerMenu(player));
    }

    @Override
    protected void clickEntry(int index, ClickType click) {
        if (index >= current.size()) {
            return;
        }
        Strain s = current.get(index);
        boolean admin = player.hasPermission("kushcraft.admin");
        if (admin && click.isShiftClick()) {
            InventoryUtil.give(player, Items.strainItem(ItemType.SEED_PACK, s, 3, 4));
            successSound();
            return;
        }
        if (!mine(s) && !admin) {
            return;
        }
        ChatInput.ask(player, "<green>New name for " + s.colored() + "<green>?", text -> {
            String n = StrainMakerMenu.clean(text);
            if (n.length() < 2 || n.length() > 24) {
                player.sendMessage(Text.msg("<red>Names must be 2-24 letters/numbers."));
            } else if (KushCraft.get().strains().nameTaken(n)) {
                player.sendMessage(Text.msg("<red>That name is already used."));
            } else {
                Strain renamed = KushCraft.get().strains().rename(s, n);
                player.sendMessage(Text.msg("<gray>Renamed to " + renamed.colored()
                        + "<gray>. New seeds and buds will use the new name."));
            }
            open();
        }, this::open);
    }
}
