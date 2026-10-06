package dev.kushcraft;

import dev.kushcraft.award.Awards;
import dev.kushcraft.cartel.Cartels;
import dev.kushcraft.command.KushCommand;
import dev.kushcraft.effect.EffectManager;
import dev.kushcraft.gui.ChatInput;
import dev.kushcraft.gui.MenuListener;
import dev.kushcraft.listener.InteractListener;
import dev.kushcraft.listener.MachineListener;
import dev.kushcraft.listener.PlantListener;
import dev.kushcraft.listener.PlayerListener;
import dev.kushcraft.listener.WorldListener;
import dev.kushcraft.machine.MachineManager;
import dev.kushcraft.pack.ResourcePackManager;
import dev.kushcraft.plant.PlantManager;
import dev.kushcraft.recipe.Recipes;
import dev.kushcraft.jobs.Jobs;
import dev.kushcraft.shop.Economy;
import dev.kushcraft.shop.Exchange;
import dev.kushcraft.shop.Market;
import dev.kushcraft.shop.Ranks;
import dev.kushcraft.shop.Shop;
import dev.kushcraft.strain.StrainRegistry;
import dev.kushcraft.worker.Workers;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * KushCraft - custom plants, strains, a lab, rolling, a dealer and custom
 * effects, all server side. Textures come from a resource pack the plugin
 * builds and hosts itself.
 */
public final class KushCraft extends JavaPlugin {

    private static KushCraft instance;

    private StrainRegistry strains;
    private PlantManager plants;
    private MachineManager machines;
    private EffectManager effects;
    private Economy economy;
    private Shop shop;
    private Market market;
    private Ranks ranks;
    private Exchange exchange;
    private Jobs jobs;
    private Awards awards;
    private Cartels cartels;
    private Workers workers;
    private dev.kushcraft.plant.WildPlants wild;
    private dev.kushcraft.effect.HighAnimals animals;
    private ResourcePackManager pack;

    public static KushCraft get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        Keys.init(this);
        saveDefaultConfig();
        migrateConfig();

        strains = new StrainRegistry(this);
        strains.load();
        economy = new Economy(this);
        economy.load();
        awards = new Awards(this);
        awards.load();
        shop = new Shop(this);
        shop.load();
        ranks = new Ranks(this);
        ranks.load();
        market = new Market(this);
        market.load();
        exchange = new Exchange(this);
        exchange.load();
        cartels = new Cartels(this);
        cartels.load();
        jobs = new Jobs(this);
        jobs.load();
        machines = new MachineManager(this);
        machines.load();
        plants = new PlantManager(this);
        plants.load();
        workers = new Workers(this);
        workers.load();
        effects = new EffectManager(this);
        wild = new dev.kushcraft.plant.WildPlants(this);
        animals = new dev.kushcraft.effect.HighAnimals(this);
        pack = new ResourcePackManager(this, getFile());

        Recipes.register(this);

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(pack, this);
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new ChatInput(), this);
        pm.registerEvents(new InteractListener(this), this);
        pm.registerEvents(new PlantListener(this), this);
        pm.registerEvents(new MachineListener(this), this);
        pm.registerEvents(new WorldListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(jobs, this);
        pm.registerEvents(jobs.placed(), this);
        pm.registerEvents(awards, this);
        pm.registerEvents(workers, this);
        pm.registerEvents(animals, this);

        PluginCommand cmd = getCommand("kush");
        if (cmd != null) {
            KushCommand kc = new KushCommand(this);
            cmd.setExecutor(kc);
            cmd.setTabCompleter(kc);
        }

        pack.start();
        economy.start();
        awards.start();
        awards.registerAdvancements();
        market.start();
        exchange.start();
        cartels.start();
        effects.start();
        machines.start();
        plants.start();
        workers.start();
        wild.start();
        animals.start();
        MenuListener.start(this);
        // Vault's economy provider registers on enable, so hook one tick later
        Bukkit.getScheduler().runTask(this, economy::hook);

        for (Player p : Bukkit.getOnlinePlayers()) {
            economy.join(p);
            ranks.showInTab(p);
            p.discoverRecipes(Recipes.keys());
            pack.send(p);
        }
        getLogger().info("KushCraft enabled - " + strains.all().size() + " strains, "
                + plants.all().size() + " plants, " + machines.all().size() + " machines, " + workers.all().size()
                + " workers.");
    }

    @Override
    public void onDisable() {
        MenuListener.closeAll();
        if (effects != null) {
            effects.shutdown();
        }
        if (workers != null) {
            workers.shutdown();
        }
        if (plants != null) {
            plants.shutdown();
        }
        if (machines != null) {
            machines.shutdown();
        }
        if (pack != null) {
            pack.stop();
        }
        if (economy != null) {
            economy.save();
        }
        if (awards != null) {
            awards.save();
        }
        if (market != null) {
            market.save();
        }
        if (exchange != null) {
            exchange.save();
        }
        if (cartels != null) {
            cartels.save();
        }
        Recipes.unregister();
    }

    /** /kush reload */
    public void reload() {
        reloadConfig();
        strains.load();
        shop.load();
        ranks.load();
        exchange.load();
        cartels.save();
        cartels.load();
        jobs.load();
        economy.hook();
        Recipes.register(this);
        pack.refreshExternal();
    }

    public StrainRegistry strains() {
        return strains;
    }

    public PlantManager plants() {
        return plants;
    }

    public MachineManager machines() {
        return machines;
    }

    public EffectManager effects() {
        return effects;
    }

    public Economy economy() {
        return economy;
    }

    public Shop shop() {
        return shop;
    }

    public Market market() {
        return market;
    }

    public Ranks ranks() {
        return ranks;
    }

    public Exchange exchange() {
        return exchange;
    }

    public Jobs jobs() {
        return jobs;
    }

    public Awards awards() {
        return awards;
    }

    public Cartels cartels() {
        return cartels;
    }

    public Workers workers() {
        return workers;
    }

    public dev.kushcraft.plant.WildPlants wild() {
        return wild;
    }

    public dev.kushcraft.effect.HighAnimals animals() {
        return animals;
    }

    /**
     * Older configs are brought up to date. 2.0 (version 5) brought new shop
     * prices, the leaderboard ranks and the Trade list; 3.0 (version 6)
     * cheaper gear and recipes, strain seed prices from strains.yml, Trade
     * shelves, 30 second drying and cartels; 4.0 (version 7) workers, new
     * shop prices, a tougher market and the new-player kit; 5.0 (version 8)
     * the Cook, pricier workers and ores, better drug prices, cash lost on
     * death and the hosted resource pack. Options added since are filled in;
     * everything else you set yourself is kept.
     */
    private void migrateConfig() {
        int version = getConfig().getInt("config-version", 1);
        if (version >= 8) {
            return;
        }
        java.io.InputStream in = getResource("config.yml");
        if (in == null) {
            return;
        }
        YamlConfiguration def = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        if (version < 5) {
            for (String key : List.of("ranks", "strain-maker.anywhere")) {
                getConfig().set(key, null);
            }
            for (String key : List.of("economy.starting-balance", "exchange.price-multiplier", "exchange.sell-ratio")) {
                getConfig().set(key, def.get(key));
            }
        }
        if (version < 6) {
            // removed here, filled in again from the defaults below
            for (String key : List.of("exchange.categories", "exchange.items", "drying.minutes")) {
                getConfig().set(key, null);
            }
        }
        if (version < 7) {
            for (String key : List.of("strain-maker.cost", "lab.upgrade-costs", "shop.seed-price-multiplier",
                    "market.demand-drop", "market.min-price")) {
                getConfig().set(key, def.get(key));
            }
        }
        // 5.0: shop prices (with the Cook), pricier workers and ores, a faster market recovery
        // (removed keys come back from the defaults when saving, in their usual place)
        getConfig().set("shop.buy", null);
        getConfig().set("shop.sell", null);
        getConfig().set("exchange.categories.ores", null);
        getConfig().set("cartel.levels", null);
        for (String key : List.of("market.recovery-per-minute", "workers.upgrade-costs", "workers.farmhand.wage",
                "workers.dryer.wage")) {
            getConfig().set(key, def.get(key));
        }
        // players on most hosts can't reach the built-in pack server: use the hosted copy
        String url = getConfig().getString("resource-pack.url", "");
        if (url == null || url.isBlank() || url.contains("raw.githubusercontent.com/Monkevr689/ypm/")) {
            getConfig().set("resource-pack.url", "auto");
        }
        getConfig().setDefaults(def);
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", 8);
        saveConfig();
        getLogger().info("Updated config.yml to version 8 (5.0: the Cook, new prices, cash lost on death,"
                + " resource pack from GitHub). Your other settings were kept.");
    }

    public ResourcePackManager pack() {
        return pack;
    }
}
