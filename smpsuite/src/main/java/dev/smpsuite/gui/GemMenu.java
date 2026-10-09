package dev.smpsuite.gui;

import dev.smpsuite.Msg;
import dev.smpsuite.SMPSuite;
import dev.smpsuite.data.PlayerData;
import dev.smpsuite.gem.GemType;
import dev.smpsuite.gem.Gems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/** /gem: your gem, its energy, passives and abilities. */
public final class GemMenu extends Menu {

    private final SMPSuite plugin;

    public GemMenu(SMPSuite plugin, Player player) {
        super(player, 3, "<light_purple>Your Bliss gem");
        this.plugin = plugin;
    }

    @Override
    protected void draw() {
        Gems gems = plugin.gems();
        PlayerData d = plugin.store().get(player);
        if (d.gem == null) {
            set(13, icon(Material.BARRIER, "<gray>You don't have a gem yet",
                    gems.enabled() ? "<gray>An admin can give you one." : "<red>Gems are turned off."));
            fill(Material.BLACK_STAINED_GLASS_PANE);
            return;
        }
        GemType t = d.gem;
        set(13, gems.item(d));
        double per = plugin.getConfig().getDouble("gems.xp-per-energy", 2000);
        List<String> energy = new ArrayList<>();
        energy.add(Msg.bar(d.energy / (double) gems.maxEnergy(), gems.maxEnergy(), "yellow", "dark_gray") + " <white>"
                + d.energy + "/" + gems.maxEnergy());
        energy.add(d.energy >= gems.maxEnergy() ? "<gold>Full!"
                : "<gray>Next ⚡ in <white>" + Msg.num(Math.max(0, per - d.charge)) + "</white> skill XP");
        energy.add("<dark_gray>Any skill XP charges your gem.");
        energy.add("<dark_gray>PvP: a win takes 1⚡ from the loser.");
        energy.add("<dark_gray>At 0⚡ the gem sleeps until you recharge it.");
        set(11, icon(Material.GLOWSTONE_DUST, "<yellow>⚡ Energy", energy));
        List<String> passives = new ArrayList<>();
        for (String s : t.passives()) {
            passives.add("<white>" + s);
        }
        passives.add(gems.active(player) == t ? "<green>Working" : "<red>Not working <gray>(off hand + energy)");
        set(10, icon(Material.NETHER_STAR, "<aqua>Passives", passives));
        set(15, ability(t, d, false));
        set(16, ability(t, d, true));
        boolean carries = gems.carries(player);
        set(22, carries
                ? icon(Material.ENDER_EYE, "<gray>Reroll <dark_gray>(" + plugin.getConfig().getInt("gems.reroll-cost", 5) + "⚡)",
                "<gray>Trade your gem for a random other one.", "<dark_gray>/gem reroll")
                : icon(Material.RECOVERY_COMPASS, "<yellow>Get your gem back", "<gray>Lost it? Click for a new copy.",
                "<dark_gray>Old copies stop working."));
        fill(Material.BLACK_STAINED_GLASS_PANE);
    }

    private org.bukkit.inventory.ItemStack ability(GemType t, PlayerData d, boolean secondary) {
        Gems gems = plugin.gems();
        String name = secondary ? t.secondary() : t.primary();
        String key = Gems.cooldownKey(t, secondary);
        long left = d.cooldownLeft(key);
        boolean pockets = t == GemType.WEALTH && secondary;
        int need = pockets ? 0 : gems.energyFor(secondary);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + (secondary ? t.secondaryInfo() : t.primaryInfo()));
        lore.add("<gold>" + (secondary ? "Shift + F" : "F") + "</gold> <gray>with the gem in your off hand");
        if (!pockets) {
            lore.add("<dark_gray>Needs " + need + "⚡ · cooldown " + Msg.time(gems.cooldown(t, secondary, d.energy)));
        }
        lore.add(d.energy < need ? "<red>Not enough energy" : left > 0 ? "<yellow>Ready in " + Msg.time(left) : "<green>Ready");
        return icon(secondary ? Material.BLAZE_POWDER : Material.FIRE_CHARGE, "<" + t.hex() + "><bold>" + name, lore);
    }

    @Override
    public void click(int slot, ClickType type) {
        if (slot == 22 && !plugin.gems().carries(player) && plugin.store().get(player).gem != null) {
            plugin.gems().giveNew(player);
            Msg.send(player, "Here's your gem again. <gray>Older copies no longer work.");
            render();
        }
    }
}
