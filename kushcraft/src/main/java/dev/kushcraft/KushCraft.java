package dev.kushcraft;

import dev.kushcraft.awards.Awards;
import dev.kushcraft.cartels.Cartels;
import dev.kushcraft.commands.KushCommand;
import dev.kushcraft.effects.EffectManager;
import dev.kushcraft.menus.ChatInput;
import dev.kushcraft.menus.MenuListener;
import dev.kushcraft.listeners.InteractListener;
import dev.kushcraft.listeners.MachineListener;
import dev.kushcraft.listeners.PlantListener;
import dev.kushcraft.listeners.PlayerListener;
import dev.kushcraft.listeners.WorldListener;
import dev.kushcraft.machines.MachineManager;
import dev.kushcraft.pack.ResourcePackManager;
import dev.kushcraft.plants.PlantManager;
import dev.kushcraft.recipes.Recipes;
import dev.kushcraft.jobs.Jobs;
import dev.kushcraft.economy.Economy;
import dev.kushcraft.economy.Exchange;
import dev.kushcraft.economy.Market;
import dev.kushcraft.ranks.DealerTitles;
import dev.kushcraft.economy.Shop;
import dev.kushcraft.strains.StrainRegistry;
import dev.kushcraft.workers.Workers;
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
    private DealerTitles titles;
    private Exchange exchange;
    private Jobs jobs;
    private Awards awards;
    private Cartels cartels;
    private Workers workers;
    private dev.kushcraft.plants.WildPlants wild;
    private dev.kushcraft.effects.HighAnimals animals;
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
        titles = new DealerTitles(this);
        titles.load();
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
        wild = new dev.kushcraft.plants.WildPlants(this);
        animals = new dev.kushcraft.effects.HighAnimals(this);
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
            titles.showInTab(p);
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
        titles.load();
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

    public DealerTitles titles() {
        return titles;
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

    public dev.kushcraft.plants.WildPlants wild() {
        return wild;
    }

    public dev.kushcraft.effects.HighAnimals animals() {
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
        if (version >= 11) {
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
        if (version < 8) {
            // 5.0: pricier workers and ores, one-perk cartel levels, a faster market recovery
            getConfig().set("cartel.levels", null);
            for (String key : List.of("market.recovery-per-minute", "workers.upgrade-costs", "workers.farmhand.wage",
                    "workers.dryer.wage")) {
                getConfig().set(key, def.get(key));
            }
        }
        if (version < 9) {
            // 6.0: money only from drugs (Trade only sells, no jobs pay, no award cash), the new drugs'
            // prices, the Runner in the shop, the new Trade shelves (no OP PvP or End items), workers
            // with no limit that work further and faster
            // (removed keys come back from the defaults when saving, in their usual place)
            for (String key : List.of("shop.buy", "shop.sell", "exchange.categories", "exchange.items",
                    "exchange.sell-ratio", "exchange.min-price")) {
                getConfig().set(key, null);
            }
            for (String key : List.of("jobs.enabled", "workers.max-per-player", "workers.radius",
                    "workers.rest-seconds")) {
                getConfig().set(key, def.get(key));
            }
        }
        if (version == 10) {
            // 7.x back to the 6.0 market, no more Suppliers (workers buy for themselves now)
            for (String key : List.of("market.demand-drop", "market.min-price", "market.recovery-per-minute",
                    "exchange.price-step", "exchange.recovery-per-minute")) {
                getConfig().set(key, def.get(key));
            }
            double water = getConfig().getDouble("workers.supplier.water-price", def.getDouble("workers.water-price"));
            for (String key : List.of("workers.supplier", "workers.work-offline", "workers.work-offline-max-chunks")) {
                getConfig().set(key, null);
            }
            getConfig().set("workers.water-price", water);
            List<java.util.Map<?, ?>> buy = new java.util.ArrayList<>(getConfig().getMapList("shop.buy"));
            if (buy.removeIf(m -> "supplier".equals(String.valueOf(m.get("item"))))) {
                getConfig().set("shop.buy", buy);
            }
        }
        // players on most hosts can't reach the built-in pack server: use the hosted copy
        String url = getConfig().getString("resource-pack.url", "");
        if (url == null || url.isBlank() || url.contains("raw.githubusercontent.com/Monkevr689/ypm/")) {
            getConfig().set("resource-pack.url", "auto");
        }
        getConfig().setDefaults(def);
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", 11);
        saveConfig();
        getLogger().info("Updated config.yml to version 11 (8.0: workers use any of your chests and buy what they"
                + " need, Mythic seeds in the Shop, no Suppliers). Your other settings were kept.");
    }

    public ResourcePackManager pack() {
        return pack;
    }
}
