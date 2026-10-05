package dev.kushcraft;

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
import dev.kushcraft.shop.Shop;
import dev.kushcraft.strain.StrainRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

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
    private Exchange exchange;
    private Jobs jobs;
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
        shop = new Shop(this);
        shop.load();
        market = new Market(this);
        market.load();
        exchange = new Exchange(this);
        exchange.load();
        jobs = new Jobs(this);
        jobs.load();
        machines = new MachineManager(this);
        machines.load();
        plants = new PlantManager(this);
        plants.load();
        effects = new EffectManager(this);
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

        PluginCommand cmd = getCommand("kush");
        if (cmd != null) {
            KushCommand kc = new KushCommand(this);
            cmd.setExecutor(kc);
            cmd.setTabCompleter(kc);
        }

        pack.start();
        economy.start();
        market.start();
        exchange.start();
        effects.start();
        machines.start();
        plants.start();
        MenuListener.start(this);
        // Vault's economy provider registers on enable, so hook one tick later
        Bukkit.getScheduler().runTask(this, economy::hook);

        for (Player p : Bukkit.getOnlinePlayers()) {
            economy.join(p);
            p.discoverRecipes(Recipes.keys());
            pack.send(p);
        }
        getLogger().info("KushCraft enabled - " + strains.all().size() + " strains, "
                + plants.all().size() + " plants, " + machines.all().size() + " machines.");
    }

    @Override
    public void onDisable() {
        MenuListener.closeAll();
        if (effects != null) {
            effects.shutdown();
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
        if (market != null) {
            market.save();
        }
        if (exchange != null) {
            exchange.save();
        }
        Recipes.unregister();
    }

    /** /kush reload */
    public void reload() {
        reloadConfig();
        strains.load();
        shop.load();
        exchange.load();
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

    public Exchange exchange() {
        return exchange;
    }

    public Jobs jobs() {
        return jobs;
    }

    /**
     * Older configs: v1 gets the new shop lists (the old stations were removed
     * and new drugs added); every version gets the options added since
     * (exchange, jobs, menu...). Everything you set yourself is kept,
     * including resource-pack.url.
     */
    private void migrateConfig() {
        int version = getConfig().getInt("config-version", 1);
        if (version >= 3) {
            return;
        }
        java.io.InputStream in = getResource("config.yml");
        if (in == null) {
            return;
        }
        YamlConfiguration def = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        if (version < 2) {
            getConfig().set("shop.buy", def.getMapList("shop.buy"));
            ConfigurationSection sell = def.getConfigurationSection("shop.sell");
            if (sell != null) {
                for (String k : sell.getKeys(false)) {
                    getConfig().set("shop.sell." + k, sell.get(k));
                }
            }
        }
        getConfig().setDefaults(def);
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", 3);
        saveConfig();
        getLogger().info("Updated config.yml to version 3 (exchange, jobs and menu options added).");
    }

    public ResourcePackManager pack() {
        return pack;
    }
}
