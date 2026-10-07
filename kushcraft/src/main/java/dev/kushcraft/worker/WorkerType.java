package dev.kushcraft.worker;

import dev.kushcraft.item.ItemType;
import org.bukkit.Material;

import java.util.Locale;

/**
 * The people you can hire in the Shop. Each one has a skin and a hat from
 * the resource pack (tools/workers.py) and holds a vanilla tool.
 */
public enum WorkerType {
    FARMHAND("Farmhand", "<green>", ItemType.FARMHAND, Material.IRON_HOE,
            "Harvests your ripe plants and plants them again.",
            "Hands the harvest to your Runner (or the Dryer)."),
    DRYER("Dryer", "<gold>", ItemType.DRYER, Material.SHEARS,
            "Dries fresh buds on your Drug Lab racks.",
            "Gets fresh buds from a Runner; dried ones go on to sale."),
    COOK("Cook", "<aqua>", ItemType.COOK, Material.GLASS_BOTTLE,
            "Cooks your pick at the Drug Lab, rolls, or mixes strains.",
            "Gets what they need from your other workers."),
    RUNNER("Runner", "<light_purple>", ItemType.RUNNER, Material.BUNDLE,
            "Carries work between your workers and sells it on the spot.",
            "Keeps a small cut. Gets through walls the back way."),
    SUPPLIER("Supplier", "<yellow>", ItemType.SUPPLIER, Material.WRITABLE_BOOK,
            "Buys whatever your workers are short of, with your money.",
            "No budget: they stop only when you can't pay.");

    private final String display;
    private final String color;
    private final ItemType item;
    private final Material tool;
    private final String job;
    private final String tip;

    WorkerType(String display, String color, ItemType item, Material tool, String job, String tip) {
        this.display = display;
        this.color = color;
        this.item = item;
        this.tool = tool;
        this.job = job;
        this.tip = tip;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String display() {
        return display;
    }

    /** MiniMessage colour tag. */
    public String color() {
        return color;
    }

    public String colored() {
        return color + display;
    }

    public ItemType item() {
        return item;
    }

    public Material tool() {
        return tool;
    }

    /** What they do, one line. */
    public String job() {
        return job;
    }

    public String tip() {
        return tip;
    }

    /** Skin texture in the resource pack: assets/kush/textures/entity/worker/&lt;id&gt;.png */
    public String skin() {
        return "entity/worker/" + id();
    }

    /** 3D hat item model: kush:worker_hat_&lt;id&gt; */
    public String hat() {
        return "worker_hat_" + id();
    }

    public static WorkerType of(ItemType t) {
        for (WorkerType w : values()) {
            if (w.item == t) {
                return w;
            }
        }
        return null;
    }

    public static WorkerType parse(String s) {
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }
}
