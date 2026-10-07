package dev.kushcraft.item;

import dev.kushcraft.machine.MachineType;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/** Every custom item. The look comes from the resource pack (item_model kush:&lt;model&gt;). */
public enum ItemType {
    // --- cannabis -------------------------------------------------------
    SEED_PACK("Seeds", "seed_pack", true, true,
            "<gray>Plant on farmland, grass or a Planter."),
    BUD_FRESH("Fresh Bud", "bud_fresh", true, true,
            "<gray>Dry it at a Drug Lab."),
    BUD_DRIED("Dried Bud", "bud_dried", true, true,
            "<gray>Roll it, cook it or sell it."),
    JOINT("Joint", "joint", true, false,
            "<gray>Right-click to smoke."),
    BLUNT("Blunt", "blunt", true, false,
            "<gray>Right-click to smoke."),
    VAPE_PEN("Vape Pen", "vape_pen", true, true,
            "<gray>Right-click to puff."),
    // old weed products: not made any more, but old ones still work and sell
    KIEF("Kief", "kief", true, false,
            "<dark_gray>Old product - no longer made. Sell it."),
    HASH("Hash", "hash", true, false,
            "<gray>Smoke it in a Bong.", "<dark_gray>Old product - no longer made."),
    MOON_ROCK("Moon Rock", "moon_rock", true, true,
            "<gray>Smoke it in a Bong.", "<dark_gray>Old product - no longer made."),
    CANNA_BUTTER("Canna Butter", "canna_butter", true, false,
            "<dark_gray>Old product - no longer made. Sell it."),
    SPACE_BROWNIE("Space Brownie", "space_brownie", true, false,
            "<gray>Right-click to eat.", "<dark_gray>Old product - no longer made."),
    GUMMIES("THC Gummies", "gummies", true, true,
            "<gray>Right-click to eat.", "<dark_gray>Old product - no longer made."),
    WAX("Wax", "wax", true, true,
            "<gray>Right-click to dab.", "<dark_gray>Old product - no longer made."),
    // --- mushrooms ------------------------------------------------------
    MAGIC_MUSHROOM("Magic Mushroom", "magic_mushroom", false, false,
            "<gray>Right-click to eat."),
    MUSHROOM_SPORES("Mushroom Spores", "mushroom_spores", false, false,
            "<gray>Plant on dirt, moss or mycelium."),
    SHROOM_TEA("Shroom Tea", "shroom_tea", false, false,
            "<gray>Right-click to drink."),
    ERGOT("Ergot", "ergot", false, false,
            "<gray>Fungus from wheat. Cook it into LSD."),
    ERGOT_EXTRACT("Ergot Extract", "ergot_extract", false, false,
            "<gray>Drip it on paper for LSD tabs."),
    // --- hard drugs -----------------------------------------------------
    LUCID_TAB("LSD Tab", "lucid_tab", false, false,
            "<gray>Right-click to use."),
    BLUE_CRYSTAL("Meth", "blue_crystal", false, false,
            "<gray>Right-click to use."),
    PIXIE_DUST("Pixie Dust", "pixie_dust", false, false,
            "<gray>Right-click to use."),
    COCA_SEEDS("Coca Seeds", "coca_seeds", false, false,
            "<gray>Plant it. Likes warm biomes."),
    COCA_LEAVES("Coca Leaves", "coca_leaves", false, false,
            "<gray>Cook into Coca Paste."),
    COCA_PASTE("Coca Paste", "coca_paste", false, false,
            "<gray>Cook into Cocaine."),
    COCAINE("Cocaine", "cocaine", false, false,
            "<gray>Right-click to use."),
    POPPY_SEEDS("Poppy Seeds", "poppy_seeds", false, false,
            "<gray>Plant it. Likes mild biomes."),
    POPPY_POD("Poppy Pod", "poppy_pod", false, false,
            "<gray>Cook into Opium."),
    MORPHINE("Morphine Base", "morphine", false, false,
            "<gray>Crafted from 4 Poppy Seeds.", "<gray>Cook into Heroin or Oxy."),
    HEROIN("Heroin", "heroin", false, false,
            "<gray>Right-click to use."),
    CRACK("Crack Rock", "crack", false, false,
            "<gray>Right-click to use."),
    OPIUM("Opium", "opium", false, false,
            "<gray>Right-click to use."),
    COUGH_SYRUP("Cough Syrup", "cough_syrup", false, false,
            "<gray>Mix it into Lean."),
    LEAN("Lean", "lean", false, false,
            "<gray>Right-click to sip."),
    ECSTASY("Ecstasy", "ecstasy", false, false,
            "<gray>Right-click to use."),
    KETAMINE("Ketamine", "ketamine", false, false,
            "<gray>Right-click to use."),
    DMT("DMT", "dmt", false, false,
            "<gray>Right-click to use."),
    PEYOTE_SEEDS("Peyote Seeds", "peyote_seeds", false, false,
            "<gray>Plant on sand. Likes deserts."),
    PEYOTE_BUTTON("Peyote Button", "peyote_button", false, false,
            "<gray>Right-click to chew."),
    MESCALINE("Mescaline", "mescaline", false, false,
            "<gray>Right-click to use."),
    ANGEL_DUST("Angel Dust", "angel_dust", false, false,
            "<gray>Right-click to use."),
    // --- 6.0 drugs --------------------------------------------------------
    SHROOM_CHOCOLATE("Shroom Chocolate", "shroom_chocolate", false, false,
            "<gray>Right-click to eat. Kicks in slowly."),
    AYAHUASCA("Ayahuasca", "ayahuasca", false, false,
            "<gray>Right-click to drink. A long trip."),
    SPEED("Speed", "speed", false, false,
            "<gray>Right-click to use."),
    OXY("Oxy Pills", "oxy", false, false,
            "<gray>Right-click to use."),
    XANNY_BARS("Xanny Bars", "xanny_bars", false, false,
            "<gray>Right-click to use."),
    MOONSHINE("Moonshine", "moonshine", false, false,
            "<gray>Right-click to drink."),
    LAUGHING_GAS("Laughing Gas", "laughing_gas", false, false,
            "<gray>Right-click to huff the balloon."),
    // --- supplies -------------------------------------------------------
    ROLLING_PAPERS("Rolling Papers", "rolling_papers", false, false,
            "<gray>For rolling joints."),
    BLUNT_WRAP("Blunt Wrap", "blunt_wrap", false, false,
            "<gray>For rolling blunts."),
    BONG("Bong", "bong", false, false,
            "<gray>Right-click: smoke bud, hash or moon rock."),
    LAB_SOLVENT("Lab Solvent", "lab_solvent", false, false,
            "<gray>Used in some Drug Lab recipes."),
    CATALYST("Catalyst", "catalyst", false, false,
            "<dark_gray>Not needed any more."),
    FERTILIZER("Fertilizer", "fertilizer", false, false,
            "<gray>Right-click a plant: faster, better."),
    GROWER_GUIDE("KushCraft Menu", "grower_guide", false, false,
            "<gray>Right-click to open the menu."),
    // --- workers (right-click the ground to hire them there) -------------
    FARMHAND("Farmhand", "worker_farmhand", false, false,
            "<gray>Harvests and replants your plants.", "<gray>Right-click the ground to hire them there."),
    DRYER("Dryer", "worker_dryer", false, false,
            "<gray>Dries your buds at your Drug Lab.", "<gray>Right-click the ground near a lab to hire them."),
    COOK("Cook", "worker_cook", false, false,
            "<gray>Cooks or rolls the drug you pick.", "<gray>Right-click the ground near a lab to hire them."),
    RUNNER("Runner", "worker_runner", false, false,
            "<gray>Sells your workers' finished product.", "<gray>Right-click the ground to hire them."),
    // --- blocks (placed like a block, punch to pick up) ------------------
    LAB_STATION("Drug Lab", "machine_lab_station", MachineType.LAB_STATION,
            "<gray>Cook, roll, dry and breed."),
    STRAIN_MAKER("Strain Maker", "machine_strain_maker", MachineType.STRAIN_MAKER,
            "<dark_gray>Old block - use the Drug Lab."),
    ROLLING_TABLE("Rolling Table", "machine_rolling_table", MachineType.ROLLING_TABLE,
            "<dark_gray>Old block - use the Drug Lab."),
    DRYING_RACK("Drying Rack", "machine_drying_rack", MachineType.DRYING_RACK,
            "<dark_gray>Old block - use the Drug Lab."),
    GROW_LAMP("Grow Lamp", "machine_grow_lamp", MachineType.GROW_LAMP,
            "<gray>Lights and speeds up nearby plants."),
    PLANTER_BOX("Planter", "machine_planter_box", MachineType.PLANTER_BOX,
            "<gray>Faster growth, better quality."),
    DEALER("Dealer Stand", "machine_dealer", MachineType.DEALER,
            "<gray>Right-click to open the Shop.");

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
        if (this == BONG || this == GROWER_GUIDE || this == VAPE_PEN) {
            return 1;
        }
        return machine != null || this == FARMHAND || this == DRYER || this == COOK || this == RUNNER ? 16 : 64;
    }

    /** Old stations replaced by the Drug Lab: still work, but not sold or crafted any more. */
    public boolean retired() {
        return this == STRAIN_MAKER || this == ROLLING_TABLE || this == DRYING_RACK || this == CATALYST;
    }

    /** Made at the Drug Lab on the way to a drug (coca paste, morphine base...). */
    public boolean ingredient() {
        return switch (this) {
            case ERGOT, ERGOT_EXTRACT, COCA_PASTE, MORPHINE, COUGH_SYRUP -> true;
            default -> false;
        };
    }

    /** Old weed products (6.0 keeps weed to buds, joints, blunts and vape pens): not made any more, still sell. */
    public boolean legacy() {
        return switch (this) {
            case KIEF, HASH, WAX, MOON_ROCK, CANNA_BUTTER, SPACE_BROWNIE, GUMMIES -> true;
            default -> false;
        };
    }

    /** Things players consume for an effect. */
    public boolean isDrug() {
        return switch (this) {
            case JOINT, BLUNT, SPACE_BROWNIE, GUMMIES, WAX, VAPE_PEN, MAGIC_MUSHROOM, SHROOM_TEA, LUCID_TAB, BLUE_CRYSTAL,
                 PIXIE_DUST, COCAINE, HEROIN, CRACK, OPIUM, LEAN, ECSTASY, KETAMINE, DMT, PEYOTE_BUTTON, MESCALINE,
                 ANGEL_DUST, SHROOM_CHOCOLATE, AYAHUASCA, SPEED, OXY, XANNY_BARS, MOONSHINE, LAUGHING_GAS -> true;
            default -> false;
        };
    }

    private static final java.util.Map<String, ItemType> BY_ID = new java.util.HashMap<>();

    static {
        for (ItemType t : values()) {
            BY_ID.put(t.name(), t);
            BY_ID.put(t.name().toLowerCase(Locale.ROOT), t);
        }
    }

    public static ItemType parse(String s) {
        if (s == null) {
            return null;
        }
        ItemType t = BY_ID.get(s);
        if (t != null) {
            return t;
        }
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
