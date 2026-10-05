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
import dev.kushcraft.shop.Economy;
import dev.kushcraft.shop.Shop;
import dev.kushcraft.strain.StrainRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
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
    private ResourcePackManager pack;

    public static KushCraft get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        Keys.init(this);
        saveDefaultConfig();

        strains = new StrainRegistry(this);
        strains.load();
        economy = new Economy(this);
        shop = new Shop(this);
        shop.load();
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

        PluginCommand cmd = getCommand("kush");
        if (cmd != null) {
            KushCommand kc = new KushCommand(this);
            cmd.setExecutor(kc);
            cmd.setTabCompleter(kc);
        }

        pack.start();
        effects.start();
        machines.start();
        plants.start();
        MenuListener.start(this);
        // Vault's economy provider registers on enable, so hook one tick later
        Bukkit.getScheduler().runTask(this, economy::hook);

        for (Player p : Bukkit.getOnlinePlayers()) {
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
        Recipes.unregister();
    }

    /** /kush reload */
    public void reload() {
        reloadConfig();
        strains.load();
        shop.load();
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

    public ResourcePackManager pack() {
        return pack;
    }
}
