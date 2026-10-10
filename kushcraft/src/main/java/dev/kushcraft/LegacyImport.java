package dev.kushcraft;

import dev.kushcraft.economy.Economy;
import dev.kushcraft.economy.Tx;
import dev.kushcraft.storage.PlayerRecord;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.UUID;

/**
 * 8.0 and older kept balances and lifetime sales in balances.yml. The first
 * start of 9.0 reads them into the database (logged as START "imported"),
 * and the file is moved to legacy-yaml/ with the other old files.
 */
final class LegacyImport {

    private LegacyImport() {
    }

    static void balances(KushCraft plugin) {
        File file = new File(plugin.getDataFolder(), "balances.yml");
        if (!file.exists() || plugin.players().size() > 0) {
            return;
        }
        plugin.legacyFile(file);
        ConfigurationSection sec = YamlConfiguration.loadConfiguration(file).getConfigurationSection("players");
        if (sec == null) {
            return;
        }
        int n = 0;
        for (String k : sec.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(k);
            } catch (IllegalArgumentException e) {
                continue;
            }
            PlayerRecord r = plugin.players().getOrCreate(id, sec.getString(k + ".name", "?"));
            long balance = Math.max(0, Economy.cents(sec.getDouble(k + ".balance",
                    plugin.economy().startingBalance())));
            r.balance(balance);
            r.sales(Math.max(0, Economy.cents(sec.getDouble(k + ".sales"))));
            plugin.economy().ledger().log(id, Tx.START, balance, balance, null, "imported from balances.yml");
            n++;
        }
        plugin.getLogger().info("Imported " + n + " balances from balances.yml.");
    }
}
