package dev.kushcraft.award;

import com.google.gson.JsonObject;
import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.lab.LabRecipe;
import dev.kushcraft.plant.Plant;
import dev.kushcraft.strain.Climate;
import dev.kushcraft.strain.Rarity;
import dev.kushcraft.util.Text;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.SoundCategory;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Keeps track of every player's achievements (awards.yml), pays the reward
 * and shows the unlock. The awards are also loaded as real advancements in a
 * "KushCraft" tab, so players get the normal toast and can browse them with
 * the advancements key; if that ever fails the plugin falls back to a title.
 */
public final class Awards implements Listener {

    public static final String NAMESPACE = "kush";
    static final String CRITERION = "done";

    private static final class Data {
        final Set<Award> done = EnumSet.noneOf(Award.class);
        final Map<String, Integer> stats = new HashMap<>();
        final Set<String> cooked = new HashSet<>();
        final Set<String> tried = new HashSet<>();
        final Set<String> climates = new HashSet<>();
        final Set<String> grown = new HashSet<>();
        final Set<String> signatures = new HashSet<>();
    }

    private final KushCraft plugin;
    private final File file;
    private final Map<UUID, Data> data = new HashMap<>();
    private final Map<Award, Advancement> advancements = new LinkedHashMap<>();
    private Advancement root;
    private boolean dirty;

    public Awards(KushCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "awards.yml");
    }

    // ------------------------------------------------------------------
    // storage
    // ------------------------------------------------------------------

    public void load() {
        data.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec == null) {
            return;
        }
        for (String k : sec.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(k);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Data d = new Data();
            for (String a : sec.getStringList(k + ".done")) {
                try {
                    d.done.add(Award.valueOf(a.toUpperCase(java.util.Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            ConfigurationSection st = sec.getConfigurationSection(k + ".stats");
            if (st != null) {
                for (String s : st.getKeys(false)) {
                    d.stats.put(s, st.getInt(s));
                }
            }
            d.cooked.addAll(sec.getStringList(k + ".cooked"));
            d.tried.addAll(sec.getStringList(k + ".tried"));
            d.climates.addAll(sec.getStringList(k + ".climates"));
            d.grown.addAll(sec.getStringList(k + ".grown"));
            d.signatures.addAll(sec.getStringList(k + ".signatures"));
            data.put(id, d);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Data> e : data.entrySet()) {
            String k = "players." + e.getKey();
            Data d = e.getValue();
            List<String> done = new ArrayList<>();
            d.done.forEach(a -> done.add(a.id()));
            y.set(k + ".done", done);
            d.stats.forEach((s, v) -> y.set(k + ".stats." + s, v));
            y.set(k + ".cooked", new ArrayList<>(d.cooked));
            y.set(k + ".tried", new ArrayList<>(d.tried));
            y.set(k + ".climates", new ArrayList<>(d.climates));
            y.set(k + ".grown", new ArrayList<>(d.grown));
            if (!d.signatures.isEmpty()) {
                y.set(k + ".signatures", new ArrayList<>(d.signatures));
            }
        }
        try {
            y.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save awards.yml", ex);
        }
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                save();
            }
        }, 20L * 30, 20L * 30);
    }

    private Data of(UUID id) {
        return data.computeIfAbsent(id, k -> new Data());
    }

    // ------------------------------------------------------------------
    // reading
    // ------------------------------------------------------------------

    public boolean has(OfflinePlayer p, Award a) {
        Data d = data.get(p.getUniqueId());
        return d != null && d.done.contains(a);
    }

    public int count(OfflinePlayer p) {
        Data d = data.get(p.getUniqueId());
        return d == null ? 0 : d.done.size();
    }

    public int stat(OfflinePlayer p, String stat) {
        Data d = data.get(p.getUniqueId());
        return d == null ? 0 : d.stats.getOrDefault(stat, 0);
    }

    public int cookedKinds(OfflinePlayer p) {
        Data d = data.get(p.getUniqueId());
        return d == null ? 0 : d.cooked.size();
    }

    private int set(OfflinePlayer p, java.util.function.Function<Data, Set<String>> which) {
        Data d = data.get(p.getUniqueId());
        return d == null ? 0 : which.apply(d).size();
    }

    public int triedKinds(OfflinePlayer p) {
        Data d = data.get(p.getUniqueId());
        return d == null ? 0 : d.tried.size();
    }

    /** Every drug a player can take (for "Tried It All"). */
    public static List<ItemType> drugs() {
        List<ItemType> out = new ArrayList<>();
        for (ItemType t : ItemType.values()) {
            if (t.isDrug() && !t.legacy()) {
                out.add(t);
            }
        }
        return out;
    }

    /** "12/36" style progress for an award that counts something, or null. */
    public String progress(OfflinePlayer p, Award a) {
        return switch (a) {
            case HARVEST_100 -> Math.min(100, stat(p, "harvest")) + "/100";
            case HARVEST_1000 -> Math.min(1000, stat(p, "harvest")) + "/1,000";
            case COOK_100 -> Math.min(100, stat(p, "cook")) + "/100";
            case ALL_RECIPES -> cookedKinds(p) + "/" + LabRecipe.values().length;
            case ORDERS_25 -> Math.min(25, stat(p, "orders")) + "/25";
            case TRY_10 -> Math.min(10, triedKinds(p)) + "/10";
            case TRY_ALL -> triedKinds(p) + "/" + drugs().size();
            case ALL_CLIMATES -> Math.min(6, set(p, d -> d.climates)) + "/6";
            case COLLECTOR -> Math.min(10, set(p, d -> d.grown)) + "/10";
            case SIGNATURES -> Math.min(10, set(p, d -> d.signatures)) + "/10";
            case SOLD_10K, SOLD_100K, SOLD_1M -> plugin.economy().format(plugin.economy().sales(p));
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // unlocking
    // ------------------------------------------------------------------

    public void grant(Player p, Award a) {
        Data d = of(p.getUniqueId());
        if (!d.done.add(a)) {
            return;
        }
        dirty = true;
        if (a.reward() > 0) {
            plugin.economy().deposit(p, a.reward());
        }
        boolean toast = awardVanilla(p, a);
        String reward = a.reward() > 0 ? " <gold>+" + plugin.economy().format(a.reward()) : "";
        if (!toast) {
            p.showTitle(Title.title(Text.mm("<" + a.color() + "><bold>" + a.title()), Text.mm("<gray>" + a.description() + reward),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(600))));
            p.playSound(p.getLocation(), a.frame() == Award.Frame.CHALLENGE ? "minecraft:ui.toast.challenge_complete"
                    : "minecraft:entity.player.levelup", SoundCategory.MASTER, 0.8f, 1.1f);
            Bukkit.broadcast(Text.msg("<white>" + Text.escape(p.getName()) + " <gray>unlocked <" + a.color() + ">["
                    + a.title() + "]"));
        }
        p.sendMessage(Text.msg("<" + a.color() + ">" + a.title() + " <gray>- " + a.description() + reward));
        for (Starter s : Starter.values()) {
            if (s.justDoneBy(p, a)) {
                Starter next = Starter.next(p);
                if (next != null) {
                    p.sendMessage(Text.msg("<aqua>Next step (" + (next.ordinal() + 1) + "/" + Starter.values().length
                            + "): <white>" + next.title() + " <gray>- " + next.how().get(0) + " " + next.how().get(1)));
                } else {
                    p.sendMessage(Text.msg("<aqua>You know the basics now! <gray>Breed strains (Drug Lab > Mix),"
                            + " start a cartel and climb Top Dealers."));
                }
                break;
            }
        }
    }


    private int add(Player p, String stat, int n) {
        Data d = of(p.getUniqueId());
        dirty = true;
        return d.stats.merge(stat, n, Integer::sum);
    }

    public void planted(Player p) {
        grant(p, Award.FIRST_SEED);
    }

    public void harvested(Player p, Plant.Kind kind, String strain, Climate.Fit fit, Climate where) {
        int n = add(p, "harvest", 1);
        Data d = of(p.getUniqueId());
        grant(p, Award.FIRST_HARVEST);
        switch (kind) {
            case MUSHROOM -> grant(p, Award.SHROOMS);
            case COCA -> grant(p, Award.COCA);
            case POPPY -> grant(p, Award.POPPY);
            case PEYOTE -> grant(p, Award.PEYOTE);
            default -> {
            }
        }
        if (n >= 100) {
            grant(p, Award.HARVEST_100);
        }
        if (n >= 1000) {
            grant(p, Award.HARVEST_1000);
        }
        if (fit == Climate.Fit.IDEAL) {
            grant(p, Award.IDEAL_CLIMATE);
        }
        if (where != null && kind != Plant.Kind.MUSHROOM && d.climates.add(where.name())) {
            dirty = true;
        }
        if (d.climates.size() >= Climate.values().length) {
            grant(p, Award.ALL_CLIMATES);
        }
        if (strain != null && kind == Plant.Kind.CANNABIS && d.grown.add(strain)) {
            dirty = true;
        }
        if (d.grown.size() >= 10) {
            grant(p, Award.COLLECTOR);
        }
    }

    public void labPlaced(Player p) {
        grant(p, Award.BUILD_LAB);
    }

    public void labLevel(Player p, int level, int max) {
        if (level >= max) {
            grant(p, Award.MAX_LAB);
        }
    }

    /** A worker was hired (count = how many the player has now). */
    public void foraged(Player p) {
        grant(p, Award.FORAGER);
    }

    public void partyAnimal(Player p) {
        grant(p, Award.PARTY_ANIMAL);
    }

    public void hiredCook(Player p) {
        grant(p, Award.HEAD_CHEF);
    }

    /** A Farmhand, Dryer, Cook and Runner working together. */
    public void assemblyLine(Player p) {
        grant(p, Award.ASSEMBLY_LINE);
    }

    public void hiredSupplier(Player p) {
        grant(p, Award.SUPPLIER);
    }

    /** A Runner passed a Farmhand's harvest to a Dryer. */
    public void logistics(Player p) {
        grant(p, Award.LOGISTICS);
    }

    /** A drug's signature effect started. */
    public void signature(Player p, String id) {
        Data d = of(p.getUniqueId());
        if (d.signatures.add(id)) {
            dirty = true;
        }
        if (d.signatures.size() >= 10) {
            grant(p, Award.SIGNATURES);
        }
    }

    public void hired(Player p, int count) {
        grant(p, Award.HIRED);
        if (count >= 4) {
            grant(p, Award.WORKFORCE);
        }
    }

    public void dried(Player p) {
        grant(p, Award.FIRST_DRY);
    }

    public void fullRacks(Player p) {
        grant(p, Award.FULL_RACKS);
    }

    public void rolled(Player p, ItemType type) {
        grant(p, type == ItemType.BLUNT ? Award.BLUNT : Award.FIRST_ROLL);
    }

    public void cooked(Player p, LabRecipe r) {
        int n = add(p, "cook", 1);
        Data d = of(p.getUniqueId());
        d.cooked.add(r.name());
        grant(p, Award.FIRST_COOK);
        if (r == LabRecipe.BLUE_CRYSTAL) {
            grant(p, Award.BLUE_SKY);
        }
        if (r == LabRecipe.LUCID_TAB) {
            grant(p, Award.ACID);
        }
        if (n >= 100) {
            grant(p, Award.COOK_100);
        }
        boolean all = true;
        for (LabRecipe x : LabRecipe.values()) {
            all &= d.cooked.contains(x.name());
        }
        if (all) {
            grant(p, Award.ALL_RECIPES);
        }
    }

    /** total = the player's lifetime sales after this sale. */
    public void sales(Player p, double total) {
        grant(p, Award.FIRST_SALE);
        if (total >= 10_000) {
            grant(p, Award.SOLD_10K);
        }
        if (total >= 100_000) {
            grant(p, Award.SOLD_100K);
        }
        if (total >= 1_000_000) {
            grant(p, Award.SOLD_1M);
        }
    }

    public void order(Player p) {
        int n = add(p, "orders", 1);
        grant(p, Award.ORDER);
        if (n >= 25) {
            grant(p, Award.ORDERS_25);
        }
    }

    public void traded(Player p, Material m) {
        grant(p, Award.TRADE);
        if (m == Material.DIAMOND) {
            grant(p, Award.DIAMONDS);
        }
    }

    public void bred(Player p, Rarity rarity) {
        grant(p, Award.FIRST_BREED);
        if (rarity.ordinal() >= Rarity.EPIC.ordinal()) {
            grant(p, Award.RARE_STRAIN);
        }
        if (rarity.ordinal() >= Rarity.LEGENDARY.ordinal()) {
            grant(p, Award.LEGENDARY);
        }
        if (rarity.animated()) {
            grant(p, Award.MYTHIC);
        }
        if (rarity == Rarity.EXOTIC) {
            grant(p, Award.EXOTIC);
        }
    }

    public void jackpot(Player p) {
        grant(p, Award.JACKPOT);
    }

    /** A drug was taken. */
    public void used(Player p, ItemType type) {
        Data d = of(p.getUniqueId());
        if (d.tried.add(type.id())) {
            dirty = true;
        }
        int tried = 0;
        for (ItemType t : drugs()) {
            if (d.tried.contains(t.id())) {
                tried++;
            }
        }
        if (tried >= 10) {
            grant(p, Award.TRY_10);
        }
        if (tried >= drugs().size()) {
            grant(p, Award.TRY_ALL);
        }
    }

    /** Called when effects start; count = how many effects are active now. */
    public void high(Player p, int count) {
        grant(p, Award.FIRST_HIGH);
        if (count >= 6) {
            grant(p, Award.COCKTAIL);
        }
    }

    public void greenOut(Player p) {
        grant(p, Award.GREEN_OUT);
    }

    public void badTrip(Player p) {
        grant(p, Award.BAD_TRIP);
    }

    // ------------------------------------------------------------------
    // vanilla advancements
    // ------------------------------------------------------------------

    public static NamespacedKey key(String id) {
        return new NamespacedKey(NAMESPACE, id);
    }

    /** Advancement JSON for an award (same format as a datapack's advancement file). */
    static String json(Award a) {
        JsonObject display = display(a.icon(), a.title(), a.description()
                + (a.reward() > 0 ? " (+$" + Text.number(a.reward()) + ")" : ""), a.frame().id(), true, true, a.secret());
        JsonObject o = new JsonObject();
        o.add("display", display);
        o.addProperty("parent", key(a.parent() == null ? "root" : a.parent().id()).asString());
        o.add("criteria", criteria());
        return o.toString();
    }

    static String rootJson() {
        JsonObject display = display("bud_fresh", "KushCraft", "Grow, cook, deal - and unlock them all.", "task",
                false, false, false);
        display.addProperty("background", NAMESPACE + ":gui/advancements/kush");
        JsonObject o = new JsonObject();
        o.add("display", display);
        o.add("criteria", criteria());
        return o.toString();
    }

    private static JsonObject display(String icon, String title, String description, String frame, boolean toast,
                                      boolean chat, boolean hidden) {
        JsonObject components = new JsonObject();
        components.addProperty("minecraft:item_model", NAMESPACE + ":" + icon);
        JsonObject item = new JsonObject();
        item.addProperty("id", "minecraft:paper");
        item.add("components", components);
        JsonObject d = new JsonObject();
        d.add("icon", item);
        d.addProperty("title", title);
        d.addProperty("description", description);
        d.addProperty("frame", frame);
        d.addProperty("show_toast", toast);
        d.addProperty("announce_to_chat", chat);
        d.addProperty("hidden", hidden);
        return d;
    }

    private static JsonObject criteria() {
        JsonObject trigger = new JsonObject();
        trigger.addProperty("trigger", "minecraft:impossible");
        JsonObject c = new JsonObject();
        c.add(CRITERION, trigger);
        return c;
    }

    /** Loads the KushCraft advancement tab (start-up and after /minecraft:reload). */
    public void registerAdvancements() {
        advancements.clear();
        root = null;
        if (!plugin.getConfig().getBoolean("awards.advancements", true)) {
            return;
        }
        Map<Key, String> json = new LinkedHashMap<>();
        json.put(key("root"), rootJson());
        for (Award a : Award.values()) {
            json.put(key(a.id()), json(a));
        }
        try {
            for (Key k : json.keySet()) {
                NamespacedKey nk = new NamespacedKey(k.namespace(), k.value());
                if (Bukkit.getAdvancement(nk) != null) {
                    Bukkit.getUnsafe().removeAdvancement(nk);
                }
            }
            Bukkit.getUnsafe().loadAdvancements(json, false);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Could not add the KushCraft advancement tab - awards show as titles instead.", t);
            return;
        }
        root = Bukkit.getAdvancement(key("root"));
        for (Award a : Award.values()) {
            Advancement adv = Bukkit.getAdvancement(key(a.id()));
            if (adv != null) {
                advancements.put(a, adv);
            }
        }
        if (root == null || advancements.size() != Award.values().length) {
            plugin.getLogger().warning("Only " + advancements.size() + "/" + Award.values().length
                    + " KushCraft advancements loaded - the rest show as titles.");
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            sync(p);
        }
    }

    /** How many awards are real advancements (for the self test). */
    public int advancementsLoaded() {
        return root == null ? 0 : advancements.size();
    }

    private boolean awardVanilla(Player p, Award a) {
        Advancement adv = advancements.get(a);
        if (adv == null || root == null) {
            return false;
        }
        try {
            awardRoot(p);
            AdvancementProgress pr = p.getAdvancementProgress(adv);
            return pr.isDone() || pr.awardCriteria(CRITERION);
        } catch (Throwable t) {
            return false;
        }
    }

    private void awardRoot(Player p) {
        AdvancementProgress pr = p.getAdvancementProgress(root);
        if (!pr.isDone()) {
            pr.awardCriteria(CRITERION);
        }
    }

    /** Opens the tab for the player and catches up awards earned while the tab was missing. */
    public void sync(Player p) {
        if (root == null) {
            return;
        }
        try {
            awardRoot(p);
            Data d = data.get(p.getUniqueId());
            if (d == null) {
                return;
            }
            for (Award a : d.done) {
                Advancement adv = advancements.get(a);
                if (adv != null && !p.getAdvancementProgress(adv).isDone()) {
                    p.getAdvancementProgress(adv).awardCriteria(CRITERION);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "advancement sync failed", t);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        sync(e.getPlayer());
    }

    @EventHandler
    public void onReload(ServerResourcesReloadedEvent e) {
        // a datapack reload drops advancements that aren't in a datapack
        Bukkit.getScheduler().runTask(plugin, this::registerAdvancements);
    }
}
