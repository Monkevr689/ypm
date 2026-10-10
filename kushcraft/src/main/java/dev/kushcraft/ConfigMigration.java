package dev.kushcraft;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Brings an older config.yml up to date.
 *
 * 9.0 (config-version 13) is a relaunch: the config was regrouped by system
 * (storage, economy, ranks, workers, pvp, menus, reset...), every price you
 * pay went up about 5x, there's a rank ladder, and worker auto-buy is off.
 * An older config.yml is therefore kept as config-old-v&lt;version&gt;.yml and
 * a fresh, fully commented config.yml is written - with the settings that
 * belong to your server (resource pack link, world, effects, which systems
 * are on) carried over. Prices are NOT carried over: they're the 9.0 ones.
 *
 * Later versions only add what's missing and keep everything you set.
 */
final class ConfigMigration {

    static final int VERSION = 13;

    /** Settings carried over into the fresh 9.0 config (everything under these paths). */
    private static final List<String> KEEP = List.of(
            "resource-pack", "effects", "animals", "wild", "harvest", "drying", "menu.shift-f",
            "menu.give-book-on-command", "growth.tick-seconds", "growth.cannabis-minutes", "growth.mushroom-minutes",
            "growth.coca-minutes", "growth.poppy-minutes", "growth.peyote-minutes", "growth.min-light",
            "growth.lamp-radius", "growth.respect-protection", "lab.time-multiplier", "strain-maker.max-per-player",
            "strain-maker.seeds-given", "cartel.enabled", "cartel.max-name-length", "workers.enabled",
            "workers.chain-radius", "workers.radius", "workers.rest-seconds", "exchange.enabled", "market.anywhere",
            "jobs.enabled", "awards.advancements", "economy.symbol", "dealer-titles.titles", "dealer-titles.everyone",
            "ranks.tab-list", "new-players.starter-kit", "new-players.give-guide", "pvp.death-cash-lost");

    private ConfigMigration() {
    }

    static void run(KushCraft plugin) {
        int version = plugin.getConfig().getInt("config-version", 1);
        InputStream in = plugin.getResource("config.yml");
        if (in == null) {
            return;
        }
        YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        if (version >= VERSION) {
            // same or newer: only fill in what's missing
            plugin.getConfig().setDefaults(def);
            return;
        }
        File file = new File(plugin.getDataFolder(), "config.yml");
        org.bukkit.configuration.file.FileConfiguration old = plugin.getConfig();
        renameOldKeys(old, version);
        File kept = new File(plugin.getDataFolder(), "config-old-v" + version + ".yml");
        try {
            Files.copy(file.toPath(), kept.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.saveResource("config.yml", true);
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().severe("Could not write the new config.yml (" + e.getMessage() + ") - using the defaults.");
            return;
        }
        plugin.reloadConfig();
        org.bukkit.configuration.file.FileConfiguration fresh = plugin.getConfig();
        List<String> carried = new ArrayList<>();
        for (String path : KEEP) {
            if (old.isConfigurationSection(path)) {
                ConfigurationSection sec = old.getConfigurationSection(path);
                for (String k : sec.getKeys(true)) {
                    if (!sec.isConfigurationSection(k)) {
                        fresh.set(path + "." + k, sec.get(k));
                    }
                }
                carried.add(path);
            } else if (old.isSet(path)) {
                fresh.set(path, old.get(path));
                carried.add(path);
            }
        }
        // players on most hosts can't reach the built-in pack server: use the hosted copy
        String url = fresh.getString("resource-pack.url", "");
        if (url == null || url.isBlank() || url.contains("raw.githubusercontent.com/Monkevr689/ypm/")) {
            fresh.set("resource-pack.url", "auto");
        }
        // 8.1's add-on mode put a Dealer Stand in the starter kit: not any more
        List<Map<?, ?>> kit = new ArrayList<>(fresh.getMapList("new-players.starter-kit"));
        if (version == 12 && kit.removeIf(m -> "dealer".equals(String.valueOf(m.get("item"))))) {
            fresh.set("new-players.starter-kit", kit);
        }
        fresh.set("config-version", VERSION);
        try {
            fresh.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save config.yml: " + e.getMessage());
        }
        plugin.getConfig().setDefaults(def);
        plugin.getLogger().info("Updated config.yml to version " + VERSION + " (9.0: grouped by system, about 5x prices,"
                + " the rank ladder, worker auto-buy off). Kept your " + carried.size() + " server settings ("
                + String.join(", ", carried) + "); the old file is " + kept.getName() + ".");
    }

    /** Keys that moved before 9.0 are read from their old place. */
    private static void renameOldKeys(org.bukkit.configuration.file.FileConfiguration old, int version) {
        // 8.0: the leaderboard titles were "ranks" - in 9.0 "ranks" is the ladder
        if (old.isList("ranks.titles")) {
            old.set("dealer-titles.titles", old.get("ranks.titles"));
            ConfigurationSection everyone = old.getConfigurationSection("ranks.everyone");
            if (everyone != null) {
                old.createSection("dealer-titles.everyone", everyone.getValues(false));
            }
            boolean tab = old.getBoolean("ranks.tab-list", true);
            old.set("ranks", null);
            old.set("ranks.tab-list", tab);
        } else if (version < 5) {
            old.set("ranks", null); // 1.x rank settings
        }
        if (old.isSet("give-guide-on-first-join")) {
            old.set("new-players.give-guide", old.getBoolean("give-guide-on-first-join"));
        }
        if (old.isSet("death.cash-lost")) {
            old.set("pvp.death-cash-lost", old.getDouble("death.cash-lost"));
        }
        if (version == 12) {
            // 8.1's add-on mode is gone: these come back from the 9.0 defaults
            for (String k : List.of("market.anywhere", "ranks.tab-list", "new-players.give-guide", "menu.shift-f")) {
                old.set(k, null);
            }
        }
    }
}
