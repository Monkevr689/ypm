package dev.kushcraft.plant;

import dev.kushcraft.KushCraft;
import dev.kushcraft.Keys;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.jobs.Jobs;
import dev.kushcraft.machine.Machine;
import dev.kushcraft.machine.MachineType;
import dev.kushcraft.strain.Climate;
import dev.kushcraft.strain.Exotic;
import dev.kushcraft.strain.Look;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.BlockKey;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/** Stores, grows and draws every plant. */
public final class PlantManager {

    private static final Set<Material> CANNABIS_SOIL = EnumSet.of(Material.FARMLAND, Material.GRASS_BLOCK,
            Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.PODZOL, Material.MUD,
            Material.MOSS_BLOCK);
    /** Peyote also grows in sand, like in the desert. */
    private static final Set<Material> PEYOTE_SOIL = EnumSet.of(Material.FARMLAND, Material.GRASS_BLOCK,
            Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.SAND, Material.RED_SAND,
            Material.TERRACOTTA);
    private static final Set<Material> MUSHROOM_SOIL = EnumSet.of(Material.MYCELIUM, Material.PODZOL,
            Material.MOSS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.GRASS_BLOCK,
            Material.MUD, Material.FARMLAND);

    private final KushCraft plugin;
    private final File file;
    private final Map<BlockKey, Plant> plants = new HashMap<>();
    private final Map<String, Set<BlockKey>> byChunk = new HashMap<>();
    private boolean dirty;

    public PlantManager(KushCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "plants.yml");
    }

    // ------------------------------------------------------------------
    // storage
    // ------------------------------------------------------------------

    public void load() {
        plants.clear();
        byChunk.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("plants");
        if (sec == null) {
            return;
        }
        for (String k : sec.getKeys(false)) {
            BlockKey key = BlockKey.parse(k);
            ConfigurationSection s = sec.getConfigurationSection(k);
            if (key == null || s == null) {
                continue;
            }
            Plant.Kind kind;
            try {
                kind = Plant.Kind.valueOf(s.getString("kind", "CANNABIS"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            UUID owner = null;
            try {
                String o = s.getString("owner");
                owner = o == null ? null : UUID.fromString(o);
            } catch (IllegalArgumentException ignored) {
            }
            Plant plant = new Plant(key, kind, s.getString("strain"), s.getDouble("growth"), s.getBoolean("fert"), owner);
            plant.wildUntil(s.getLong("wild-until", 0));
            add(plant);
        }
        plugin.getLogger().info("Loaded " + plants.size() + " plants.");
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        ConfigurationSection sec = y.createSection("plants");
        for (Plant p : plants.values()) {
            ConfigurationSection s = sec.createSection(p.key().serialize());
            s.set("kind", p.kind().name());
            if (p.strainId() != null) {
                s.set("strain", p.strainId());
            }
            s.set("growth", Math.round(p.growth() * 100) / 100.0);
            s.set("fert", p.fertilized());
            if (p.owner() != null) {
                s.set("owner", p.owner().toString());
            }
            if (p.wild()) {
                s.set("wild-until", p.wildUntil());
            }
        }
        try {
            y.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save plants.yml", e);
        }
    }

    public void markDirty() {
        dirty = true;
    }

    public void start() {
        int tickSeconds = Math.max(1, plugin.getConfig().getInt("growth.tick-seconds", 10));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> growAll(tickSeconds), 20L * tickSeconds, 20L * tickSeconds);
        Bukkit.getScheduler().runTaskTimer(plugin, this::sparkle, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                save();
            }
        }, 20L * 60, 20L * 60);
        // draw plants in chunks that are already loaded
        for (World w : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk c : w.getLoadedChunks()) {
                chunkLoaded(w, c.getX(), c.getZ());
            }
        }
    }

    public void shutdown() {
        for (Plant p : plants.values()) {
            despawn(p);
        }
        save();
    }

    private void add(Plant p) {
        plants.put(p.key(), p);
        byChunk.computeIfAbsent(p.key().chunkId(), k -> new HashSet<>()).add(p.key());
    }

    private void forget(Plant p) {
        plants.remove(p.key());
        Set<BlockKey> set = byChunk.get(p.key().chunkId());
        if (set != null) {
            set.remove(p.key());
            if (set.isEmpty()) {
                byChunk.remove(p.key().chunkId());
            }
        }
        markDirty();
    }

    public Plant at(BlockKey key) {
        return plants.get(key);
    }

    public Collection<Plant> all() {
        return plants.values();
    }

    public Plant fromEntity(Entity e) {
        String s = e.getPersistentDataContainer().get(Keys.PLANT, PersistentDataType.STRING);
        if (s == null) {
            return null;
        }
        BlockKey key = BlockKey.parse(s);
        return key == null ? null : plants.get(key);
    }

    // ------------------------------------------------------------------
    // planting / removing
    // ------------------------------------------------------------------

    public boolean isSoil(Block soil, Plant.Kind kind) {
        Machine m = plugin.machines().at(BlockKey.of(soil));
        if (m != null) {
            return m.type() == MachineType.PLANTER_BOX;
        }
        Set<Material> ok = switch (kind) {
            case MUSHROOM -> MUSHROOM_SOIL;
            case PEYOTE -> PEYOTE_SOIL;
            default -> CANNABIS_SOIL;
        };
        return ok.contains(soil.getType());
    }

    /** Plants on top of soil. Returns false (with a message) when it can't. */
    public boolean plant(Player player, Block soil, Plant.Kind kind, Strain strain) {
        Block space = soil.getRelative(0, 1, 0);
        BlockKey key = BlockKey.of(space);
        if (!isSoil(soil, kind)) {
            player.sendActionBar(Text.mm(switch (kind) {
                case MUSHROOM -> "<red>Plant spores on mycelium, podzol, moss, dirt or a Planter.";
                case PEYOTE -> "<red>Plant peyote on sand, dirt, farmland or a Planter.";
                default -> "<red>Plant seeds on farmland, grass, dirt, moss or a Planter.";
            }));
            return false;
        }
        if (!space.getType().isAir() || plants.containsKey(key) || plugin.machines().at(key) != null) {
            player.sendActionBar(Text.mm("<red>There is no room to plant here."));
            return false;
        }
        if (!dev.kushcraft.util.Protection.canBuild(player, space)) {
            player.sendActionBar(Text.mm("<red>You can't plant here."));
            return false;
        }
        Plant p = plantAt(key, kind, strain, player.getUniqueId());
        plugin.awards().planted(player);
        Location c = key.bottomCenter();
        c.getWorld().playSound(c, "minecraft:item.crop.plant", SoundCategory.BLOCKS, 1f, 1f);
        c.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, c.clone().add(0, 0.2, 0), 6, 0.25, 0.1, 0.25, 0);
        return true;
    }

    /** Creates a plant without any checks (used by planting and /kush selftest). */
    public Plant plantAt(BlockKey key, Plant.Kind kind, Strain strain, UUID owner) {
        Plant p = new Plant(key, kind, strain == null ? null : strain.id(), 0, false, owner);
        add(p);
        markDirty();
        spawn(p);
        return p;
    }

    /** Removes a plant. Drops harvest when mature, the seed back when it was still tiny. */
    public void destroy(Plant p, Player who) {
        if (p.mature()) {
            harvest(p, who);
            return;
        }
        Location c = p.key().bottomCenter();
        if (p.stage() <= 1 && c != null) {
            ItemStack back = switch (p.kind()) {
                case MUSHROOM -> Items.create(ItemType.MUSHROOM_SPORES);
                case COCA -> Items.create(ItemType.COCA_SEEDS);
                case POPPY -> Items.create(ItemType.POPPY_SEEDS);
                case PEYOTE -> Items.create(ItemType.PEYOTE_SEEDS);
                case CANNABIS -> Items.strainItem(ItemType.SEED_PACK, plugin.strains().getOrDefault(p.strainId()), 3, 1);
            };
            c.getWorld().dropItemNaturally(c.add(0, 0.3, 0), back);
        } else if (who != null) {
            who.sendActionBar(Text.mm("<gray>You pulled out the plant. <dark_gray>(Wait until it's ready next time!)"));
        }
        remove(p);
        breakEffect(p);
    }

    public void remove(Plant p) {
        despawn(p);
        forget(p);
    }

    private void breakEffect(Plant p) {
        Location c = p.key().center();
        if (c == null) {
            return;
        }
        c.getWorld().playSound(c, p.kind() == Plant.Kind.MUSHROOM ? "minecraft:block.fungus.break"
                : "minecraft:block.azalea_leaves.break", SoundCategory.BLOCKS, 1f, 1f);
        c.getWorld().spawnParticle(Particle.BLOCK, c, 20, 0.3, 0.3, 0.3, 0,
                (p.kind() == Plant.Kind.MUSHROOM ? Material.BROWN_MUSHROOM_BLOCK : Material.AZALEA_LEAVES).createBlockData());
    }

    /** What a ripe plant gives: the items, the action bar message and the conditions it grew in. */
    public record Harvest(List<ItemStack> drops, String message, Conditions conditions) {
    }

    /** Works out the harvest of a ripe plant (does not remove it). */
    public Harvest harvestDrops(Plant p) {
        Conditions cond = conditions(p);
        int q = cond.quality;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<ItemStack> drops = new ArrayList<>();
        String msg;
        if (p.kind() == Plant.Kind.CANNABIS) {
            Strain s = plugin.strains().getOrDefault(p.strainId());
            int buds = Math.max(1, 2 + (q >= 4 ? 1 : 0) + (q >= 5 ? 1 : 0) + r.nextInt(2) + (p.fertilized() ? 1 : 0)
                    + cond.fit().buds());
            int seeds = 1 + (r.nextDouble() < 0.4 ? 1 : 0);
            drops.add(Items.strainItem(ItemType.BUD_FRESH, s, q, buds));
            drops.add(Items.strainItem(ItemType.SEED_PACK, s, 3, seeds));
            msg = "<green>Harvested " + buds + "x " + s.colored() + " <gray>" + Text.stars(q)
                    + (cond.fit() == Climate.Fit.IDEAL ? " <green>+1 ideal climate" : "");
        } else if (p.kind() == Plant.Kind.COCA) {
            int leaves = Math.max(1, cond.fit().buds() + 3 + r.nextInt(2)
                    + (q >= 4 ? 1 : 0) + (q >= 5 ? 1 : 0) + (p.fertilized() ? 1 : 0));
            drops.add(Items.create(ItemType.COCA_LEAVES, leaves));
            drops.add(Items.create(ItemType.COCA_SEEDS, 1 + (r.nextDouble() < 0.4 ? 1 : 0)));
            msg = "<green>Picked " + leaves + " Coca Leaves <gray>" + Text.stars(q);
        } else if (p.kind() == Plant.Kind.POPPY) {
            int pods = Math.max(1, cond.fit().buds() + 2 + r.nextInt(2)
                    + (q >= 4 ? 1 : 0) + (q >= 5 ? 1 : 0) + (p.fertilized() ? 1 : 0));
            drops.add(Items.create(ItemType.POPPY_POD, pods));
            drops.add(Items.create(ItemType.POPPY_SEEDS, 1 + (r.nextDouble() < 0.4 ? 1 : 0)));
            msg = "<green>Picked " + pods + " Poppy Pods <gray>" + Text.stars(q);
        } else if (p.kind() == Plant.Kind.PEYOTE) {
            int buttons = Math.max(1, cond.fit().buds() + 2 + r.nextInt(2)
                    + (q >= 4 ? 1 : 0) + (q >= 5 ? 1 : 0) + (p.fertilized() ? 1 : 0));
            drops.add(Items.create(ItemType.PEYOTE_BUTTON, buttons));
            drops.add(Items.create(ItemType.PEYOTE_SEEDS, 1 + (r.nextDouble() < 0.4 ? 1 : 0)));
            msg = "<green>Cut " + buttons + " Peyote Buttons <gray>" + Text.stars(q);
        } else {
            int shrooms = 2 + r.nextInt(2) + (q >= 4 ? 1 : 0) + (p.fertilized() ? 1 : 0);
            drops.add(Items.create(ItemType.MAGIC_MUSHROOM, shrooms));
            if (r.nextDouble() < 0.4) {
                drops.add(Items.create(ItemType.MUSHROOM_SPORES, 1));
            }
            msg = "<gold>Picked " + shrooms + " Magic Mushrooms";
        }
        return new Harvest(drops, msg, cond);
    }

    public void harvest(Plant p, Player who) {
        Location c = p.key().bottomCenter();
        if (c == null) {
            remove(p);
            return;
        }
        Harvest h = harvestDrops(p);
        if (who != null) {
            // Grower job: paid for every harvest
            double earned = plugin.jobs().pay(who, Jobs.Job.GROWER,
                    plugin.jobs().rate(Jobs.Job.GROWER, p.kind().name()), false);
            who.sendActionBar(Text.mm(h.message() + (earned > 0 ? " <gold>+" + plugin.economy().format(earned) : "")));
        }
        if (who != null && plugin.getConfig().getBoolean("harvest.to-inventory", true)) {
            dev.kushcraft.util.InventoryUtil.give(who, h.drops().toArray(new ItemStack[0]));
        } else {
            for (ItemStack it : h.drops()) {
                c.getWorld().dropItemNaturally(c.clone().add(0, 0.4, 0), it);
            }
        }
        if (who != null) {
            who.giveExp(3);
            plugin.awards().harvested(who, p.kind(), p.strainId(), h.conditions().fit(), Climate.of(p.key().block()));
            if (p.wild()) {
                plugin.awards().foraged(who);
            }
        }
        pickEffect(p);
        remove(p);
    }

    /**
     * A worker picks a ripe plant: returns what it gave (minus the seed it
     * replanted, when replant is true and a seed came off the plant).
     */
    public List<ItemStack> pick(Plant p, boolean replant) {
        Harvest h = harvestDrops(p);
        List<ItemStack> drops = new ArrayList<>(h.drops());
        pickEffect(p);
        remove(p);
        if (replant) {
            ItemType seed = seedOf(p.kind());
            for (ItemStack it : drops) {
                if (Items.type(it) == seed && it.getAmount() > 0) {
                    it.setAmount(it.getAmount() - 1);
                    Strain s = p.kind() == Plant.Kind.CANNABIS ? plugin.strains().getOrDefault(p.strainId()) : null;
                    plantAt(p.key(), p.kind(), s, p.owner());
                    break;
                }
            }
            drops.removeIf(it -> it.getAmount() <= 0);
        }
        return drops;
    }

    /** The seed item a plant kind grows from. */
    public static ItemType seedOf(Plant.Kind kind) {
        return switch (kind) {
            case CANNABIS -> ItemType.SEED_PACK;
            case MUSHROOM -> ItemType.MUSHROOM_SPORES;
            case COCA -> ItemType.COCA_SEEDS;
            case POPPY -> ItemType.POPPY_SEEDS;
            case PEYOTE -> ItemType.PEYOTE_SEEDS;
        };
    }

    /** The plant kind a seed item grows, or null. */
    public static Plant.Kind kindOf(ItemType seed) {
        for (Plant.Kind k : Plant.Kind.values()) {
            if (seedOf(k) == seed) {
                return k;
            }
        }
        return null;
    }

    private void pickEffect(Plant p) {
        Location c = p.key().bottomCenter();
        if (c == null) {
            return;
        }
        c.getWorld().playSound(c, "minecraft:block.sweet_berry_bush.pick_berries", SoundCategory.BLOCKS, 1f, 0.9f);
        c.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, c.clone().add(0, 0.6, 0), 12, 0.35, 0.4, 0.35, 0);
        breakEffect(p);
    }

    public boolean fertilize(Plant p) {
        if (p.fertilized() || p.mature()) {
            return false;
        }
        p.fertilized(true);
        p.growth(p.growth() + 5);
        markDirty();
        refresh(p);
        Location c = p.key().center();
        c.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, c, 15, 0.35, 0.35, 0.35, 0);
        c.getWorld().playSound(c, "minecraft:item.bone_meal.use", SoundCategory.BLOCKS, 1f, 1f);
        return true;
    }

    public boolean boneMeal(Plant p) {
        if (p.mature()) {
            return false;
        }
        p.growth(p.growth() + 8);
        markDirty();
        refresh(p);
        Location c = p.key().center();
        c.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, c, 10, 0.35, 0.35, 0.35, 0);
        c.getWorld().playSound(c, "minecraft:item.bone_meal.use", SoundCategory.BLOCKS, 1f, 1f);
        return true;
    }

    // ------------------------------------------------------------------
    // growing
    // ------------------------------------------------------------------

    /** Current growing conditions of a plant. */
    public record Conditions(double multiplier, int quality, String light, String soil, String climate, String problem,
                             Climate.Fit fit) {
    }

    public Conditions conditions(Plant p) {
        Block at = p.key().block();
        if (at == null) {
            return new Conditions(0, 1, "", "", "", "World not loaded", Climate.Fit.OK);
        }
        Block soil = at.getRelative(0, -1, 0);
        double mult = 1.0;
        int quality = 3;
        String problem = null;
        boolean lamp = plugin.machines().lampNear(p.key(), plugin.getConfig().getInt("growth.lamp-radius", 6));
        int light = at.getLightLevel();
        String lightText;
        Machine soilMachine = plugin.machines().at(BlockKey.of(soil));
        boolean planter = soilMachine != null && soilMachine.type() == MachineType.PLANTER_BOX;
        String soilText;
        String climateText = "";
        Climate.Fit fit = Climate.Fit.OK;

        if (p.kind() != Plant.Kind.MUSHROOM) {
            int minLight = plugin.getConfig().getInt("growth.min-light", 9);
            if (lamp) {
                mult *= 1.35;
                lightText = "<light_purple>Grow Lamp ✔";
            } else if (light < minLight) {
                mult = 0;
                lightText = "<red>Too dark (" + light + ")";
                problem = "Needs light! (sun, torches or a Grow Lamp)";
            } else {
                lightText = "<yellow>☀ " + light;
            }
            if (planter) {
                mult *= 1.3;
                quality++;
                soilText = "<green>Planter Box ✔";
            } else if (soil.getType() == Material.FARMLAND) {
                Farmland f = (Farmland) soil.getBlockData();
                if (f.getMoisture() >= f.getMaximumMoisture()) {
                    mult *= 1.25;
                    quality++;
                    soilText = "<aqua>Watered farmland ✔";
                } else {
                    soilText = "<yellow>Dry farmland";
                    if (problem == null) {
                        problem = "Water nearby would help";
                    }
                }
            } else if (p.kind() == Plant.Kind.PEYOTE && (soil.getType() == Material.SAND
                    || soil.getType() == Material.RED_SAND)) {
                mult *= 1.25;
                quality++;
                soilText = "<gold>Desert sand ✔";
            } else {
                mult *= 0.8;
                quality--;
                soilText = "<gray>Plain ground";
            }
            Climate here = Climate.of(at);
            Climate home = switch (p.kind()) {
                case CANNABIS -> plugin.strains().getOrDefault(p.strainId()).climate();
                case COCA -> Climate.TROPICAL;
                case PEYOTE -> Climate.DESERT;
                default -> Climate.TEMPERATE;
            };
            fit = home.fit(here);
            // a Grow Lamp works like a greenhouse: no bad climate under it
            if (fit == Climate.Fit.HARSH && lamp) {
                fit = Climate.Fit.OK;
            }
            mult *= fit.growth();
            quality += fit.quality();
            climateText = here.colored() + switch (fit) {
                case IDEAL -> " <green>✔ ideal";
                case HARSH -> " <red>✘ loves " + home.display();
                default -> lamp && home.harsh(here) ? " <light_purple>(lamp)" : "";
            };
            if (fit == Climate.Fit.HARSH && problem == null) {
                problem = "Wrong climate: it loves " + home.display() + " (or use a Grow Lamp)";
            }
        } else {
            if (light <= 7) {
                mult *= 1.5;
                lightText = "<dark_gray>Dark ✔";
            } else if (light <= 11) {
                lightText = "<gray>Dim";
            } else {
                mult *= 0.25;
                quality--;
                lightText = "<yellow>Too bright";
                problem = "Mushrooms like the dark";
            }
            Material m = soil.getType();
            if (planter) {
                mult *= 1.3;
                quality++;
                soilText = "<green>Planter Box ✔";
            } else if (m == Material.MYCELIUM || m == Material.PODZOL) {
                mult *= 1.5;
                quality++;
                soilText = "<light_purple>" + Text.titleCase(m.name()) + " ✔";
            } else if (m == Material.MOSS_BLOCK || m == Material.ROOTED_DIRT || m == Material.MUD) {
                mult *= 1.2;
                soilText = "<green>" + Text.titleCase(m.name());
            } else {
                mult *= 0.8;
                soilText = "<gray>Plain ground";
            }
            String biome = at.getBiome().getKey().getKey().toLowerCase(Locale.ROOT);
            if (biome.contains("mushroom")) {
                mult *= 2.0;
                quality++;
                climateText = "<light_purple>Mushroom island ✔";
            } else if (biome.contains("dark_forest") || biome.contains("swamp") || biome.contains("lush")
                    || biome.contains("deep_dark") || biome.contains("pale")) {
                mult *= 1.3;
                climateText = "<green>Damp biome ✔";
            } else {
                climateText = "<gray>Normal biome";
            }
        }
        if (p.fertilized()) {
            mult *= 1.4;
            quality++;
        }
        mult *= 1 + plugin.cartels().growBonus(p.owner());
        if (plugin.effects().greenThumbNear(at.getLocation())) {
            mult *= 1.25;
        }
        quality = Math.max(1, Math.min(5, quality));
        return new Conditions(mult, quality, lightText, soilText, climateText, problem, fit);
    }

    private void growAll(int tickSeconds) {
        double cannabisMinutes = Math.max(0.1, plugin.getConfig().getDouble("growth.cannabis-minutes", 20));
        double mushroomMinutes = Math.max(0.1, plugin.getConfig().getDouble("growth.mushroom-minutes", 12));
        double cocaMinutes = Math.max(0.1, plugin.getConfig().getDouble("growth.coca-minutes", 16));
        double poppyMinutes = Math.max(0.1, plugin.getConfig().getDouble("growth.poppy-minutes", 14));
        double peyoteMinutes = Math.max(0.1, plugin.getConfig().getDouble("growth.peyote-minutes", 18));
        Map<UUID, Integer> ripened = new HashMap<>();
        long now = System.currentTimeMillis();
        for (Plant p : new ArrayList<>(plants.values())) {
            if (p.wild() && now > p.wildUntil()) {
                // nobody picked it: it withers away
                if (p.key().isLoaded()) {
                    breakEffect(p);
                }
                remove(p);
                continue;
            }
            if (p.mature() || !p.key().isLoaded()) {
                continue;
            }
            Block at = p.key().block();
            Block soil = at.getRelative(0, -1, 0);
            if (!isSoil(soil, p.kind())) {
                // the soil got removed in a way we did not catch
                destroy(p, null);
                continue;
            }
            Conditions c = conditions(p);
            double minutes = switch (p.kind()) {
                case CANNABIS -> cannabisMinutes;
                case MUSHROOM -> mushroomMinutes;
                case COCA -> cocaMinutes;
                case POPPY -> poppyMinutes;
                case PEYOTE -> peyoteMinutes;
            };
            double perTick = 100.0 / (minutes * 60.0 / tickSeconds);
            p.status = c.problem() == null ? "" : c.problem();
            if (c.multiplier() <= 0) {
                continue;
            }
            p.growth(p.growth() + perTick * c.multiplier());
            markDirty();
            if (p.stage() != p.shownStage) {
                refresh(p);
                if (p.mature()) {
                    Location l = p.key().center();
                    l.getWorld().spawnParticle(Particle.WAX_ON, l.add(0, 0.4, 0), 10, 0.3, 0.4, 0.3, 0);
                    if (p.owner() != null) {
                        ripened.merge(p.owner(), 1, Integer::sum);
                    }
                }
            }
        }
        if (plugin.getConfig().getBoolean("harvest.ripe-notice", true)) {
            ripened.forEach((id, n) -> {
                Player o = Bukkit.getPlayer(id);
                if (o != null) {
                    o.sendActionBar(Text.mm("<green>☘ " + (n == 1 ? "One of your plants is" : n + " of your plants are")
                            + " ready to harvest!"));
                    o.playSound(o.getLocation(), "minecraft:block.note_block.bell", SoundCategory.PLAYERS, 0.4f, 1.6f);
                }
            });
        }
    }

    // ------------------------------------------------------------------
    // visuals: an item display (the model) + an interaction (the hitbox)
    // ------------------------------------------------------------------

    private ItemStack visualItem(Plant p) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        int stage = p.stage();
        if (p.kind() != Plant.Kind.CANNABIS) {
            meta.setItemModel(Keys.model("plant_" + p.kind().name().toLowerCase(Locale.ROOT) + "_" + stage));
        } else {
            Strain s = plugin.strains().getOrDefault(p.strainId());
            meta.setItemModel(Keys.model("plant_" + s.type().plantModel() + "_" + stage));
            Look l = s.look();
            // young flowers have white hairs that turn the strain's colour when ripe
            int pistil = stage >= 4 ? l.pistil() : Look.mix(l.pistil(), 0xFFFFFF, 0.6);
            Items.tint(meta, l.bud(), leafTint(s, p), pistil);
            Items.strings(meta, l.shape().id(), stage >= 3 ? l.exotic().id() : Exotic.NONE.id());
        }
        it.setItemMeta(meta);
        return it;
    }

    /** The strain's leaf colour, every plant a little lighter or darker than the next. */
    static int leafTint(Strain s, Plant p) {
        double light = 0.92 + Math.floorMod(p.key().hashCode() * 7, 15) / 100.0;
        return Look.scale(s.look().leaf(), light);
    }

    /** Plants vary in size a little (0.88x - 1.12x), standing on the same spot. */
    static float sizeOf(Plant p) {
        return 0.88f + Math.floorMod(p.key().hashCode() * 31 + 7, 25) / 100f;
    }

    private long sparkleTick;

    /** Mythic plants sparkle in their colours once they flower. */
    private void sparkle() {
        sparkleTick++;
        for (Plant p : plants.values()) {
            if (p.kind() != Plant.Kind.CANNABIS || p.stage() < 3 || p.displayId == null) {
                continue;
            }
            Exotic ex = plugin.strains().getOrDefault(p.strainId()).exotic();
            if (ex == Exotic.NONE || ex.particle() == null) {
                continue;
            }
            Location c = p.key().center();
            if (c == null) {
                continue;
            }
            Location at = c.add(0, hitboxHeight(p) * 0.55, 0);
            if (ex == Exotic.RAINBOW) {
                at.getWorld().spawnParticle(Particle.DUST, at, 3, 0.3, 0.4, 0.3, 0,
                        new Particle.DustOptions(Exotic.rainbow(sparkleTick * 3), 0.9f));
            } else {
                at.getWorld().spawnParticle(ex.particle(), at, ex == Exotic.INFERNO ? 2 : 1, 0.3, 0.4, 0.3, 0.005);
            }
        }
    }

    private void glow(ItemDisplay d, Plant p) {
        boolean lit = p.kind() == Plant.Kind.CANNABIS && p.stage() >= 3
                && plugin.strains().getOrDefault(p.strainId()).exotic().glows();
        d.setBrightness(lit ? new org.bukkit.entity.Display.Brightness(15, 15) : null);
    }

    private float hitboxHeight(Plant p) {
        int st = p.stage();
        if (p.kind() == Plant.Kind.MUSHROOM) {
            return st >= 2 ? 0.6f : 0.35f;
        }
        if (p.kind() != Plant.Kind.CANNABIS) {
            return st >= 2 ? 0.9f : 0.5f;
        }
        Strain s = plugin.strains().getOrDefault(p.strainId());
        if (st <= 1) {
            return 0.6f;
        }
        if (st == 2) {
            return 1.0f;
        }
        return switch (s.type()) {
            case SATIVA -> 1.9f;
            case HYBRID -> 1.4f;
            case INDICA -> 1.0f;
        };
    }

    public void spawn(Plant p) {
        despawn(p);
        Location center = p.key().center();
        if (center == null || !p.key().isLoaded()) {
            return;
        }
        World w = center.getWorld();
        float yaw = Math.floorMod(p.key().hashCode(), 360);
        center.setYaw(yaw);
        ItemDisplay d = w.spawn(center, ItemDisplay.class, e -> {
            e.setItemStack(visualItem(p));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            float k = sizeOf(p);
            e.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(0, 0.5f * (k - 1), 0),
                    new org.joml.AxisAngle4f(), new org.joml.Vector3f(k, k, k), new org.joml.AxisAngle4f()));
            e.setPersistent(false);
            e.setViewRange(0.75f);
            e.setDisplayWidth(1.5f);
            e.setDisplayHeight(2.5f);
            glow(e, p);
            e.getPersistentDataContainer().set(Keys.VISUAL, PersistentDataType.STRING, p.key().serialize());
        });
        Interaction i = w.spawn(p.key().bottomCenter(), Interaction.class, e -> {
            e.setInteractionWidth(0.85f);
            e.setInteractionHeight(hitboxHeight(p));
            e.setResponsive(true);
            e.setPersistent(false);
            e.getPersistentDataContainer().set(Keys.PLANT, PersistentDataType.STRING, p.key().serialize());
            e.getPersistentDataContainer().set(Keys.VISUAL, PersistentDataType.STRING, p.key().serialize());
        });
        p.displayId = d.getUniqueId();
        p.hitboxId = i.getUniqueId();
        p.shownStage = p.stage();
    }

    public void refresh(Plant p) {
        Entity d = p.displayId == null ? null : Bukkit.getEntity(p.displayId);
        Entity i = p.hitboxId == null ? null : Bukkit.getEntity(p.hitboxId);
        if (d instanceof ItemDisplay display && i instanceof Interaction hit) {
            display.setItemStack(visualItem(p));
            glow(display, p);
            hit.setInteractionHeight(hitboxHeight(p));
            p.shownStage = p.stage();
        } else if (p.key().isLoaded()) {
            spawn(p);
        }
    }

    public void despawn(Plant p) {
        if (p.displayId != null) {
            Entity e = Bukkit.getEntity(p.displayId);
            if (e != null) {
                e.remove();
            }
            p.displayId = null;
        }
        if (p.hitboxId != null) {
            Entity e = Bukkit.getEntity(p.hitboxId);
            if (e != null) {
                e.remove();
            }
            p.hitboxId = null;
        }
        p.shownStage = -1;
    }

    public void chunkLoaded(World w, int cx, int cz) {
        Set<BlockKey> keys = byChunk.get(BlockKey.chunkId(w.getName(), cx, cz));
        if (keys == null) {
            return;
        }
        for (BlockKey k : new ArrayList<>(keys)) {
            Plant p = plants.get(k);
            if (p != null) {
                spawn(p);
            }
        }
    }

    public void chunkUnloaded(World w, int cx, int cz) {
        Set<BlockKey> keys = byChunk.get(BlockKey.chunkId(w.getName(), cx, cz));
        if (keys == null) {
            return;
        }
        for (BlockKey k : keys) {
            Plant p = plants.get(k);
            if (p != null) {
                despawn(p);
            }
        }
    }

    /** Short status for the action bar when a plant is right-clicked. */
    public void showInfo(Player player, Plant p) {
        Conditions c = conditions(p);
        String name;
        name = switch (p.kind()) {
            case MUSHROOM -> "<gold>Magic Mushrooms";
            case COCA -> "<green>Coca Bush";
            case POPPY -> "<red>Opium Poppy";
            case PEYOTE -> "<gold>Peyote Cactus";
            case CANNABIS -> plugin.strains().getOrDefault(p.strainId()).colored();
        };
        if (p.wild()) {
            name = "<green>Wild</green> " + name;
        }
        String line = name + " <dark_gray>|</dark_gray> <white>" + p.stageName() + " <gray>"
                + (int) p.growth() + "%</gray> <dark_gray>|</dark_gray> " + Text.stars(c.quality());
        if (p.status != null && !p.status.isEmpty()) {
            line += " <dark_gray>|</dark_gray> <red>" + p.status;
        } else if (c.problem() != null) {
            line += " <dark_gray>|</dark_gray> <yellow>" + c.problem();
        }
        player.sendActionBar(Text.mm(line));
        if (player.isSneaking()) {
            player.sendMessage(Text.msg(name + " <gray>- " + p.stageName() + " (" + (int) p.growth() + "%)"));
            player.sendMessage(Text.mm("  <gray>Light: " + c.light() + "  <gray>Soil: " + c.soil()));
            player.sendMessage(Text.mm("  <gray>Climate: " + c.climate() + "  <gray>Fertilized: "
                    + (p.fertilized() ? "<green>yes" : "<gray>no") + "  <gray>Expected quality: " + Text.stars(c.quality())));
            player.sendMessage(Text.mm("  <gray>Growth speed: <white>x" + String.format(Locale.ROOT, "%.2f", c.multiplier())));
        }
    }

    /** Glow colour for strain-coloured particles. */
    public static Color strainColor(Strain s) {
        return Color.fromRGB(s.color());
    }
}
