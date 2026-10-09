package dev.smpsuite;

import dev.smpsuite.command.AdminCommand;
import dev.smpsuite.command.GemCommand;
import dev.smpsuite.command.PartyCommand;
import dev.smpsuite.command.SkillsCommand;
import dev.smpsuite.command.VoiceCommand;
import dev.smpsuite.data.PlayerData;
import dev.smpsuite.data.PlayerStore;
import dev.smpsuite.economy.Money;
import dev.smpsuite.gem.GemListener;
import dev.smpsuite.gem.Gems;
import dev.smpsuite.gui.MenuListener;
import dev.smpsuite.pack.PackSender;
import dev.smpsuite.skill.Abilities;
import dev.smpsuite.skill.Combat;
import dev.smpsuite.skill.PlacedBlocks;
import dev.smpsuite.skill.Skill;
import dev.smpsuite.skill.SkillListener;
import dev.smpsuite.skill.Skills;
import dev.smpsuite.team.Teams;
import dev.smpsuite.voice.VoiceBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * SMPSuite: skills with capped bonuses and jobs pay, skill abilities, Bliss
 * gems, opt-in teams and Simple Voice Chat groups - a survival-first SMP where
 * nothing stacks into a PvP or economy runaway.
 */
public final class SMPSuite extends JavaPlugin implements Listener {

    private static SMPSuite instance;

    private PlayerStore store;
    private Money money;
    private Skills skills;
    private PlacedBlocks placed;
    private Abilities abilities;
    private Combat combat;
    private SkillListener skillListener;
    private Gems gems;
    private GemListener gemListener;
    private Teams teams;
    private VoiceBridge voice;
    private PackSender pack;

    public static SMPSuite get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Keys.init(this);
        store = new PlayerStore(this);
        money = new Money(this);
        skills = new Skills(this);
        skills.load();
        placed = new PlacedBlocks(this);
        abilities = new Abilities(this);
        combat = new Combat(this);
        skillListener = new SkillListener(this);
        gems = new Gems(this);
        gemListener = new GemListener(this);
        teams = new Teams(this);
        voice = new VoiceBridge(this);
        pack = new PackSender(this);

        store.start();
        teams.load();

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(placed, this);
        pm.registerEvents(skillListener, this);
        pm.registerEvents(abilities, this);
        pm.registerEvents(combat, this);
        pm.registerEvents(gemListener, this);
        pm.registerEvents(teams, this);
        pm.registerEvents(pack, this);
        pm.registerEvents(new MenuListener(), this);

        command("skills", new SkillsCommand(this));
        command("gem", new GemCommand(this));
        PartyCommand party = new PartyCommand(this);
        command("party", party);
        command("tc", party);
        command("vc", new VoiceCommand(this));
        command("smp", new AdminCommand(this));

        skills.start();
        gems.start();
        pack.start();
        for (Player p : Bukkit.getOnlinePlayers()) {
            joined(p);
        }
        // Vault providers and Simple Voice Chat finish starting after us
        Bukkit.getScheduler().runTask(this, () -> {
            money.hook();
            voice.hook();
        });
        getLogger().info("SMPSuite enabled - skills " + on(skills.enabled()) + ", gems " + on(gems.enabled())
                + ", teams " + on(teams.enabled()) + " (" + teams.all().size() + " teams).");
    }

    private static String on(boolean b) {
        return b ? "on" : "off";
    }

    private void command(String name, Object handler) {
        PluginCommand c = getCommand(name);
        if (c != null) {
            c.setExecutor((CommandExecutor) handler);
            if (handler instanceof TabCompleter tc) {
                c.setTabCompleter(tc);
            }
        }
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.stop();
        }
        if (teams != null) {
            teams.save();
        }
    }

    /** /smp reload. */
    public void reload() {
        reloadConfig();
        skills.load();
        teams.load();
        for (Player p : Bukkit.getOnlinePlayers()) {
            skills.applyAttributes(p);
        }
        money.hook();
    }

    // ------------------------------------------------------------------
    // players joining and leaving
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        joined(e.getPlayer());
    }

    private void joined(Player p) {
        store.get(p);
        skills.applyAttributes(p);
        teams.joined(p);
        gems.firstJoin(p);
        gems.refreshItems(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        skills.quit(id);
        abilities.quit(id);
        skillListener.quit(id);
        gemListener.quit(id);
        teams.quit(id);
        store.unload(id);
    }

    // ------------------------------------------------------------------
    // API (KushCraft asks for skill levels through this, by reflection)
    // ------------------------------------------------------------------

    /** A player's level in a skill ("mining", "farming"...), or -1 for an unknown skill. */
    public int skillLevel(UUID player, String skill) {
        Skill s = Skill.parse(skill);
        if (s == null) {
            return -1;
        }
        PlayerData d = Bukkit.getPlayer(player) != null || store.known(player) ? store.get(player) : null;
        return d == null ? 0 : d.level(s);
    }

    // ------------------------------------------------------------------
    // modules
    // ------------------------------------------------------------------

    public PlayerStore store() {
        return store;
    }

    public Money money() {
        return money;
    }

    public Skills skills() {
        return skills;
    }

    public PlacedBlocks placed() {
        return placed;
    }

    public Abilities abilities() {
        return abilities;
    }

    public Combat combat() {
        return combat;
    }

    public Gems gems() {
        return gems;
    }

    public Teams teams() {
        return teams;
    }

    public VoiceBridge voice() {
        return voice;
    }

    public PackSender pack() {
        return pack;
    }
}
