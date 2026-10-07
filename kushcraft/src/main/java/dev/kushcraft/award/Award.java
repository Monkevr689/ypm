package dev.kushcraft.award;

import java.util.Locale;

/**
 * Achievements. Each one is also a vanilla advancement (kush:&lt;id&gt;) in its
 * own "KushCraft" tab, so unlocking one pops the normal advancement toast.
 * icon = item model of the picture (tools/gen_assets.py makes a grey
 * "locked" copy of each, see AWARD_ICONS). parent = the advancement it
 * hangs from in the tree (null = the KushCraft root).
 */
public enum Award {
    // growing
    FIRST_SEED("Green Thumb", "Plant your first seed.", "seed_pack", 50, Frame.TASK, null, false),
    FIRST_HARVEST("Harvest Time", "Harvest a fully grown plant.", "bud_fresh", 100, Frame.TASK, FIRST_SEED, false),
    HARVEST_100("Farmer", "Harvest 100 plants.", "award_farmer", 1000, Frame.GOAL, FIRST_HARVEST, false),
    HARVEST_1000("Plantation", "Harvest 1,000 plants.", "award_plantation", 10000, Frame.CHALLENGE, HARVEST_100, false),
    SHROOMS("Shroom Hunter", "Harvest magic mushrooms.", "magic_mushroom", 150, Frame.TASK, FIRST_HARVEST, false),
    COCA("Coca Farmer", "Harvest a coca bush.", "coca_leaves", 200, Frame.TASK, FIRST_HARVEST, false),
    POPPY("Poppy Fields", "Harvest opium poppies.", "poppy_pod", 200, Frame.TASK, FIRST_HARVEST, false),
    PEYOTE("Desert Bloom", "Harvest a peyote cactus.", "peyote_button", 200, Frame.TASK, FIRST_HARVEST, false),
    IDEAL_CLIMATE("Green Climate", "Harvest a plant grown in its ideal climate.", "award_climate", 200, Frame.TASK, FIRST_HARVEST, false),
    ALL_CLIMATES("World Grower", "Harvest in all 6 climates.", "award_globe", 5000, Frame.GOAL, IDEAL_CLIMATE, false),
    COLLECTOR("Seed Collector", "Grow 10 different strains.", "award_seeds", 2000, Frame.GOAL, FIRST_HARVEST, false),
    FORAGER("Forager", "Pick a wild plant.", "award_forager", 150, Frame.TASK, FIRST_HARVEST, false),
    // the lab
    BUILD_LAB("Breaking Bad", "Place a Drug Lab.", "award_lab", 250, Frame.TASK, null, false),
    FIRST_DRY("Dry Season", "Dry your first buds.", "bud_dried", 100, Frame.TASK, BUILD_LAB, false),
    FIRST_ROLL("Roll One Up", "Roll a joint.", "joint", 100, Frame.TASK, FIRST_DRY, false),
    BLUNT("Blunt Force", "Roll a blunt.", "blunt", 150, Frame.TASK, FIRST_ROLL, false),
    FIRST_COOK("Let Him Cook", "Cook your first batch.", "vape_pen", 200, Frame.TASK, BUILD_LAB, false),
    BLUE_SKY("Blue Sky", "Cook Meth.", "blue_crystal", 1000, Frame.TASK, FIRST_COOK, false),
    COOK_100("Head Chemist", "Cook 100 batches.", "lab_solvent", 5000, Frame.GOAL, FIRST_COOK, false),
    ALL_RECIPES("Mad Chemist", "Cook every Drug Lab recipe.", "award_chemist", 25000, Frame.CHALLENGE, COOK_100, false),
    MAX_LAB("State of the Art", "Upgrade a Drug Lab to the top level.", "award_upgrade", 10000, Frame.GOAL, BUILD_LAB, false),
    FULL_RACKS("Drying Room", "Fill all 5 drying racks at once.", "award_racks", 300, Frame.TASK, FIRST_DRY, false),
    ACID("Acid Test", "Cook LSD from ergot.", "ergot", 500, Frame.TASK, FIRST_COOK, false),
    // workers
    HIRED("Hired Help", "Hire a worker.", "award_worker", 250, Frame.TASK, FIRST_HARVEST, false),
    WORKFORCE("Workforce", "Have 4 workers at once.", "award_workforce", 2500, Frame.GOAL, HIRED, false),
    HEAD_CHEF("Head Chef", "Hire a Cook.", "award_chef", 1000, Frame.GOAL, HIRED, false),
    ASSEMBLY_LINE("Assembly Line", "Have a Farmhand, Dryer, Cook and Runner work together.", "award_chain", 5000,
            Frame.CHALLENGE, HIRED, false),
    SUPPLIER("Fully Stocked", "Hire a Supplier to buy your ingredients.", "award_supplier", 1000, Frame.GOAL, HIRED,
            false),
    LOGISTICS("Logistics", "Have a worker use a chest of yours.", "award_logistics", 300, Frame.TASK, HIRED, false),
    // money
    FIRST_SALE("First Deal", "Sell some product.", "cash", 100, Frame.TASK, null, false),
    SOLD_10K("Hustler", "Sell $10,000 of product.", "award_cash_stack", 500, Frame.TASK, FIRST_SALE, false),
    SOLD_100K("Big Money", "Sell $100,000 of product.", "award_money_bag", 5000, Frame.GOAL, SOLD_10K, false),
    SOLD_1M("Millionaire", "Sell $1,000,000 of product.", "award_gold_bars", 50000, Frame.CHALLENGE, SOLD_100K, false),
    TOP_1("Cartel Boss", "Be the #1 seller on the server.", "award_crown", 10000, Frame.CHALLENGE, SOLD_100K, false),
    ORDER("Special Delivery", "Complete a daily order.", "award_order", 300, Frame.TASK, FIRST_SALE, false),
    ORDERS_25("Trusted Supplier", "Complete 25 daily orders.", "award_orders", 5000, Frame.GOAL, ORDER, false),
    TRADE("Trader", "Buy something in Trade.", "award_trade", 50, Frame.TASK, FIRST_SALE, false),
    DIAMONDS("Diamond Hands", "Buy a diamond in Trade.", "award_diamond", 1000, Frame.GOAL, TRADE, false),
    CARTEL("La Familia", "Start or join a cartel.", "award_cartel", 500, Frame.TASK, FIRST_SALE, false),
    SHIPMENT("Shipped", "Deliver a cartel shipment.", "award_shipment", 2500, Frame.GOAL, CARTEL, false),
    CARTEL_MAX("Empire", "Get your cartel to the top level.", "award_empire", 25000, Frame.CHALLENGE, CARTEL, false),
    // breeding
    FIRST_BREED("Mad Scientist", "Breed a new strain.", "award_dna", 300, Frame.TASK, null, false),
    RARE_STRAIN("Rare Genetics", "Breed an Epic strain or better.", "award_epic", 2000, Frame.GOAL, FIRST_BREED, false),
    LEGENDARY("Legendary Grower", "Breed a Legendary strain.", "award_legendary", 10000, Frame.CHALLENGE, RARE_STRAIN, false),
    JACKPOT("Jackpot!", "Roll a potency jackpot while breeding.", "award_jackpot", 1000, Frame.GOAL, FIRST_BREED, true),
    MYTHIC("Mythical", "Breed a Mythic strain.", "award_mythic", 50000, Frame.CHALLENGE, LEGENDARY, true),
    EXOTIC("Out of This World", "Breed an Exotic strain from two Mythic ones.", "award_exotic", 100000,
            Frame.CHALLENGE, MYTHIC, true),
    // getting high
    FIRST_HIGH("First Puff", "Get high for the first time.", "award_smoke", 50, Frame.TASK, null, false),
    GREEN_OUT("Lightweight", "Green out.", "effect_green_out", 100, Frame.TASK, FIRST_HIGH, true),
    BAD_TRIP("Bad Trip", "Have a bad trip.", "effect_bad_trip", 100, Frame.TASK, FIRST_HIGH, true),
    COCKTAIL("Cocktail", "Feel 6 effects at once.", "effect_trippy", 500, Frame.GOAL, FIRST_HIGH, false),
    PARTY_ANIMAL("Party Animal", "Give an animal a treat... and see its eyes go red.", "award_red_eyes", 150,
            Frame.TASK, FIRST_HIGH, true),
    TRY_10("Connoisseur", "Try 10 different drugs.", "lucid_tab", 1500, Frame.GOAL, FIRST_HIGH, false),
    TRY_ALL("Tried It All", "Try every drug.", "award_rainbow", 10000, Frame.CHALLENGE, TRY_10, false),
    SIGNATURES("Signature Moves", "Feel the signature effect of 10 different drugs.", "award_signature", 2500,
            Frame.GOAL, TRY_10, false);

    public enum Frame {
        TASK, GOAL, CHALLENGE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final String title;
    private final String description;
    private final String icon;
    private final double reward;
    private final Frame frame;
    private final Award parent;
    private final boolean secret;

    Award(String title, String description, String icon, double reward, Frame frame, Award parent, boolean secret) {
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.reward = reward;
        this.frame = frame;
        this.parent = parent;
        this.secret = secret;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    /** Item model (kush:&lt;icon&gt;); the locked look is kush:&lt;icon&gt;_locked. */
    public String icon() {
        return icon;
    }

    /** Cash for unlocking it: 0 unless awards.cash-rewards is on (money only comes from selling drugs). */
    public double reward() {
        dev.kushcraft.KushCraft k = dev.kushcraft.KushCraft.get();
        return k != null && k.getConfig().getBoolean("awards.cash-rewards", false) ? reward : 0;
    }

    public Frame frame() {
        return frame;
    }

    public Award parent() {
        return parent;
    }

    /** Hidden until unlocked (in the menu and the advancement screen). */
    public boolean secret() {
        return secret;
    }

    public String color() {
        return switch (frame) {
            case TASK -> "green";
            case GOAL -> "aqua";
            case CHALLENGE -> "light_purple";
        };
    }
}
