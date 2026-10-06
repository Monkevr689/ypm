package dev.kushcraft.command;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effect.Dose;
import dev.kushcraft.guide.Guide;
import dev.kushcraft.gui.GuiFont;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.lab.LabRecipe;
import dev.kushcraft.machine.Machine;
import dev.kushcraft.machine.MachineType;
import dev.kushcraft.plant.Plant;
import dev.kushcraft.plant.PlantManager;
import dev.kushcraft.recipe.RecipeBook;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.strain.StrainType;
import dev.kushcraft.util.BlockKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /kush selftest (console, admin): exercises items, plants, machines, the
 * shop and the guide inside the running server and reports problems.
 * Used by CI against a real Paper server; safe to run on a test world.
 */
final class SelfTest {

    private final KushCraft plugin;
    private final List<String> fails = new ArrayList<>();
    private int checks;

    SelfTest(KushCraft plugin) {
        this.plugin = plugin;
    }

    private void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            fails.add(what);
        }
    }

    List<String> run() {
        try {
            items();
            strains();
            climates();
            shop();
            ranks();
            breeding();
            cartels();
            recipes();
            exchange();
            money();
            awards();
            menus();
            World w = Bukkit.getWorlds().get(0);
            Location spawn = w.getSpawnLocation();
            int bx = spawn.getBlockX() + 4, bz = spawn.getBlockZ() + 4;
            int y = w.getHighestBlockYAt(bx, bz);
            plants(w, bx, y, bz);
            machines(w, bx + 3, y, bz);
            guide();
            jobs(w, bx - 4, y, bz);
        } catch (Throwable t) {
            fails.add("exception: " + t);
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "selftest crashed", t);
        }
        plugin.getLogger().info("SELFTEST " + (fails.isEmpty() ? "PASS" : "FAIL") + " - " + checks + " checks, "
                + fails.size() + " failed");
        for (String f : fails) {
            plugin.getLogger().warning("  selftest failure: " + f);
        }
        return fails;
    }

    private void items() {
        Strain s = plugin.strains().all().iterator().next();
        for (ItemType t : ItemType.values()) {
            ItemStack it = t.strainBound() ? Items.strainItem(t, s, 4, 2) : Items.create(t, 2);
            check(Items.type(it) == t, "item round trip " + t);
            check(it.getItemMeta().hasItemModel(), "item model set " + t);
            NamespacedKey model = it.getItemMeta().getItemModel();
            check(model != null && model.getNamespace().equals("kush") && model.getKey().equals(t.model()),
                    "item model key " + t + " = " + model);
            if (t.strainBound()) {
                check(Items.strain(it) != null && Items.strain(it).id().equals(s.id()), "strain round trip " + t);
                if (t != ItemType.SEED_PACK) {
                    check(Items.quality(it) == 4, "quality round trip " + t);
                }
            }
            if (t.tinted()) {
                var cmd = it.getItemMeta().getCustomModelDataComponent();
                check(cmd.getColors().size() == 3, "bud, leaf and hair colours " + t);
                check(cmd.getStrings().size() == 2 && cmd.getStrings().get(0).equals(s.look().shape().id())
                        && cmd.getStrings().get(1).equals(s.exotic().id()), "bud shape + look strings " + t);
            }
        }
        check(Items.hits(Items.strainItem(ItemType.JOINT, s, 3, 1)) == Items.JOINT_HITS, "joint hits");
        check(Items.hits(Items.strainItem(ItemType.VAPE_PEN, s, 3, 1)) == Items.VAPE_HITS, "vape pen puffs");
        check(Dose.strain(s, 5, 60, 10).effects().size() == s.effects().size(), "dose effects");
        check(GuiFont.space(-169).length() == 4, "negative space builder");
        check(LabRecipe.values().length == 21, "lab recipes");
        for (LabRecipe r : LabRecipe.values()) {
            check(r.ingredients().size() <= 2, "easy recipe (1-2 ingredients): " + r);
            int count = 0;
            for (LabRecipe.Ingredient in : r.ingredients()) {
                check(in.custom() != ItemType.CATALYST, "no catalyst needed: " + r);
                count += in.amount();
            }
            check(count <= 5, "cheap recipe (5 items at most): " + r);
            check(r.seconds() <= 40, "quick recipe (40s at most): " + r);
        }
        check(LabRecipe.LUCID_TAB.ingredients().stream().anyMatch(i -> i.custom() == ItemType.ERGOT),
                "LSD is made from ergot");
        check(LabRecipe.ERGOT.ingredients().stream().anyMatch(i -> i.vanilla() == Material.WHEAT),
                "ergot comes from wheat");
        for (ItemType t : ItemType.values()) {
            if (!t.retired() && dev.kushcraft.catalog.Catalog.of(t) == null
                    && t.machine() == null) {
                // every non-block item should be explained in the catalog
                check(false, "catalog entry for " + t);
            }
            if (t.isDrug() && !t.strainBound() && t != ItemType.SPACE_BROWNIE) {
                check(dev.kushcraft.catalog.Catalog.dose(t) != null, "dose for " + t);
            }
        }
    }

    private void shop() {
        check(!plugin.shop().buyEntries().isEmpty(), "shop has buy entries");
        var shopStrains = plugin.strains().shopStrains();
        check(shopStrains.size() >= 20, "20+ strains for sale, got " + shopStrains.size());
        check(plugin.shop().seeds().size() <= 27 && plugin.shop().gear().size() <= 9, "shop fits its rows");
        for (Strain s : shopStrains) {
            check(plugin.shop().seeds().stream().anyMatch(e -> s.id().equals(e.strain())), "seeds for sale: " + s.id());
        }
        double cheap = shopStrains.get(0).seedPrice(), dear = shopStrains.get(shopStrains.size() - 1).seedPrice();
        check(dear >= cheap * 5, "better strains cost more (" + cheap + " .. " + dear + ")");
        double commonAvg = shopStrains.stream().filter(s -> s.rarity().ordinal() <= 1).mapToDouble(Strain::seedPrice)
                .average().orElse(0);
        double epicAvg = shopStrains.stream().filter(s -> s.rarity().ordinal() >= 3).mapToDouble(Strain::seedPrice)
                .average().orElse(1e9);
        check(epicAvg > commonAvg, "epic seeds cost more than common ones");
        for (var e : plugin.shop().gear()) {
            check(e.price() <= 400, "gear is cheap: " + e.type() + " " + e.price());
        }
        for (var e : plugin.shop().buyEntries()) {
            check(Items.type(plugin.shop().create(e)) == e.type(), "shop item " + e.type());
        }
        Strain s = plugin.strains().all().iterator().next();
        check(plugin.shop().sellPrice(Items.strainItem(ItemType.BUD_DRIED, s, 3, 1)) > 0, "dried bud sells");
        check(plugin.shop().sellPrice(Items.strainItem(ItemType.BUD_DRIED, s, 5, 1))
                > plugin.shop().sellPrice(Items.strainItem(ItemType.BUD_DRIED, s, 1, 1)), "quality raises price");
        check(plugin.shop().sellPrice(Items.create(ItemType.BONG)) == 0, "dealer does not buy bongs");
        // market: selling lowers the price, orders exist and pay more than the market
        double before = plugin.shop().sellPrice(Items.create(ItemType.COCAINE));
        check(before > 0, "cocaine sells");
        plugin.market().sold(ItemType.COCAINE, 10);
        check(plugin.shop().sellPrice(Items.create(ItemType.COCAINE)) < before, "selling lowers the price");
        plugin.market().tick();
        check(!plugin.market().orders().isEmpty(), "contracts exist");
        for (var o : plugin.market().orders()) {
            check(o.reward() >= plugin.shop().basePrice(o.type()) * o.amount(), "order pays a bonus: " + o.type());
        }
        check(plugin.market().hot() != null, "a hot item is picked");
        for (var e : plugin.shop().buyEntries()) {
            check(!e.type().retired(), "retired block not sold: " + e.type());
        }
        File zip = new File(plugin.getDataFolder(), "KushCraft-pack.zip");
        check(zip.isFile() && zip.length() > 10_000, "resource pack zip written");
    }

    private ItemDisplay display(UUID id) {
        Entity e = id == null ? null : Bukkit.getEntity(id);
        return e instanceof ItemDisplay d ? d : null;
    }

    private String shownModel(UUID id) {
        ItemDisplay d = display(id);
        return d == null ? null : d.getItemStack().getItemMeta().getItemModel().getKey();
    }

    private void plants(World w, int x, int y, int z) {
        PlantManager pm = plugin.plants();
        String[] kinds = {"SATIVA", "INDICA", "HYBRID", "MUSHROOM", "COCA", "POPPY", "PEYOTE"};
        for (int i = 0; i < kinds.length; i++) {
            Block soil = w.getBlockAt(x, y, z + i * 2);
            soil.setType(i == 3 ? Material.MYCELIUM : Material.FARMLAND);
            Plant.Kind kind = i < 3 ? Plant.Kind.CANNABIS : Plant.Kind.valueOf(kinds[i]);
            soil.getRelative(0, 1, 0).setType(Material.AIR);
            soil.getRelative(0, 2, 0).setType(Material.AIR);
            BlockKey key = BlockKey.of(soil.getRelative(0, 1, 0));
            Strain strain = null;
            if (i < 3) {
                StrainType st = StrainType.valueOf(kinds[i]);
                for (Strain s : plugin.strains().all()) {
                    if (s.type() == st) {
                        strain = s;
                        break;
                    }
                }
                if (strain == null) {
                    strain = plugin.strains().all().iterator().next();
                }
            }
            Plant p = pm.plantAt(key, kind, strain, null);
            String prefix = kind != Plant.Kind.CANNABIS ? "plant_" + kind.name().toLowerCase(java.util.Locale.ROOT) + "_"
                    : "plant_" + strain.type().plantModel() + "_";
            check(pm.at(key) == p, kinds[i] + " plant registered");
            check(prefix.concat("0").equals(shownModel(p.displayId())), kinds[i] + " seedling model, got " + shownModel(p.displayId()));
            check(p.hitboxId() != null && Bukkit.getEntity(p.hitboxId()) instanceof Interaction, kinds[i] + " hitbox");
            check(pm.fromEntity(Bukkit.getEntity(p.hitboxId())) == p, kinds[i] + " hitbox -> plant lookup");
            pm.conditions(p);
            p.growth(50);
            pm.refresh(p);
            check(p.stage() >= 1 && (prefix + p.stage()).equals(shownModel(p.displayId())), kinds[i] + " mid stage model");
            check(pm.fertilize(p), kinds[i] + " fertilize");
            p.growth(100);
            pm.refresh(p);
            int last = kind.lastStage();
            check((prefix + last).equals(shownModel(p.displayId())), kinds[i] + " mature model, got " + shownModel(p.displayId()));
            UUID disp = p.displayId();
            pm.harvest(p, null);
            check(pm.at(key) == null, kinds[i] + " removed after harvest");
            check(Bukkit.getEntity(disp) == null || Bukkit.getEntity(disp).isDead(), kinds[i] + " display removed");
            int drops = 0;
            for (Entity e : w.getNearbyEntities(key.center(), 2, 2, 2)) {
                if (e instanceof Item item && Items.isCustom(item.getItemStack())) {
                    drops++;
                    e.remove();
                }
            }
            check(drops > 0, kinds[i] + " harvest drops");
        }
    }

    private void machines(World w, int x, int y, int z) {
        int i = 0;
        for (MachineType type : MachineType.values()) {
            Block b = w.getBlockAt(x, y + 1, z + i * 2);
            b.getRelative(0, 1, 0).setType(Material.AIR);
            Machine m = plugin.machines().placeAt(b, type, 90f, null);
            check(b.getType() == Material.BARRIER, type + " barrier placed");
            check(plugin.machines().at(b) == m, type + " registered");
            check(type.model().equals(shownModel(m.displayId())), type + " model, got " + shownModel(m.displayId()));
            if (type == MachineType.DRYING_RACK) {
                Strain s = plugin.strains().all().iterator().next();
                m.fillRack(s.id(), 4, 10, System.currentTimeMillis() + 60_000);
                plugin.machines().refresh(m);
                check("machine_drying_rack_fresh".equals(shownModel(m.displayId())), "rack fresh model");
                m.fillRack(s.id(), 4, 10, System.currentTimeMillis() - 1);
                plugin.machines().refresh(m);
                check("machine_drying_rack_dry".equals(shownModel(m.displayId())), "rack dry model");
            }
            if (type == MachineType.LAB_STATION) {
                Strain s = plugin.strains().all().iterator().next();
                long now = System.currentTimeMillis();
                check(Machine.RACKS == 5 && m.racksInUse() == 0, "5 empty drying racks");
                check(plugin.machines().dryingSeconds() <= 30, "buds dry in 30s");
                for (int r = 0; r < Machine.RACKS; r++) {
                    m.rack(r, new Machine.Rack(s.id(), 3, 10 + r, now - 1000, r < 2 ? now - 1 : now + 30_000));
                }
                check(m.racksInUse() == 5 && m.racksDry() == 2, "racks dry on their own");
                check(m.rack(4).progress() > 0 && m.rack(4).progress() < 1 && m.rack(4).secondsLeft() <= 30,
                        "rack progress");
                plugin.machines().save();
                var saved = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new File(plugin.getDataFolder(), "machines.yml"));
                var racks = saved.getConfigurationSection("machines." + BlockKey.of(b).serialize() + ".racks");
                check(racks != null && racks.getKeys(false).size() == 5 && racks.getInt("3.amount") == 13,
                        "racks are saved");
                m.startJob(LabRecipe.HASH.name(), 1, Items.create(ItemType.LUCID_TAB, 2));
                check(m.busy(), "lab busy");
                double slow = dev.kushcraft.gui.LabMenu.timeFactor(m);
                m.level(3);
                check(dev.kushcraft.gui.LabMenu.timeFactor(m) < slow && dev.kushcraft.gui.LabMenu.bonusChance(m) > 0,
                        "lab upgrades cook faster");
                check(Items.level(Items.machine(ItemType.LAB_STATION, 3)) == 3, "picked-up lab keeps its level");
            }
            if (type == MachineType.GROW_LAMP) {
                check(b.getRelative(0, 1, 0).getType() == Material.LIGHT, "lamp light block");
                check(plugin.machines().lampNear(BlockKey.of(b), 3), "lamp lookup");
            }
            plugin.machines().breakMachine(m, null);
            check(b.getType() == Material.AIR, type + " barrier removed");
            check(plugin.machines().at(b) == null, type + " unregistered");
            for (Entity e : w.getNearbyEntities(b.getLocation().add(0.5, 0.5, 0.5), 2, 2, 2)) {
                if (e instanceof Item) {
                    e.remove();
                }
            }
            i++;
        }
    }

    private void guide() {
        ItemStack book = Guide.book(null);
        check(book.getItemMeta() instanceof BookMeta bm && bm.getPageCount() > 10, "guide book pages");
        List<String> pages = Guide.pages(true);
        check(pages.size() > 30 && pages.size() <= 100, "handbook has 30-100 pages, got " + pages.size());
        int pictures = 0;
        for (boolean pics : new boolean[]{true, false}) {
            for (String p : Guide.pages(pics)) {
                String plain = dev.kushcraft.util.Text.plain(dev.kushcraft.util.Text.mm(p));
                int lines = Guide.lines(plain);
                check(lines <= 14, "handbook page needs " + lines + " lines (max 14): "
                        + plain.substring(0, Math.min(40, plain.length())).replace('\n', ' '));
            }
        }
        for (String p : pages) {
            if (p.contains("<font:kush:book>")) {
                pictures++;
            }
        }
        check(pictures == RecipeBook.all().size(), "a picture page for every recipe (" + pictures + ")");
    }

    private void ranks() {
        var ranks = plugin.ranks();
        check(ranks.titles().size() == 6, "6 dealer titles, got " + ranks.titles().size());
        check(ranks.forPlace(1).name().equals("Cartel Boss"), "#1 seller is the Cartel Boss");
        check(ranks.forPlace(2) != ranks.forPlace(1) && ranks.forPlace(4) == ranks.forPlace(5), "titles by place");
        check(ranks.forPlace(0) == ranks.everyone() && ranks.forPlace(100_000) == ranks.everyone(),
                "no sales = Street Seller");
        // three sellers: whoever sold the most is on top
        var a = Bukkit.getOfflinePlayer(UUID.randomUUID());
        var b = Bukkit.getOfflinePlayer(UUID.randomUUID());
        var c = Bukkit.getOfflinePlayer(UUID.randomUUID());
        plugin.economy().addSales(a, 9e12);
        plugin.economy().addSales(b, 8e12);
        plugin.economy().addSales(c, 7e12);
        ranks.refresh();
        check(ranks.place(a) == 1 && ranks.place(b) == 2 && ranks.place(c) == 3, "leaderboard order");
        check(ranks.of(a) == ranks.forPlace(1) && ranks.of(a).bonus() > ranks.of(c).bonus(), "top seller has the best title");
        plugin.economy().addSales(c, 3e12);
        ranks.refresh();
        check(ranks.place(c) == 1 && ranks.place(a) == 2, "outselling takes the title");
        for (var x : List.of(a, b, c)) {
            plugin.economy().setSales(x, 0);
        }
        ranks.refresh();
        check(ranks.place(a) == 0, "test sellers removed");
        check(plugin.shop().buyEntries().stream().anyMatch(e -> e.type() == ItemType.LAB_STATION && e.price() <= 500),
                "the Drug Lab is cheap to start with");
    }

    private void breeding() {
        var all = new ArrayList<>(plugin.strains().all());
        Strain a = all.get(0), b = all.get(Math.min(1, all.size() - 1));
        java.util.Random r = new java.util.Random(42);
        java.util.Set<java.util.List<dev.kushcraft.effect.EffectType>> seen = new java.util.HashSet<>();
        boolean mutated = false;
        for (int i = 0; i < 200; i++) {
            var res = dev.kushcraft.strain.Breeding.cross(a, b, r);
            check(!res.effects().isEmpty() && res.effects().size() <= dev.kushcraft.strain.Breeding.MAX_EFFECTS,
                    "bred strain has 1-4 effects");
            check(new java.util.HashSet<>(res.effects()).size() == res.effects().size(), "no duplicate effects");
            check(res.potency() >= 5 && res.potency() <= 35, "bred potency in range");
            for (var e : res.effects()) {
                check(e.selectable(), "bred effect is a strain effect: " + e);
            }
            mutated |= !res.mutations().isEmpty();
            seen.add(res.effects());
        }
        check(mutated, "mutations happen");
        int mythic = 0, newLooks = 0;
        java.util.Set<dev.kushcraft.strain.Climate> climatesSeen = java.util.EnumSet.noneOf(dev.kushcraft.strain.Climate.class);
        for (int i = 0; i < 20_000; i++) {
            var res = dev.kushcraft.strain.Breeding.cross(a, b, r);
            check(res.look() != null && res.look().shape() != null && res.climate() != null, "bred look");
            if (res.look().exotic() != dev.kushcraft.strain.Exotic.NONE) {
                mythic++;
                check(res.rarity() == dev.kushcraft.strain.Rarity.MYTHIC, "exotic look = Mythic");
            }
            newLooks += res.newLook() ? 1 : 0;
            climatesSeen.add(res.climate());
        }
        check(mythic > 100 && mythic < 600, "Mythic is very rare (" + mythic + " in 20,000)");
        check(newLooks > 3000, "new colours mutate in");
        check(climatesSeen.size() == dev.kushcraft.strain.Climate.values().length, "climates can mutate");
        Strain aurora = plugin.strains().get("aurora_kush");
        if (aurora != null) {
            int kept = 0;
            for (int i = 0; i < 5000; i++) {
                if (dev.kushcraft.strain.Breeding.cross(aurora, a, r).look().exotic() != dev.kushcraft.strain.Exotic.NONE) {
                    kept++;
                }
            }
            check(kept > 800 && kept < 1400, "a Mythic parent passes its look on about 1 in 5 (" + kept + ")");
        }
        check(seen.size() > 3, "breeding is random (" + seen.size() + " different results)");
        check(dev.kushcraft.strain.Rarity.of(35, 4) == dev.kushcraft.strain.Rarity.LEGENDARY
                && dev.kushcraft.strain.Rarity.of(5, 1) == dev.kushcraft.strain.Rarity.COMMON, "rarity tiers");
        check(dev.kushcraft.strain.Rarity.of(5, 1, dev.kushcraft.strain.Exotic.NEON) == dev.kushcraft.strain.Rarity.MYTHIC,
                "exotic = Mythic");
        check(dev.kushcraft.strain.Rarity.MYTHIC.priceFactor() > dev.kushcraft.strain.Rarity.LEGENDARY.priceFactor(),
                "Mythic sells for the most");
        check(dev.kushcraft.effect.EffectType.values().length >= 28, "28 effects");
    }

    /** The recipe pictures in the resource pack must show the real recipes. */
    private void recipes() {
        List<RecipeBook.Entry> all = RecipeBook.all();
        check(all.size() >= 20, "recipe book has every recipe, got " + all.size());
        for (RecipeBook.Entry e : all) {
            RecipeBook.Picture pic = RecipeBook.pictures().get(e.id());
            check(pic != null, "recipe picture for " + e.id());
            if (pic != null && e.signature() != null) {
                check(e.signature().equals(pic.sig()), "recipe picture of " + e.id() + " is out of date: game "
                        + e.signature() + " vs picture " + pic.sig());
            }
            check(e.grid().length == 9, "recipe grid " + e.id());
        }
        check(!RecipeBook.making(dev.kushcraft.item.ItemType.COCAINE).isEmpty(), "catalog -> recipe link");
    }

    private void exchange() {
        var ex = plugin.exchange();
        check(ex.enabled(), "exchange enabled with items");
        check(!ex.categories().isEmpty() && ex.categories().size() <= 7, "1-7 trade shelves, got " + ex.categories().size());
        for (var c : ex.categories()) {
            check(!c.offers().isEmpty() && c.offers().size() <= 27, "shelf fits 3 rows: " + c.id());
        }
        check(ex.offers().size() >= 100, "100+ resources to trade, got " + ex.offers().size());
        for (var o : ex.offers()) {
            check(ex.sellPrice(o.material()) < ex.buyPrice(o), "exchange sells cheaper than it buys: " + o.material());
        }
        if (ex.offer(Material.DIAMOND) != null) {
            check(ex.buyPrice(ex.offer(Material.DIAMOND)) >= 10 * plugin.shop().basePrice(ItemType.COCAINE),
                    "resources cost a lot of product (a diamond >= 10 cocaine)");
        }
        var diamond = ex.offer(Material.DIAMOND);
        check(diamond != null, "exchange trades diamonds");
        if (diamond != null) {
            double buy = ex.buyPrice(diamond);
            ex.bought(Material.DIAMOND, 5);
            check(ex.buyPrice(diamond) > buy, "buying raises the price");
            check(ex.sellPrice(Material.DIAMOND) < buy, "no profit from buying and selling back");
            ex.sold(Material.DIAMOND, 50);
            check(ex.buyPrice(diamond) < buy, "selling lowers the price");
            check(ex.multiplier(Material.DIAMOND) >= plugin.getConfig().getDouble("exchange.min-price", 0.8) - 1e-9,
                    "price stays above min-price");
            for (int i = 0; i < 30; i++) {
                ex.tick();
            }
            check(Math.abs(ex.multiplier(Material.DIAMOND) - 1) < 1e-9, "price recovers to normal");
        }
        check(ex.sellable(new ItemStack(Material.DIAMOND, 3)), "plain diamonds can be sold");
        ItemStack named = new ItemStack(Material.DIAMOND);
        named.editMeta(m -> m.customName(net.kyori.adventure.text.Component.text("x")));
        check(!ex.sellable(named), "renamed items can't be sold");
        check(!ex.sellable(Items.create(ItemType.COCAINE)), "KushCraft items don't sell at the exchange");
    }

    private void awards() {
        var aw = plugin.awards();
        dev.kushcraft.award.Award[] all = dev.kushcraft.award.Award.values();
        check(all.length >= 40 && all.length <= 45, "40-45 awards (one menu page), got " + all.length);
        for (var a : all) {
            check(a.parent() == null || a.parent().ordinal() < a.ordinal(), "award parent comes first: " + a);
            var adv = Bukkit.getAdvancement(dev.kushcraft.award.Awards.key(a.id()));
            check(adv != null, "advancement loaded: kush:" + a.id());
            if (adv != null && adv.getDisplay() != null) {
                check(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(adv.getDisplay().title()).equals(a.title()), "advancement title " + a);
            }
        }
        check(aw.advancementsLoaded() == all.length, "KushCraft advancement tab loaded ("
                + aw.advancementsLoaded() + "/" + all.length + ")");
        check(Bukkit.getAdvancement(dev.kushcraft.award.Awards.key("root")) != null, "advancement tab root");
        var p = Bukkit.getOfflinePlayer(UUID.randomUUID());
        check(!aw.has(p, dev.kushcraft.award.Award.FIRST_SEED) && aw.count(p) == 0, "new players have no awards");
        check("0/100".equals(aw.progress(p, dev.kushcraft.award.Award.HARVEST_100)), "award progress");
        check(dev.kushcraft.award.Awards.drugs().size() >= 20, "drugs to try");
    }

    /** Every page fits its layout (tools/gui.py draws the matching backgrounds). */
    private void menus() {
        for (var t : dev.kushcraft.gui.TabMenu.Tab.values()) {
            check(GuiFont.GUIS.contains(t.name().toLowerCase(java.util.Locale.ROOT)), "background for tab " + t);
        }
        for (var t : dev.kushcraft.gui.LabTabMenu.Tab.values()) {
            check(GuiFont.GUIS.contains(t.name().toLowerCase(java.util.Locale.ROOT)), "background for lab tab " + t);
        }
        for (var c : dev.kushcraft.catalog.Catalog.Category.values()) {
            if (c != dev.kushcraft.catalog.Catalog.Category.GROW) {
                check(dev.kushcraft.catalog.Catalog.entries(c).size() <= 9, "one row of drugs: " + c);
            }
        }
        check(GuiFont.GUIS.contains("top"), "background for Top Dealers");
        check(LabRecipe.values().length <= 27, "cook page fits 3 rows");
        check(dev.kushcraft.gui.TabMenu.Tab.values().length == 5, "5 main panels");
    }

    private void strains() {
        var all = new ArrayList<>(plugin.strains().all());
        check(all.size() >= 25, "25+ strains, got " + all.size());
        java.util.Set<String> names = new java.util.HashSet<>(), looks = new java.util.HashSet<>();
        java.util.Set<dev.kushcraft.strain.Climate> climates = java.util.EnumSet.noneOf(dev.kushcraft.strain.Climate.class);
        java.util.Set<dev.kushcraft.strain.BudShape> shapes = java.util.EnumSet.noneOf(dev.kushcraft.strain.BudShape.class);
        int mythic = 0;
        for (Strain s : all) {
            if (s.isCustom()) {
                continue;
            }
            check(names.add(s.name().toLowerCase(java.util.Locale.ROOT)), "unique strain name " + s.name());
            check(looks.add(s.look().bud() + "/" + s.look().leaf() + "/" + s.look().pistil() + "/" + s.look().shape()),
                    "unique look " + s.name());
            check(!s.flavor().isEmpty(), "flavour for " + s.name());
            check(!s.effects().isEmpty() && s.effects().size() <= 4, "1-4 effects " + s.name());
            climates.add(s.climate());
            shapes.add(s.look().shape());
            if (s.exotic() != dev.kushcraft.strain.Exotic.NONE) {
                mythic++;
                check(!s.inShop() && s.wildWeight() < 0.1, "Mythic strains are not sold and very rare: " + s.name());
                check(s.rarity() == dev.kushcraft.strain.Rarity.MYTHIC, "Mythic rarity " + s.name());
            }
        }
        check(climates.size() == dev.kushcraft.strain.Climate.values().length, "a strain for every climate");
        check(shapes.size() == dev.kushcraft.strain.BudShape.values().length, "every bud shape is used");
        check(mythic >= 2, "Mythic strains exist");
        // legacy strains (no look in strains.yml) still get one
        var legacy = dev.kushcraft.strain.Look.legacy(0xFF0000, dev.kushcraft.strain.StrainType.INDICA, "x");
        check(legacy.leaf() != 0 && legacy.pistil() == dev.kushcraft.strain.Look.DEFAULT_PISTIL, "legacy look");
        // the Mythic ones turn up in their biomes, but rarely
        Strain aurora = plugin.strains().get("aurora_kush");
        check(aurora != null && aurora.exotic() == dev.kushcraft.strain.Exotic.GALAXY, "Aurora Kush is a galaxy strain");
        check(Strain.class.getSimpleName().equals("Strain"), "strain class");
    }

    private void climates() {
        var C = dev.kushcraft.strain.Climate.class;
        check(dev.kushcraft.strain.Climate.of("jungle", 0.95, 0.9, 70) == dev.kushcraft.strain.Climate.TROPICAL, "jungle is tropical");
        check(dev.kushcraft.strain.Climate.of("desert", 2.0, 0, 70) == dev.kushcraft.strain.Climate.DESERT, "desert");
        check(dev.kushcraft.strain.Climate.of("savanna", 2.0, 0, 70) == dev.kushcraft.strain.Climate.DESERT, "savanna");
        check(dev.kushcraft.strain.Climate.of("plains", 0.8, 0.4, 70) == dev.kushcraft.strain.Climate.TEMPERATE, "plains");
        check(dev.kushcraft.strain.Climate.of("swamp", 0.8, 0.9, 63) == dev.kushcraft.strain.Climate.WETLAND, "swamp");
        check(dev.kushcraft.strain.Climate.of("river", 0.5, 0.5, 62) == dev.kushcraft.strain.Climate.WETLAND, "river");
        check(dev.kushcraft.strain.Climate.of("snowy_plains", 0.0, 0.5, 70) == dev.kushcraft.strain.Climate.COLD, "snow");
        check(dev.kushcraft.strain.Climate.of("taiga", 0.25, 0.8, 70) == dev.kushcraft.strain.Climate.COLD, "taiga");
        check(dev.kushcraft.strain.Climate.of("meadow", 0.5, 0.8, 120) == dev.kushcraft.strain.Climate.MOUNTAIN, "meadow");
        check(dev.kushcraft.strain.Climate.of("plains", 0.8, 0.4, 130) == dev.kushcraft.strain.Climate.MOUNTAIN, "high up");
        var tropical = dev.kushcraft.strain.Climate.TROPICAL;
        check(tropical.fit(tropical) == dev.kushcraft.strain.Climate.Fit.IDEAL, "ideal climate");
        check(tropical.fit(dev.kushcraft.strain.Climate.COLD) == dev.kushcraft.strain.Climate.Fit.HARSH, "opposite climate");
        check(tropical.fit(dev.kushcraft.strain.Climate.TEMPERATE) == dev.kushcraft.strain.Climate.Fit.OK, "ok climate");
        for (var a : C.getEnumConstants()) {
            for (var b : C.getEnumConstants()) {
                check(a.harsh(b) == b.harsh(a), "climates are symmetric");
            }
            check(a.fit(dev.kushcraft.strain.Climate.TEMPERATE) != dev.kushcraft.strain.Climate.Fit.HARSH,
                    "everything grows in temperate: " + a);
        }
        check(dev.kushcraft.strain.Climate.Fit.IDEAL.growth() > 1 && dev.kushcraft.strain.Climate.Fit.HARSH.growth() < 1,
                "climate changes growth");
        check(dev.kushcraft.strain.Climate.parse("WARM", null) == tropical, "2.x climate names");
    }

    private void cartels() {
        var cs = plugin.cartels();
        check(cs.enabled(), "cartels on");
        check(cs.tiers().size() == 5, "5 cartel levels");
        UUID boss = UUID.randomUUID(), member = UUID.randomUUID(), other = UUID.randomUUID();
        check(cs.checkName("x") != null && cs.checkName("Los Selftest") == null, "cartel names are checked");
        var c = cs.found(boss, "Los Selftest");
        var d = cs.found(other, "Selftest Crew");
        check(cs.of(boss) == c && cs.byName("los selftest") == c, "cartel lookup");
        check(cs.checkName("Los Selftest") != null, "cartel names are unique");
        cs.addMember(c, member);
        check(cs.of(member) == c && c.members().size() == 2, "joining a cartel");
        check(cs.sellBonus(member) == 0, "a new crew has no bonus");
        cs.sold(member, 10_000);
        check(Math.abs(c.sales() - 10_000) < 1e-6, "member sales count for the cartel");
        check(Math.abs(c.bank() - 10_000 * plugin.getConfig().getDouble("cartel.bank-cut", 0.05)) < 1e-6,
                "the bank gets its cut");
        cs.contract(member, 1000);
        check(c.bank() > 500, "contracts add to the bank");
        cs.level(c, 3);
        check(cs.sellBonus(member) > 0 && cs.growBonus(member) > 0 && cs.labBonus(member) > 0, "levels give bonuses");
        check(cs.sellBonus(UUID.randomUUID()) == 0, "no cartel, no bonus");
        cs.sold(other, 1);
        check(cs.top().get(cs.top().indexOf(c)) == c && cs.place(c) < cs.place(d), "cartel leaderboard");
        check(c.shipment() != null && c.shipment().amount() >= 8
                && c.shipment().reward() > plugin.shop().basePrice(c.shipment().type()) * c.shipment().amount(),
                "a shipment pays a bonus");
        cs.removeMember(c, boss);
        check(c.isLeader(member), "the boss leaving passes the cartel on");
        cs.disband(c);
        cs.disband(d);
        check(cs.of(member) == null && cs.byName("los selftest") == null, "cartels removed");
    }

    private void money() {
        var eco = plugin.economy();
        var a = Bukkit.getOfflinePlayer(UUID.randomUUID());
        var b = Bukkit.getOfflinePlayer(UUID.randomUUID());
        eco.set(a, 100);
        eco.set(b, 0);
        check(eco.withdraw(a, 40) && Math.abs(eco.balance(a) - 60) < 1e-6, "withdraw");
        eco.deposit(b, 40);
        check(Math.abs(eco.balance(b) - 40) < 1e-6, "deposit");
        check(!eco.withdraw(a, 1000), "can't spend more than you have");
    }

    private void jobs(World w, int x, int y, int z) {
        var jobs = plugin.jobs();
        check(jobs.rate(dev.kushcraft.jobs.Jobs.Job.MINER, "diamond_ore") > 0, "miner pays for diamonds");
        check(jobs.rate(dev.kushcraft.jobs.Jobs.Job.HUNTER, "ZOMBIE") > 0, "hunter pays for zombies");
        check(jobs.rate(dev.kushcraft.jobs.Jobs.Job.GROWER, Plant.Kind.CANNABIS.name()) > 0, "grower pays for cannabis");
        Block b = w.getBlockAt(x, y + 1, z);
        b.setType(Material.DIAMOND_ORE);
        check(!jobs.placed().isPlaced(b), "natural ore is not marked as placed");
        jobs.placed().mark(b);
        jobs.placed().mark(b.getRelative(0, 1, 0));
        check(jobs.placed().isPlaced(b), "placed ore is remembered");
        check(jobs.placed().unmark(b), "unmark finds the placed ore");
        check(!jobs.placed().isPlaced(b) && jobs.placed().isPlaced(b.getRelative(0, 1, 0)), "unmark only that block");
        jobs.placed().unmark(b.getRelative(0, 1, 0));
        b.setType(Material.AIR);
    }
}
