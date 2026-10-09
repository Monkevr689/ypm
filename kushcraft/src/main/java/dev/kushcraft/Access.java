package dev.kushcraft;

import dev.kushcraft.listener.PlayerListener;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Add-on mode: KushCraft is a side activity players unlock on purpose (a
 * cost, and a Farming level when SMPSuite is installed), it can't be set up
 * near spawn, and it pays a fraction of the full prices (market.income-
 * multiplier) so normal survival work stays the main way to earn.
 */
public final class Access {

    private final KushCraft plugin;
    private final File file;
    private final Set<UUID> unlocked = new HashSet<>();

    public Access(KushCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "access.yml");
    }

    public boolean gated() {
        return plugin.getConfig().getBoolean("access.unlock", true);
    }

    public double cost() {
        return Math.max(0, plugin.getConfig().getDouble("access.unlock-cost", 2500));
    }

    public String skill() {
        return plugin.getConfig().getString("access.skill", "farming");
    }

    public int skillLevel() {
        return Math.max(0, plugin.getConfig().getInt("access.skill-level", 10));
    }

    public int spawnDistance() {
        return Math.max(0, plugin.getConfig().getInt("access.spawn-distance", 300));
    }

    // ------------------------------------------------------------------
    // storage
    // ------------------------------------------------------------------

    /** Loads who unlocked it. The first time, players who already grew or sold here keep access. */
    public void load() {
        unlocked.clear();
        if (file.exists()) {
            for (String s : YamlConfiguration.loadConfiguration(file).getStringList("unlocked")) {
                try {
                    unlocked.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {
                    // not a player id
                }
            }
            return;
        }
        if (plugin.getConfig().getBoolean("access.grandfather", true)) {
            for (var r : plugin.economy().topSales()) {
                unlocked.add(r.id());
            }
            plugin.plants().all().forEach(p -> {
                if (p.owner() != null) {
                    unlocked.add(p.owner());
                }
            });
            plugin.machines().all().forEach(m -> {
                if (m.owner() != null) {
                    unlocked.add(m.owner());
                }
            });
            plugin.workers().all().forEach(w -> unlocked.add(w.owner()));
            if (!unlocked.isEmpty()) {
                plugin.getLogger().info("Add-on mode: " + unlocked.size()
                        + " players who already grew, cooked or sold here keep access to KushCraft.");
            }
        }
        save();
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> ids = new ArrayList<>();
        for (UUID u : unlocked) {
            ids.add(u.toString());
        }
        y.set("unlocked", ids);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save access.yml", e);
        }
    }

    // ------------------------------------------------------------------
    // checks
    // ------------------------------------------------------------------

    public boolean unlocked(UUID id) {
        return unlocked.contains(id);
    }

    /** May this player grow, cook, sell and use KushCraft items? */
    public boolean allowed(Player p) {
        return !gated() || p.hasPermission("kushcraft.admin") || unlocked.contains(p.getUniqueId());
    }

    /** The player's level in the SMPSuite skill, or -1 when SMPSuite isn't installed (no skill needed then). */
    public int playerSkill(OfflinePlayer p) {
        String skill = skill();
        if (skill == null || skill.isBlank() || skillLevel() <= 0) {
            return -1;
        }
        Plugin smp = Bukkit.getPluginManager().getPlugin("SMPSuite");
        if (smp == null || !smp.isEnabled()) {
            return -1;
        }
        try {
            Object lv = smp.getClass().getMethod("skillLevel", UUID.class, String.class).invoke(smp, p.getUniqueId(), skill);
            return lv instanceof Number n ? n.intValue() : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    /** Too close to the main world's spawn for KushCraft? */
    public boolean nearSpawn(Location l) {
        int d = spawnDistance();
        if (d <= 0 || l == null || l.getWorld() == null) {
            return false;
        }
        World main = Bukkit.getWorlds().get(0);
        if (!l.getWorld().equals(main)) {
            return false;
        }
        Location s = main.getSpawnLocation();
        double dx = l.getX() - s.getX(), dz = l.getZ() - s.getZ();
        return dx * dx + dz * dz < (double) d * d;
    }

    /** Checks spawn distance; tells the player when it's too close. */
    public boolean farEnough(Player p, Location l) {
        if (!nearSpawn(l) || p.hasPermission("kushcraft.admin")) {
            return true;
        }
        p.sendActionBar(Text.mm("<red>Not this close to spawn. <gray>KushCraft lives at least " + spawnDistance()
                + " blocks out."));
        return false;
    }

    // ------------------------------------------------------------------
    // unlocking
    // ------------------------------------------------------------------

    /** Explains what unlocking needs, with a clickable [Unlock]. */
    public void explain(Player p) {
        int need = skillLevel();
        int have = playerSkill(p);
        double money = plugin.economy().balance(p);
        p.sendMessage(Text.msg("<green>KushCraft</green> <gray>(the Drug Lab) is a side activity here: you unlock it when "
                + "you want it. Skills, building and exploring come first."));
        List<String> needs = new ArrayList<>();
        if (have >= 0) {
            needs.add((have >= need ? "<green>✔" : "<red>✘") + " " + cap(skill()) + " " + need + " <gray>(you: " + have + ")");
        }
        if (cost() > 0) {
            needs.add((money >= cost() ? "<green>✔" : "<red>✘") + " " + plugin.economy().format(cost())
                    + " <gray>(you have " + plugin.economy().format(money) + ")");
        }
        if (!needs.isEmpty()) {
            p.sendMessage(Text.mm("<gray>Needs: " + String.join(" <dark_gray>·</dark_gray> ", needs)));
        }
        p.sendMessage(Text.mm("<dark_gray>It can't be set up within " + spawnDistance() + " blocks of spawn. ")
                .append(Text.mm("<green><bold>[Unlock]").clickEvent(ClickEvent.runCommand("/kush unlock")))
                .append(Component.text(" "))
                .append(Text.mm("<aqua>[Trade shop]").clickEvent(ClickEvent.runCommand("/kush trade"))));
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    /** /kush unlock: checks the level, takes the cost, opens the doors. Returns an error, or null. */
    public String unlock(Player p) {
        if (!gated() || unlocked.contains(p.getUniqueId())) {
            return "KushCraft is already open to you.";
        }
        int have = playerSkill(p);
        if (have >= 0 && have < skillLevel()) {
            return "You need " + cap(skill()) + " " + skillLevel() + " first (you're " + have + "). /skills";
        }
        if (cost() > 0 && !plugin.economy().withdraw(p, cost())) {
            return "Unlocking costs " + plugin.economy().format(cost()) + ".";
        }
        grant(p.getUniqueId());
        p.playSound(p.getLocation(), "minecraft:ui.toast.challenge_complete", SoundCategory.PLAYERS, 0.6f, 1.2f);
        p.sendMessage(Text.msg("<green>KushCraft unlocked!</green> <gray>Here's the menu book and a starter kit."
                + " Remember: not within " + spawnDistance() + " blocks of spawn."));
        InventoryUtil.give(p, dev.kushcraft.item.Items.create(dev.kushcraft.item.ItemType.GROWER_GUIDE));
        PlayerListener.starterKit(p);
        return null;
    }

    /** Admin: unlock for free. */
    public void grant(UUID id) {
        unlocked.add(id);
        save();
    }

    public void revoke(UUID id) {
        unlocked.remove(id);
        save();
    }

    /** Message for a locked player who tried to use something. */
    public void denied(Player p) {
        p.sendActionBar(Text.mm("<gray>KushCraft is locked for you. <white>/kush</white> shows how to unlock it."));
    }
}
