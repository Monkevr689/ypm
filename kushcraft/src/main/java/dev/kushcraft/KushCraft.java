package dev.kushcraft;

import dev.kushcraft.awards.Awards;
import dev.kushcraft.cartels.Cartels;
import dev.kushcraft.commands.KushCommand;
import dev.kushcraft.commands.ServerCommands;
import dev.kushcraft.economy.Economy;
import dev.kushcraft.economy.Exchange;
import dev.kushcraft.economy.Ledger;
import dev.kushcraft.economy.Market;
import dev.kushcraft.economy.Shop;
import dev.kushcraft.economy.VaultBridge;
import dev.kushcraft.effects.EffectManager;
import dev.kushcraft.effects.HighAnimals;
import dev.kushcraft.jobs.Jobs;
import dev.kushcraft.listeners.InteractListener;
import dev.kushcraft.listeners.MachineListener;
import dev.kushcraft.listeners.Onboarding;
import dev.kushcraft.listeners.PlantListener;
import dev.kushcraft.listeners.PlayerListener;
import dev.kushcraft.listeners.WorldListener;
import dev.kushcraft.machines.MachineManager;
import dev.kushcraft.menus.ChatInput;
import dev.kushcraft.menus.MenuListener;
import dev.kushcraft.menus.MenuTexts;
import dev.kushcraft.pack.ResourcePackManager;
import dev.kushcraft.plants.PlantManager;
import dev.kushcraft.plants.WildPlants;
import dev.kushcraft.pvp.WorkerRaids;
import dev.kushcraft.ranks.DealerTitles;
import dev.kushcraft.ranks.Playtime;
import dev.kushcraft.ranks.RankLadder;
import dev.kushcraft.recipes.Recipes;
import dev.kushcraft.reset.SeasonReset;
import dev.kushcraft.storage.Backups;
import dev.kushcraft.storage.Database;
import dev.kushcraft.storage.Docs;
import dev.kushcraft.storage.Persistence;
import dev.kushcraft.storage.PlayerStore;
import dev.kushcraft.strains.StrainRegistry;
import dev.kushcraft.util.RateLimit;
import dev.kushcraft.workers.Workers;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

/**
 * KushCraft - a drug-economy for an anarchy server: custom plants and
 * strains, a Drug Lab, workers, a rank ladder, cartels, a living market.
 * Everything is server side; textures come from a resource pack the plugin
 * builds and hosts itself. All game state is in an SQLite database
 * (storage/), the money is KushCraft's own (economy/), and every economy
 * event is in the transaction log.
 */
public final class KushCraft extends JavaPlugin {

    private static KushCraft instance;

    // storage
    private Database db;
    private Persistence persistence;
    private PlayerStore players;
    private Docs docs;
    private Backups backups;
    // economy
    private Economy economy;
    private VaultBridge vault;
    private Shop shop;
    private Market market;
    private Exchange exchange;
    private Jobs jobs;
    // progression
    private RankLadder ranks;
    private Playtime playtime;
    private DealerTitles titles;
    private Awards awards;
    private Cartels cartels;
    // the world
    private StrainRegistry strains;
    private PlantManager plants;
    private MachineManager machines;
    private Workers workers;
    private WildPlants wild;
    private EffectManager effects;
    private HighAnimals animals;
    // players
    private Onboarding onboarding;
    private MenuTexts menuTexts;
    private RateLimit rates;
    private SeasonReset reset;
    private WorkerRaids raids;
    private ResourcePackManager pack;

    private int season = 1;
    private int inventoryWipeSeason;
    private long downtime;
    private boolean legacyImported;
    private final List<File> legacyFiles = new ArrayList<>();

    public static KushCraft get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        Keys.init(this);
        saveDefaultConfig();
        ConfigMigration.run(this);

        // the database first: everything else loads from it
        db = new Database(this);
        try {
            db.open();
        } catch (SQLException | RuntimeException e) {
            getLogger().log(Level.SEVERE, "KushCraft can't open its database - disabling. " + e.getMessage(), e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        season = Integer.parseInt(db.meta("season", "1"));
        inventoryWipeSeason = Integer.parseInt(db.meta("inventory-wipe-season", "0"));
        legacyImported = "1".equals(db.meta("legacy-imported", "0"));
        long alive = Long.parseLong(db.meta("alive-at", "0"));
        downtime = alive > 0 ? Math.max(0, System.currentTimeMillis() - alive) : 0;
        persistence = new Persistence(this, db);
        docs = new Docs(this, db);
        backups = new Backups(this, db);
        players = new PlayerStore(this, db);
        players.load();
        Ledger ledger = new Ledger(this, db);
        ledger.season(season);
        economy = new Economy(this, players, ledger);
        if (!legacyImported) {
            LegacyImport.balances(this);
        }
        rates = new RateLimit();
        menuTexts = new MenuTexts(this);
        menuTexts.load();

        strains = new StrainRegistry(this);
        strains.load();
        awards = new Awards(this);
        awards.load();
        shop = new Shop(this);
        shop.load();
        ranks = new RankLadder(this);
        ranks.load();
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
        wild = new WildPlants(this);
        animals = new HighAnimals(this);
        playtime = new Playtime(this);
        onboarding = new Onboarding(this);
        reset = new SeasonReset(this);
        raids = new WorkerRaids(this);
        vault = new VaultBridge(this);
        pack = new ResourcePackManager(this, getFile());

        // one snapshot holds all of it
        persistence.register(players);
        persistence.register(ledger);
        persistence.register(workers);
        persistence.register(plants);
        persistence.register(machines);
        persistence.register(cartels);
        persistence.register(awards);
        persistence.register(market);
        persistence.register(exchange);
        finishLegacyImport();

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
        pm.registerEvents(playtime, this);
        pm.registerEvents(raids, this);

        KushCommand kc = new KushCommand(this);
        command("kush", kc);
        ServerCommands sc = new ServerCommands(this);
        for (String c : List.of("menu", "rankup", "balance", "pay", "baltop")) {
            command(c, sc);
        }

        persistence.start();
        pack.start();
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
        playtime.start();
        MenuListener.start(this);
        // once a minute: "the server is running" (downtime isn't growing time), the log's daily clean-up
        Bukkit.getScheduler().runTaskTimer(this, this::heartbeat, 20L, 1200L);
        // Vault and EssentialsX have enabled by the first tick
        Bukkit.getScheduler().runTask(this, () -> {
            getLogger().info(vault.register());
            ServerCommands.reportCollisions(this);
        });

        for (Player p : Bukkit.getOnlinePlayers()) {
            economy.join(p);
            ranks.showInTab(p);
            p.discoverRecipes(Recipes.keys());
            pack.send(p);
        }
        getLogger().info("KushCraft enabled - season " + season + ", " + players.size() + " players, "
                + strains.all().size() + " strains, " + plants.all().size() + " plants, " + machines.all().size()
                + " machines, " + workers.all().size() + " workers"
                + (downtime > 60_000L ? " (server was off for " + dev.kushcraft.util.Text.duration(downtime)
                + ": that time doesn't count for growing or workers)" : "") + ".");
    }

    private void command(String name, Object handler) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor((org.bukkit.command.CommandExecutor) handler);
            cmd.setTabCompleter((org.bukkit.command.TabCompleter) handler);
        }
    }

    private void heartbeat() {
        long now = System.currentTimeMillis();
        db.run(c -> Database.setMeta(c, "alive-at", String.valueOf(now)));
        economy.ledger().purgeOld();
    }

    @Override
    public void onDisable() {
        MenuListener.closeAll();
        if (effects != null) {
            effects.shutdown();
        }
        if (workers != null) {
            workers.stopping();
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
        if (vault != null) {
            vault.unregister();
        }
        if (persistence != null) {
            persistence.stop();
            if (!persistence.flushNow()) {
                getLogger().severe("The last changes could not be saved to the database!");
            }
        }
        if (db != null && db.isOpen()) {
            db.setMetaNow("alive-at", String.valueOf(System.currentTimeMillis()));
            db.close();
        }
        Recipes.unregister();
    }

    /** /kush reload: config.yml, menus.yml, strains, prices and recipes. Game data stays as it is. */
    public void reload() {
        persistence.flushNow();
        reloadConfig();
        strains.load();
        shop.load();
        ranks.load();
        titles.load();
        exchange.reloadOffers();
        cartels.reloadTiers();
        jobs.load();
        menuTexts.load();
        Recipes.register(this);
        pack.refreshExternal();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ranks.showInTab(p);
        }
    }

    /** After a reset: everything is read again from the (just wiped) database. */
    public void reloadData(boolean world) {
        players.load();
        economy.ledger().season(season);
        workers.load();
        if (world) {
            plants.load();
            machines.load();
            cartels.load();
            awards.load();
            market.load();
            exchange.load();
        }
        titles.refresh();
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk c : w.getLoadedChunks()) {
                if (world) {
                    plants.chunkLoaded(w, c.getX(), c.getZ());
                    machines.chunkLoaded(w, c.getX(), c.getZ());
                }
                workers.chunkLoaded(w, c.getX(), c.getZ());
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            economy.join(p);
            ranks.showInTab(p);
            if (world) {
                awards.sync(p);
            }
        }
    }

    // ------------------------------------------------------------------
    // the old YAML files (8.0 and older) are read once, then moved aside
    // ------------------------------------------------------------------

    public boolean legacyImported() {
        return legacyImported;
    }

    /** A subsystem read this old file: it's moved to legacy-yaml/ once everything is in the database. */
    public void legacyFile(File f) {
        if (!legacyFiles.contains(f)) {
            legacyFiles.add(f);
        }
    }

    private void finishLegacyImport() {
        if (legacyImported) {
            return;
        }
        if (!legacyFiles.isEmpty()) {
            if (!persistence.flushNow()) {
                getLogger().severe("Could not write the imported data - the old files stay where they are.");
                return;
            }
            File dir = new File(new File(getDataFolder(), "legacy-yaml"), new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
            dir.mkdirs();
            for (File f : legacyFiles) {
                try {
                    Files.move(f.toPath(), new File(dir, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    getLogger().warning("Could not move " + f.getName() + " aside: " + e.getMessage());
                }
            }
            getLogger().info("Moved " + legacyFiles.size() + " old data files (" + legacyFiles.stream().map(File::getName)
                    .toList() + ") into the database; the originals are in " + getDataFolder().getName() + "/legacy-yaml/"
                    + dir.getName() + ".");
        }
        legacyImported = true;
        db.setMetaNow("legacy-imported", "1");
    }

    // ------------------------------------------------------------------
    // getters
    // ------------------------------------------------------------------

    public Database db() {
        return db;
    }

    public Persistence persistence() {
        return persistence;
    }

    public PlayerStore players() {
        return players;
    }

    public Docs docs() {
        return docs;
    }

    public Backups backups() {
        return backups;
    }

    /** The current season (1 until the first reset). */
    public int season() {
        return season;
    }

    public void season(int s) {
        season = s;
    }

    /** The last season that wiped inventories (players catch up on their next join). */
    public int inventoryWipeSeason() {
        return inventoryWipeSeason;
    }

    public void inventoryWipeSeason(int s) {
        inventoryWipeSeason = s;
    }

    /** How long the server was off before this start (millis); not counted as growing or working time. */
    public long downtime() {
        return downtime;
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

    public RankLadder ranks() {
        return ranks;
    }

    public Playtime playtime() {
        return playtime;
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

    public WildPlants wild() {
        return wild;
    }

    public HighAnimals animals() {
        return animals;
    }

    public Onboarding onboarding() {
        return onboarding;
    }

    public MenuTexts menuTexts() {
        return menuTexts;
    }

    public RateLimit rates() {
        return rates;
    }

    public SeasonReset reset() {
        return reset;
    }

    public WorkerRaids raids() {
        return raids;
    }

    public ResourcePackManager pack() {
        return pack;
    }
}
