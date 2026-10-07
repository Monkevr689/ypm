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
import dev.kushcraft.util.Text;
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
            workers(w, bx + 8, y, bz);
            nature(w, bx + 18, y, bz);
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
        for (dev.kushcraft.recipe.Recipes.Info r : dev.kushcraft.recipe.Recipes.info()) {
            if (r.result() == ItemType.MORPHINE) {
                craft = java.util.Arrays.stream(r.grid()).filter(java.util.Objects::nonNull)
                        .allMatch("kush:poppy_seeds"::equals);
            }
        }
        check(craft, "morphine base crafts straight from poppy seeds");
        var morphineKey = new NamespacedKey(plugin, ItemType.MORPHINE.id());
        check(dev.kushcraft.recipe.Recipes.usesCustomItems(morphineKey) && Bukkit.getRecipe(morphineKey) != null,
                "the morphine base recipe is registered and allowed past the custom-item guard");
        // every drug has its own signature, all different
        java.util.Set<dev.kushcraft.effect.EffectType> sigs = java.util.EnumSet.noneOf(dev.kushcraft.effect.EffectType.class);
        for (ItemType t : dev.kushcraft.award.Awards.drugs()) {
            var sig = dev.kushcraft.catalog.Catalog.signature(t);
            check(sig != null && sig.signature() && sigs.add(sig), "a signature of its own: " + t);
            if (!t.strainBound()) {
                check(dev.kushcraft.catalog.Catalog.dose(t).effects().containsKey(sig), "the dose has the signature: " + t);
            }
            check(Items.create(t).getItemMeta().lore().stream().anyMatch(c -> Text.plain(c).contains(sig.display())),
                    "the item shows its signature: " + t);
        }
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
                check(plugin.shop().basePrice(t) > 0 && !dev.kushcraft.award.Awards.drugs().contains(t),
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
                    && t.machine() == null && dev.kushcraft.worker.WorkerType.of(t) == null) {
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
        check(plugin.shop().seeds().size() > 36 && plugin.shop().gear().size() <= 36
                && plugin.shop().hires().size() == dev.kushcraft.worker.WorkerType.values().length,
                "shop pages: more than one page of seeds, gear fits, every worker for hire");
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
            check(e.price() <= 800, "gear is affordable: " + e.type() + " " + e.price());
        }
        for (var e : plugin.shop().hires()) {
            check(e.price() >= 8000, "workers are expensive: " + e.type() + " " + e.price());
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
        check(one > 0.99 && pile < 0.95 && pile >= plugin.getConfig().getDouble("market.min-price", 0.7) - 1e-9,
                "dumping 200 of one thing pays a bit less (" + pile + ")");
        check(pile >= 0.7 && plugin.getConfig().getDouble("market.min-price", 0) >= 0.7,
                "mass production never drops a drug below 70% (" + pile + ")");
        check(plugin.getConfig().getDouble("market.recovery-per-minute", 0) >= 0.05, "prices recover fast");
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
                plugin.machines().save();
                var saved = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new File(plugin.getDataFolder(), "machines.yml"));
                var racks = saved.getConfigurationSection("machines." + BlockKey.of(b).serialize() + ".racks");
                check(racks != null && racks.getKeys(false).size() == 5 && racks.getInt("3.amount") == 13,
                        "racks are saved");
                m.startJob(LabRecipe.SPEED.name(), 1, Items.create(ItemType.LUCID_TAB, 2));
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
            plugin.plants().save();
            var saved = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new File(plugin.getDataFolder(), "plants.yml"));
            check(saved.getLong("plants." + wild.key().serialize() + ".wild-until") > System.currentTimeMillis(),
                    "wild plants are saved with the time they wither");
            plugin.plants().remove(wild);
        }
        check(dev.kushcraft.plant.WildPlants.kindFor("desert", Material.SAND) == Plant.Kind.PEYOTE,
                "peyote grows wild in deserts");
        check(dev.kushcraft.plant.WildPlants.kindFor("mushroom_fields", Material.MYCELIUM) == Plant.Kind.MUSHROOM,
                "mushrooms grow wild on mycelium");
        check(dev.kushcraft.plant.WildPlants.kindFor("deep_ocean", Material.GRASS_BLOCK) == null,
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
        check(Math.abs(plugin.getConfig().getDouble("death.cash-lost") - 0.2) < 1e-9, "dying costs 20% of your cash");
        check(dev.kushcraft.listener.PlayerListener.cashLost(1234.56, 0.2) == 246.91
                && dev.kushcraft.listener.PlayerListener.cashLost(0, 0.2) == 0, "the death loss is worked out right");
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
        eco.set(owner, 1000);
        check(ws.enabled(), "workers are on");
        // a ripe plant on farmland, an empty farmland next to it, the farmhand standing beside them
        for (int dx = -1; dx <= 7; dx++) {
            for (int dz = -1; dz <= 5; dz++) {
                w.getBlockAt(x + dx, y, z + dz).setType(Material.STONE);
                for (int dy = 1; dy <= 4; dy++) {
                    w.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR);
                }
            }
        }
        w.getBlockAt(x, y, z).setType(Material.FARMLAND);
        w.getBlockAt(x, y, z + 2).setType(Material.FARMLAND);
        ws.refreshAreas();
        Strain s = plugin.strains().get("og_kush");
        if (s == null) {
            s = plugin.strains().all().iterator().next();
        }
        BlockKey key = BlockKey.of(w.getBlockAt(x, y + 1, z));
        Plant ripe = plugin.plants().plantAt(key, Plant.Kind.CANNABIS, s, boss);
        ripe.growth(100);
        var farm = ws.hireAt(new Location(w, x + 2.5, y + 1, z + 0.5), dev.kushcraft.worker.WorkerType.FARMHAND, boss, 1);
        check(ws.of(boss).contains(farm), "farmhand hired");
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
        check(count(farm.satchel(), ItemType.SEED_PACK) == 0, "harvested seeds go in the seed backpack, not the satchel");
        check(eco.balance(owner) < 1000 && farm.jobs() == 1, "the farmhand was paid");
        // seeds go in the backpack, and from there on the empty farmland right away
        ws.stash(farm, List.of(Items.create(ItemType.COCA_SEEDS, 2)));
        check(count(farm.seedBag(), ItemType.COCA_SEEDS) == 2 && count(farm.satchel(), ItemType.COCA_SEEDS) == 0,
                "seeds you give a farmhand go in their backpack");
        BlockKey empty = BlockKey.of(w.getBlockAt(x, y + 1, z + 2));
        check(ws.workNow(farm) && plugin.plants().at(empty) != null, "the farmhand plants seeds from the backpack first");
        // drying racks: topping one up doesn't start its timer again (racks topped up all the time never dried)
        Block testLab = w.getBlockAt(x + 7, y + 1, z + 5);
        Machine racks = plugin.machines().placeAt(testLab, MachineType.LAB_STATION, 0f, boss);
        if (racks != null) {
            racks.hang(s.id(), 3, 4, 1_000L, 31_000L);
            racks.hang(s.id(), 3, 4, 11_000L, 41_000L);
            Machine.Rack rack = racks.rack(0);
            check(rack != null && rack.amount() == 8 && rack.done() == 36_000L && rack.start() == 1_000L,
                    "topping up a rack only moves its finish time a little (" + (rack == null ? "-" : rack.done()) + ")");
            plugin.machines().breakMachine(racks, null);
        }
        // the dryer next to a Drug Lab fetches the buds, hangs them and collects them dry
        Block labBlock = w.getBlockAt(x + 5, y + 1, z);
        Machine lab = plugin.machines().placeAt(labBlock, MachineType.LAB_STATION, 0f, boss);
        ws.refreshAreas();
        var dryer = ws.hireAt(new Location(w, x + 5.5, y + 1, z + 2.5), dev.kushcraft.worker.WorkerType.DRYER, boss, 1);
        check(ws.workNow(dryer), "the dryer fetches fresh buds from the farmhand");
        check(dryer.carried() > 0 && count(farm.satchel(), ItemType.BUD_FRESH) == 0, "the dryer carries the buds");
        check(ws.workNow(dryer) && lab.racksInUse() > 0, "the dryer hangs them on the racks");
        lab.finishNow();
        check(lab.racksDry() > 0, "admin finish dries the racks");
        check(ws.workNow(dryer) && lab.racksInUse() == 0, "the dryer takes them off dry");
        check(count(dryer.satchel(), ItemType.BUD_DRIED) > 0, "dried buds in the dryer's satchel");
        // the cook rolls joints from the dryer's buds; what they're missing they say
        var cook = ws.hireAt(new Location(w, x + 4.5, y + 1, z + 2.5), dev.kushcraft.worker.WorkerType.COOK, boss, 1);
        check(!ws.workNow(cook) && cook.status().contains("Pick"), "a new cook waits for a drug to make");
        ws.setJob(cook, dev.kushcraft.worker.Worker.ROLL_JOINT);
        check(cook.rolls() == ItemType.JOINT && cook.product() == ItemType.JOINT, "the cook rolls joints");
        ws.stash(dryer, List.of(Items.strainItem(ItemType.BUD_DRIED, s, 3, 4)));
        check(ws.workNow(cook) && count(cook.satchel(), ItemType.BUD_DRIED) >= 4
                && count(dryer.satchel(), ItemType.BUD_DRIED) == 0, "the cook takes dried buds from the dryer (work chain)");
        check(!ws.workNow(cook) && cook.status().contains("Rolling Papers") && "Rolling Papers".equals(cook.wants()),
                "the cook says what's missing");
        ws.stash(cook, List.of(Items.create(ItemType.ROLLING_PAPERS, 8)));
        check(ws.workNow(cook) && cook.jobs() == 1, "the cook rolls");
        int joints = count(cook.satchel(), ItemType.JOINT);
        check(joints > 0 && joints + count(cook.satchel(), ItemType.ROLLING_PAPERS) == 8,
                "the cook rolls joints, one paper each (" + joints + ")");
        // the runner picks up the joints and sells them on the spot; the owner gets the money, minus their cut
        var runner = ws.hireAt(new Location(w, x + 0.5, y + 1, z + 4.5), dev.kushcraft.worker.WorkerType.RUNNER, boss, 1);
        check(ws.crew(runner).size() == 3 && ws.crew(cook).contains(runner),
                "the runner works with the farmhand, dryer and cook");
        double before = eco.balance(owner);
        check(ws.workNow(runner) && count(cook.satchel(), ItemType.JOINT) == 0 && runner.carried() == 0
                && eco.balance(owner) > before && runner.wages() > 0, "the runner picks up the joints and sells them right away");
        check(eco.sales(owner) > 0, "runner sales count for the leaderboard");
        // no chests: the runner takes the farmhand's harvest straight to the dryer
        int dryerFresh = count(dryer.satchel(), ItemType.BUD_FRESH);
        ws.stash(farm, List.of(Items.strainItem(ItemType.BUD_FRESH, s, 3, 5)));
        check(ws.workNow(runner) && count(farm.satchel(), ItemType.BUD_FRESH) == 0
                && count(dryer.satchel(), ItemType.BUD_FRESH) == dryerFresh + 5 && runner.carried() == 0,
                "the runner takes the farmhand's harvest to the dryer");
        // a couple of buds wait for more (less running around), four make a trip
        ws.stash(farm, List.of(Items.strainItem(ItemType.BUD_FRESH, s, 3, 2)));
        check(!ws.workNow(runner) && count(farm.satchel(), ItemType.BUD_FRESH) == 2, "two buds wait for a few more");
        ws.stash(farm, List.of(Items.strainItem(ItemType.BUD_FRESH, s, 3, 2)));
        check(ws.workNow(runner) && count(farm.satchel(), ItemType.BUD_FRESH) == 0, "four buds make the trip");
        // nobody gets more than they have room for: the dryer takes up to 192 fresh buds, the runner sells the rest
        List<ItemStack> heap = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            heap.add(Items.strainItem(ItemType.BUD_FRESH, s, 2, 64));
        }
        ws.stash(runner, heap);
        double cashBefore = eco.balance(owner);
        for (int i = 0; i < 3 && count(runner.satchel(), ItemType.BUD_FRESH) > 0; i++) {
            ws.workNow(runner);
        }
        check(count(dryer.satchel(), ItemType.BUD_FRESH) <= 192 && dryer.freeSlots() >= 6,
                "the dryer never gets so many buds there's no room for the dried ones ("
                        + count(dryer.satchel(), ItemType.BUD_FRESH) + ")");
        check(count(runner.satchel(), ItemType.BUD_FRESH) == 0 && eco.balance(owner) > cashBefore,
                "the runner sells what the dryer has no room for");
        dryer.satchel().clear();
        ws.stash(dryer, List.of(Items.strainItem(ItemType.BUD_FRESH, s, 3, dryerFresh + 9)));
        // dryer -> cook: the runner brings the dried buds to the cook who rolls them
        int cookBuds = count(cook.satchel(), ItemType.BUD_DRIED);
        ws.stash(dryer, List.of(Items.strainItem(ItemType.BUD_DRIED, s, 3, 4)));
        check(ws.workNow(runner) && count(dryer.satchel(), ItemType.BUD_DRIED) == 0
                && count(cook.satchel(), ItemType.BUD_DRIED) == cookBuds + 4, "the runner takes the dryer's buds to the cook");
        // seeds never get sold
        ws.stash(runner, List.of(Items.strainItem(ItemType.SEED_PACK, s, 3, 3)));
        before = eco.balance(owner);
        ws.workNow(runner);
        check(eco.balance(owner) == before, "the runner never sells seeds");
        check(count(runner.satchel(), ItemType.SEED_PACK) == 0 && count(farm.seedBag(), ItemType.SEED_PACK) >= 3,
                "seeds go in the farmhand's backpack");
        runner.satchel().clear();
        // the cook cooks a vape pen at the lab and collects it; the runner sells it from their satchel
        ws.setRecipe(cook, LabRecipe.VAPE_PEN);
        ws.stash(cook, List.of(Items.strainItem(ItemType.BUD_DRIED, s, 3, 4), Items.create(ItemType.LAB_SOLVENT, 1),
                new ItemStack(Material.IRON_NUGGET, 2), new ItemStack(Material.GLASS_PANE, 1)));
        check(ws.workNow(cook) && lab.busy() && "VAPE_PEN".equals(lab.job()), "the cook starts a batch at the lab");
        check(cook.jobs() == 2, "the cook is paid per batch");
        lab.finishNow();
        check(ws.workNow(cook) && !lab.busy() && count(cook.satchel(), ItemType.VAPE_PEN) == 1, "the cook collects the batch");
        before = eco.balance(owner);
        check(ws.workNow(runner) && count(cook.satchel(), ItemType.VAPE_PEN) == 0 && eco.balance(owner) > before,
                "the runner sells the cook's vape pen");
        // dried buds nobody uses: the runner sells them; a Dryer set to sell gives them all, Cook or not
        ws.setRecipe(cook, LabRecipe.CRACK);
        cook.satchel().clear();
        dryer.satchel().clear();
        ws.stash(dryer, List.of(Items.strainItem(ItemType.BUD_DRIED, s, 3, 4)));
        before = eco.balance(owner);
        check(ws.workNow(runner) && count(dryer.satchel(), ItemType.BUD_DRIED) == 0 && eco.balance(owner) > before,
                "the runner sells the dryer's buds when no cook uses them");
        ws.setJob(cook, dev.kushcraft.worker.Worker.ROLL_JOINT);
        ws.stash(dryer, List.of(Items.strainItem(ItemType.BUD_DRIED, s, 3, 4)));
        check(ws.uses(cook, Items.strainItem(ItemType.BUD_DRIED, s, 3, 1)), "the cook uses dried buds");
        ws.setSell(dryer, true);
        before = eco.balance(owner);
        check(ws.workNow(runner) && count(dryer.satchel(), ItemType.BUD_DRIED) == 0 && count(cook.satchel(),
                ItemType.BUD_DRIED) == 0 && eco.balance(owner) > before, "a Dryer set to sell gives the Runner every dried bud");
        ws.setSell(dryer, false);
        runner.satchel().clear();
        ws.setRecipe(cook, LabRecipe.CRACK);
        // workers keep their chunks loaded so they work while you're away
        ws.syncChunks();
        check(ws.workOffline() && ws.keptChunks() > 0, "workers keep the chunks around them loaded");
        // no working through walls: a cook walled into a stone box can't be reached on foot - a Runner gets there
        for (int dx = 5; dx <= 7; dx++) {
            for (int dy = 1; dy <= 4; dy++) {
                for (int dz = 3; dz <= 5; dz++) {
                    w.getBlockAt(x + dx, y + dy, z + dz).setType(dx == 6 && dz == 4 && (dy == 2 || dy == 3)
                            ? Material.AIR : Material.STONE);
                }
            }
        }
        ws.refreshAreas();
        BlockKey inside = BlockKey.of(w.getBlockAt(x + 6, y + 2, z + 4));
        check(!ws.reaches(cook, inside, false) && ws.reaches(cook, BlockKey.of(w.getBlockAt(x + 1, y + 1, z + 4)), false),
                "walk areas: nobody walks through walls");
        var hidden = ws.hireAt(new Location(w, x + 6.5, y + 2, z + 4.5), dev.kushcraft.worker.WorkerType.COOK, boss, 1);
        ws.setRecipe(hidden, LabRecipe.COCAINE);
        ws.stash(hidden, List.of(Items.create(ItemType.COCAINE, 3)));
        check(!ws.workNow(cook) && "Cocaine".equals(cook.wants()), "the cook says it needs cocaine");
        check(ws.workNow(runner) && count(cook.satchel(), ItemType.COCAINE) == 3 && count(hidden.satchel(), ItemType.COCAINE) == 0,
                "the runner brings it from the cook behind the walls");
        ws.dismiss(hidden, null);
        // the supplier buys what the cook and farmhand are short of, with the owner's money - no budget
        eco.set(owner, 50_000);
        var supplier = ws.hireAt(new Location(w, x + 1.5, y + 1, z + 1.5), dev.kushcraft.worker.WorkerType.SUPPLIER, boss, 1);
        check(!ws.needs(cook).isEmpty(), "the cook needs bone meal and water");
        double cash = eco.balance(owner);
        for (int i = 0; i < 4; i++) {
            ws.workNow(supplier);
        }
        int bone = 0;
        for (ItemStack it : cook.satchel().getStorageContents()) {
            if (it != null && it.getType() == Material.BONE_MEAL && !Items.isCustom(it)) {
                bone += it.getAmount();
            }
        }
        check(bone >= 2 && count(farm.satchel(), ItemType.FERTILIZER) >= 4, "the supplier stocks the cook and the farmhand ("
                + bone + " bone meal)");
        check(eco.balance(owner) < cash && supplier.spent() > 0 && supplier.jobs() > 0, "the supplier pays with your money");
        check(ws.needs(cook).isEmpty() && !ws.workNow(supplier), "nothing more to buy once everyone is stocked");
        cook.satchel().clear();
        eco.set(owner, 0);
        check(!ws.workNow(supplier) && supplier.status().contains("Not enough money"), "the supplier only stops when you're broke");
        eco.set(owner, 50_000);
        // a cook on AUTO picks the best drug they have everything for
        ws.setJob(cook, dev.kushcraft.worker.Worker.AUTO);
        ItemStack waterBottle = new ItemStack(Material.POTION);
        waterBottle.editMeta(org.bukkit.inventory.meta.PotionMeta.class,
                m -> m.setBasePotionType(org.bukkit.potion.PotionType.WATER));
        ws.stash(cook, List.of(Items.create(ItemType.COCAINE, 2), waterBottle, new ItemStack(Material.BONE_MEAL, 2),
                Items.create(ItemType.COCA_LEAVES, 6), Items.create(ItemType.LAB_SOLVENT, 1)));
        check(ws.workNow(cook) && "CRACK".equals(lab.job()) && cook.recipe() == LabRecipe.CRACK,
                "an Auto cook picks crack (worth the most) and cooks it");
        lab.finishNow();
        check(ws.workNow(cook) && count(cook.satchel(), ItemType.CRACK) >= 2, "and collects it");
        // a cook mixing strains: the supplier buys seeds, the cook crosses the two best strains at the lab
        ws.setJob(cook, dev.kushcraft.worker.Worker.MIX);
        cook.satchel().clear();
        check(ws.uses(cook, Items.strainItem(ItemType.SEED_PACK, s, 3, 1)) && !ws.needs(cook).isEmpty(),
                "a mixing cook wants seeds of two strains (the supplier buys them)");
        Strain s2 = null;
        for (Strain o : plugin.strains().all()) {
            if (!o.id().equals(s.id()) && o.creator() == null) {
                s2 = o;
                break;
            }
        }
        ws.stash(cook, List.of(Items.strainItem(ItemType.SEED_PACK, s, 3, 2), Items.strainItem(ItemType.SEED_PACK, s2, 3, 2)));
        check(ws.mixPair(cook) != null, "two strains to mix");
        int bredBefore = plugin.strains().countCreatedBy(boss);
        cash = eco.balance(owner);
        check(ws.workNow(cook) && eco.balance(owner) <= cash - plugin.getConfig().getDouble("strain-maker.cost", 1500)
                && count(cook.satchel(), ItemType.SEED_PACK) >= 2, "the cook mixes them at the lab with your money");
        check(cook.status().startsWith("Mixed") || cook.status().startsWith("Bred"), "and keeps the child if it's rare enough ("
                + dev.kushcraft.util.Text.plain(dev.kushcraft.util.Text.mm(cook.status())) + ")");
        check(plugin.strains().countCreatedBy(boss) >= bredBefore, "bred strains are the owner's");
        for (Strain made : new ArrayList<>(plugin.strains().all())) {
            if (boss.equals(made.creator())) {
                plugin.strains().remove(made.id());
            }
        }
        // a farmhand hands on spare poppy seeds past 32; a cook crafts morphine base from them
        cook.satchel().clear();
        farm.satchel().clear();
        farm.seedBag().clear();
        ws.stash(farm, List.of(Items.create(ItemType.POPPY_SEEDS, 64)));
        ws.setRecipe(cook, LabRecipe.MORPHINE);
        runner.satchel().clear();
        check(!ws.workNow(cook) && cook.wants() != null, "with a Runner around the cook stays put and waits for poppy seeds");
        for (int i = 0; i < 3; i++) {
            ws.workNow(runner);
        }
        int cookPoppy = count(cook.satchel(), ItemType.POPPY_SEEDS), farmPoppy = count(farm.seedBag(), ItemType.POPPY_SEEDS);
        check(cookPoppy > 0 && farmPoppy >= 32 && cookPoppy + farmPoppy + count(runner.satchel(), ItemType.POPPY_SEEDS) == 64,
                "the runner brings the farmhand's spare poppy seeds; the farmhand keeps 32 (" + cookPoppy + "/" + farmPoppy + ")");
        // a full backpack never stops a farmhand: common seeds past 64 a kind become fertilizer, rare ones are kept
        farm.satchel().clear();
        farm.seedBag().clear();
        Strain rare = null;
        for (Strain o : plugin.strains().all()) {
            if (o.rarity().ordinal() >= dev.kushcraft.strain.Rarity.RARE.ordinal()) {
                rare = o;
                break;
            }
        }
        List<ItemStack> seedsHeap = new ArrayList<>();
        for (int i = 0; i < 44; i++) {
            seedsHeap.add(Items.create(ItemType.COCA_SEEDS, 64));
        }
        if (rare != null) {
            seedsHeap.add(Items.strainItem(ItemType.SEED_PACK, rare, 3, 64));
        }
        ws.stash(farm, seedsHeap);
        check(farm.seedCount() >= 44 * 64 && farm.carried() == 0, "a farmhand's backpack holds thousands of seeds ("
                + farm.seedCount() + ")");
        ws.compost(farm);
        check(count(farm.seedBag(), ItemType.COCA_SEEDS) == 64 && count(farm.satchel(), ItemType.FERTILIZER) == 688
                && (rare == null || count(farm.seedBag(), ItemType.SEED_PACK) == 64),
                "a full backpack: spare common seeds become fertilizer (" + count(farm.satchel(), ItemType.FERTILIZER) + ")");
        farm.seedBag().clear();
        // ...and the runner takes the harvest off them, so they go on harvesting
        farm.satchel().clear();
        List<ItemStack> papers = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            papers.add(Items.create(ItemType.ROLLING_PAPERS, 64));
        }
        ws.stash(farm, papers);
        ws.stash(farm, List.of(Items.strainItem(ItemType.BUD_FRESH, s, 3, 10)));
        Plant grown = plugin.plants().at(key);
        if (grown != null) {
            grown.growth(100);
        }
        check(!ws.workNow(farm) && farm.status().contains("Runner"), "a full farmhand waits for the runner");
        runner.satchel().clear();
        check(ws.workNow(runner) && count(farm.satchel(), ItemType.BUD_FRESH) == 0, "the runner empties the farmhand");
        int jobs = farm.jobs();
        check(ws.workNow(farm) && farm.jobs() > jobs, "and the farmhand goes on harvesting");
        // nobody to pay: no work
        farm.satchel().clear();
        eco.set(owner, 0);
        grown = plugin.plants().at(key);
        if (grown != null) {
            grown.growth(100);
        }
        check(!ws.workNow(farm) && farm.status().contains("Not paid"), "unpaid workers stop");
        // save, level up, dismiss
        ws.save();
        var saved = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "workers.yml"));
        check(saved.isConfigurationSection("workers." + runner.id()) && saved.isConfigurationSection(
                "workers." + cook.id() + ".satchel") && "MORPHINE".equals(saved.getString("workers." + cook.id() + ".recipe")),
                "workers, their satchels and a cook's recipe are saved");
        check(saved.getDouble("workers." + supplier.id() + ".spent") > 0, "supplier spending is saved");
        farm.seedBag().addItem(Items.create(ItemType.COCA_SEEDS, 5));
        ws.save();
        saved = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "workers.yml"));
        check(saved.isConfigurationSection("workers." + farm.id() + ".seeds"), "the seed backpack is saved");
        check(ws.radius(farm) < ws.radius(ws.hireAt(new Location(w, x + 3.5, y + 1, z + 3.5),
                dev.kushcraft.worker.WorkerType.FARMHAND, boss, 3)), "trained workers reach further");
        check(Items.level(Items.machine(ItemType.FARMHAND, 2)) == 2, "a dismissed worker keeps their level");
        for (var wk : ws.of(boss)) {
            UUID ent = wk.entityId();
            ws.dismiss(wk, null);
            check(ent == null || Bukkit.getEntity(ent) == null || Bukkit.getEntity(ent).isDead(), "mannequin removed");
        }
        check(ws.of(boss).isEmpty(), "workers dismissed");
        ws.syncChunks();
        check(ws.keptChunks() == 0, "chunks are let go when the workers are gone");
        plugin.machines().breakMachine(lab, null);
        for (BlockKey k : List.of(key, empty)) {
            Plant p = plugin.plants().at(k);
            if (p != null) {
                plugin.plants().remove(p);
            }
        }
        for (int dx = 5; dx <= 7; dx++) {
            for (int dy = 1; dy <= 4; dy++) {
                for (int dz = 3; dz <= 5; dz++) {
                    w.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR);
                }
            }
        }
        for (Entity e : w.getNearbyEntities(new Location(w, x + 3, y + 1, z + 2), 7, 4, 7)) {
            if (e instanceof Item) {
                e.remove();
            }
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
        check(plugin.shop().buyEntries().stream().anyMatch(e -> e.type() == ItemType.LAB_STATION && e.price() <= 800),
                "the Drug Lab is affordable to start with");
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
        double expect = dev.kushcraft.strain.Breeding.newMythicChance(a, b) * 20_000;
        check(mythic > expect * 0.8 && mythic < expect * 1.2, "about 1 in 15 is Mythic (" + mythic + " in 20,000)");
        // two Legendary parents: a much better chance of a Mythic child
        List<Strain> legendary = all.stream().filter(s -> s.rarity() == dev.kushcraft.strain.Rarity.LEGENDARY).toList();
        check(legendary.size() >= 2, "there are Legendary strains to breed");
        if (legendary.size() >= 2) {
            int fromLegend = 0;
            for (int i = 0; i < 10_000; i++) {
                if (dev.kushcraft.strain.Breeding.cross(legendary.get(0), legendary.get(1), r).rarity()
                        == dev.kushcraft.strain.Rarity.MYTHIC) {
                    fromLegend++;
                }
            }
            check(fromLegend > 2000 && fromLegend < 2900, "two Legendary parents: about 1 in 4 Mythic (" + fromLegend
                    + " of 10,000)");
            check(dev.kushcraft.strain.Breeding.mythicChance(legendary.get(0), legendary.get(1))
                    > 3 * dev.kushcraft.strain.Breeding.MYTHIC, "Legendary parents raise the Mythic chance");
        }
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
            check(kept > 1100 && kept < 1550, "a Mythic parent passes its look on (" + kept + " of 5,000)");
        }
        // Exotic: only from two Mythic parents (or better), and some pairs find a built-in Exotic strain
        Strain blood = plugin.strains().get("blood_moon_kush");
        Strain horizon = plugin.strains().get("event_horizon");
        if (aurora != null && blood != null && horizon != null) {
            int exotic = 0, found = 0, fromOne = 0;
            for (int i = 0; i < 20_000; i++) {
                var res = dev.kushcraft.strain.Breeding.cross(aurora, blood, r, plugin.strains().all());
                if (res.rarity() == dev.kushcraft.strain.Rarity.EXOTIC) {
                    exotic++;
                    check(res.look().exotic().exoticTier(), "an Exotic child has an Exotic look");
                }
                if (res.discovered() == horizon) {
                    found++;
                }
                var one = dev.kushcraft.strain.Breeding.cross(aurora, a, r, plugin.strains().all());
                fromOne += one.look().exotic().exoticTier() || one.discovered() != null ? 1 : 0;
                var fromExotic = dev.kushcraft.strain.Breeding.cross(horizon, a, r, plugin.strains().all());
                fromOne += fromExotic.look().exotic().exoticTier() ? 1 : 0;
            }
            check(fromOne == 0, "one Mythic (or Exotic) parent never gives an Exotic child");
            check(exotic > 1200 && exotic < 2400, "two Mythic parents: Exotic now and then (" + exotic + " in 20,000)");
            check(found > 800 && found < 1700, "Aurora Kush x Blood Moon finds Event Horizon (" + found + ")");
            check(horizon.rarity() == dev.kushcraft.strain.Rarity.EXOTIC && !horizon.inShop()
                    && horizon.wildBiomes().isEmpty() && horizon.wildWeight() == 0, "Exotic strains: never sold or wild");
            check(dev.kushcraft.strain.Breeding.exoticChance(aurora, a) == 0
                    && dev.kushcraft.strain.Breeding.exoticChance(aurora, blood) > 0, "Exotic odds need two Mythics");
        } else {
            check(false, "Aurora Kush, Blood Moon and Event Horizon exist");
        }
        // two-tone strains: patterns are passed on and mutate in
        int patterns = 0;
        Strain tiger = plugin.strains().get("tiger_kush");
        for (int i = 0; i < 2000; i++) {
            var res = dev.kushcraft.strain.Breeding.cross(a, tiger != null ? tiger : b, r);
            patterns += res.look().pattern() != dev.kushcraft.strain.BudPattern.NONE ? 1 : 0;
        }
        check(patterns > 300 && patterns < 1800, "two-tone patterns are passed on (" + patterns + " in 2,000)");
        check(seen.size() > 3, "breeding is random (" + seen.size() + " different results)");
        check(dev.kushcraft.strain.Rarity.of(35, 4) == dev.kushcraft.strain.Rarity.LEGENDARY
                && dev.kushcraft.strain.Rarity.of(5, 1) == dev.kushcraft.strain.Rarity.COMMON, "rarity tiers");
        check(dev.kushcraft.strain.Rarity.of(5, 1, dev.kushcraft.strain.Exotic.NEON) == dev.kushcraft.strain.Rarity.MYTHIC,
                "exotic = Mythic");
        check(dev.kushcraft.strain.Rarity.of(5, 1, dev.kushcraft.strain.Exotic.VOID) == dev.kushcraft.strain.Rarity.EXOTIC,
                "an Exotic look = Exotic");
        check(dev.kushcraft.strain.Rarity.EXOTIC.priceFactor() >= 1.5 * dev.kushcraft.strain.Rarity.MYTHIC.priceFactor()
                && dev.kushcraft.strain.Rarity.MYTHIC.priceFactor() >= 2 * dev.kushcraft.strain.Rarity.LEGENDARY.priceFactor(),
                "Mythic sells for much more, Exotic for even more");
        check(dev.kushcraft.effect.EffectType.values().length >= 34, "34 effects");
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
        check(!RecipeBook.making(dev.kushcraft.item.ItemType.COCAINE).isEmpty(), "catalog -> recipe link");
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
        dev.kushcraft.award.Award[] all = dev.kushcraft.award.Award.values();
        check(all.length >= 45 && all.length <= 90, "45-90 awards (two menu pages), got " + all.length);
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
        check(dev.kushcraft.award.Starter.next(p) == dev.kushcraft.award.Starter.PLANT
                && dev.kushcraft.award.Starter.doneCount(p) == 0, "new players start at step 1");
        check(dev.kushcraft.award.Starter.values().length == 7, "7 getting-started steps");
        check(dev.kushcraft.award.Starter.values()[6] == dev.kushcraft.award.Starter.FORAGE, "the last step: a wild plant");
        check(!plugin.getConfig().getMapList("new-players.starter-kit").isEmpty(), "a starter kit is set up");
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
        for (String g : List.of("gear", "worker", "guide", "admin")) {
            check(GuiFont.GUIS.contains(g), "background for " + g);
        }
        check(LabRecipe.values().length <= 27, "cook page fits 3 rows");
        check(dev.kushcraft.gui.TabMenu.Tab.values().length == 6 && dev.kushcraft.gui.TabMenu.Tab.parse("workers")
                == dev.kushcraft.gui.TabMenu.Tab.WORKERS, "6 main panels, a Workers tab and /kush <tab>");
    }

    private void strains() {
        var all = new ArrayList<>(plugin.strains().all());
        check(all.size() >= 80, "80+ strains, got " + all.size());
        java.util.Set<String> names = new java.util.HashSet<>(), looks = new java.util.HashSet<>();
        java.util.Set<dev.kushcraft.strain.Climate> climates = java.util.EnumSet.noneOf(dev.kushcraft.strain.Climate.class);
        java.util.Set<dev.kushcraft.strain.BudShape> shapes = java.util.EnumSet.noneOf(dev.kushcraft.strain.BudShape.class);
        int mythic = 0, exotic = 0, patterned = 0;
        for (Strain s : all) {
            if (s.isCustom()) {
                continue;
            }
            check(names.add(s.name().toLowerCase(java.util.Locale.ROOT)), "unique strain name " + s.name());
            check(looks.add(s.look().bud() + "/" + s.look().leaf() + "/" + s.look().pistil() + "/" + s.look().shape()),
                    "unique look " + s.name());
            if (s.look().pattern() != dev.kushcraft.strain.BudPattern.NONE) {
                patterned++;
            }
            for (String p : s.parents()) {
                check(plugin.strains().get(p) != null && plugin.strains().get(p).exotic() != dev.kushcraft.strain.Exotic.NONE
                        && !plugin.strains().get(p).exotic().exoticTier(), "an Exotic strain's parents are Mythic: " + s.name());
            }
            check(!s.flavor().isEmpty(), "flavour for " + s.name());
            check(!s.effects().isEmpty() && s.effects().size() <= 4, "1-4 effects " + s.name());
            climates.add(s.climate());
            shapes.add(s.look().shape());
            if (s.exotic() != dev.kushcraft.strain.Exotic.NONE) {
                check(!s.inShop() && s.wildWeight() < 0.1, "Mythic strains are not sold and very rare: " + s.name());
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
        java.util.Set<dev.kushcraft.strain.Exotic> usedLooks = java.util.EnumSet.noneOf(dev.kushcraft.strain.Exotic.class);
        all.forEach(s -> usedLooks.add(s.exotic()));
        check(usedLooks.size() >= 18, "most animated looks are used by a strain (" + usedLooks.size() + ")");
        check(climates.size() == dev.kushcraft.strain.Climate.values().length, "a strain for every climate");
        check(shapes.size() == dev.kushcraft.strain.BudShape.values().length, "every bud shape is used");
        check(mythic >= 3, "Mythic strains exist");
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
        // money only comes from selling drugs
        check(!jobs.enabled(), "jobs pay nothing by default");
        check(dev.kushcraft.award.Award.FIRST_SALE.reward() == 0, "awards pay no cash by default");
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
