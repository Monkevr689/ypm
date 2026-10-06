package dev.kushcraft.cartel;

import dev.kushcraft.KushCraft;
import dev.kushcraft.award.Award;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Cartels: players team up under a name and a banner colour. Every sale a
 * member makes adds a cut to the shared bank (on top, nobody pays it), the
 * boss spends the bank on cartel levels (bigger sale, grow and lab bonuses
 * for everyone) and the cartel fills big shipments together. Saved in
 * cartels.yml; levels and prices in config.yml (cartel.*).
 */
public final class Cartels {

    /** One cartel level from config.yml: name, upgrade cost and the bonuses every member gets. */
    public record Tier(String name, double cost, int members, double sell, double grow, double lab) {
    }

    private record Invite(String cartel, long expires) {
    }

    private final KushCraft plugin;
    private final File file;
    private final Map<String, Cartel> cartels = new LinkedHashMap<>();
    private final Map<UUID, Cartel> byMember = new HashMap<>();
    private final Map<UUID, List<Invite>> invites = new HashMap<>();
    private final List<Tier> tiers = new ArrayList<>();
    private boolean dirty;

    public Cartels(KushCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cartels.yml");
    }

    // ------------------------------------------------------------------
    // storage
    // ------------------------------------------------------------------

    public void load() {
        loadTiers();
        cartels.clear();
        byMember.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("cartels");
        if (sec == null) {
            return;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            try {
                Cartel c = new Cartel(id, s.getString("name", id), s.getInt("color", Cartel.COLORS[0]),
                        UUID.fromString(s.getString("leader", "")));
                for (String m : s.getStringList("members")) {
                    c.members.add(UUID.fromString(m));
                }
                c.bank = s.getDouble("bank");
                c.level = Math.max(1, Math.min(tiers.size(), s.getInt("level", 1)));
                c.sales = s.getDouble("sales");
                c.shipments = s.getInt("shipments");
                c.created = s.getLong("created");
                c.nextShipment = s.getLong("next-shipment");
                ConfigurationSection sh = s.getConfigurationSection("shipment");
                ItemType t = sh == null ? null : ItemType.parse(sh.getString("item"));
                if (t != null) {
                    c.shipment = new Cartel.Shipment(t, sh.getInt("amount"), sh.getInt("delivered"),
                            sh.getDouble("reward"), sh.getLong("expires"));
                }
                add(c);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping broken cartel '" + id + "' in cartels.yml");
            }
        }
    }

    private void loadTiers() {
        tiers.clear();
        for (Map<?, ?> m : plugin.getConfig().getMapList("cartel.levels")) {
            tiers.add(new Tier(String.valueOf(m.get("name")), num(m.get("cost")), (int) Math.max(1, num(m.get("members"))),
                    num(m.get("sell-bonus")), num(m.get("grow-bonus")), num(m.get("lab-bonus"))));
        }
        if (tiers.isEmpty()) {
            tiers.add(new Tier("Crew", 0, 4, 0, 0, 0));
        }
    }

    private static double num(Object o) {
        return o instanceof Number n ? Math.max(0, n.doubleValue()) : 0;
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Cartel c : cartels.values()) {
            String k = "cartels." + c.id + ".";
            y.set(k + "name", c.name);
            y.set(k + "color", c.color);
            y.set(k + "leader", c.leader.toString());
            List<String> members = new ArrayList<>();
            c.members.forEach(m -> members.add(m.toString()));
            y.set(k + "members", members);
            y.set(k + "bank", Math.round(c.bank * 100) / 100.0);
            y.set(k + "level", c.level);
            y.set(k + "sales", Math.round(c.sales * 100) / 100.0);
            y.set(k + "shipments", c.shipments);
            y.set(k + "created", c.created);
            y.set(k + "next-shipment", c.nextShipment);
            if (c.shipment != null) {
                y.set(k + "shipment.item", c.shipment.type().id());
                y.set(k + "shipment.amount", c.shipment.amount());
                y.set(k + "shipment.delivered", c.shipment.delivered());
                y.set(k + "shipment.reward", c.shipment.reward());
                y.set(k + "shipment.expires", c.shipment.expires());
            }
        }
        try {
            y.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save cartels.yml", ex);
        }
    }

    public void start() {
        tick();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick();
            if (dirty) {
                save();
            }
        }, 20L * 60, 20L * 60);
    }

    /** Once a minute: expired shipments go, new ones arrive, old invites are dropped. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Cartel c : cartels.values()) {
            if (c.shipment != null && now > c.shipment.expires()) {
                tell(c, "<gray>The " + c.shipment.type().display() + " shipment ran out of time.");
                c.shipment = null;
                c.nextShipment = now + cooldown();
                dirty = true;
            }
            if (c.shipment == null && now >= c.nextShipment) {
                c.shipment = newShipment(c);
                if (c.shipment != null) {
                    tell(c, "<gold>New shipment: <white>" + c.shipment.amount() + "x " + c.shipment.type().display()
                            + " <gray>for <gold>" + money(c.shipment.reward()) + " <gray>(Cartel tab)");
                }
                dirty = true;
            }
        }
        invites.values().forEach(l -> l.removeIf(i -> now > i.expires() || !cartels.containsKey(i.cartel())));
        invites.values().removeIf(List::isEmpty);
    }

    private long cooldown() {
        return Math.max(0, plugin.getConfig().getLong("cartel.shipment-cooldown-minutes", 30)) * 60_000L;
    }

    private Cartel.Shipment newShipment(Cartel c) {
        List<ItemType> pool = plugin.market().products();
        if (pool.isEmpty()) {
            return null;
        }
        ItemType t = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        double base = Math.max(1, plugin.shop().basePrice(t));
        double value = plugin.getConfig().getDouble("cartel.shipment-value", 6000) * (0.5 + 0.5 * c.level);
        int amount = (int) Math.max(8, Math.min(640, Math.round(value / base)));
        double reward = Math.round(base * amount * plugin.getConfig().getDouble("cartel.shipment-bonus", 1.5) / 50.0) * 50.0;
        long hours = Math.max(1, plugin.getConfig().getLong("cartel.shipment-hours", 24));
        return new Cartel.Shipment(t, amount, 0, reward, System.currentTimeMillis() + hours * 3_600_000L);
    }

    // ------------------------------------------------------------------
    // reading
    // ------------------------------------------------------------------

    public boolean enabled() {
        return plugin.getConfig().getBoolean("cartel.enabled", true);
    }

    public Cartel of(UUID player) {
        return player == null ? null : byMember.get(player);
    }

    public Cartel of(OfflinePlayer p) {
        return of(p.getUniqueId());
    }

    public Cartel byName(String name) {
        return cartels.get(key(name));
    }

    public List<Cartel> all() {
        return new ArrayList<>(cartels.values());
    }

    /** Best selling cartels first. */
    public List<Cartel> top() {
        List<Cartel> out = new ArrayList<>(cartels.values());
        out.sort((a, b) -> a.sales != b.sales ? Double.compare(b.sales, a.sales) : a.name.compareToIgnoreCase(b.name));
        return out;
    }

    /** Leaderboard place of a cartel (1 = sold the most). */
    public int place(Cartel c) {
        return top().indexOf(c) + 1;
    }

    public List<Tier> tiers() {
        return tiers;
    }

    public Tier tier(Cartel c) {
        return tiers.get(Math.max(0, Math.min(tiers.size() - 1, c.level - 1)));
    }

    /** The next level, or null at the top. */
    public Tier next(Cartel c) {
        return c.level < tiers.size() ? tiers.get(c.level) : null;
    }

    public double sellBonus(UUID player) {
        Cartel c = enabled() ? of(player) : null;
        return c == null ? 0 : tier(c).sell();
    }

    public double growBonus(UUID player) {
        Cartel c = enabled() ? of(player) : null;
        return c == null ? 0 : tier(c).grow();
    }

    public double labBonus(UUID player) {
        Cartel c = enabled() ? of(player) : null;
        return c == null ? 0 : Math.min(0.9, tier(c).lab());
    }

    /** Cartels that invited this player. */
    public List<Cartel> invitesFor(UUID player) {
        List<Cartel> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Invite i : invites.getOrDefault(player, List.of())) {
            Cartel c = cartels.get(i.cartel());
            if (c != null && now <= i.expires() && !out.contains(c)) {
                out.add(c);
            }
        }
        return out;
    }

    public double createCost() {
        return Math.max(0, plugin.getConfig().getDouble("cartel.create-cost", 2500));
    }

    // ------------------------------------------------------------------
    // changing (each returns an error for the player, or null when it worked)
    // ------------------------------------------------------------------

    static String key(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    /** Checks a cartel name: 3-16 letters, numbers, spaces, ' and -; not taken. */
    public String checkName(String name) {
        int max = Math.max(3, plugin.getConfig().getInt("cartel.max-name-length", 16));
        if (name.length() < 3 || name.length() > max || !name.matches("[A-Za-z0-9 '\\-]+") || key(name).isEmpty()) {
            return "Names must be 3-" + max + " letters or numbers.";
        }
        if (cartels.containsKey(key(name))) {
            return "There is already a cartel called that.";
        }
        return null;
    }

    /** Founds a cartel without paying (used by create and the selftest). */
    public Cartel found(UUID leader, String name) {
        Cartel c = new Cartel(key(name), name, Cartel.COLORS[Math.floorMod(name.hashCode(), Cartel.COLORS.length)], leader);
        c.created = System.currentTimeMillis();
        c.nextShipment = c.created;
        add(c);
        dirty = true;
        tick();
        return c;
    }

    public String create(Player p, String name) {
        if (!enabled()) {
            return "Cartels are turned off on this server.";
        }
        if (of(p) != null) {
            return "Leave your cartel first.";
        }
        String bad = checkName(name);
        if (bad != null) {
            return bad;
        }
        if (!plugin.economy().withdraw(p, createCost())) {
            return "Starting a cartel costs " + money(createCost()) + ".";
        }
        Cartel c = found(p.getUniqueId(), name);
        invites.remove(p.getUniqueId());
        plugin.awards().grant(p, Award.CARTEL);
        Bukkit.broadcast(Text.msg("<white>" + Text.escape(p.getName()) + " <gray>started a cartel: " + c.colored()));
        plugin.ranks().showInTab(p);
        return null;
    }

    public String invite(Player from, OfflinePlayer to) {
        Cartel c = of(from);
        if (c == null) {
            return "You're not in a cartel.";
        }
        if (of(to) == c) {
            return Text.escape(String.valueOf(to.getName())) + " is already in your cartel.";
        }
        if (c.members.size() >= tier(c).members()) {
            return "Your cartel is full (" + tier(c).members() + "). Level it up for more room.";
        }
        invites.computeIfAbsent(to.getUniqueId(), k -> new ArrayList<>())
                .add(new Invite(c.id, System.currentTimeMillis() + 10 * 60_000L));
        Player online = to.getPlayer();
        if (online != null) {
            online.sendMessage(Text.msg(c.colored() + " <gray>invited you! <click:run_command:/kush cartel join "
                    + c.id + "><hover:show_text:'Join " + Text.escape(c.name) + "'><green><u>[Join]</u></green></hover></click>"
                    + " <dark_gray>(or Cartel tab)"));
            online.playSound(online.getLocation(), "minecraft:block.note_block.pling", SoundCategory.MASTER, 0.8f, 1.2f);
        }
        return null;
    }

    public String join(Player p, Cartel c) {
        if (of(p) != null) {
            return "Leave your cartel first.";
        }
        if (!invitesFor(p.getUniqueId()).contains(c)) {
            return "You need an invite from " + Text.escape(c.name) + ".";
        }
        if (c.members.size() >= tier(c).members()) {
            return Text.escape(c.name) + " is full.";
        }
        invites.remove(p.getUniqueId());
        addMember(c, p.getUniqueId());
        plugin.awards().grant(p, Award.CARTEL);
        tell(c, "<white>" + Text.escape(p.getName()) + " <gray>joined the cartel!");
        plugin.ranks().showInTab(p);
        return null;
    }

    /** Adds a member without checks (selftest). */
    public void addMember(Cartel c, UUID id) {
        c.members.add(id);
        byMember.put(id, c);
        dirty = true;
    }

    public String leave(Player p) {
        Cartel c = of(p);
        if (c == null) {
            return "You're not in a cartel.";
        }
        removeMember(c, p.getUniqueId());
        p.sendMessage(Text.msg("<gray>You left " + c.colored() + "<gray>."));
        if (cartels.containsKey(c.id)) {
            tell(c, "<white>" + Text.escape(p.getName()) + " <gray>left the cartel.");
        }
        plugin.ranks().showInTab(p);
        return null;
    }

    /** Takes someone out; a cartel without members is closed (its bank is lost). */
    public void removeMember(Cartel c, UUID id) {
        c.members.remove(id);
        byMember.remove(id);
        dirty = true;
        if (c.members.isEmpty()) {
            cartels.remove(c.id);
            return;
        }
        if (c.leader.equals(id)) {
            c.leader = c.members.iterator().next();
            tell(c, "<white>" + Text.escape(name(c.leader)) + " <gray>is the new boss.");
        }
    }

    public String kick(Player boss, UUID member) {
        Cartel c = of(boss);
        if (c == null || !c.isLeader(boss.getUniqueId())) {
            return "Only the boss can kick members.";
        }
        if (member.equals(boss.getUniqueId()) || !c.members.contains(member)) {
            return "They're not in your cartel.";
        }
        removeMember(c, member);
        tell(c, "<white>" + Text.escape(name(member)) + " <gray>was kicked out.");
        Player kicked = Bukkit.getPlayer(member);
        if (kicked != null) {
            kicked.sendMessage(Text.msg("<red>You were kicked out of " + c.colored() + "<red>."));
            plugin.ranks().showInTab(kicked);
        }
        return null;
    }

    public String deposit(Player p, double amount) {
        Cartel c = of(p);
        if (c == null) {
            return "You're not in a cartel.";
        }
        amount = Math.min(amount, plugin.economy().balance(p));
        if (amount < 1 || !plugin.economy().withdraw(p, amount)) {
            return "You don't have any money to put in.";
        }
        c.bank += amount;
        dirty = true;
        tell(c, "<white>" + Text.escape(p.getName()) + " <gray>put <gold>" + money(amount) + " <gray>in the bank.");
        return null;
    }

    public String withdraw(Player p, double amount) {
        Cartel c = of(p);
        if (c == null || !c.isLeader(p.getUniqueId())) {
            return "Only the boss can take money out.";
        }
        amount = Math.min(amount, c.bank);
        if (amount < 1) {
            return "The bank is empty.";
        }
        c.bank -= amount;
        plugin.economy().deposit(p, amount);
        dirty = true;
        tell(c, "<white>" + Text.escape(p.getName()) + " <gray>took <gold>" + money(amount) + " <gray>out of the bank.");
        return null;
    }

    public String upgrade(Player p) {
        Cartel c = of(p);
        if (c == null || !c.isLeader(p.getUniqueId())) {
            return "Only the boss can level up the cartel.";
        }
        Tier next = next(c);
        if (next == null) {
            return "Your cartel is at the top level.";
        }
        if (c.bank < next.cost()) {
            return "The bank needs " + money(next.cost()) + ".";
        }
        c.bank -= next.cost();
        c.level++;
        dirty = true;
        Bukkit.broadcast(Text.msg(c.colored() + " <gray>is now a <gold>" + next.name() + "<gray>!"));
        for (UUID m : c.members) {
            Player o = Bukkit.getPlayer(m);
            if (o != null) {
                o.playSound(o.getLocation(), "minecraft:ui.toast.challenge_complete", SoundCategory.MASTER, 0.7f, 1.2f);
                if (next(c) == null) {
                    plugin.awards().grant(o, Award.CARTEL_MAX);
                }
            }
        }
        return null;
    }

    public String recolor(Player p) {
        Cartel c = of(p);
        if (c == null || !c.isLeader(p.getUniqueId())) {
            return "Only the boss can change the colour.";
        }
        int i = 0;
        for (int k = 0; k < Cartel.COLORS.length; k++) {
            if (Cartel.COLORS[k] == c.color) {
                i = k;
            }
        }
        c.color = Cartel.COLORS[(i + 1) % Cartel.COLORS.length];
        dirty = true;
        for (UUID m : c.members) {
            Player o = Bukkit.getPlayer(m);
            if (o != null) {
                plugin.ranks().showInTab(o);
            }
        }
        return null;
    }

    /** Hands in as much of the shipment as the player carries. They're paid the normal price for it. */
    public String deliver(Player p) {
        Cartel c = of(p);
        if (c == null) {
            return "Join a cartel to deliver shipments.";
        }
        Cartel.Shipment s = c.shipment;
        if (s == null) {
            return "No shipment right now.";
        }
        int have = InventoryUtil.count(p, it -> Items.type(it) == s.type());
        if (have <= 0) {
            return "You have no " + s.type().display() + ".";
        }
        int take = InventoryUtil.remove(p, it -> Items.type(it) == s.type(), Math.min(have, s.left()));
        double pay = Math.round(plugin.shop().basePrice(s.type()) * take * 100) / 100.0;
        plugin.economy().deposit(p, pay);
        plugin.ranks().sold(p, pay);
        c.shipment = new Cartel.Shipment(s.type(), s.amount(), s.delivered() + take, s.reward(), s.expires());
        dirty = true;
        p.sendActionBar(Text.mm("<green>Delivered " + take + "x " + s.type().display() + " <gold>+" + money(pay)));
        if (c.shipment.done()) {
            c.bank += s.reward();
            c.shipments++;
            c.shipment = null;
            c.nextShipment = System.currentTimeMillis() + cooldown();
            Bukkit.broadcast(Text.msg(c.colored() + " <gray>delivered a shipment of <white>" + s.amount() + "x "
                    + s.type().display() + " <gray>for <gold>" + money(s.reward()) + "<gray>!"));
            for (UUID m : c.members) {
                Player o = Bukkit.getPlayer(m);
                if (o != null) {
                    plugin.awards().grant(o, Award.SHIPMENT);
                    o.playSound(o.getLocation(), "minecraft:ui.toast.challenge_complete", SoundCategory.MASTER, 0.7f, 1f);
                }
            }
        }
        return null;
    }

    /** A member sold product: the cartel's sales go up and the bank gets its cut on top. */
    public void sold(UUID player, double money) {
        Cartel c = of(player);
        if (c == null || !enabled() || money <= 0) {
            return;
        }
        c.sales += money;
        c.bank += money * Math.max(0, plugin.getConfig().getDouble("cartel.bank-cut", 0.05));
        dirty = true;
    }

    /** A member filled a contract: the bank gets a cut of the reward. */
    public void contract(UUID player, double reward) {
        Cartel c = of(player);
        if (c == null || !enabled()) {
            return;
        }
        c.bank += reward * Math.max(0, plugin.getConfig().getDouble("cartel.contract-cut", 0.1));
        dirty = true;
    }

    /** Admin / selftest: set a cartel's level. */
    public void level(Cartel c, int level) {
        c.level = Math.max(1, Math.min(tiers.size(), level));
        dirty = true;
    }

    /** Admin: put money in a cartel's bank. */
    public void addBank(Cartel c, double amount) {
        c.bank = Math.max(0, c.bank + amount);
        dirty = true;
    }

    /** Admin: every cartel gets a new shipment now. */
    public void newShipments() {
        for (Cartel c : cartels.values()) {
            c.shipment = null;
            c.nextShipment = 0;
        }
        tick();
        dirty = true;
    }

    /** Selftest clean-up, admin panel. */
    public void disband(Cartel c) {
        for (UUID m : new ArrayList<>(c.members)) {
            byMember.remove(m);
        }
        cartels.remove(c.id);
        dirty = true;
    }

    // ------------------------------------------------------------------

    private void add(Cartel c) {
        cartels.put(c.id, c);
        for (UUID m : c.members) {
            byMember.put(m, c);
        }
    }

    private void tell(Cartel c, String msg) {
        for (UUID m : c.members) {
            Player o = Bukkit.getPlayer(m);
            if (o != null) {
                o.sendMessage(Text.msg(c.colored() + " <dark_gray>» " + msg));
            }
        }
    }

    public static String name(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    private String money(double v) {
        return plugin.economy().format(v);
    }
}
