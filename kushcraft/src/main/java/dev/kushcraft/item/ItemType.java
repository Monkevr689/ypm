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
            "<gray>dirt or a <green>Planter Box</green> to plant."),
    BUD_FRESH("Fresh Bud", "bud_fresh", true, true,
            "<gray>Hang it on a <yellow>Drying Rack</yellow> first.",
            "<gray>Fresh buds are worth little."),
    BUD_DRIED("Dried Bud", "bud_dried", true, true,
            "<gray>Roll it at a <yellow>Rolling Table</yellow>,",
            "<gray>smoke it in a <aqua>Bong</aqua>, cook it in the Lab",
            "<gray>or sell it to a <green>Dealer</green>."),
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
            "<gray>Brew it into tea or tabs in the Lab."),
    MUSHROOM_SPORES("Mushroom Spores", "mushroom_spores", false, false,
            "<gray>Plant on mycelium, podzol, moss, dirt",
            "<gray>or a Planter Box. Likes the <dark_gray>dark</dark_gray>."),
    SHROOM_TEA("Shroom Tea", "shroom_tea", false, false,
            "<gray>Right-click to drink."),
    // --- lab synthetics -------------------------------------------------
    LUCID_TAB("Lucid Tab", "lucid_tab", false, false,
            "<gray>Right-click to drop it on your tongue.",
            "<light_purple>Long, colourful trip."),
    BLUE_CRYSTAL("Blue Crystal", "blue_crystal", false, false,
            "<gray>Right-click to use.",
            "<red>Extreme rush, nasty crash."),
    PIXIE_DUST("Pixie Dust", "pixie_dust", false, false,
            "<gray>Right-click to sprinkle on yourself.",
            "<yellow>Glow and float!"),
    // --- supplies -------------------------------------------------------
    ROLLING_PAPERS("Rolling Papers", "rolling_papers", false, false,
            "<gray>Used at the Rolling Table for joints."),
    BLUNT_WRAP("Blunt Wrap", "blunt_wrap", false, false,
            "<gray>Used at the Rolling Table for blunts."),
    BONG("Bong", "bong", false, false,
            "<gray>Right-click to smoke a Dried Bud, Hash or",
            "<gray>Moon Rock from your inventory.",
            "<dark_gray>Hold one in your off-hand to pick which."),
    LAB_SOLVENT("Lab Solvent", "lab_solvent", false, false,
            "<gray>Base for lab synthetics."),
    CATALYST("Catalyst", "catalyst", false, false,
            "<gray>Speeds up reactions in the Lab."),
    FERTILIZER("Fertilizer", "fertilizer", false, false,
            "<gray>Right-click a plant: faster growth",
            "<gray>and <gold>+1 quality</gold> at harvest."),
    GROWER_GUIDE("Grower's Handbook", "grower_guide", false, false,
            "<gray>Right-click to read. Everything you",
            "<gray>need to know about KushCraft."),
    // --- machines (placed as blocks) -------------------------------------
    LAB_STATION("Lab Station", "machine_lab_station", MachineType.LAB_STATION,
            "<gray>Cook hash, moon rocks, brownies, tea",
            "<gray>and synthetic stuff."),
    STRAIN_MAKER("Strain Maker", "machine_strain_maker", MachineType.STRAIN_MAKER,
            "<gray>Cross two seeds and design your",
            "<gray>own strain: name, type, colour, effects."),
    ROLLING_TABLE("Rolling Table", "machine_rolling_table", MachineType.ROLLING_TABLE,
            "<gray>Roll joints and blunts."),
    DRYING_RACK("Drying Rack", "machine_drying_rack", MachineType.DRYING_RACK,
            "<gray>Right-click with Fresh Buds to dry them."),
    GROW_LAMP("Grow Lamp", "machine_grow_lamp", MachineType.GROW_LAMP,
            "<gray>Lights up and speeds up nearby plants.",
            "<gray>Grow indoors and underground!"),
    PLANTER_BOX("Planter Box", "machine_planter_box", MachineType.PLANTER_BOX,
            "<gray>Perfect soil: plants grow faster",
            "<gray>and get <gold>+1 quality</gold>."),
    DEALER("Dealer Stand", "machine_dealer", MachineType.DEALER,
            "<gray>Buy seeds and gear, sell your product.");

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
