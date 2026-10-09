package dev.smpsuite.command;

import dev.smpsuite.Keys;
import dev.smpsuite.SMPSuite;
import dev.smpsuite.data.PlayerData;
import dev.smpsuite.gem.GemType;
import dev.smpsuite.gem.Gems;
import dev.smpsuite.skill.Abilities;
import dev.smpsuite.skill.Skill;
import dev.smpsuite.skill.Skills;
import dev.smpsuite.team.Team;
import dev.smpsuite.team.Teams;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * /smp selftest (console): checks the curve, every cap, the anti-stacking
 * rules, gem items and energy, teams, the placed-block tracker and the tree
 * feller on a test world. Logs SMPSELFTEST PASS / FAIL (CI greps for it).
 */
final class SelfTest {

    private final SMPSuite plugin;
    private final List<String> fails = new ArrayList<>();
    private int checks;

    SelfTest(SMPSuite plugin) {
        this.plugin = plugin;
    }

    private void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            fails.add(what);
        }
    }

    List<String> run() {
        try {
            curve();
            caps();
            combat();
            tables();
            abilities();
            gems();
            teams();
            world();
            pay();
        } catch (RuntimeException e) {
            fails.add("crashed: " + e);
            plugin.getLogger().log(java.util.logging.Level.WARNING, "selftest crashed", e);
        }
        plugin.getLogger().info("SMPSELFTEST " + (fails.isEmpty() ? "PASS" : "FAIL") + " - " + checks + " checks, "
                + fails.size() + " failed");
        for (String f : fails) {
            plugin.getLogger().warning("  selftest failure: " + f);
        }
        return fails;
    }

    // ------------------------------------------------------------------

    private void curve() {
        Skills s = plugin.skills();
        check(s.maxLevel() == 50, "max level 50");
        for (Skill k : Skill.values()) {
            for (int l = 1; l < s.maxLevel(); l++) {
                if (s.xpToNext(k, l) < s.xpToNext(k, l - 1)) {
                    check(false, "curve never gets easier: " + k + " " + l);
                    break;
                }
            }
        }
        double ten = s.totalXpFor(Skill.MINING, 10), quarter = s.totalXpFor(Skill.MINING, 25),
                all = s.totalXpFor(Skill.MINING, 50);
        // ~1,200 XP an hour of mining: level 10 in ~2 h, 25 in ~15 h, 50 in ~60 h
        check(ten > 1800 && ten < 3500, "level 10 takes about 2 hours (" + ten + " XP)");
        check(quarter > 12000 && quarter < 24000, "level 25 takes about 15 hours (" + quarter + " XP)");
        check(all > 55000 && all < 100000, "level 50 takes about 60 hours (" + all + " XP)");
        check(s.totalXpFor(Skill.COMBAT, 50) > all * 1.2, "combat levels slower than the other skills");
        check(s.totalXpFor(Skill.VITALITY, 50) > s.totalXpFor(Skill.COMBAT, 50), "vitality levels slowest");
    }

    private void caps() {
        Skills s = plugin.skills();
        int huge = 10_000;
        check(s.miningEfficiency(huge) <= 4.0 + 1e-9 && s.miningEfficiency(50) > 3.9, "mining speed capped at +4 (~1 Efficiency level)");
        check(s.miningEfficiency(1) < 0.5, "mining speed starts small");
        for (Skill k : List.of(Skill.MINING, Skill.FARMING, Skill.FORAGING, Skill.EXCAVATION)) {
            check(s.doubleDrop(k, huge) <= 0.20 + 1e-9, "double drops capped: " + k);
        }
        check(s.fasterBites(huge) <= 0.25 + 1e-9 && s.doubleCatch(huge) <= 0.10 + 1e-9, "fishing capped");
        check(s.foragingDamage(huge) <= 1.0 + 1e-9, "forager's strength capped at +1 damage");
        check(s.combatPercent(huge) <= 0.10 + 1e-9, "combat damage capped at +10%");
        check(s.vitalityHearts(huge) <= 2.0 + 1e-9 && s.vitalityHearts(9) == 0 && s.vitalityHearts(10) == 0.5,
                "vitality hearts: +0.5 at 10, capped at +2");
        check(s.maxHearts() == 12, "12 hearts at most");
        // combat bonuses scale slower than the non-combat ones (share of their cap at level 25)
        double combatShare = s.combatPercent(25) / s.combatPercent(huge);
        double miningShare = s.miningEfficiency(25) / s.miningEfficiency(huge);
        check(combatShare <= miningShare + 1e-9, "combat bonus grows no faster than mining's");
        check(s.totalXpFor(Skill.COMBAT, 25) > s.totalXpFor(Skill.MINING, 25), "and its levels cost more XP");
    }

    private void combat() {
        var c = plugin.combat();
        PlayerData max = new PlayerData(UUID.randomUUID());
        for (Skill k : Skill.values()) {
            max.set(k, 50, 0);
        }
        PlayerData none = new PlayerData(UUID.randomUUID());
        double sword = 7; // diamond sword
        double vsMob = c.bonus(max, 1.0, sword, true, false);
        double vsPlayer = c.bonus(max, 1.0, sword, true, true);
        check(vsMob <= sword * 0.20 + 1e-9 && vsMob > 0, "maxed skills + Strength gem vs a mob: at most +20% (" + vsMob + ")");
        check(vsPlayer <= sword * 0.08 + 1e-9 && vsPlayer > 0, "maxed skills vs a player: at most +8% (" + vsPlayer + ")");
        check(c.bonus(max, 1.0, sword, true, true) == c.bonus(max, 0, sword, true, true), "the Strength gem adds nothing vs players");
        check(c.bonus(none, 0, sword, true, false) == 0, "no levels, no bonus");
        check(c.bonus(none, 1.0, sword, true, false) == 1.0, "Strength gem alone: +1 vs mobs");
        check(c.bonus(max, 0, 1.0, true, false) <= 0.2 + 1e-9, "weak hits get a tiny bonus at most");
        check(c.bonus(max, 0, sword, false, false) <= sword * 0.10 + 1e-9, "ranged: combat percent only");
    }

    private void tables() {
        Skills s = plugin.skills();
        check(s.blockXp(Skill.MINING, Material.DIAMOND_ORE) == 18, "diamond ore XP");
        check(s.skillOf(Material.OAK_LOG) == Skill.FORAGING && s.skillOf(Material.CHERRY_LOG) == Skill.FORAGING,
                "logs (by tag) are foraging");
        check(s.skillOf(Material.GRAVEL) == Skill.EXCAVATION, "gravel is excavation");
        check(s.skillOf(Material.WHEAT) == Skill.FARMING && s.skillOf(Material.PUMPKIN) == Skill.FARMING, "crops are farming");
        check(s.skillOf(Material.COBBLESTONE) == null, "cobblestone gives nothing (no generator farms)");
        check(s.mobXp(EntityType.ZOMBIE, true) == 4 && s.mobXp(EntityType.WARDEN, true) == 150, "mob XP");
        check(s.mobXp(EntityType.COW, false) < 1, "animals give little");
        check(s.special("combat.player") > 0 && s.special("farming.breed") > 0 && s.special("fishing.fish") > 0,
                "player, breeding and fishing XP");
        // a casual non-PvP hour vs a PvP hour: PvP is never the fast way up
        double fishHour = 120 * s.special("fishing.fish");
        double pvpHour = 4 * s.special("combat.player"); // the per-victim cooldown limits repeat kills
        check(fishHour > pvpHour * 5, "an hour of fishing beats an hour of PvP for XP");
    }

    private void abilities() {
        Abilities a = plugin.abilities();
        for (String id : List.of(Abilities.HASTE, Abilities.FELLER)) {
            check(a.cooldown(id) >= 180 && a.cooldown(id) <= 300, "3-5 minute cooldown: " + id);
            check(a.unlockLevel(id) >= 5, "level-gated: " + id);
        }
        check(plugin.getConfig().getInt("skills.abilities.haste-pulse.amplifier") == 2
                && a.seconds(Abilities.HASTE) == 30, "Haste Pulse is Haste III for 30s");
        for (Skill k : Skill.values()) {
            if (k.ability() != null) {
                check(a.cooldown(k.ability()) >= 120, "abilities aren't spammable: " + k);
                check(!a.howTo(k).isEmpty(), "ability explained: " + k);
            }
        }
        check(Skill.COMBAT.ability() == null && Skill.VITALITY.ability() == null, "no combat ability (PvP-light)");
    }

    private void gems() {
        Gems g = plugin.gems();
        Set<Material> bases = EnumSet.noneOf(Material.class);
        Set<String> models = new HashSet<>();
        PlayerData d = new PlayerData(UUID.randomUUID());
        d.name = "Tester";
        d.energy = 5;
        d.gemSerial = 3;
        for (GemType t : GemType.values()) {
            bases.add(t.base());
            models.add(t.model());
            d.gem = t;
            ItemStack it = g.item(d);
            check(Gems.isGem(it) && Gems.typeOf(it) == t && d.id().equals(Gems.ownerOf(it)), "gem item round trip: " + t);
            check(it.getItemMeta().getMaxStackSize() == 1, "gems don't stack: " + t);
            check(it.getItemMeta().getCustomModelDataComponent().getStrings().contains(t.model()), "gem model: " + t);
            check(it.getPersistentDataContainer().get(Keys.GEM_SERIAL, PersistentDataType.INTEGER) == 3, "gem serial: " + t);
            check(!t.passives().isEmpty(), "passives: " + t);
            check(g.cooldown(t, false, 5) > 0, "primary cooldown: " + t);
            if (t != GemType.WEALTH) {
                check(g.cooldown(t, true, 5) > 0, "secondary cooldown: " + t);
            }
            check(g.cooldown(t, false, 10) < g.cooldown(t, false, 5) || g.cooldown(t, false, 5) < 7,
                    "high energy shortens cooldowns a little: " + t);
            check(g.cooldown(t, false, 10) >= g.cooldown(t, false, 5) * 0.85 - 1, "but only a little: " + t);
            for (String p : t.passives()) {
                String l = p.toLowerCase(java.util.Locale.ROOT);
                check(!l.contains("heart") || t == GemType.ASTRA, "no gem adds hearts: " + t);
                check(!l.contains("resistance") || l.contains("fire") || l.contains("knockback"), "no Resistance: " + t);
                check(!l.startsWith("strength"), "no Strength effect: " + t);
            }
        }
        check(bases.size() == GemType.values().length && models.size() == GemType.values().length, "8 different gems");
        // energy comes from playing
        PlayerData e = new PlayerData(UUID.randomUUID());
        e.gem = GemType.LIFE;
        e.energy = 5;
        double per = plugin.getConfig().getDouble("gems.xp-per-energy");
        check(g.chargeData(e, per - 1) == 0 && e.energy == 5, "not yet a point of energy");
        check(g.chargeData(e, 1) == 1 && e.energy == 6, "skill XP charges the gem");
        e.energy = g.maxEnergy();
        check(g.chargeData(e, per * 3) == 0 && e.energy == g.maxEnergy() && e.charge == 0, "energy stops at the max");
        e.energy = 0;
        check(g.chargeData(e, per) == 1 && e.energy == 1, "a sleeping gem wakes up from survival play");
        check(per <= 3000, "a few hours of normal play fill a gem");
        check(plugin.getConfig().getInt("gems.pvp-kill-energy") <= 1, "PvP gives at most 1 energy a kill");
    }

    private void teams() {
        Teams t = plugin.teams();
        check(t.validName("ab") != null && t.validName("Builders") == null && t.validName("x".repeat(40)) != null,
                "team name rules");
        check(t.validTag("TOOLONG") != null && t.validTag("ABC") == null, "tag rules");
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        String name = "Test " + a.toString().substring(0, 6);
        check(t.create(a, name, null, NamedTextColor.AQUA) == null, "make a team");
        Team team = t.of(a);
        check(team != null && team.leader().equals(a) && !team.tag().isEmpty(), "leader and a tag");
        check(t.create(a, name + "x", null, null) != null, "one team at a time");
        check(t.join(b, team) == null && t.sameTeam(a, b) && !t.sameTeam(a, c), "join and same-team check");
        var board = Bukkit.getScoreboardManager().getMainScoreboard().getTeam("smp_" + team.id().toString().substring(0, 8));
        check(board != null && !board.allowFriendlyFire(), "name tag team without friendly fire");
        check(t.byName(name) == team, "find a team by name");
        t.remove(a);
        check(team.leader().equals(b) && t.of(a) == null, "a leaving leader hands over");
        t.remove(b);
        check(t.of(b) == null && t.byName(name) == null, "the last one out closes the team");
        check(Bukkit.getScoreboardManager().getMainScoreboard().getTeam("smp_" + team.id().toString().substring(0, 8)) == null,
                "and its name tag team");
        check(plugin.getConfig().getDouble("teams.shared-xp") <= 0.25, "shared XP is a small bonus");
    }

    private void world() {
        World w = Bukkit.getWorlds().get(0);
        Location spawn = w.getSpawnLocation();
        int x = spawn.getBlockX() + 60, z = spawn.getBlockZ() + 60;
        int y = Math.max(w.getMinHeight() + 5, Math.min(w.getMaxHeight() - 40, w.getHighestBlockYAt(x, z) + 1));
        // placed blocks give nothing
        Block ore = w.getBlockAt(x, y, z);
        ore.setType(Material.DIAMOND_ORE);
        check(!plugin.placed().isPlaced(ore), "natural ore");
        plugin.placed().mark(ore);
        check(plugin.placed().isPlaced(ore), "placed ore is remembered");
        plugin.placed().unmark(ore);
        check(!plugin.placed().isPlaced(ore), "and forgotten again");
        ore.setType(Material.AIR);
        // tree feller: a trunk with a branch, one placed log that must stay
        List<Block> tree = new ArrayList<>();
        for (int dy = 0; dy < 6; dy++) {
            tree.add(w.getBlockAt(x + 3, y + dy, z));
        }
        tree.add(w.getBlockAt(x + 4, y + 4, z));
        tree.add(w.getBlockAt(x + 5, y + 5, z + 1));
        for (Block b : tree) {
            b.setType(Material.OAK_LOG);
        }
        Block placedLog = w.getBlockAt(x + 3, y + 6, z);
        placedLog.setType(Material.OAK_LOG);
        plugin.placed().mark(placedLog);
        List<Block> found = plugin.abilities().treeAround(tree.get(0), 150);
        check(found.size() == tree.size() - 1, "tree feller finds the whole tree (" + found.size() + ")");
        check(!found.contains(placedLog), "tree feller skips logs players placed");
        check(plugin.abilities().treeAround(tree.get(0), 3).size() == 3, "tree feller has a log limit");
        for (Block b : tree) {
            b.setType(Material.AIR);
        }
        plugin.placed().unmark(placedLog);
        placedLog.setType(Material.AIR);
    }

    private void pay() {
        Skills s = plugin.skills();
        check(s.payRate(Skill.VITALITY) == 0, "vitality doesn't pay");
        check(s.payRate(Skill.COMBAT) <= s.payRate(Skill.MINING) && s.payRate(Skill.COMBAT) <= s.payRate(Skill.FARMING),
                "combat pays no more than the survival skills");
        check(s.payLevelBonus(10_000) <= 0.5 + 1e-9, "pay level bonus capped");
        check(plugin.getConfig().getDouble("pay.hourly-cap") > 0, "an hourly pay cap stops AFK farms");
        // an hour of each at level 1 (rough activity rates), none far above the others
        double mining = s.payFor(null, Skill.MINING, 1, 2000 * 0.2 + 150 * 4.5);
        double farming = s.payFor(null, Skill.FARMING, 1, 1500 * 1.0);
        double combat = s.payFor(null, Skill.COMBAT, 1, 200 * 5.0);
        check(combat <= mining && combat <= farming, "an hour of fighting pays less than mining or farming");
        check(plugin.skillLevel(UUID.randomUUID(), "farming") == 0 && plugin.skillLevel(UUID.randomUUID(), "nope") == -1,
                "skill level API (used by KushCraft)");
        plugin.getLogger().info("Jobs pay goes through: " + plugin.money().mode());
    }
}
