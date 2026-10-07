package dev.kushcraft.command;

import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
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
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /kush selftest live (console, admin): a minute or two of real work on the
 * server's own clock, nobody online. A Farmhand, a Dryer at a Drug Lab, a
 * Cook rolling joints and a Runner around a small farm with eight ripe
 * plants, empty farmland and a chest with fertilizer: the workers walk,
 * harvest, plant, fetch from the chest, dry, buy what's missing (seeds,
 * papers), roll and sell by themselves - and the worker tick has to stay
 * cheap. Logs LIVETEST PASS / FAIL (used by CI). Safe on a test world.
 */
final class LiveTest {

    private static final int TIMEOUT_SECONDS = 150;
    /** Average milliseconds a server tick may spend on the workers. */
    private static final double MAX_AVERAGE_MS = 1.5;

    private final KushCraft plugin;
    private final List<long[]> tickets = new ArrayList<>();

    LiveTest(KushCraft plugin) {
        this.plugin = plugin;
    }

    private static int count(Inventory inv, ItemType t) {
        int n = 0;
        if (inv != null) {
            for (ItemStack it : inv.getStorageContents()) {
                if (Items.type(it) == t) {
                    n += it.getAmount();
                }
            }
        }
        return n;
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
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
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
        // a chest of fertilizer by the farm: the Farmhand fetches it from there
        Block chestBlock = w.getBlockAt(x - 7, y + 1, z - 4);
        chestBlock.setType(Material.CHEST);
        Inventory chest = ((Container) chestBlock.getState(false)).getInventory();
        chest.addItem(Items.create(ItemType.FERTILIZER, 16));
        int oldDrying = plugin.getConfig().getInt("drying.seconds", 30);
        plugin.getConfig().set("drying.seconds", 5);
        Machine lab = plugin.machines().placeAt(w.getBlockAt(x + 5, y + 1, z + 3), MachineType.LAB_STATION, 0f, boss);
        Workers ws = plugin.workers();
        Worker farm = ws.hireAt(new Location(w, x + 0.5, y + 1, z - 1 + 0.5), WorkerType.FARMHAND, boss, 3);
        Worker dryer = ws.hireAt(new Location(w, x + 5.5, y + 1, z + 5.5), WorkerType.DRYER, boss, 3);
        Worker cook = ws.hireAt(new Location(w, x + 2.5, y + 1, z + 7.5), WorkerType.COOK, boss, 3);
        ws.setJob(cook, Worker.ROLL_JOINT);
        Worker runner = ws.hireAt(new Location(w, x - 3 + 0.5, y + 1, z + 3.5), WorkerType.RUNNER, boss, 3);
        ws.setAutoBuy(boss, true);
        ws.resetTiming();
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
                double[] timing = ws.timing();
                List<String> missing = new ArrayList<>();
                if (stillRipe > 0) {
                    missing.add(stillRipe + " ripe plants not harvested");
                }
                if (planted < farmland.size() * 3 / 4) {
                    missing.add("only " + planted + "/" + farmland.size() + " farmland planted");
                }
                if (count(chest, ItemType.FERTILIZER) > 0) {
                    missing.add("the farmhand didn't fetch the fertilizer from the chest");
                }
                if (dryer.jobs() < 2) {
                    missing.add("the dryer hung and collected nothing (" + dryer.jobs() + " jobs)");
                }
                if (cook.jobs() < 1) {
                    missing.add("the cook rolled nothing (they have to buy papers)");
                }
                if (eco.sales(owner) <= 0) {
                    missing.add("the runner sold nothing");
                }
                if (farm.freeSlots() < 4) {
                    missing.add("the farmhand's satchel is full");
                }
                if (seconds >= 10 && timing[0] > MAX_AVERAGE_MS) {
                    missing.add(String.format(java.util.Locale.ROOT, "workers take %.3f ms a tick on average (max %.1f)",
                            timing[0], MAX_AVERAGE_MS));
                }
                if (seconds % 10 == 0 || (missing.isEmpty() && seconds >= 10)) {
                    plugin.getLogger().info("LIVETEST " + seconds + "s: planted " + planted + "/" + farmland.size()
                            + ", ripe left " + stillRipe + ", sales " + eco.format(eco.sales(owner)) + ", money left "
                            + eco.format(eco.balance(owner)) + String.format(java.util.Locale.ROOT,
                            ", worker tick avg %.3f ms, slowest %.2f ms", timing[0], timing[1]));
                    for (Worker wk : List.of(farm, dryer, cook, runner)) {
                        plugin.getLogger().info("  " + wk.type().display() + ": " + wk.jobs() + " jobs, "
                                + wk.carried() + " carried - " + Text.plain(Text.mm(String.valueOf(wk.status()))));
                    }
                }
                if (!(missing.isEmpty() && seconds >= 10) && seconds < TIMEOUT_SECONDS) {
                    return;
                }
                cancel();
                plugin.getLogger().info("LIVETEST " + (missing.isEmpty() ? "PASS" : "FAIL") + " after " + seconds + " s"
                        + String.format(java.util.Locale.ROOT, " (worker tick avg %.3f ms, slowest %.2f ms)",
                        timing[0], timing[1]));
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
                chest.clear();
                chestBlock.setType(Material.AIR);
                for (Entity e : w.getNearbyEntities(new Location(w, x, y + 1, z), 12, 5, 12)) {
                    if (e instanceof Item) {
                        e.remove();
                    }
                }
                for (long[] t : tickets) {
                    w.removePluginChunkTicket((int) t[0], (int) t[1], plugin);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
}
