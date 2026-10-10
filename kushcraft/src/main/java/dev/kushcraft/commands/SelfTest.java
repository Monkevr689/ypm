package dev.kushcraft.commands;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effects.Dose;
import dev.kushcraft.guide.Guide;
import dev.kushcraft.menus.GuiFont;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.lab.LabRecipe;
import dev.kushcraft.machines.Machine;
import dev.kushcraft.machines.MachineType;
import dev.kushcraft.plants.Plant;
import dev.kushcraft.plants.PlantManager;
import dev.kushcraft.recipes.RecipeBook;
import dev.kushcraft.strains.Strain;
import dev.kushcraft.strains.StrainType;
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
            ladder();
            awards();
            menus();
            guideMenu();
            World w = Bukkit.getWorlds().get(0);
            Location spawn = w.getSpawnLocation();
            int bx = spawn.getBlockX() + 4, bz = spawn.getBlockZ() + 4;
            int y = w.getHighestBlockYAt(bx, bz);
            plants(w, bx, y, bz);
            machines(w, bx + 3, y, bz);
            workers(w, bx + 8, y, bz);
            away(w, bx + 8, y, bz + 12);
            nature(w, bx + 18, y, bz);
            guide();
            jobs(w, bx - 4, y, bz);
        } catch (Throwable t) {
            fails.add("exception: " + t);
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "selftest crashed", t);
        } finally {
            plugin.getConfig().set("workers.auto-buy", false);
            plugin.getConfig().set("pvp.worker-raids.enabled", false);
            for (UUID id : fake) {
                plugin.players().delete(id);
            }
            plugin.persistence().flushNow();
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
                check(cmd.getColors().size() == 4, "bud, leaf, hair and accent colours " + t);
                check(cmd.getStrings().size() == 3 && cmd.getStrings().get(0).equals(s.look().shape().id())
                        && cmd.getStrings().get(1).equals(s.exotic().id())
                        && cmd.getStrings().get(2).equals(s.look().pattern().id()), "bud shape, look + pattern strings " + t);
            }
        }
        check(Items.hits(Items.strainItem(ItemType.JOINT, s, 3, 1)) == Items.JOINT_HITS, "joint hits");
        check(Items.hits(Items.strainItem(ItemType.VAPE_PEN, s, 3, 1)) == Items.VAPE_HITS, "vape pen puffs");
        check(Dose.strain(s, 5, 60, 10).effects().size() == s.effects().size(), "dose effects");
        check(GuiFont.space(-169).length() == 4, "negative space builder");
        check(LabRecipe.values().length == 27, "lab recipes");
        int seconds = 0;
        for (LabRecipe r : LabRecipe.values()) {
            check(r.ingredients().size() <= 4, "recipe needs 1-4 kinds of things: " + r);
            int count = 0;
            double in = 0;
            for (LabRecipe.Ingredient ing : r.ingredients()) {
                check(ing.custom() != ItemType.CATALYST, "no catalyst needed: " + r);
                check(!ing.where().isEmpty(), "recipe says where to get " + ing.name());
                count += ing.amount();
                if (ing.custom() != null) {
                    in += plugin.shop().basePrice(ing.custom()) * ing.amount();
                }
            }
            check(count <= 10, "recipe needs 10 items at most: " + r);
            check(r.seconds() <= 60, "recipe takes a minute at most: " + r);
            double out = plugin.shop().basePrice(r.output()) * r.amount();
            check(out > in, "every cooking step adds value: " + r + " (" + in + " -> " + out + ")");
            seconds += r.seconds();
        }
        check(seconds / LabRecipe.values().length >= 30, "cooking takes some effort (30s+ on average)");
        // the steps follow real life loosely: drugs come from an in-between product
        check(uses(LabRecipe.LUCID_TAB, ItemType.ERGOT_EXTRACT) && uses(LabRecipe.ERGOT_EXTRACT, ItemType.ERGOT),
                "LSD: ergot > extract > tabs");
        check(LabRecipe.ERGOT.ingredients().stream().anyMatch(i -> i.vanilla() == Material.WHEAT),
                "ergot comes from wheat");
        check(uses(LabRecipe.COCAINE, ItemType.COCA_PASTE) && uses(LabRecipe.COCA_PASTE, ItemType.COCA_LEAVES),
                "cocaine: leaves > paste > cocaine");
        check(uses(LabRecipe.HEROIN, ItemType.MORPHINE) && uses(LabRecipe.MORPHINE, ItemType.POPPY_SEEDS)
                && LabRecipe.MORPHINE.ingredients().size() == 1, "heroin: poppy seeds > morphine base > heroin");
        boolean craft = false;
        for (dev.kushcraft.recipes.Recipes.Info r : dev.kushcraft.recipes.Recipes.info()) {
            if (r.result() == ItemType.MORPHINE) {
                craft = java.util.Arrays.stream(r.grid()).filter(java.util.Objects::nonNull)
                        .allMatch("kush:poppy_seeds"::equals);
            }
        }
        check(craft, "morphine base crafts straight from poppy seeds");
        var morphineKey = new NamespacedKey(plugin, ItemType.MORPHINE.id());
        check(dev.kushcraft.recipes.Recipes.usesCustomItems(morphineKey) && Bukkit.getRecipe(morphineKey) != null,
                "the morphine base recipe is registered and allowed past the custom-item guard");
        check(uses(LabRecipe.VAPE_PEN, ItemType.BUD_DRIED) && uses(LabRecipe.LEAN, ItemType.COUGH_SYRUP),
                "vape pens are made from dried buds, lean has its own step");
        check(uses(LabRecipe.AYAHUASCA, ItemType.DMT) && uses(LabRecipe.OXY, ItemType.MORPHINE)
                && uses(LabRecipe.SHROOM_CHOCOLATE, ItemType.MAGIC_MUSHROOM), "new drugs build on old ones");
        for (LabRecipe r : LabRecipe.values()) {
            check(!r.output().strainBound() || r == LabRecipe.VAPE_PEN, "weed is just buds, joints, blunts and vape pens: " + r);
            check(!r.output().legacy(), "old weed products are not made any more: " + r);
        }
        for (ItemType t : ItemType.values()) {
            if (t.legacy()) {
                check(plugin.shop().basePrice(t) > 0 && !dev.kushcraft.awards.Awards.drugs().contains(t),
                        "old weed products still sell: " + t);
            }
        }
        for (ItemType t : ItemType.values()) {
            if (!t.ingredient()) {
                continue;
            }
            check(LabRecipe.making(t) != null || t == ItemType.ERGOT, "something makes " + t);
            check(java.util.Arrays.stream(LabRecipe.values()).anyMatch(r -> uses(r, t)), "something uses " + t);
            check(plugin.shop().basePrice(t) > 0, "in-between product sells: " + t);
        }
        // water bottles come back as bottles
        ItemStack water = new ItemStack(Material.POTION);
        water.editMeta(org.bukkit.inventory.meta.PotionMeta.class, m -> m.setBasePotionType(org.bukkit.potion.PotionType.WATER));
        ItemStack swift = new ItemStack(Material.POTION);
        swift.editMeta(org.bukkit.inventory.meta.PotionMeta.class, m -> m.setBasePotionType(org.bukkit.potion.PotionType.SWIFTNESS));
        LabRecipe.Ingredient w = LabRecipe.SHROOM_TEA.ingredients().get(1);
        check(w.matches(water) && !w.matches(swift) && w.name().equals("Water Bottle")
                && w.remainder() == Material.GLASS_BOTTLE, "water bottle ingredient");
        for (ItemType t : ItemType.values()) {
            if (!t.retired() && dev.kushcraft.catalog.Catalog.of(t) == null
                    && t.machine() == null && dev.kushcraft.workers.WorkerType.of(t) == null) {
                // every non-block item should be explained in the catalog
                check(false, "catalog entry for " + t);
            }
            if (t.isDrug() && !t.strainBound() && !t.legacy()) {
                check(dev.kushcraft.catalog.Catalog.dose(t) != null, "dose for " + t);
            }
        }
    }

    private static int count(org.bukkit.inventory.Inventory inv, ItemType t) {
        int n = 0;
        for (ItemStack it : inv.getStorageContents()) {
            if (Items.type(it) == t) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private static boolean uses(LabRecipe r, ItemType t) {
        return r.ingredients().stream().anyMatch(i -> i.custom() == t);
    }

    private void shop() {
        check(!plugin.shop().buyEntries().isEmpty(), "shop has buy entries");
        var shopStrains = plugin.strains().shopStrains();
        check(shopStrains.size() >= 20, "20+ strains for sale, got " + shopStrains.size());
        check(plugin.shop().gear().size() <= 18
                && plugin.shop().hires().size() == dev.kushcraft.workers.WorkerType.values().length, "shop fits its rows");
        // Mythic seeds: for sale, last, and pricier than any other seed; Exotic ones never
        var seedList = plugin.shop().seeds();
        int firstMythic = -1;
        double dearestNormal = 0, cheapestMythic = Double.MAX_VALUE;
        for (int i = 0; i < seedList.size(); i++) {
            Strain st = seedList.get(i).strain() == null ? null : plugin.strains().get(seedList.get(i).strain());
            boolean myth = st != null && st.rarity().animated();
            check(st == null || st.rarity() != dev.kushcraft.strains.Rarity.EXOTIC, "no Exotic seeds for sale");
            if (myth) {
                firstMythic = firstMythic < 0 ? i : firstMythic;
                cheapestMythic = Math.min(cheapestMythic, seedList.get(i).price());
            } else {
                check(firstMythic < 0, "Mythic seeds come last in the Shop");
                dearestNormal = Math.max(dearestNormal, seedList.get(i).price());
            }
        }
        check(firstMythic >= 0 && seedList.size() - firstMythic >= 15, "15+ Mythic seeds for sale");
        check(cheapestMythic > dearestNormal, "Mythic seeds cost more than any other seed (" + cheapestMythic + ")");
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
            check(e.price() <= 4000, "gear costs at most 4000: " + e.type() + " " + e.price());
        }
        for (var e : plugin.shop().hires()) {
            check(e.price() >= 45000, "9.0: workers are very expensive: " + e.type() + " " + e.price());
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
        // selling a big pile pays less per item than selling one
        double one = plugin.market().bulkFactor(ItemType.HEROIN, 0, 1);
        double pile = plugin.market().bulkFactor(ItemType.HEROIN, 0, 200);
        check(one > 0.99 && pile < 0.8 && pile >= plugin.getConfig().getDouble("market.min-price", 0.4) - 1e-9,
                "dumping 200 of one thing pays less (" + pile + ")");
        check(plugin.market().bulkFactor(ItemType.HEROIN, 100, 100) < plugin.market().bulkFactor(ItemType.HEROIN, 0, 100),
                "the second hundred pays less than the first");
        double base = plugin.shop().sellPrice(Items.create(ItemType.HEROIN));
        plugin.market().startBoost(1.5, 5);
        check(plugin.shop().sellPrice(Items.create(ItemType.HEROIN)) > base * 1.4, "a market boom raises prices");
        plugin.market().startBoost(1, 0);
        check(plugin.market().boost() == 1, "the boom can be stopped");
        plugin.market().resetPrices();
        check(plugin.market().demand(ItemType.COCAINE) == 1 && plugin.market().flooded().isEmpty(), "admin price reset");
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
                check(plugin.persistence().flushNow(), "the lab is written to the database");
                String racks = dbString("SELECT racks FROM machines WHERE pos=?", BlockKey.of(b).serialize());
                check(racks != null && racks.split(";").length == 5 && racks.contains("3|" + s.id() + "|3|13|"),
                        "racks are saved (" + racks + ")");
                m.startJob(LabRecipe.SPEED.name(), 1, Items.create(ItemType.LUCID_TAB, 2));
                check(m.busy(), "lab busy");
                double slow = dev.kushcraft.menus.LabMenu.timeFactor(m);
                m.level(3);
                check(dev.kushcraft.menus.LabMenu.timeFactor(m) < slow && dev.kushcraft.menus.LabMenu.bonusChance(m) > 0,
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

    /** A Farmhand harvests and replants, a Dryer fetches, hangs and collects - all for one owner. */
    /** Wild plants, high animals and what dying costs. */
    private void nature(World w, int x, int y, int z) {
        Block soil = w.getBlockAt(x, y, z);
        soil.setType(Material.GRASS_BLOCK);
        w.getBlockAt(x, y + 1, z).setType(Material.AIR);
        Plant wild = plugin.wild().grow(soil, Plant.Kind.CANNABIS);
        check(wild != null && wild.wild() && wild.owner() == null && wild.strainId() != null,
                "a wild plant grows by itself");
        check(wild != null && plugin.plants().at(BlockKey.of(soil).up()) == wild, "the wild plant is in the world");
        check(plugin.wild().grow(soil, Plant.Kind.CANNABIS) == null, "only one plant per spot");
        if (wild != null) {
            check(plugin.persistence().flushNow(), "plants are written to the database");
            String until = dbString("SELECT wild_until FROM plants WHERE pos=?", wild.key().serialize());
            check(until != null && Long.parseLong(until) > System.currentTimeMillis(),
                    "wild plants are saved with the time they wither");
            plugin.plants().remove(wild);
        }
        check(dev.kushcraft.plants.WildPlants.kindFor("desert", Material.SAND) == Plant.Kind.PEYOTE,
                "peyote grows wild in deserts");
        check(dev.kushcraft.plants.WildPlants.kindFor("mushroom_fields", Material.MYCELIUM) == Plant.Kind.MUSHROOM,
                "mushrooms grow wild on mycelium");
        check(dev.kushcraft.plants.WildPlants.kindFor("deep_ocean", Material.GRASS_BLOCK) == null,
                "nothing grows wild in the ocean");
        check(plugin.getConfig().getBoolean("wild.plants") && plugin.getConfig().getInt("wild.max-plants") > 0,
                "wild plants are on");
        // animals get high: red eyes, slowed down
        var cow = w.spawn(new Location(w, x + 0.5, y + 1, z + 2.5), org.bukkit.entity.Cow.class);
        plugin.animals().makeHigh(cow, dev.kushcraft.catalog.Catalog.Category.WEED, 5);
        check(plugin.animals().isHigh(cow) && cow.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS),
                "a cow gets high");
        check(cow.getPersistentDataContainer().has(dev.kushcraft.Keys.HIGH), "it stays high when its chunk reloads");
        cow.remove();
        // dying costs 20% of your cash, and nobody gets it
        double lost = plugin.getConfig().getDouble("pvp.death-cash-lost");
        check(lost >= 0 && lost <= 1, "dying costs part of your cash (" + lost + ")");
        check(dev.kushcraft.listeners.PlayerListener.cashLost(1234.56, 0.2) == 246.91
                && dev.kushcraft.listeners.PlayerListener.cashLost(0, 0.2) == 0, "the death loss is worked out right");
        // the pack: by default players get the copy of this version from GitHub
        String url = plugin.pack().externalUrl();
        check(url != null && url.startsWith(dev.kushcraft.pack.ResourcePackManager.HOSTED)
                && url.endsWith("KushCraft-pack-" + plugin.getPluginMeta().getVersion() + ".zip"),
                "the resource pack comes from GitHub (" + url + ")");
    }

    private void workers(World w, int x, int y, int z) {
        var ws = plugin.workers();
        var eco = plugin.economy();
        UUID boss = UUID.randomUUID();
        var owner = Bukkit.getOfflinePlayer(boss);
        fake.add(boss);
        eco.set(owner, 1000, "selftest");
        check(ws.enabled(), "workers are on");
        check(!ws.autoBuyAllowed() && !ws.autoBuy(boss), "9.0: workers don't buy their own supplies");
        // the rest of this test also covers auto-buy, for servers that switch it back on
        plugin.getConfig().set("workers.auto-buy", true);
        check(ws.autoBuy(boss) && ws.autoBuyAllowed(), "with workers.auto-buy on, it's on for new players");
        // a ripe plant on farmland, an empty farmland next to it, the farmhand standing beside them
        for (int dx = -1; dx <= 6; dx++) {
            for (int dz = -1; dz <= 4; dz++) {
                w.getBlockAt(x + dx, y, z + dz).setType(Material.STONE);
                w.getBlockAt(x + dx, y + 1, z + dz).setType(Material.AIR);
                w.getBlockAt(x + dx, y + 2, z + dz).setType(Material.AIR);
            }
        }
        w.getBlockAt(x, y, z).setType(Material.FARMLAND);
        w.getBlockAt(x, y, z + 2).setType(Material.FARMLAND);
        Strain s = plugin.strains().get("og_kush");
        if (s == null) {
            s = plugin.strains().all().iterator().next();
        }
        BlockKey key = BlockKey.of(w.getBlockAt(x, y + 1, z));
        Plant ripe = plugin.plants().plantAt(key, Plant.Kind.CANNABIS, s, boss);
        ripe.growth(100);
        var farm = ws.hireAt(new Location(w, x + 2.5, y + 1, z + 0.5), dev.kushcraft.workers.WorkerType.FARMHAND, boss, 1);
        check(ws.of(boss).contains(farm), "farmhand hired");
        check(farm.satchel().getSize() == 54, "satchels hold 54 stacks");
        check(farm.entityId() != null && Bukkit.getEntity(farm.entityId()) instanceof org.bukkit.entity.Mannequin,
                "the farmhand is a mannequin in the world");
        if (farm.entityId() != null && Bukkit.getEntity(farm.entityId()) != null) {
            check(ws.fromEntity(Bukkit.getEntity(farm.entityId())) == farm, "mannequin -> worker lookup");
        }
        check(ws.workNow(farm), "the farmhand harvests a ripe plant");
        Plant again = plugin.plants().at(key);
        check(again != null && again != ripe && !again.mature() && s.id().equals(again.strainId())
                && boss.equals(again.owner()), "and plants it again");
        int fresh = count(farm.satchel(), ItemType.BUD_FRESH);
        check(fresh > 0, "the harvest is in the satchel (" + fresh + " buds)");
        check(eco.balance(owner) < 1000 && farm.jobs() == 1, "the farmhand was paid");
        // seeds in the satchel go on the empty farmland
        ws.stash(farm, List.of(Items.create(ItemType.COCA_SEEDS, 2)));
        BlockKey empty = BlockKey.of(w.getBlockAt(x, y + 1, z + 2));
        boolean planted = false;
        for (int i = 0; i < 3 && !planted; i++) {
            ws.workNow(farm);
            planted = plugin.plants().at(empty) != null;
        }
        check(planted, "the farmhand plants seeds from the satchel on empty farmland");
        // no fertilizer anywhere: with auto-buy off they wait, with it on they buy some and use it
        ws.setAutoBuy(boss, false);
        check(!ws.autoBuy(boss) && !ws.workNow(farm) && count(farm.satchel(), ItemType.FERTILIZER) == 0,
                "auto-buy off: nothing is bought");
        ws.setAutoBuy(boss, true);
        double cash = eco.balance(owner);
        check(ws.workNow(farm) && count(farm.satchel(), ItemType.FERTILIZER) > 0 && eco.balance(owner) < cash,
                "auto-buy on: the farmhand buys fertilizer with the owner's money");
        check(ws.workNow(farm) && plugin.plants().at(key) != null && plugin.plants().at(key).fertilized(),
                "and fertilizes the plants with it");
        // empty farmland and no seeds: they buy seeds for it (never Mythic ones)
        List<ItemStack> keep = new ArrayList<>();
        for (ItemStack it : farm.satchel().getStorageContents()) {
            if (it != null && PlantManager.kindOf(Items.type(it)) == null) {
                keep.add(it);
            }
        }
        farm.satchel().clear();
        ws.stash(farm, keep);
        w.getBlockAt(x + 1, y, z + 2).setType(Material.FARMLAND);
        BlockKey third = BlockKey.of(w.getBlockAt(x + 1, y + 1, z + 2));
        cash = eco.balance(owner);
        check(ws.workNow(farm) && count(farm.satchel(), ItemType.SEED_PACK) > 0 && eco.balance(owner) < cash,
                "the farmhand buys seeds for empty farmland");
        for (ItemStack it : farm.satchel().getStorageContents()) {
            Strain bought = Items.type(it) == ItemType.SEED_PACK ? Items.strain(it) : null;
            check(bought == null || !bought.rarity().animated(), "workers never buy Mythic seeds");
        }
        check(ws.workNow(farm) && plugin.plants().at(third) != null, "and plants them");
        // the dryer next to a Drug Lab fetches the buds, hangs them and collects them dry
        Block labBlock = w.getBlockAt(x + 5, y + 1, z);
        Machine lab = plugin.machines().placeAt(labBlock, MachineType.LAB_STATION, 0f, boss);
        var dryer = ws.hireAt(new Location(w, x + 5.5, y + 1, z + 2.5), dev.kushcraft.workers.WorkerType.DRYER, boss, 1);
        check(ws.workNow(dryer), "the dryer fetches fresh buds from the farmhand");
        check(dryer.carried() > 0, "the dryer carries the buds");
        check(ws.workNow(dryer) && lab.racksInUse() > 0, "the dryer hangs them on the racks");
        lab.finishNow();
        check(lab.racksDry() > 0, "admin finish dries the racks");
        check(ws.workNow(dryer) && lab.racksInUse() == 0, "the dryer takes them off dry");
        check(count(dryer.satchel(), ItemType.BUD_DRIED) > 0, "dried buds in the dryer's satchel");
        dryer.satchel().clear(); // (one strain and quality from here on: the cook rolls all of them at once)
        // the cook: rolls joints from the dryer's buds and papers from a chest
        var cook = ws.hireAt(new Location(w, x + 4.5, y + 1, z + 2.5), dev.kushcraft.workers.WorkerType.COOK, boss, 1);
        check(!ws.workNow(cook) && cook.status().contains("Pick"), "a new cook waits for a drug to make");
        ws.setJob(cook, dev.kushcraft.workers.Worker.ROLL_JOINT);
        check(cook.rolls() == ItemType.JOINT && cook.product() == ItemType.JOINT, "the cook rolls joints");
        ws.stash(dryer, List.of(Items.strainItem(ItemType.BUD_DRIED, s, 3, 8)));
        check(ws.workNow(cook) && count(cook.satchel(), ItemType.BUD_DRIED) == 8,
                "the cook fetches dried buds from the dryer (work chain)");
        ws.setAutoBuy(boss, false);
        check(!ws.workNow(cook) && cook.status().contains("Rolling Papers"), "the cook says what's missing");
        ws.setAutoBuy(boss, true);
        Block chestBlock = w.getBlockAt(x + 3, y + 1, z + 3);
        chestBlock.setType(Material.CHEST);
        var chest = ((org.bukkit.block.Container) chestBlock.getState(false)).getInventory();
        chest.addItem(Items.create(ItemType.ROLLING_PAPERS, 8));
        ws.chestsChanged(chestBlock); // (what placing a chest does)
        check(ws.chests(cook).stream().anyMatch(b -> b.getLocation().equals(chestBlock.getLocation())),
                "any chest of yours near the cook is theirs to use");
        // a chest someone else placed is never touched
        Block theirs = w.getBlockAt(x + 6, y + 1, z - 1);
        theirs.setType(Material.BARREL);
        if (theirs.getState() instanceof org.bukkit.block.TileState ts) {
            ts.getPersistentDataContainer().set(dev.kushcraft.Keys.PLACER,
                    org.bukkit.persistence.PersistentDataType.STRING, UUID.randomUUID().toString());
            ts.update();
        }
        ws.chestsChanged(theirs);
        check(ws.chests(cook).stream().noneMatch(b -> b.getLocation().equals(theirs.getLocation())),
                "workers leave other players' chests alone");
        check(ws.workNow(cook) && count(cook.satchel(), ItemType.ROLLING_PAPERS) == 8 && count(chest,
                ItemType.ROLLING_PAPERS) == 0, "the cook takes papers from the chest");
        check(ws.workNow(cook) && cook.jobs() == 1, "the cook rolls");
        int joints = count(cook.satchel(), ItemType.JOINT);
        check(joints == 8 && count(cook.satchel(), ItemType.ROLLING_PAPERS) == 0,
                "the cook rolls joints, one paper each (" + joints + ")");
        // the runner picks up the joints and sells them right away; the owner gets the money minus a cut
        var runner = ws.hireAt(new Location(w, x + 0.5, y + 1, z + 4.5), dev.kushcraft.workers.WorkerType.RUNNER, boss, 1);
        check(ws.crew(runner).size() == 3 && ws.crew(cook).contains(runner),
                "the runner works with the farmhand, dryer and cook");
        check(ws.chestRadius(runner) >= ws.chainRadius() && !ws.chests(runner).isEmpty(),
                "the runner uses every chest of the crew");
        double before = eco.balance(owner);
        check(ws.workNow(runner) && count(cook.satchel(), ItemType.JOINT) == 0 && eco.balance(owner) > before
                && runner.carried() == 0 && runner.wages() > 0, "the runner picks up the joints and sells them at once");
        check(eco.sales(owner) > 0, "runner sales count for the leaderboard");
        // nobody takes what another worker needs: the dryer needs fresh buds, so the runner leaves them
        ws.stash(farm, List.of(Items.strainItem(ItemType.BUD_FRESH, s, 3, 5)));
        check(ws.uses(dryer, Items.strainItem(ItemType.BUD_FRESH, s, 3, 1)) && !ws.workNow(runner)
                && count(farm.satchel(), ItemType.BUD_FRESH) >= 5, "the runner leaves what the dryer needs");
        // the cook gathers a vape pen's ingredients from two chests in one trip and cooks 4 batches
        ws.setRecipe(cook, LabRecipe.VAPE_PEN);
        cook.satchel().clear();
        dryer.satchel().clear();
        Block chest2Block = w.getBlockAt(x + 6, y + 1, z + 3);
        chest2Block.setType(Material.BARREL);
        var chest2 = ((org.bukkit.block.Container) chest2Block.getState(false)).getInventory();
        ws.chestsChanged(chest2Block);
        chest.addItem(new ItemStack(Material.IRON_NUGGET, 8), new ItemStack(Material.GLASS_PANE, 4));
        chest2.addItem(Items.create(ItemType.LAB_SOLVENT, 4), Items.strainItem(ItemType.BUD_DRIED, s, 3, 16));
        check(ws.workNow(cook) && count(cook.satchel(), ItemType.LAB_SOLVENT) == 4
                && count(cook.satchel(), ItemType.BUD_DRIED) == 16 && chest.isEmpty() && chest2.isEmpty(),
                "the cook takes ingredients from several chests in one trip");
        check(ws.workNow(cook) && lab.busy() && "VAPE_PEN".equals(lab.job()), "the cook starts a batch at the lab");
        check(cook.jobs() == 2, "the cook is paid per batch");
        lab.finishNow();
        int pens = lab.output() == null ? 0 : lab.output().getAmount(); // vape pens don't stack: one batch at a time
        check(pens > 0 && ws.workNow(cook) && !lab.busy() && count(cook.satchel(), ItemType.VAPE_PEN) == pens,
                "the cook collects the batch");
        // the old stuck-lab bug: a finished batch of another drug is collected, the lab is freed
        ws.stash(cook, List.of(Items.strainItem(ItemType.BUD_DRIED, s, 3, 4), Items.create(ItemType.LAB_SOLVENT, 1),
                new ItemStack(Material.IRON_NUGGET, 2), new ItemStack(Material.GLASS_PANE, 1)));
        check(ws.workNow(cook) && lab.busy(), "the cook starts another batch");
        lab.finishNow();
        pens += lab.output() == null ? 0 : lab.output().getAmount();
        ws.setRecipe(cook, LabRecipe.SHROOM_TEA);
        check(ws.workNow(cook) && !lab.busy() && count(cook.satchel(), ItemType.VAPE_PEN) == pens,
                "a cook on a new drug collects the old batch first (no stuck lab)");
        // nothing to make: they put their work in a chest
        ws.setAutoBuy(boss, false);
        ws.workNow(cook);
        check(count(chest, ItemType.VAPE_PEN) + count(chest2, ItemType.VAPE_PEN) == pens
                && count(cook.satchel(), ItemType.VAPE_PEN) == 0, "an idle cook puts their work in a chest");
        while (count(chest, ItemType.VAPE_PEN) + count(chest2, ItemType.VAPE_PEN) < 4) {
            chest.addItem(Items.strainItem(ItemType.VAPE_PEN, s, 3, 1)); // a Runner comes for 4 or more
        }
        before = eco.balance(owner);
        check(ws.workNow(runner) && count(chest, ItemType.VAPE_PEN) + count(chest2, ItemType.VAPE_PEN) == 0
                && eco.balance(owner) > before && runner.carried() == 0, "the runner sells what's in the chests");
        // auto-buy: a cook missing an ingredient buys it
        ws.setAutoBuy(boss, true);
        ws.setRecipe(cook, LabRecipe.VAPE_PEN);
        cook.satchel().clear();
        ws.stash(cook, List.of(Items.strainItem(ItemType.BUD_DRIED, s, 3, 4), new ItemStack(Material.IRON_NUGGET, 2),
                new ItemStack(Material.GLASS_PANE, 1)));
        eco.set(owner, 5000, "selftest");
        check(ws.nextBuy(cook) != null && ws.workNow(cook) && count(cook.satchel(), ItemType.LAB_SOLVENT) > 0
                && eco.balance(owner) < 5000, "the cook buys the Lab Solvent they're missing");
        check(ws.maxPerPlayer() == 0 && ws.limit(boss) == plugin.ranks().get(1).workers(),
                "the rank decides how many workers (" + ws.limit(boss) + ")");
        check(ws.overLimit(cook) && !ws.overLimit(farm), "workers past the rank's slots don't work (the oldest do)");
        plugin.ranks().set(boss, plugin.ranks().top(), "selftest");
        check(!ws.overLimit(cook) && ws.limit(boss) == plugin.ranks().get(plugin.ranks().top()).workers(),
                "ranking up gives more slots");
        plugin.ranks().set(boss, 1, "selftest");
        // spare seeds become fertilizer when the satchel gets full and the chests are full or gone
        chestBlock.setType(Material.AIR);
        chest2Block.setType(Material.AIR);
        theirs.setType(Material.AIR);
        List<ItemStack> seeds = new ArrayList<>();
        for (int i = 0; i < 44; i++) {
            seeds.add(Items.create(ItemType.ROLLING_PAPERS, 64));
        }
        for (int i = 0; i < 26; i++) {
            seeds.add(Items.create(ItemType.COCA_SEEDS, 16));
        }
        farm.satchel().clear();
        ws.stash(farm, seeds);
        check(farm.freeSlots() < 6, "the farmhand's satchel is nearly full");
        ws.compost(farm);
        check(count(farm.satchel(), ItemType.COCA_SEEDS) == 64 && count(farm.satchel(), ItemType.FERTILIZER) == 88,
                "spare seeds become fertilizer");
        // nobody to pay: no work
        eco.set(owner, 0, "selftest");
        ripe = plugin.plants().at(key);
        if (ripe != null) {
            ripe.growth(100);
        }
        check(!ws.workNow(farm) && farm.status().contains("Not paid"), "unpaid workers stop");
        // saved in the database: the row, the satchel, the recipe, the auto-buy switch
        ws.setAutoBuy(boss, false);
        check(plugin.persistence().flushNow(), "workers are written to the database");
        check(dbString("SELECT id FROM workers WHERE id=?", runner.id().toString()) != null
                && "VAPE_PEN".equals(dbString("SELECT recipe FROM workers WHERE id=?", cook.id().toString())),
                "workers and a cook's recipe are saved");
        byte[] blob = plugin.db().call(c -> {
            try (var ps = c.prepareStatement("SELECT satchel FROM workers WHERE id=?")) {
                ps.setString(1, farm.id().toString());
                try (var rs = ps.executeQuery()) {
                    return rs.next() ? rs.getBytes(1) : null;
                }
            }
        });
        ItemStack[] back = dev.kushcraft.storage.ItemCodec.decode(blob, 54);
        int seedsBack = 0;
        for (ItemStack it : back) {
            if (Items.type(it) == ItemType.COCA_SEEDS) {
                seedsBack += it.getAmount();
            }
        }
        check(seedsBack == count(farm.satchel(), ItemType.COCA_SEEDS), "a satchel comes back from the database item for item");
        String flags = dbString("SELECT flags FROM players WHERE uuid=?", boss.toString());
        check(flags != null && (Integer.parseInt(flags) & dev.kushcraft.storage.PlayerRecord.AUTO_BUY_OFF) != 0,
                "the auto-buy switch is saved");
        ws.setAutoBuy(boss, true);
        raids(farm);
        check(ws.radius(farm) < ws.radius(ws.hireAt(new Location(w, x + 3.5, y + 1, z + 3.5),
                dev.kushcraft.workers.WorkerType.FARMHAND, boss, 3)), "trained workers reach further");
        check(Items.level(Items.machine(ItemType.FARMHAND, 2)) == 2, "a dismissed worker keeps their level");
        for (var wk : ws.of(boss)) {
            UUID ent = wk.entityId();
            ws.dismiss(wk, null);
            check(ent == null || Bukkit.getEntity(ent) == null || Bukkit.getEntity(ent).isDead(), "mannequin removed");
        }
        check(ws.of(boss).isEmpty(), "workers dismissed");
        plugin.getConfig().set("workers.auto-buy", false);
        plugin.machines().breakMachine(lab, null);
        for (BlockKey k : List.of(key, empty, third)) {
            Plant p = plugin.plants().at(k);
            if (p != null) {
                plugin.plants().remove(p);
            }
        }
        for (Entity e : w.getNearbyEntities(new Location(w, x + 2, y + 1, z + 1), 8, 3, 8)) {
            if (e instanceof Item) {
                e.remove();
            }
        }
    }

    /** The rank ladder: exponential costs, playtime and waits, worker slots, the gates. */
    private void ladder() {
        var ranks = plugin.ranks();
        var all = ranks.all();
        check(all.size() >= 10, "a long ladder (" + all.size() + " ranks)");
        check(all.get(0).cost() == 0 && all.get(0).workers() >= 1, "rank 1 is free and has a worker slot");
        for (int i = 2; i < all.size(); i++) {
            var lo = all.get(i - 1);
            var hi = all.get(i);
            check(hi.cost() >= lo.cost() * 1.7, "each rank costs a lot more than the last: " + hi.name());
            check(hi.playtimeHours() > lo.playtimeHours(), "each rank needs more playtime: " + hi.name());
            check(hi.waitHours() >= lo.waitHours(), "the waits never get shorter: " + hi.name());
            check(hi.workers() >= lo.workers() && hi.workers() - lo.workers() <= 1, "worker slots grow slowly: " + hi.name());
        }
        check(all.get(1).cost() <= 10_000 && all.get(1).playtimeHours() <= 3, "rank 2 is reachable in a first session");
        check(all.get(all.size() - 1).playtimeHours() >= 150, "the top rank is a long grind");
        check(ranks.minGapMillis() >= 10 * 60_000L, "rank-ups are at least 10 minutes apart");
        // the gates, on a fake player
        UUID id = UUID.randomUUID();
        fake.add(id);
        var eco = plugin.economy();
        var r = eco.account(id);
        var p = Bukkit.getOfflinePlayer(id);
        eco.set(p, all.get(1).cost() * 10, "selftest");
        r.rank(1, System.currentTimeMillis());
        check(ranks.rankUp(id, 1, null) != null && r.rank() == 1, "no playtime: no rank-up");
        r.setPlaytime((long) (all.get(1).playtimeHours() * 3600));
        r.lastRankTry = 0;
        check(ranks.rankUp(id, 1, null) != null && r.rank() == 1, "too soon after the last rank-up: no rank-up");
        r.rank(1, System.currentTimeMillis() - ranks.minGapMillis() - 1000);
        r.lastRankTry = 0;
        double before = eco.balance(p);
        check(ranks.rankUp(id, 1, null) == null && r.rank() == 2
                && Math.abs(before - eco.balance(p) - all.get(1).cost()) < 1e-6, "money + playtime + wait: rank 2, paid");
        r.lastRankTry = 0;
        check(ranks.rankUp(id, 1, null) != null && r.rank() == 2, "a second click from rank 1 does nothing");
        check(ranks.rankUp(id, 2, null) != null && r.rank() == 2, "and rank 3 needs its own wait");
        eco.set(p, 0, "selftest");
        r.setPlaytime(1_000_000);
        r.rank(2, 0);
        r.lastRankTry = 0;
        check(ranks.rankUp(id, 2, null) != null && r.rank() == 2, "no money: no rank-up");
        check(plugin.persistence().flushNow()
                && "2".equals(dbString("SELECT rank FROM players WHERE uuid=?", id.toString()))
                && dbString("SELECT id FROM ledger WHERE player=? AND type='RANK_UP'", id.toString()) != null,
                "the rank and the rank-up payment are in the database together");
    }

    /** Worker raids: off by default; when on, a knocked-out worker drops their satchel and rests. */
    private void raids(dev.kushcraft.workers.Worker w) {
        var raids = plugin.raids();
        check(!raids.enabled(), "worker raids are off until the admin turns them on");
        plugin.getConfig().set("pvp.worker-raids.enabled", true);
        var ws = plugin.workers();
        ws.stash(w, List.of(Items.create(ItemType.FERTILIZER, 5)));
        check(ws.hurt(w, 10, raids.maxHealth()) == raids.maxHealth() - 10, "a hit takes health off");
        List<ItemStack> dropped = ws.empty(w);
        ws.knockOut(w, System.currentTimeMillis() + raids.knockoutMillis());
        check(w.knockedOut() && w.carried() == 0 && dropped.stream().mapToInt(ItemStack::getAmount).sum() >= 5,
                "a knocked-out worker drops their satchel");
        ws.knockOut(w, 0);
        check(!w.knockedOut(), "and gets up again");
        ws.stash(w, dropped);
        plugin.getConfig().set("pvp.worker-raids.enabled", false);
    }

    /** Work while nobody is around: plants grow and a farmhand harvests and replants, caught up at once. */
    private void away(World w, int x, int y, int z) {
        var ws = plugin.workers();
        UUID boss = UUID.randomUUID();
        fake.add(boss);
        var owner = Bukkit.getOfflinePlayer(boss);
        plugin.economy().set(owner, 100_000, "selftest");
        for (int dx = -1; dx <= 5; dx++) {
            for (int dz = -1; dz <= 3; dz++) {
                w.getBlockAt(x + dx, y, z + dz).setType(Material.STONE);
                for (int dy = 1; dy <= 3; dy++) {
                    w.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR);
                }
            }
        }
        w.getBlockAt(x, y, z).setType(Material.WATER);
        Strain s = plugin.strains().get("og_kush");
        if (s == null) {
            s = plugin.strains().all().iterator().next();
        }
        List<BlockKey> keys = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            w.getBlockAt(x + i, y, z).setType(Material.FARMLAND);
            BlockKey k = BlockKey.of(w.getBlockAt(x + i, y + 1, z));
            plugin.plants().plantAt(k, Plant.Kind.CANNABIS, s, boss);
            keys.add(k);
        }
        var farm = ws.hireAt(new Location(w, x + 2.5, y + 1, z + 2.5), dev.kushcraft.workers.WorkerType.FARMHAND, boss, 1);
        check(ws.awayEnabled() && plugin.getConfig().getDouble("workers.away.rate") < 1, "away work is on, and slower");
        // a plant alone (no farmhand near it) catches up its growth when its chunk loads
        int jobs = ws.catchUpNow(farm, 3 * 3_600_000L);
        int buds = count(farm.satchel(), ItemType.BUD_FRESH);
        check(jobs > 0 && buds >= 6, "3 hours alone: the farmhand harvested again and again (" + jobs + " jobs, "
                + buds + " buds)");
        check(keys.stream().allMatch(k -> plugin.plants().at(k) != null), "and replanted every plant");
        check(ws.catchUpNow(farm, 30_000L) == 0, "half a minute alone isn't worth catching up");
        double cap = plugin.getConfig().getDouble("workers.away.max-hours");
        farm.satchel().clear();
        int capped = ws.catchUpNow(farm, (long) ((cap + 48) * 3_600_000L));
        farm.satchel().clear();
        int full = ws.catchUpNow(farm, (long) (cap * 3_600_000L));
        check(capped <= full * 1.5 + 5, "time alone is capped at " + cap + "h (" + capped + " vs " + full + ")");
        for (var wk : ws.of(boss)) {
            ws.dismiss(wk, null);
        }
        for (BlockKey k : keys) {
            Plant p = plugin.plants().at(k);
            if (p != null) {
                plugin.plants().remove(p);
            }
        }
        // a plant with nobody tending it grows while its chunk is unloaded
        w.getBlockAt(x + 1, y, z + 2).setType(Material.FARMLAND);
        BlockKey lone = BlockKey.of(w.getBlockAt(x + 1, y + 1, z + 2));
        Plant p = plugin.plants().plantAt(lone, Plant.Kind.CANNABIS, s, boss);
        p.growth(0);
        try {
            var f = Plant.class.getDeclaredField("grownAt");
            f.setAccessible(true);
            f.setLong(p, System.currentTimeMillis() - 3_600_000L);
        } catch (ReflectiveOperationException e) {
            check(false, "grownAt: " + e);
        }
        plugin.plants().catchUp(p);
        check(p.growth() > 0, "an untended plant grew while nobody was around (" + p.growth() + "%)");
        plugin.plants().remove(p);
        for (Entity e : w.getNearbyEntities(new Location(w, x + 2, y + 1, z + 1), 8, 3, 8)) {
            if (e instanceof Item) {
                e.remove();
            }
        }
    }

    /** menus.yml: the guide pages are there and point at real pages. */
    private void guideMenu() {
        var t = plugin.menuTexts();
        List<String> pages = List.of("main", "economy", "ranks", "rules", "community");
        for (String page : pages) {
            check(!t.entries(page).isEmpty(), "the guide has a " + page + " page");
            for (var e : t.entries(page)) {
                check(e.slot() >= 0 && e.slot() < 54 && e.slot() != 45, "guide item slot " + page + " " + e.slot());
                check(e.page() == null || pages.contains(e.page()), "guide link to a real page: " + e.page());
            }
        }
        check(t.link("discord") != null && !t.voteLinks().isEmpty(), "discord and vote links are set up in menus.yml");
        check(t.openOnFirstJoin(), "the guide opens on the first join");
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
        var ranks = plugin.titles();
        check(!ranks.titles().isEmpty(), "dealer titles are set up");
        check(ranks.forPlace(1) == ranks.titles().get(0), "#1 seller has the first title");
        for (int i = 1; i < ranks.titles().size(); i++) {
            check(ranks.titles().get(i).top() > ranks.titles().get(i - 1).top(), "titles by place");
        }
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
            plugin.economy().setSales(x, 0, "selftest");
            fake.add(x.getUniqueId());
        }
        ranks.refresh();
        check(ranks.place(a) == 0, "test sellers removed");
        check(plugin.shop().buyEntries().stream().anyMatch(e -> e.type() == ItemType.LAB_STATION && e.price() >= 3000),
                "9.0: a Drug Lab costs 5x the 8.0 price (or craft one)");
    }

    private void breeding() {
        var all = new ArrayList<>(plugin.strains().all());
        Strain a = all.get(0), b = all.get(Math.min(1, all.size() - 1));
        java.util.Random r = new java.util.Random(42);
        java.util.Set<java.util.List<dev.kushcraft.effects.EffectType>> seen = new java.util.HashSet<>();
        boolean mutated = false;
        for (int i = 0; i < 200; i++) {
            var res = dev.kushcraft.strains.Breeding.cross(a, b, r);
            check(!res.effects().isEmpty() && res.effects().size() <= dev.kushcraft.strains.Breeding.MAX_EFFECTS,
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
        java.util.Set<dev.kushcraft.strains.Climate> climatesSeen = java.util.EnumSet.noneOf(dev.kushcraft.strains.Climate.class);
        for (int i = 0; i < 20_000; i++) {
            var res = dev.kushcraft.strains.Breeding.cross(a, b, r);
            check(res.look() != null && res.look().shape() != null && res.climate() != null, "bred look");
            if (res.look().exotic() != dev.kushcraft.strains.Exotic.NONE) {
                mythic++;
                check(res.rarity() == dev.kushcraft.strains.Rarity.MYTHIC, "exotic look = Mythic");
            }
            newLooks += res.newLook() ? 1 : 0;
            climatesSeen.add(res.climate());
        }
        double expect = dev.kushcraft.strains.Breeding.newMythicChance(a, b) * 20_000;
        check(mythic > expect * 0.8 && mythic < expect * 1.2, "about 1 in 15 is Mythic (" + mythic + " in 20,000)");
        // two Legendary parents: a much better chance of a Mythic child
        List<Strain> legendary = all.stream().filter(s -> s.rarity() == dev.kushcraft.strains.Rarity.LEGENDARY).toList();
        check(legendary.size() >= 2, "there are Legendary strains to breed");
        if (legendary.size() >= 2) {
            int fromLegend = 0;
            for (int i = 0; i < 10_000; i++) {
                if (dev.kushcraft.strains.Breeding.cross(legendary.get(0), legendary.get(1), r).rarity()
                        == dev.kushcraft.strains.Rarity.MYTHIC) {
                    fromLegend++;
                }
            }
            check(fromLegend > 2000 && fromLegend < 2900, "two Legendary parents: about 1 in 4 Mythic (" + fromLegend
                    + " of 10,000)");
            check(dev.kushcraft.strains.Breeding.mythicChance(legendary.get(0), legendary.get(1))
                    > 3 * dev.kushcraft.strains.Breeding.MYTHIC, "Legendary parents raise the Mythic chance");
        }
        check(newLooks > 3000, "new colours mutate in");
        check(climatesSeen.size() == dev.kushcraft.strains.Climate.values().length, "climates can mutate");
        Strain aurora = plugin.strains().get("aurora_kush");
        if (aurora != null) {
            int kept = 0;
            for (int i = 0; i < 5000; i++) {
                if (dev.kushcraft.strains.Breeding.cross(aurora, a, r).look().exotic() != dev.kushcraft.strains.Exotic.NONE) {
                    kept++;
                }
            }
            check(kept > 1100 && kept < 1550, "a Mythic parent passes its look on (" + kept + " of 5,000)");
        }
        // Exotic: only from two Mythic parents (or better), and some pairs find a built-in Exotic strain
        Strain blood = plugin.strains().get("blood_moon_kush");
        Strain horizon = plugin.strains().get("event_horizon");
        if (aurora != null && blood != null && horizon != null) {
            int exotic = 0, found = 0, fromOne = 0;
            for (int i = 0; i < 20_000; i++) {
                var res = dev.kushcraft.strains.Breeding.cross(aurora, blood, r, plugin.strains().all());
                if (res.rarity() == dev.kushcraft.strains.Rarity.EXOTIC) {
                    exotic++;
                    check(res.look().exotic().exoticTier(), "an Exotic child has an Exotic look");
                }
                if (res.discovered() == horizon) {
                    found++;
                }
                var one = dev.kushcraft.strains.Breeding.cross(aurora, a, r, plugin.strains().all());
                fromOne += one.look().exotic().exoticTier() || one.discovered() != null ? 1 : 0;
                var fromExotic = dev.kushcraft.strains.Breeding.cross(horizon, a, r, plugin.strains().all());
                fromOne += fromExotic.look().exotic().exoticTier() ? 1 : 0;
            }
            check(fromOne == 0, "one Mythic (or Exotic) parent never gives an Exotic child");
            check(exotic > 1200 && exotic < 2400, "two Mythic parents: Exotic now and then (" + exotic + " in 20,000)");
            check(found > 800 && found < 1700, "Aurora Kush x Blood Moon finds Event Horizon (" + found + ")");
            check(horizon.rarity() == dev.kushcraft.strains.Rarity.EXOTIC && !horizon.inShop()
                    && horizon.wildBiomes().isEmpty() && horizon.wildWeight() == 0, "Exotic strains: never sold or wild");
            check(dev.kushcraft.strains.Breeding.exoticChance(aurora, a) == 0
                    && dev.kushcraft.strains.Breeding.exoticChance(aurora, blood) > 0, "Exotic odds need two Mythics");
        } else {
            check(false, "Aurora Kush, Blood Moon and Event Horizon exist");
        }
        // two-tone strains: patterns are passed on and mutate in
        int patterns = 0;
        Strain tiger = plugin.strains().get("tiger_kush");
        for (int i = 0; i < 2000; i++) {
            var res = dev.kushcraft.strains.Breeding.cross(a, tiger != null ? tiger : b, r);
            patterns += res.look().pattern() != dev.kushcraft.strains.BudPattern.NONE ? 1 : 0;
        }
        check(patterns > 300 && patterns < 1800, "two-tone patterns are passed on (" + patterns + " in 2,000)");
        check(seen.size() > 3, "breeding is random (" + seen.size() + " different results)");
        check(dev.kushcraft.strains.Rarity.of(35, 4) == dev.kushcraft.strains.Rarity.LEGENDARY
                && dev.kushcraft.strains.Rarity.of(5, 1) == dev.kushcraft.strains.Rarity.COMMON, "rarity tiers");
        check(dev.kushcraft.strains.Rarity.of(5, 1, dev.kushcraft.strains.Exotic.NEON) == dev.kushcraft.strains.Rarity.MYTHIC,
                "exotic = Mythic");
        check(dev.kushcraft.strains.Rarity.of(5, 1, dev.kushcraft.strains.Exotic.VOID) == dev.kushcraft.strains.Rarity.EXOTIC,
                "an Exotic look = Exotic");
        check(dev.kushcraft.strains.Rarity.EXOTIC.priceFactor() >= 1.5 * dev.kushcraft.strains.Rarity.MYTHIC.priceFactor()
                && dev.kushcraft.strains.Rarity.MYTHIC.priceFactor() >= 2 * dev.kushcraft.strains.Rarity.LEGENDARY.priceFactor(),
                "Mythic sells for much more, Exotic for even more");
        check(dev.kushcraft.effects.EffectType.values().length >= 34, "34 effects");
        check(!plugin.effects().greenThumbNear(Bukkit.getWorlds().get(0).getSpawnLocation()), "no Green Thumb nearby");
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
        check(!RecipeBook.making(dev.kushcraft.items.ItemType.COCAINE).isEmpty(), "catalog -> recipe link");
    }

    private void exchange() {
        var ex = plugin.exchange();
        check(ex.enabled(), "exchange enabled with items");
        check(!ex.categories().isEmpty() && ex.categories().size() <= 18, "1-18 trade shelves, got " + ex.categories().size());
        for (var c : ex.categories()) {
            check(!c.offers().isEmpty() && c.offers().size() <= 27, "shelf fits 3 rows: " + c.id());
        }
        check(ex.categories().size() >= 10, "10+ trade shelves, got " + ex.categories().size());
        check(ex.offers().size() >= 200, "200+ items to buy, got " + ex.offers().size());
        // no OP PvP gear and nothing from the End
        for (String banned : List.of("NETHERITE_INGOT", "NETHERITE_SCRAP", "TOTEM_OF_UNDYING", "GOLDEN_APPLE",
                "ENCHANTED_GOLDEN_APPLE", "ENDER_PEARL", "ELYTRA", "END_CRYSTAL", "NETHER_STAR", "SHULKER_SHELL",
                "DRAGON_BREATH", "DRAGON_HEAD", "END_STONE", "PURPUR_BLOCK", "CHORUS_FRUIT", "ENDER_EYE", "END_ROD",
                "DIAMOND_SWORD", "MACE", "BREEZE_ROD", "WIND_CHARGE", "RESPAWN_ANCHOR", "CRYING_OBSIDIAN")) {
            Material m = Material.matchMaterial(banned);
            check(m == null || ex.offer(m) == null, "Trade doesn't sell " + banned);
        }
        // the Lab Ingredients shelf has everything the recipes need (water: fill a bottle)
        var lab = ex.categories().stream().filter(c -> c.id().equals("lab")).findFirst().orElse(null);
        check(lab != null, "a Lab Ingredients shelf");
        for (LabRecipe r : LabRecipe.values()) {
            for (LabRecipe.Ingredient ing : r.ingredients()) {
                if (lab != null && ing.vanilla() != null && ing.vanilla() != Material.POTION) {
                    check(lab.offers().stream().anyMatch(o -> o.material() == ing.vanilla()),
                            "Lab Ingredients sells " + ing.name());
                }
            }
        }
        var ores = ex.categories().stream().filter(c -> c.id().equals("ores")).findFirst().orElse(null);
        check(ores != null && lab != null && ores.offers().stream().anyMatch(o -> lab.offers().contains(o)),
                "one item can be on two shelves (same offer)");
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
            ex.bought(Material.DIAMOND, 5000);
            check(ex.multiplier(Material.DIAMOND) <= plugin.getConfig().getDouble("exchange.max-price", 1.25) + 1e-9,
                    "price stays under max-price");
            for (int i = 0; i < 30; i++) {
                ex.tick();
            }
            check(Math.abs(ex.multiplier(Material.DIAMOND) - 1) < 1e-9, "price recovers to normal");
        }
    }

    private void awards() {
        var aw = plugin.awards();
        dev.kushcraft.awards.Award[] all = dev.kushcraft.awards.Award.values();
        check(all.length >= 45 && all.length <= 90, "45-90 awards (two menu pages), got " + all.length);
        for (var a : all) {
            check(a.parent() == null || a.parent().ordinal() < a.ordinal(), "award parent comes first: " + a);
            var adv = Bukkit.getAdvancement(dev.kushcraft.awards.Awards.key(a.id()));
            check(adv != null, "advancement loaded: kush:" + a.id());
            if (adv != null && adv.getDisplay() != null) {
                check(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(adv.getDisplay().title()).equals(a.title()), "advancement title " + a);
            }
        }
        check(aw.advancementsLoaded() == all.length, "KushCraft advancement tab loaded ("
                + aw.advancementsLoaded() + "/" + all.length + ")");
        check(Bukkit.getAdvancement(dev.kushcraft.awards.Awards.key("root")) != null, "advancement tab root");
        var p = Bukkit.getOfflinePlayer(UUID.randomUUID());
        check(!aw.has(p, dev.kushcraft.awards.Award.FIRST_SEED) && aw.count(p) == 0, "new players have no awards");
        check("0/100".equals(aw.progress(p, dev.kushcraft.awards.Award.HARVEST_100)), "award progress");
        check(dev.kushcraft.awards.Awards.drugs().size() >= 20, "drugs to try");
        check(dev.kushcraft.awards.Starter.next(p) == dev.kushcraft.awards.Starter.PLANT
                && dev.kushcraft.awards.Starter.doneCount(p) == 0, "new players start at step 1");
        check(dev.kushcraft.awards.Starter.values().length == 7, "7 getting-started steps");
        check(dev.kushcraft.awards.Starter.values()[6] == dev.kushcraft.awards.Starter.FORAGE, "the last step: a wild plant");
        check(!plugin.getConfig().getMapList("new-players.starter-kit").isEmpty(), "a starter kit is set up");
    }

    /** Every page fits its layout (tools/gui.py draws the matching backgrounds). */
    private void menus() {
        for (var t : dev.kushcraft.menus.TabMenu.Tab.values()) {
            check(GuiFont.GUIS.contains(t.name().toLowerCase(java.util.Locale.ROOT)), "background for tab " + t);
        }
        for (var t : dev.kushcraft.menus.LabTabMenu.Tab.values()) {
            check(GuiFont.GUIS.contains(t.name().toLowerCase(java.util.Locale.ROOT)), "background for lab tab " + t);
        }
        for (var c : dev.kushcraft.catalog.Catalog.Category.values()) {
            if (c != dev.kushcraft.catalog.Catalog.Category.GROW) {
                check(dev.kushcraft.catalog.Catalog.entries(c).size() <= 9, "one row of drugs: " + c);
            }
        }
        check(GuiFont.GUIS.contains("top"), "background for Top Dealers");
        for (String g : List.of("gear", "worker", "guide", "admin")) {
            check(GuiFont.GUIS.contains(g), "background for " + g);
        }
        check(LabRecipe.values().length <= 27, "cook page fits 3 rows");
        check(dev.kushcraft.menus.TabMenu.Tab.values().length == 5, "5 main panels");
    }

    private void strains() {
        var all = new ArrayList<>(plugin.strains().all());
        check(all.size() >= 100, "100+ strains, got " + all.size());
        java.util.Set<String> names = new java.util.HashSet<>(), looks = new java.util.HashSet<>();
        java.util.Set<dev.kushcraft.strains.Climate> climates = java.util.EnumSet.noneOf(dev.kushcraft.strains.Climate.class);
        java.util.Set<dev.kushcraft.strains.BudShape> shapes = java.util.EnumSet.noneOf(dev.kushcraft.strains.BudShape.class);
        int mythic = 0, exotic = 0, patterned = 0;
        for (Strain s : all) {
            if (s.isCustom()) {
                continue;
            }
            check(names.add(s.name().toLowerCase(java.util.Locale.ROOT)), "unique strain name " + s.name());
            check(looks.add(s.look().bud() + "/" + s.look().leaf() + "/" + s.look().pistil() + "/" + s.look().shape()),
                    "unique look " + s.name());
            if (s.look().pattern() != dev.kushcraft.strains.BudPattern.NONE) {
                patterned++;
            }
            for (String p : s.parents()) {
                check(plugin.strains().get(p) != null && plugin.strains().get(p).exotic() != dev.kushcraft.strains.Exotic.NONE
                        && !plugin.strains().get(p).exotic().exoticTier(), "an Exotic strain's parents are Mythic: " + s.name());
            }
            check(!s.flavor().isEmpty(), "flavour for " + s.name());
            check(!s.effects().isEmpty() && s.effects().size() <= 4, "1-4 effects " + s.name());
            climates.add(s.climate());
            shapes.add(s.look().shape());
            if (s.exotic() != dev.kushcraft.strains.Exotic.NONE) {
                check(s.wildWeight() < 0.1, "Mythic strains are very rare in the wild: " + s.name());
                check(s.inShop() != s.exotic().exoticTier(), "Mythic seeds are sold, Exotic ones never: " + s.name());
                check(s.rarity() == s.exotic().rarity(), "Mythic / Exotic rarity " + s.name());
                if (s.exotic().exoticTier()) {
                    exotic++;
                    check(s.parents().size() == 2 && s.wildWeight() == 0, "an Exotic strain comes from two parents: "
                            + s.name());
                } else {
                    mythic++;
                }
            }
        }
        check(mythic >= 12 && exotic >= 8, "plenty of Mythic (" + mythic + ") and Exotic (" + exotic + ") strains");
        check(patterned >= 40, "plenty of two-tone strains (" + patterned + ")");
        java.util.Set<dev.kushcraft.strains.Exotic> usedLooks = java.util.EnumSet.noneOf(dev.kushcraft.strains.Exotic.class);
        all.forEach(s -> usedLooks.add(s.exotic()));
        check(usedLooks.size() >= 18, "most animated looks are used by a strain (" + usedLooks.size() + ")");
        check(climates.size() == dev.kushcraft.strains.Climate.values().length, "a strain for every climate");
        check(shapes.size() == dev.kushcraft.strains.BudShape.values().length, "every bud shape is used");
        check(mythic >= 3, "Mythic strains exist");
        // legacy strains (no look in strains.yml) still get one
        var legacy = dev.kushcraft.strains.Look.legacy(0xFF0000, dev.kushcraft.strains.StrainType.INDICA, "x");
        check(legacy.leaf() != 0 && legacy.pistil() == dev.kushcraft.strains.Look.DEFAULT_PISTIL, "legacy look");
        // the Mythic ones turn up in their biomes, but rarely
        Strain aurora = plugin.strains().get("aurora_kush");
        check(aurora != null && aurora.exotic() == dev.kushcraft.strains.Exotic.GALAXY, "Aurora Kush is a galaxy strain");
        check(Strain.class.getSimpleName().equals("Strain"), "strain class");
    }

    private void climates() {
        var C = dev.kushcraft.strains.Climate.class;
        check(dev.kushcraft.strains.Climate.of("jungle", 0.95, 0.9, 70) == dev.kushcraft.strains.Climate.TROPICAL, "jungle is tropical");
        check(dev.kushcraft.strains.Climate.of("desert", 2.0, 0, 70) == dev.kushcraft.strains.Climate.DESERT, "desert");
        check(dev.kushcraft.strains.Climate.of("savanna", 2.0, 0, 70) == dev.kushcraft.strains.Climate.DESERT, "savanna");
        check(dev.kushcraft.strains.Climate.of("plains", 0.8, 0.4, 70) == dev.kushcraft.strains.Climate.TEMPERATE, "plains");
        check(dev.kushcraft.strains.Climate.of("swamp", 0.8, 0.9, 63) == dev.kushcraft.strains.Climate.WETLAND, "swamp");
        check(dev.kushcraft.strains.Climate.of("river", 0.5, 0.5, 62) == dev.kushcraft.strains.Climate.WETLAND, "river");
        check(dev.kushcraft.strains.Climate.of("snowy_plains", 0.0, 0.5, 70) == dev.kushcraft.strains.Climate.COLD, "snow");
        check(dev.kushcraft.strains.Climate.of("taiga", 0.25, 0.8, 70) == dev.kushcraft.strains.Climate.COLD, "taiga");
        check(dev.kushcraft.strains.Climate.of("meadow", 0.5, 0.8, 120) == dev.kushcraft.strains.Climate.MOUNTAIN, "meadow");
        check(dev.kushcraft.strains.Climate.of("plains", 0.8, 0.4, 130) == dev.kushcraft.strains.Climate.MOUNTAIN, "high up");
        var tropical = dev.kushcraft.strains.Climate.TROPICAL;
        check(tropical.fit(tropical) == dev.kushcraft.strains.Climate.Fit.IDEAL, "ideal climate");
        check(tropical.fit(dev.kushcraft.strains.Climate.COLD) == dev.kushcraft.strains.Climate.Fit.HARSH, "opposite climate");
        check(tropical.fit(dev.kushcraft.strains.Climate.TEMPERATE) == dev.kushcraft.strains.Climate.Fit.OK, "ok climate");
        for (var a : C.getEnumConstants()) {
            for (var b : C.getEnumConstants()) {
                check(a.harsh(b) == b.harsh(a), "climates are symmetric");
            }
            check(a.fit(dev.kushcraft.strains.Climate.TEMPERATE) != dev.kushcraft.strains.Climate.Fit.HARSH,
                    "everything grows in temperate: " + a);
        }
        check(dev.kushcraft.strains.Climate.Fit.IDEAL.growth() > 1 && dev.kushcraft.strains.Climate.Fit.HARSH.growth() < 1,
                "climate changes growth");
        check(dev.kushcraft.strains.Climate.parse("WARM", null) == tropical, "2.x climate names");
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
        check(cs.sellBonus(member) > 0, "levels give every member better prices");
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

    /** Fake players made by the test (their rows are removed at the end). */
    private final List<UUID> fake = new ArrayList<>();

    private String dbString(String sql, String arg) {
        return plugin.db().call(c -> {
            try (var ps = c.prepareStatement(sql)) {
                ps.setString(1, arg);
                try (var rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
    }

    private void money() {
        var eco = plugin.economy();
        UUID ia = UUID.randomUUID(), ib = UUID.randomUUID();
        fake.add(ia);
        fake.add(ib);
        var a = Bukkit.getOfflinePlayer(ia);
        var b = Bukkit.getOfflinePlayer(ib);
        eco.set(a, 100, "selftest");
        eco.set(b, 0, "selftest");
        check(eco.withdraw(a, 40, dev.kushcraft.economy.Tx.BUY, "test") && Math.abs(eco.balance(a) - 60) < 1e-9, "withdraw");
        eco.deposit(b, 40, dev.kushcraft.economy.Tx.SELL, "test");
        check(Math.abs(eco.balance(b) - 40) < 1e-9, "deposit");
        check(!eco.withdraw(a, 1000, dev.kushcraft.economy.Tx.BUY, "test") && Math.abs(eco.balance(a) - 60) < 1e-9,
                "can't spend more than you have (and nothing changes)");
        check(!eco.withdraw(a, Double.NaN, dev.kushcraft.economy.Tx.BUY, "test")
                && !eco.withdraw(a, -5, dev.kushcraft.economy.Tx.BUY, "test")
                && !eco.withdraw(a, Double.POSITIVE_INFINITY, dev.kushcraft.economy.Tx.BUY, "test")
                && Math.abs(eco.balance(a) - 60) < 1e-9, "NaN, negative and infinite amounts are refused");
        for (int i = 0; i < 10; i++) {
            eco.deposit(b, 0.1, dev.kushcraft.economy.Tx.SELL, "test");
        }
        check(eco.account(ib).balance() == 4100, "money is whole cents: ten dimes are exactly one dollar");
        // the transaction log and the balance go out in the same snapshot
        check(plugin.persistence().flushNow(), "the snapshot is written");
        check("6000".equals(dbString("SELECT balance FROM players WHERE uuid=?", ia.toString())),
                "the balance is in the database");
        String last = dbString("SELECT balance FROM ledger WHERE player=? ORDER BY id DESC LIMIT 1", ib.toString());
        check("4100".equals(last), "the log's last balance matches the wallet (" + last + ")");
        // small, frequent payments are added up per minute in one log row
        for (int i = 0; i < 25; i++) {
            eco.frequent(ib, -1, dev.kushcraft.economy.Tx.WAGES, "selftest-worker", "wage");
        }
        check(plugin.persistence().flushNow(), "the sums are written");
        String count = dbString("SELECT SUM(count) FROM ledger WHERE player=? AND type='WAGES'", ib.toString());
        String rows = dbString("SELECT COUNT(*) FROM ledger WHERE player=? AND type='WAGES'", ib.toString());
        check("25".equals(count) && rows != null && Integer.parseInt(rows) <= 2,
                "25 wages are 1-2 log rows that count 25 (" + rows + " rows)");
        // atomic: a snapshot that fails leaves the database as it was, and is written in full later
        dev.kushcraft.storage.Persistence.Source broken = (batch, full) -> batch.write(c -> {
            throw new java.sql.SQLException("selftest: a write fails");
        });
        plugin.persistence().register(broken);
        eco.deposit(a, 5, dev.kushcraft.economy.Tx.SELL, "atomic test");
        check(!plugin.persistence().flushNow(), "the failing snapshot is reported");
        check("6000".equals(dbString("SELECT balance FROM players WHERE uuid=?", ia.toString())),
                "nothing of a failed snapshot is in the database");
        plugin.persistence().unregister(broken);
        check(plugin.persistence().flushNow() && "6500".equals(dbString("SELECT balance FROM players WHERE uuid=?",
                ia.toString())), "the retry writes it all");
        // Vault: when it's installed, KushCraft is the economy other plugins use
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            vault(a);
        }
        // a backup of the live database: checked, with a manifest
        var bk = plugin.backups().make("selftest", "selftest");
        check(bk.verified() && new File(bk.dir(), "manifest.txt").isFile() && bk.size() > 0,
                "a backup is made and checked (" + bk.problem() + ")");
        if (bk.dir() != null) {
            for (File f : java.util.Objects.requireNonNullElse(bk.dir().listFiles(), new File[0])) {
                f.delete();
            }
            bk.dir().delete();
        }
    }

    /** Vault's Economy, called the way another plugin would. */
    private void vault(org.bukkit.OfflinePlayer a) {
        try {
            Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy");
            var reg = Bukkit.getServicesManager().getRegistration(ecoClass);
            check(reg != null && reg.getPlugin() == plugin, "Vault's economy is KushCraft");
            Object v = reg.getProvider();
            double bal = (double) ecoClass.getMethod("getBalance", org.bukkit.OfflinePlayer.class).invoke(v, a);
            check(Math.abs(bal - plugin.economy().balance(a)) < 1e-9, "Vault reads the KushCraft balance");
            Object r = ecoClass.getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class).invoke(v, a, 10.0);
            check((boolean) r.getClass().getMethod("transactionSuccess").invoke(r)
                    && Math.abs(plugin.economy().balance(a) - bal - 10) < 1e-9, "Vault deposits land in the wallet");
            r = ecoClass.getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class).invoke(v, a, 1e9);
            check(!(boolean) r.getClass().getMethod("transactionSuccess").invoke(r), "Vault can't overdraw");
            plugin.getLogger().info("SELFTEST vault ok");
        } catch (ReflectiveOperationException e) {
            check(false, "vault: " + e);
        }
    }

    private void jobs(World w, int x, int y, int z) {
        var jobs = plugin.jobs();
        // money only comes from selling drugs
        check(!jobs.enabled(), "jobs pay nothing by default");
        check(dev.kushcraft.awards.Award.FIRST_SALE.reward() == 0, "awards pay no cash by default");
        for (ItemType t : List.of(ItemType.SEED_PACK, ItemType.COCA_SEEDS, ItemType.MUSHROOM_SPORES, ItemType.LAB_SOLVENT,
                ItemType.FERTILIZER)) {
            check(plugin.shop().basePrice(t) == 0, "only drugs sell, not " + t);
        }
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
