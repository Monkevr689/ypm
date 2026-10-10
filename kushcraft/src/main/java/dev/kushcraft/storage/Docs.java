package dev.kushcraft.storage;

import dev.kushcraft.KushCraft;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Small whole-system states (market prices and contracts, Trade prices) are
 * one YAML text each in the docs table, written with the same snapshot as
 * everything else.
 */
public final class Docs {

    private final KushCraft plugin;
    private final Database db;

    public Docs(KushCraft plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    /** The saved state, or the old .yml file the first time (8.0 and older), or null when there's none. */
    public YamlConfiguration read(String name, File legacy) {
        String text = db.call(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT data FROM docs WHERE name=?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
        if (text != null) {
            YamlConfiguration y = new YamlConfiguration();
            try {
                y.loadFromString(text);
                return y;
            } catch (InvalidConfigurationException e) {
                plugin.getLogger().warning("The saved " + name + " state is broken - starting it fresh.");
                return null;
            }
        }
        if (legacy != null && legacy.exists() && !plugin.legacyImported()) {
            plugin.legacyFile(legacy);
            return YamlConfiguration.loadConfiguration(legacy);
        }
        return null;
    }

    /** Adds the write of a state to a snapshot. */
    public static void write(Persistence.Batch b, String name, String text, Runnable failed) {
        b.write(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO docs(name, data) VALUES(?,?) ON CONFLICT(name) DO UPDATE SET data=excluded.data")) {
                ps.setString(1, name);
                ps.setString(2, text);
                ps.executeUpdate();
            }
        });
        b.onFailure(failed);
    }
}
