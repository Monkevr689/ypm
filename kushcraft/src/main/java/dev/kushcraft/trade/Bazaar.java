package dev.kushcraft.trade;

import dev.kushcraft.KushCraft;
import dev.kushcraft.economy.Economy;
import dev.kushcraft.economy.Tx;
import dev.kushcraft.storage.Database;
import dev.kushcraft.storage.ItemCodec;
import dev.kushcraft.storage.Persistence;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The Bazaar: players list a stack of anything for a price and anyone can buy it.
 *
 * The listed items are held here (in the database), not by the seller: listing takes them out of the
 * seller's inventory, buying puts them in the buyer's, cancelling or an expired listing gives them
 * back. A sale moves the money and removes the listing in the same server tick, so one listing can
 * never be sold twice and the money and the items can't disagree (both are saved in the same
 * database snapshot). {@code bazaar.sale-tax} of every sale is destroyed.
 */
public final class Bazaar implements Persistence.Source {

    public static final int ACTIVE = 0;
    /** Ran out: waiting for the seller to take it back. */
    public static final int EXPIRED = 1;

    public static final class Listing {
        private final long id;
        private final UUID seller;
        private String sellerName;
        private final long price;
        private final ItemStack item;
        private final long created;
        private final long expires;
        private int state;
        private boolean dirty;

        Listing(long id, UUID seller, String sellerName, long price, ItemStack item, long created, long expires, int state) {
            this.id = id;
            this.seller = seller;
            this.sellerName = sellerName;
            this.price = price;
            this.item = item;
            this.created = created;
            this.expires = expires;
            this.state = state;
        }

        public long id() {
            return id;
        }

        public UUID seller() {
            return seller;
        }

        public String sellerName() {
            return sellerName;
        }

        /** Price of the whole stack, in cents. */
        public long price() {
            return price;
        }

        /** A copy of the listed stack. */
        public ItemStack item() {
            return item.clone();
        }

        public long created() {
            return created;
        }

        public long expires() {
            return expires;
        }

        public int state() {
            return state;
        }

        public boolean active() {
            return state == ACTIVE;
        }
    }

    /** What a buyer gets. */
    public record Purchase(Listing listing, ItemStack item, long price, long tax) {
    }

    /** Either a result or the reason there is none. */
    public record Result<T>(T value, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    private final KushCraft plugin;
    private final Database db;
    private final Map<Long, Listing> listings = new LinkedHashMap<>();
    private final Set<Long> added = new HashSet<>();
    private final Set<Long> removed = new HashSet<>();
    private long nextId = 1;
    private long lastExpiry;

    public Bazaar(KushCraft plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    // ------------------------------------------------------------------
    // settings
    // ------------------------------------------------------------------

    public boolean enabled() {
        return plugin.getConfig().getBoolean("bazaar.enabled", true);
    }

    public double saleTax() {
        return Math.max(0, Math.min(0.9, plugin.getConfig().getDouble("bazaar.sale-tax", 0.08)));
    }

    public long minPrice() {
        return Math.max(1, Economy.cents(plugin.getConfig().getDouble("bazaar.min-price", 1)));
    }

    public long maxPrice() {
        return Math.max(minPrice(), Economy.cents(plugin.getConfig().getDouble("bazaar.max-price", 1_000_000_000)));
    }

    /** How many listings a player of this rank can have at once. */
    public int maxListings(int rankNumber) {
        int base = plugin.getConfig().getInt("bazaar.base-listings", 2);
        int per = plugin.getConfig().getInt("bazaar.listings-per-rank", 1);
        return Math.max(1, base + per * Math.max(0, rankNumber));
    }

    private long listingMillis() {
        return Math.max(1, plugin.getConfig().getLong("bazaar.listing-days", 3)) * 86_400_000L;
    }

    // ------------------------------------------------------------------
    // storage
    // ------------------------------------------------------------------

    /** Reads every listing (start-up, after a reset). */
    public void load() {
        listings.clear();
        added.clear();
        removed.clear();
        List<Listing> read = db.call(c -> {
            List<Listing> out = new ArrayList<>();
            try (var s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM listings ORDER BY id")) {
                while (rs.next()) {
                    ItemStack it = ItemCodec.decode(rs.getBytes("item"));
                    if (it == null || it.getType().isAir()) {
                        continue;
                    }
                    UUID seller;
                    try {
                        seller = UUID.fromString(rs.getString("seller"));
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    out.add(new Listing(rs.getLong("id"), seller, rs.getString("seller_name"), rs.getLong("price"), it,
                            rs.getLong("created"), rs.getLong("expires"), rs.getInt("state")));
                }
            }
            return out;
        });
        for (Listing l : read) {
            listings.put(l.id, l);
            nextId = Math.max(nextId, l.id + 1);
        }
        if (!read.isEmpty()) {
            plugin.getLogger().info("Loaded " + read.size() + " Bazaar listings.");
        }
    }

    @Override
    public void collect(Persistence.Batch b, boolean full) {
        if (added.isEmpty() && removed.isEmpty() && listings.values().stream().noneMatch(l -> l.dirty)) {
            return;
        }
        record Row(long id, String seller, String name, long price, byte[] item, long created, long expires, int state) {
        }
        List<Row> upserts = new ArrayList<>();
        List<Listing> touched = new ArrayList<>();
        for (Listing l : listings.values()) {
            if (added.contains(l.id) || l.dirty) {
                upserts.add(new Row(l.id, l.seller.toString(), l.sellerName, l.price, ItemCodec.encode(l.item), l.created,
                        l.expires, l.state));
                touched.add(l);
            }
        }
        List<Long> gone = new ArrayList<>(removed);
        Set<Long> addedNow = new HashSet<>(added);
        added.clear();
        removed.clear();
        touched.forEach(l -> l.dirty = false);
        b.write(c -> {
            if (!upserts.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO listings(id, seller, seller_name, price, item, created, expires, state) VALUES(?,?,?,?,?,?,?,?)
                        ON CONFLICT(id) DO UPDATE SET seller_name=excluded.seller_name, state=excluded.state""")) {
                    for (Row r : upserts) {
                        ps.setLong(1, r.id());
                        ps.setString(2, r.seller());
                        ps.setString(3, r.name());
                        ps.setLong(4, r.price());
                        ps.setBytes(5, r.item());
                        ps.setLong(6, r.created());
                        ps.setLong(7, r.expires());
                        ps.setInt(8, r.state());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            if (!gone.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM listings WHERE id=?")) {
                    for (long id : gone) {
                        ps.setLong(1, id);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        });
        b.onFailure(() -> {
            added.addAll(addedNow);
            touched.forEach(l -> {
                if (listings.containsKey(l.id)) {
                    l.dirty = true;
                }
            });
            for (long id : gone) {
                if (!listings.containsKey(id)) {
                    removed.add(id);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // looking at the Bazaar
    // ------------------------------------------------------------------

    public Listing get(long id) {
        return listings.get(id);
    }

    public int count() {
        return listings.size();
    }

    /** Everything on sale (newest first), not expired. */
    public List<Listing> active() {
        long now = System.currentTimeMillis();
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) {
            if (l.active() && l.expires > now) {
                out.add(l);
            }
        }
        out.sort(Comparator.comparingLong((Listing l) -> l.created).reversed());
        return out;
    }

    /** A player's own listings, expired ones included. */
    public List<Listing> of(UUID seller) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) {
            if (l.seller.equals(seller)) {
                out.add(l);
            }
        }
        out.sort(Comparator.comparingLong((Listing l) -> l.created).reversed());
        return out;
    }

    public int activeCount(UUID seller) {
        int n = 0;
        for (Listing l : listings.values()) {
            if (l.seller.equals(seller) && l.active()) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------
    // listing, buying, taking back
    // ------------------------------------------------------------------

    /**
     * Puts a stack on sale. {@code item} is exactly what is listed (the caller takes the same stack out
     * of the seller's inventory when this returns ok, and not before). Server thread only.
     */
    public Result<Listing> list(UUID seller, String name, int rankNumber, ItemStack item, long price) {
        if (!enabled()) {
            return new Result<>(null, "The Bazaar is turned off on this server.");
        }
        if (item == null || item.getType().isAir() || item.getAmount() < 1) {
            return new Result<>(null, "There is nothing to list.");
        }
        if (price < minPrice() || price > maxPrice()) {
            return new Result<>(null, "The price must be between " + plugin.economy().format(Economy.dollars(minPrice()))
                    + " and " + plugin.economy().format(Economy.dollars(maxPrice())) + ".");
        }
        int max = maxListings(rankNumber);
        if (activeCount(seller) >= max) {
            return new Result<>(null, "You can have " + max + " listings at your rank. Cancel one or rank up.");
        }
        long now = System.currentTimeMillis();
        Listing l = new Listing(nextId++, seller, name, price, item.clone(), now, now + listingMillis(), ACTIVE);
        listings.put(l.id, l);
        added.add(l.id);
        plugin.economy().ledger().log(seller, Tx.BAZAAR_LIST, 0, null, String.valueOf(l.id),
                "listed " + describe(item) + " for " + plugin.economy().format(Economy.dollars(price)));
        return new Result<>(l, null);
    }

    /**
     * Buys a listing: the money moves and the listing is gone in this same call. {@code canTake} says whether
     * the buyer has room for the stack (checked before any money moves). The caller puts the item in
     * their inventory when this returns ok. Server thread only.
     */
    public Result<Purchase> buy(UUID buyer, String buyerName, long id, Predicate<ItemStack> canTake) {
        Listing l = listings.get(id);
        long now = System.currentTimeMillis();
        if (l == null || !l.active() || l.expires <= now) {
            return new Result<>(null, "That listing is gone.");
        }
        if (l.seller.equals(buyer)) {
            return new Result<>(null, "That's your own listing.");
        }
        if (!canTake.test(l.item())) {
            return new Result<>(null, "Your inventory is full.");
        }
        long tax = Math.round(l.price * saleTax());
        String what = describe(l.item);
        String price = plugin.economy().format(Economy.dollars(l.price));
        if (!plugin.economy().sale(buyer, l.seller, l.price, tax, "bought " + what + " from " + l.sellerName + " for " + price,
                "sold " + what + " to " + buyerName + " for " + price + " (tax " + plugin.economy().format(Economy.dollars(tax)) + ")")) {
            return new Result<>(null, "You need " + price + ".");
        }
        remove(l);
        Player seller = Bukkit.getPlayer(l.seller);
        if (seller != null) {
            seller.sendMessage(Text.msg("<green>Sold!</green> <white>" + Text.escape(what) + "</white> <gray>to "
                    + Text.escape(buyerName) + " for <gold>" + plugin.economy().format(Economy.dollars(l.price - tax))
                    + "</gold> <dark_gray>(" + plugin.economy().format(Economy.dollars(tax)) + " tax)"));
            seller.playSound(seller.getLocation(), "minecraft:entity.experience_orb.pickup", 0.7f, 1.3f);
        }
        return new Result<>(new Purchase(l, l.item(), l.price, tax), null);
    }

    /**
     * The seller takes a listing back (cancel, or collect an expired one). Returns the stack for the caller
     * to give, or the reason it can't be done yet.
     */
    public Result<ItemStack> takeBack(UUID seller, long id, Predicate<ItemStack> canTake) {
        Listing l = listings.get(id);
        if (l == null || !l.seller.equals(seller)) {
            return new Result<>(null, "That listing is gone.");
        }
        ItemStack item = l.item();
        if (!canTake.test(item)) {
            return new Result<>(null, "Your inventory is full.");
        }
        remove(l);
        plugin.economy().ledger().log(seller, Tx.BAZAAR_BACK, 0, null, String.valueOf(id), "took back " + describe(item));
        return new Result<>(item, null);
    }

    private void remove(Listing l) {
        listings.remove(l.id);
        added.remove(l.id);
        removed.add(l.id);
    }

    /** Once a minute: listings past their time wait for their seller. */
    public void tick() {
        long now = System.currentTimeMillis();
        if (now - lastExpiry < 60_000L) {
            return;
        }
        lastExpiry = now;
        expire(now);
    }

    /** Listings whose time is up at {@code now} wait for their seller to take them back. */
    public void expire(long now) {
        for (Listing l : listings.values()) {
            if (l.active() && l.expires <= now) {
                l.state = EXPIRED;
                l.dirty = true;
                Player p = Bukkit.getPlayer(l.seller);
                if (p != null) {
                    p.sendMessage(Text.msg("<yellow>Your Bazaar listing of <white>" + Text.escape(describe(l.item))
                            + "</white> ran out.</yellow> <gray>Take it back in <white>/bazaar</white> > My listings."));
                }
            }
        }
    }

    /** A season reset that wipes inventories wipes what was waiting in the Bazaar too. */
    public void clearAll() {
        removed.addAll(listings.keySet());
        added.clear();
        listings.clear();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** "3x Cocaine". */
    public static String describe(ItemStack it) {
        String name = Text.plain(it.effectiveName());
        if (name.isBlank()) {
            name = Text.titleCase(it.getType().name().toLowerCase(Locale.ROOT));
        }
        return it.getAmount() + "x " + name;
    }

    /** "1500", "$1,500", "1.5k", "2m" -> cents; -1 when it isn't a price. */
    public static long parsePrice(String text) {
        if (text == null) {
            return -1;
        }
        String t = text.trim().toLowerCase(Locale.ROOT).replace("$", "").replace(",", "").replace("_", "");
        double mult = 1;
        if (t.endsWith("k")) {
            mult = 1_000;
            t = t.substring(0, t.length() - 1);
        } else if (t.endsWith("m")) {
            mult = 1_000_000;
            t = t.substring(0, t.length() - 1);
        } else if (t.endsWith("b")) {
            mult = 1_000_000_000;
            t = t.substring(0, t.length() - 1);
        }
        try {
            double v = Double.parseDouble(t) * mult;
            if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0 || v > 1e12) {
                return -1;
            }
            return Economy.cents(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** True when a player's inventory has room for all of these (nothing is changed). */
    public static boolean fits(Player p, ItemStack... items) {
        org.bukkit.inventory.Inventory tmp = Bukkit.createInventory(null, 36);
        ItemStack[] have = p.getInventory().getStorageContents();
        for (int i = 0; i < have.length && i < 36; i++) {
            tmp.setItem(i, have[i] == null ? null : have[i].clone());
        }
        for (ItemStack it : items) {
            if (it != null && !it.getType().isAir() && !tmp.addItem(it.clone()).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
