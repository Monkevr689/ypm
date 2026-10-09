package dev.smpsuite.gui;

import dev.smpsuite.Msg;
import dev.smpsuite.SMPSuite;
import dev.smpsuite.data.PlayerData;
import dev.smpsuite.skill.Skill;
import dev.smpsuite.skill.Skills;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** /skills: every skill's level, progress, bonus, ability and pay. */
public final class SkillsMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private final SMPSuite plugin;
    private final PlayerData whose;

    public SkillsMenu(SMPSuite plugin, Player viewer, PlayerData whose) {
        super(viewer, 4, "<dark_aqua>Skills</dark_aqua> <dark_gray>- " + Msg.escape(whose.name));
        this.plugin = plugin;
        this.whose = whose;
    }

    private boolean own() {
        return whose.id().equals(player.getUniqueId());
    }

    @Override
    protected void draw() {
        Skills sk = plugin.skills();
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, m -> {
            OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(whose.id());
            m.setOwningPlayer(op);
            m.displayName(Msg.mm("<white><bold>" + Msg.escape(whose.name)));
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Total level <white>" + whose.totalLevel());
            if (plugin.money().available() && plugin.getConfig().getBoolean("pay.enabled", true)) {
                double left = sk.payLeftThisHour(whose);
                lore.add("<gray>Jobs pay: <gold>" + (left == Double.MAX_VALUE ? "no limit"
                        : plugin.money().format(left) + " left this hour"));
            }
            lore.add("<dark_gray>Skills are the main way to progress here.");
            m.lore(Msg.lines(lore));
        });
        set(4, head);
        for (int i = 0; i < Skill.values().length; i++) {
            set(SLOTS[i], skillIcon(Skill.values()[i]));
        }
        set(30, icon(Material.BOOK, "<aqua>How skills work",
                "<gray>Play normally: mine, farm, fish, chop,",
                "<gray>dig and fight. Vitality grows from it all.",
                "<gray>Placed blocks and spawner mobs give little",
                "<gray>or nothing. Bonuses are small and capped:",
                "<white>at most " + Msg.pct(plugin.combat().cap(false)) + " extra damage vs mobs, "
                        + Msg.pct(plugin.combat().cap(true)) + " vs players,",
                "<white>" + Msg.num(sk.maxHearts()) + " hearts at most.",
                "<dark_gray>Teammates nearby share a little XP."));
        if (own()) {
            set(31, icon(whose.actionBar ? Material.LIME_DYE : Material.GRAY_DYE,
                    whose.actionBar ? "<green>XP messages: on" : "<gray>XP messages: off",
                    "<gray>Show \"+3 Mining XP\" above your hotbar.", "<dark_gray>Click to switch"));
            set(32, icon(Material.AMETHYST_SHARD, "<light_purple>Your gem", "<gray>Click: /gem"));
            set(33, icon(Material.WHITE_BANNER, "<aqua>Your team", "<gray>Click: /party"));
        }
        fill(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack skillIcon(Skill s) {
        Skills sk = plugin.skills();
        int lv = whose.level(s);
        boolean max = lv >= sk.maxLevel();
        List<String> lore = new ArrayList<>();
        double need = sk.xpToNext(s, lv);
        lore.add(max ? "<gold>Max level!" : Msg.bar(whose.xp(s) / need, 20, "green", "dark_gray") + " <gray>"
                + Msg.num(whose.xp(s)) + "/" + Msg.num(need) + " XP");
        lore.add("");
        lore.add("<gray>Now: <white>" + sk.bonusLine(s, lv));
        if (!max) {
            lore.add("<dark_gray>At " + sk.maxLevel() + ": " + sk.bonusLine(s, sk.maxLevel()));
        }
        if (s == Skill.VITALITY) {
            int next = sk.nextHeartLevel(lv);
            if (next > 0) {
                lore.add("<dark_gray>Next half heart at level " + next);
            }
            lore.add("<dark_gray>Grows from all your other skills (slowly).");
        }
        if (s == Skill.COMBAT) {
            lore.add("<dark_gray>Levels slower than the other skills.");
        }
        String ab = s.ability();
        if (ab != null) {
            lore.add("");
            int unlock = plugin.abilities().unlockLevel(ab);
            long cd = whose.cooldownLeft(ab);
            String state = lv < unlock ? "<red>unlocks at level " + unlock
                    : cd > 0 ? "<yellow>ready in " + Msg.time(cd) : "<green>ready";
            lore.add("<gold>" + s.abilityName() + "</gold> <dark_gray>·</dark_gray> " + state);
            lore.add("<gray>" + plugin.abilities().howTo(s));
            lore.add("<dark_gray>Cooldown " + Msg.time(plugin.abilities().cooldown(ab)));
        }
        if (s.direct() && plugin.money().available() && sk.payRate(s) > 0) {
            lore.add("");
            lore.add("<gold>Pays</gold> <gray>" + plugin.money().format(sk.payRate(s)) + " per XP, +"
                    + Msg.pct(sk.payLevelBonus(lv)) + " from your level");
        }
        ItemStack it = new ItemStack(s.icon());
        it.editMeta(m -> {
            m.displayName(Msg.mm(s.color() + "<bold>" + s.display() + "</bold> <white>" + lv));
            m.lore(Msg.lines(lore));
            m.addItemFlags(ItemFlag.values());
        });
        it.setAmount(Math.max(1, Math.min(64, lv)));
        return it;
    }

    @Override
    public void click(int slot, ClickType type) {
        if (!own()) {
            return;
        }
        switch (slot) {
            case 31 -> {
                whose.actionBar = !whose.actionBar;
                whose.dirty = true;
                render();
            }
            case 32 -> new GemMenu(plugin, player).open();
            case 33 -> new PartyMenu(plugin, player).open();
            default -> {
            }
        }
    }
}
