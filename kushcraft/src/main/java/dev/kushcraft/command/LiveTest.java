package dev.kushcraft.command;

import dev.kushcraft.KushCraft;
import dev.kushcraft.machine.Machine;
import dev.kushcraft.machine.MachineType;
import dev.kushcraft.plant.Plant;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.BlockKey;
import dev.kushcraft.util.Text;
import dev.kushcraft.worker.Worker;
import dev.kushcraft.worker.WorkerType;
import dev.kushcraft.worker.Workers;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /kush selftest live (console, admin): a minute or two of real work on the
 * server's own clock, nobody online. A Farmhand, a Runner, a Dryer at a Drug
 * Lab and a Supplier around a small farm with eight ripe plants and empty
 * farmland: the workers walk, harvest, plant, carry, dry, buy and sell by
 * themselves. Logs LIVETEST PASS / FAIL (used by CI). Safe on a test world.
 */
final class LiveTest {

    private static final int TIMEOUT_SECONDS = 150;

    private final KushCraft plugin;
    private final List<long[]> tickets = new ArrayList<>();

    LiveTest(KushCraft plugin) {
        this.plugin = plugin;
    }

    void start(CommandSender sender) {
        World w = Bukkit.getWorlds().get(0);
        Location spawn = w.getSpawnLocation();
        int x = spawn.getBlockX() + 40, z = spawn.getBlockZ() + 40;
        for (int cx = (x - 24) >> 4; cx <= (x + 24) >> 4; cx++) {
            for (int cz = (z - 24) >> 4; cz <= (z + 24) >> 4; cz++) {
                w.addPluginChunkTicket(cx, cz, plugin);
                tickets.add(new long[]{cx, cz});
            }
        }
        int y = w.getHighestBlockYAt(x, z);
        // a stone yard with air above it
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                w.getBlockAt(x + dx, y, z + dz).setType(Material.STONE);
                for (int dy = 1; dy <= 5; dy++) {
                    w.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR);
                }
            }
        }
        // the farm: four rows of farmland (dz -6..-3) either side of a water channel (dx 0)
        List<BlockKey> farmland = new ArrayList<>();
        for (int dz = -6; dz <= -3; dz++) {
            w.getBlockAt(x, y, z + dz).setType(Material.WATER);
            for (int dx = -4; dx <= 4; dx++) {
                if (dx != 0) {
                    w.getBlockAt(x + dx, y, z + dz).setType(Material.FARMLAND);
                    farmland.add(BlockKey.of(w.getBlockAt(x + dx, y + 1, z + dz)));
                }
            }
        }
        UUID boss = UUID.randomUUID();
        var owner = Bukkit.getOfflinePlayer(boss);
        var eco = plugin.economy();
        eco.set(owner, 100_000);
        Strain strain = plugin.strains().get("og_kush");
        if (strain == null) {
            strain = plugin.strains().all().iterator().next();
        }
        // eight ripe plants on the far row, the rest of the farm empty
        List<BlockKey> ripeAt = new ArrayList<>();
        for (BlockKey k : farmland) {
            if (k.z() == z - 6) {
                Plant p = plugin.plants().plantAt(k, Plant.Kind.CANNABIS, strain, boss);
                p.growth(100);
                ripeAt.add(k);
            }
        }
        int oldDrying = plugin.getConfig().getInt("drying.seconds", 30);
        plugin.getConfig().set("drying.seconds", 5);
        Machine lab = plugin.machines().placeAt(w.getBlockAt(x + 5, y + 1, z + 3), MachineType.LAB_STATION, 0f, boss);
        Workers ws = plugin.workers();
        ws.refreshAreas();
        Worker farm = ws.hireAt(new Location(w, x + 0.5, y + 1, z - 1 + 0.5), WorkerType.FARMHAND, boss, 3);
        Worker dryer = ws.hireAt(new Location(w, x + 5.5, y + 1, z + 5.5), WorkerType.DRYER, boss, 3);
        Worker runner = ws.hireAt(new Location(w, x - 3 + 0.5, y + 1, z + 3.5), WorkerType.RUNNER, boss, 3);
        Worker supplier = ws.hireAt(new Location(w, x - 6 + 0.5, y + 1, z + 6.5), WorkerType.SUPPLIER, boss, 3);
        plugin.getLogger().info("LIVETEST started: " + farmland.size() + " farmland, " + ripeAt.size()
                + " ripe plants, 4 workers (up to " + TIMEOUT_SECONDS + " s)");
        sender.sendMessage(Text.msg("<gray>Live test running (up to " + TIMEOUT_SECONDS + " s)..."));
        new BukkitRunnable() {
            int seconds;

            @Override
            public void run() {
                seconds++;
                int planted = 0;
                for (BlockKey k : farmland) {
                    if (plugin.plants().at(k) != null) {
                        planted++;
                    }
                }
                int stillRipe = 0;
                for (BlockKey k : ripeAt) {
                    Plant p = plugin.plants().at(k);
                    if (p != null && p.mature()) {
                        stillRipe++;
                    }
                }
                List<String> missing = new ArrayList<>();
                if (stillRipe > 0) {
                    missing.add(stillRipe + " ripe plants not harvested");
                }
                if (planted < farmland.size() * 3 / 4) {
                    missing.add("only " + planted + "/" + farmland.size() + " farmland planted");
                }
                if (supplier.spent() <= 0) {
                    missing.add("the supplier bought nothing");
                }
                if (dryer.jobs() < 2) {
                    missing.add("the dryer hung and collected nothing (" + dryer.jobs() + " jobs)");
                }
                if (eco.sales(owner) <= 0) {
                    missing.add("the runner sold nothing");
                }
                if (farm.freeSlots() < 4) {
                    missing.add("the farmhand's satchel is full");
                }
                if (seconds % 10 == 0 || (missing.isEmpty() && seconds >= 3)) {
                    plugin.getLogger().info("LIVETEST " + seconds + "s: planted " + planted + "/" + farmland.size()
                            + ", ripe left " + stillRipe + ", sales " + eco.format(eco.sales(owner)) + ", supplier spent "
                            + eco.format(supplier.spent()) + ", seeds in backpack " + farm.seedCount());
                    for (Worker wk : List.of(farm, runner, dryer, supplier)) {
                        plugin.getLogger().info("  " + wk.type().display() + ": " + wk.jobs() + " jobs, "
                                + wk.carried() + " carried - " + Text.plain(Text.mm(String.valueOf(wk.status()))));
                    }
                }
                if (!missing.isEmpty() && seconds < TIMEOUT_SECONDS) {
                    return;
                }
                cancel();
                plugin.getLogger().info("LIVETEST " + (missing.isEmpty() ? "PASS" : "FAIL") + " after " + seconds + " s");
                for (String m : missing) {
                    plugin.getLogger().warning("  livetest failure: " + m);
                }
                // clean up
                plugin.getConfig().set("drying.seconds", oldDrying);
                for (Worker wk : new ArrayList<>(ws.of(boss))) {
                    ws.dismiss(wk, null);
                }
                for (BlockKey k : farmland) {
                    Plant p = plugin.plants().at(k);
                    if (p != null) {
                        plugin.plants().remove(p);
                    }
                }
                if (lab != null) {
                    plugin.machines().breakMachine(lab, null);
                }
                for (Entity e : w.getNearbyEntities(new Location(w, x, y + 1, z), 10, 5, 10)) {
                    if (e instanceof Item) {
                        e.remove();
                    }
                }
                for (long[] t : tickets) {
                    w.removePluginChunkTicket((int) t[0], (int) t[1], plugin);
                }
                ws.syncChunks();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
}
