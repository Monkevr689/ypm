package dev.kushcraft.strain;

import org.bukkit.block.Block;

/** Rough biome climate, from the biome temperature at a block. */
public enum Climate {
    WARM("Warm", "<gold>"),
    MILD("Mild", "<green>"),
    COLD("Cold", "<aqua>");

    private final String display;
    private final String tag;

    Climate(String display, String tag) {
        this.display = display;
        this.tag = tag;
    }

    public String display() {
        return display;
    }

    public String colored() {
        return tag + display + "</" + tag.substring(1);
    }

    public static Climate of(Block block) {
        return of(block.getTemperature());
    }

    public static Climate of(double temperature) {
        if (temperature >= 0.95) {
            return WARM;
        }
        if (temperature <= 0.35) {
            return COLD;
        }
        return MILD;
    }
}
