package dev.kushcraft.machines;

import dev.kushcraft.items.ItemType;
import org.bukkit.Material;

import java.util.Locale;

public enum MachineType {
    LAB_STATION("machine_lab_station", Material.IRON_BLOCK),
    STRAIN_MAKER("machine_strain_maker", Material.GLASS),
    ROLLING_TABLE("machine_rolling_table", Material.DARK_OAK_PLANKS),
    DRYING_RACK("machine_drying_rack", Material.BIRCH_PLANKS),
    GROW_LAMP("machine_grow_lamp", Material.BLACK_CONCRETE),
    PLANTER_BOX("machine_planter_box", Material.DARK_OAK_PLANKS),
    DEALER("machine_dealer", Material.SPRUCE_PLANKS);

    private final String model;
    private final Material particle;

    MachineType(String model, Material particle) {
        this.model = model;
        this.particle = particle;
    }

    public String model() {
        return model;
    }

    /** Block whose break particles are shown when the machine is broken. */
    public Material particle() {
        return particle;
    }

    public ItemType item() {
        for (ItemType t : ItemType.values()) {
            if (t.machine() == this) {
                return t;
            }
        }
        throw new IllegalStateException("no item for " + this);
    }

    public static MachineType parse(String s) {
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }
}
