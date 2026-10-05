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
            shop();
            ranks();
            breeding();
            recipes();
            exchange();
            money();
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
                check(!it.getItemMeta().getCustomModelDataComponent().getColors().isEmpty(), "tint colour " + t);
            }
        }
        check(Items.hits(Items.strainItem(ItemType.JOINT, s, 3, 1)) == Items.JOINT_HITS, "joint hits");
        check(Dose.strain(s, 5, 60, 10).effects().size() == s.effects().size(), "dose effects");
        check(GuiFont.space(-169).length() == 4, "negative space builder");
        check(LabRecipe.values().length == 18, "lab recipes");
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
        check(!plugin.market().orders().isEmpty(), "daily orders exist");
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
        check(ranks.all().size() == 7, "7 dealer ranks, got " + ranks.all().size());
        check(ranks.of(0).level() == 1, "everyone starts at rank 1");
        check(ranks.of(1e12).level() == ranks.all().size(), "top rank reachable");
        for (int i = 1; i < ranks.all().size(); i++) {
            check(ranks.all().get(i).sales() > ranks.all().get(i - 1).sales(), "rank thresholds increase");
        }
        for (LabRecipe r : LabRecipe.values()) {
            check(r.rank() >= 1 && r.rank() <= ranks.all().size(), "recipe rank in range: " + r);
        }
        check(LabRecipe.HASH.rank() == 1 && LabRecipe.COCAINE.rank() > 1, "hard drugs need a higher rank");
        var a = Bukkit.getOfflinePlayer(UUID.randomUUID());
        plugin.economy().addSales(a, ranks.all().get(1).sales());
        check(ranks.of(a).level() == 2, "selling raises the rank");
        check(plugin.shop().buyEntries().stream().anyMatch(e -> e.type() == ItemType.LAB_STATION && e.price() >= 5000),
                "the Drug Lab is expensive to buy");
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
        check(seen.size() > 3, "breeding is random (" + seen.size() + " different results)");
        check(dev.kushcraft.strain.Rarity.of(35, 4) == dev.kushcraft.strain.Rarity.LEGENDARY
                && dev.kushcraft.strain.Rarity.of(5, 1) == dev.kushcraft.strain.Rarity.COMMON, "rarity tiers");
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
        check(ex.categories().size() >= 1 && ex.categories().size() <= 7, "exchange has 1-7 tabs");
        for (var c : ex.categories()) {
            for (var o : c.offers()) {
                check(ex.sellPrice(o.material()) < ex.buyPrice(o), "exchange sells cheaper than it buys: " + o.material());
            }
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
