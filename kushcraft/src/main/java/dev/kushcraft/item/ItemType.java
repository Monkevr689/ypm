package dev.kushcraft.item;

import dev.kushcraft.machine.MachineType;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/** Every custom item. The look comes from the resource pack (item_model kush:&lt;model&gt;). */
public enum ItemType {
    // --- cannabis -------------------------------------------------------
    SEED_PACK("Seeds", "seed_pack", true, true,
            "<gray>Right-click the top of farmland, grass,",
            "<gray>dirt or a <green>Planter</green> to plant."),
    BUD_FRESH("Fresh Bud", "bud_fresh", true, true,
            "<gray>Dry it in a <green>Drug Lab</green> (Dry tab).",
            "<gray>Fresh buds are worth little."),
    BUD_DRIED("Dried Bud", "bud_dried", true, true,
            "<gray>Roll or cook it in a <green>Drug Lab</green>,",
            "<gray>smoke it in a <aqua>Bong</aqua> or sell it."),
    JOINT("Joint", "joint", true, false,
            "<gray>Right-click to take a hit."),
    BLUNT("Blunt", "blunt", true, false,
            "<gray>Right-click to take a hit."),
    HASH("Hash", "hash", true, false,
            "<gray>Pressed resin. Smoke it in a <aqua>Bong</aqua>."),
    MOON_ROCK("Moon Rock", "moon_rock", true, true,
            "<gray>Bud dipped in hash oil and kief.",
            "<gray>Smoke it in a <aqua>Bong</aqua>. <red>Very strong."),
    SPACE_BROWNIE("Space Brownie", "space_brownie", true, false,
            "<gray>Right-click to eat. Kicks in after a bit..."),
    // --- mushrooms ------------------------------------------------------
    MAGIC_MUSHROOM("Magic Mushroom", "magic_mushroom", false, false,
            "<gray>Right-click to eat. Trippy!",
            "<gray>Brew it into tea or LSD in the Drug Lab."),
    MUSHROOM_SPORES("Mushroom Spores", "mushroom_spores", false, false,
            "<gray>Plant on mycelium, podzol, moss, dirt",
            "<gray>or a Planter. Likes the <dark_gray>dark</dark_gray>."),
    SHROOM_TEA("Shroom Tea", "shroom_tea", false, false,
            "<gray>Right-click to drink."),
    // --- hard drugs -----------------------------------------------------
    LUCID_TAB("LSD Tab", "lucid_tab", false, false,
            "<gray>Right-click to drop it on your tongue.",
            "<light_purple>Long, colourful trip."),
    BLUE_CRYSTAL("Meth", "blue_crystal", false, false,
            "<gray>Right-click to use.",
            "<red>Extreme rush, nasty crash."),
    PIXIE_DUST("Pixie Dust", "pixie_dust", false, false,
            "<gray>Right-click to sprinkle on yourself.",
            "<yellow>Glow and float!"),
    COCA_SEEDS("Coca Seeds", "coca_seeds", false, false,
            "<gray>Plant on farmland, grass, dirt or a Planter.",
            "<gold>Loves warm biomes</gold> <gray>(jungle, savanna)."),
    COCA_LEAVES("Coca Leaves", "coca_leaves", false, false,
            "<gray>Cook into <white>Cocaine</white> in a <green>Drug Lab</green>."),
    COCAINE("Cocaine", "cocaine", false, false,
            "<gray>Right-click to use.",
            "<aqua>Fast, focused... and paranoid."),
    POPPY_SEEDS("Poppy Seeds", "poppy_seeds", false, false,
            "<gray>Plant on farmland, grass, dirt or a Planter.",
            "<green>Loves mild biomes</green> <gray>(plains, meadows)."),
    POPPY_POD("Poppy Pod", "poppy_pod", false, false,
            "<gray>Cook into <white>Heroin</white> in a <green>Drug Lab</green>."),
    HEROIN("Heroin", "heroin", false, false,
            "<gray>Right-click to use.",
            "<red>Very strong - easy to overdo."),
    // --- supplies -------------------------------------------------------
    ROLLING_PAPERS("Rolling Papers", "rolling_papers", false, false,
            "<gray>Used in the Drug Lab to roll joints."),
    BLUNT_WRAP("Blunt Wrap", "blunt_wrap", false, false,
            "<gray>Used in the Drug Lab to roll blunts."),
    BONG("Bong", "bong", false, false,
            "<gray>Right-click to smoke a Dried Bud, Hash or",
            "<gray>Moon Rock from your inventory.",
            "<dark_gray>Hold one in your off-hand to pick which."),
    LAB_SOLVENT("Lab Solvent", "lab_solvent", false, false,
            "<gray>Base for most Drug Lab recipes."),
    CATALYST("Catalyst", "catalyst", false, false,
            "<gray>Needed for Meth and Heroin."),
    FERTILIZER("Fertilizer", "fertilizer", false, false,
            "<gray>Right-click a plant: faster growth",
            "<gray>and <gold>+1 quality</gold> at harvest."),
    GROWER_GUIDE("KushCraft Menu", "grower_guide", false, false,
            "<gray>Right-click to open the KushCraft menu:",
            "<gray>guide, market, catalog, strains and more.",
            "<dark_gray>(same as /kush)"),
    // --- blocks (placed like a block, punch to pick up) ------------------
    LAB_STATION("Drug Lab", "machine_lab_station", MachineType.LAB_STATION,
            "<gray>One station for everything:",
            "<gray>cook drugs, roll joints, dry buds",
            "<gray>and mix your own strains."),
    STRAIN_MAKER("Strain Maker", "machine_strain_maker", MachineType.STRAIN_MAKER,
            "<dark_gray>Old block - the Drug Lab does this now."),
    ROLLING_TABLE("Rolling Table", "machine_rolling_table", MachineType.ROLLING_TABLE,
            "<dark_gray>Old block - the Drug Lab does this now."),
    DRYING_RACK("Drying Rack", "machine_drying_rack", MachineType.DRYING_RACK,
            "<dark_gray>Old block - the Drug Lab does this now."),
    GROW_LAMP("Grow Lamp", "machine_grow_lamp", MachineType.GROW_LAMP,
            "<gray>Lights up and speeds up nearby plants.",
            "<gray>Grow indoors and underground!"),
    PLANTER_BOX("Planter", "machine_planter_box", MachineType.PLANTER_BOX,
            "<gray>Perfect soil: plants grow faster",
            "<gray>and get <gold>+1 quality</gold>."),
    DEALER("Dealer Stand", "machine_dealer", MachineType.DEALER,
            "<gray>Opens the market: buy gear and seeds,",
            "<gray>sell your product, hand in orders.");

    private final String display;
    private final String model;
    private final boolean strainBound;
    private final boolean tinted;
    private final MachineType machine;
    private final List<String> lore;

    ItemType(String display, String model, boolean strainBound, boolean tinted, String... lore) {
        this.display = display;
        this.model = model;
        this.strainBound = strainBound;
        this.tinted = tinted;
        this.machine = null;
        this.lore = List.of(lore);
    }

    ItemType(String display, String model, MachineType machine, String... lore) {
        this.display = display;
        this.model = model;
        this.strainBound = false;
        this.tinted = false;
        this.machine = machine;
        this.lore = List.of(lore);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String display() {
        return display;
    }

    public String model() {
        return model;
    }

    public boolean strainBound() {
        return strainBound;
    }

    public boolean tinted() {
        return tinted;
    }

    public MachineType machine() {
        return machine;
    }

    public List<String> lore() {
        return lore;
    }

    /** Machines are barrier blocks with a 3D model on top, everything else is paper. */
    public Material base() {
        return machine != null ? Material.BARRIER : Material.PAPER;
    }

    public int maxStack() {
        if (this == BONG || this == GROWER_GUIDE) {
            return 1;
        }
        return machine != null ? 16 : 64;
    }

    /** Old stations replaced by the Drug Lab: still work, but not sold or crafted any more. */
    public boolean retired() {
        return this == STRAIN_MAKER || this == ROLLING_TABLE || this == DRYING_RACK;
    }

    /** Things players consume for an effect. */
    public boolean isDrug() {
        return switch (this) {
            case JOINT, BLUNT, SPACE_BROWNIE, MAGIC_MUSHROOM, SHROOM_TEA, LUCID_TAB, BLUE_CRYSTAL, PIXIE_DUST,
                 COCAINE, HEROIN -> true;
            default -> false;
        };
    }

    public static ItemType parse(String s) {
        if (s == null) {
            return null;
        }
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
