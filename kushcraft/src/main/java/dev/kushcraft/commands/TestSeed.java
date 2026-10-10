package dev.kushcraft.commands;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.plants.Plant;
import dev.kushcraft.storage.PlayerRecord;
import dev.kushcraft.strains.Strain;
import dev.kushcraft.util.BlockKey;
import dev.kushcraft.workers.Worker;
import dev.kushcraft.workers.WorkerType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.UUID;

/**
 * /kush selftest seed (console, admin): leaves one made-up player with
 * progress on a test server, so a season reset in CI has something real to
 * wipe and the backup something real to keep: $12,345, rank 3, 2 hours of
 * playtime, $5,000 of sales, a Farmhand with 7 fertilizer in their satchel,
 * a plant and a cartel. Logs "SEED ready" once it is all in the database.
 */
final class TestSeed {

    /** The made-up player (CI looks for this id in the database). */
    static final UUID KEEPER = UUID.fromString("00000000-0000-0000-0000-00000000c1c1");

    private TestSeed() {
    }

    static void run(KushCraft plugin, CommandSender sender) {
        World w = Bukkit.getWorlds().get(0);
        Location spawn = w.getSpawnLocation();
        int x = spawn.getBlockX() - 20, z = spawn.getBlockZ() - 20;
        int y = w.getHighestBlockYAt(x, z);
        PlayerRecord r = plugin.players().getOrCreate(KEEPER, "CI-keeper");
        var keeper = Bukkit.getOfflinePlayer(KEEPER);
        plugin.economy().set(keeper, 12_345, "seed");
        plugin.economy().setSales(keeper, 5_000, "seed");
        plugin.ranks().set(KEEPER, 3, "seed");
        r.setPlaytime(7_200);
        // a plant on farmland, a Farmhand next to it carrying fertilizer
        w.getBlockAt(x, y, z).setType(Material.FARMLAND);
        w.getBlockAt(x + 1, y, z).setType(Material.WATER);
        Strain strain = plugin.strains().get("og_kush");
        if (strain == null) {
            strain = plugin.strains().all().iterator().next();
        }
        Plant plant = plugin.plants().plantAt(BlockKey.of(w.getBlockAt(x, y + 1, z)), Plant.Kind.CANNABIS, strain, KEEPER);
        Worker farm = plugin.workers().hireAt(new Location(w, x + 0.5, y + 1, z + 2.5), WorkerType.FARMHAND, KEEPER, 1);
        farm.satchel().addItem(Items.create(ItemType.FERTILIZER, 7));
        plugin.workers().touch(farm, true);
        if (plugin.cartels().of(KEEPER) == null) {
            plugin.cartels().found(KEEPER, "CI Keepers");
        }
        boolean saved = plugin.persistence().flushNow();
        String line = "SEED " + (saved && plant != null ? "ready" : "FAILED") + ": CI-keeper " + KEEPER + " with "
                + plugin.economy().format(plugin.economy().balance(keeper)) + ", rank " + r.rank() + ", a "
                + farm.type().display() + ", " + (plant != null ? "a plant" : "no plant") + " and a cartel";
        plugin.getLogger().info(line);
        sender.sendMessage(line);
    }
}
