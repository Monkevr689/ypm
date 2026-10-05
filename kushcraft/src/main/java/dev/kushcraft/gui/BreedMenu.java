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

/**
 * Breed: every strain (yours first), rename yours, and the big MIX button.
 * Layout matches tools/gui.py breed().
 */
public final class BreedMenu extends TabMenu {

    private static final int FIRST = 9;
    private static final int PER_PAGE = 36;
    private static final int PREV = at(5, 0);
    private static final int NEXT = at(5, 1);
    private static final int FILTER = at(5, 3);
    private static final int MIX = at(5, 8);

    private boolean onlyMine;
    private int page;
    private List<Strain> current = List.of();

    public BreedMenu(Player player) {
        super(player, Tab.BREED);
    }

    private boolean mine(Strain s) {
        return player.getUniqueId().equals(s.creator());
    }

    @Override
    protected void page() {
        List<Strain> list = new ArrayList<>();
        for (Strain s : KushCraft.get().strains().all()) {
            if (mine(s)) {
                list.add(s);
            }
        }
        if (!onlyMine) {
            for (Strain s : KushCraft.get().strains().all()) {
                if (!mine(s)) {
                    list.add(s);
                }
            }
        }
        current = list;
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        boolean admin = player.hasPermission("kushcraft.admin");
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= list.size()) {
                break;
            }
            Strain s = list.get(idx);
            ItemStack it = icon(s, admin);
            List<String> extra = hints(mine(s), admin);
            if (!extra.isEmpty()) {
                it.editMeta(m -> {
                    List<net.kyori.adventure.text.Component> lore = new ArrayList<>(m.lore());
                    lore.add(Text.mm(""));
                    lore.addAll(Text.lines(extra));
                    m.lore(lore);
                });
            }
            set(FIRST + i, it);
        }
        if (pages > 1) {
            set(PREV, Items.icon("ui_back", "<gray>Previous page", "<dark_gray>Page " + (page + 1) + "/" + pages));
            set(NEXT, Items.icon("ui_arrow", "<gray>Next page", "<dark_gray>Page " + (page + 1) + "/" + pages));
        }
        int mine = 0;
        for (Strain s : KushCraft.get().strains().all()) {
            if (mine(s)) {
                mine++;
            }
        }
        set(FILTER, Items.icon(onlyMine ? "ui_dna" : "seed_pack", "<yellow>Showing: <white>" + (onlyMine ? "your strains" : "all strains"),
                "<gray>You bred <white>" + mine + "</white> strains.", "<gray>Click to switch."));
        double cost = KushCraft.get().getConfig().getDouble("strain-maker.cost", 1500);
        set(MIX, Items.glint(Items.icon("ui_plus", "<green><bold>Mix two seeds",
                "<gray>Cross any two strains - the child gets",
                "<gray><white>random</white> effects from its parents,",
                "<gray>maybe a brand new <light_purple>mutation</light_purple>,",
                "<gray>and a random potency and rarity.",
                "",
                "<gray>Costs <gold>" + money(cost) + "</gold> + one seed of each."), true));
    }

    static ItemStack icon(Strain s, boolean admin) {
        List<String> lore = new ArrayList<>();
        lore.add(s.type().colored() + " <dark_gray>•</dark_gray> <gray>THC <white>" + s.potency() + "% <dark_gray>•</dark_gray> "
                + s.rarity().colored());
        StringBuilder eff = new StringBuilder();
        for (EffectType e : s.effects()) {
            eff.append(eff.isEmpty() ? "" : "<dark_gray>, </dark_gray>").append(e.colored());
        }
        lore.add("<gray>Effects: " + eff);
        lore.add("<gray>Loves " + s.type().climate().colored() + " <gray>biomes");
        if (s.isCustom()) {
            lore.add("<dark_gray>Bred by " + Text.escape(String.valueOf(s.creatorName())));
        }
        return Items.tintedIcon("seed_pack", s.color(), s.colored(), lore);
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        if (slot == MIX) {
            MixerMenu.openFor(player, this, false);
            return;
        }
        if (slot == FILTER) {
            onlyMine = !onlyMine;
            page = 0;
            clickSound();
            render();
            return;
        }
        if (slot == PREV || slot == NEXT) {
            page += slot == NEXT ? 1 : -1;
            clickSound();
            render();
            return;
        }
        if (slot < FIRST || slot >= FIRST + PER_PAGE) {
            return;
        }
        int idx = page * PER_PAGE + (slot - FIRST);
        if (idx >= current.size()) {
            return;
        }
        Strain s = current.get(idx);
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
            String n = MixerMenu.clean(text);
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

    /** Lore lines added for strains you can rename. */
    static List<String> hints(boolean mine, boolean admin) {
        List<String> l = new ArrayList<>();
        if (mine || admin) {
            l.add("<yellow>Click to rename");
        }
        if (admin) {
            l.add("<yellow>Shift-click: get 4 seeds");
        }
        return l;
    }
}
