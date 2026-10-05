package dev.kushcraft;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Persistent data keys. */
public final class Keys {

    /** Resource pack namespace used by every model / texture. */
    public static final String PACK_NS = "kush";

    public static NamespacedKey ID;
    public static NamespacedKey STRAIN;
    public static NamespacedKey QUALITY;
    public static NamespacedKey HITS;
    public static NamespacedKey PLANT;
    public static NamespacedKey MACHINE;
    public static NamespacedKey VISUAL;
    public static NamespacedKey BALANCE;
    public static NamespacedKey GOT_GUIDE;
    public static NamespacedKey ICON;

    private Keys() {
    }

    static void init(Plugin plugin) {
        ID = new NamespacedKey(plugin, "id");
        STRAIN = new NamespacedKey(plugin, "strain");
        QUALITY = new NamespacedKey(plugin, "quality");
        HITS = new NamespacedKey(plugin, "hits");
        PLANT = new NamespacedKey(plugin, "plant");
        MACHINE = new NamespacedKey(plugin, "machine");
        VISUAL = new NamespacedKey(plugin, "visual");
        BALANCE = new NamespacedKey(plugin, "balance");
        GOT_GUIDE = new NamespacedKey(plugin, "got_guide");
        ICON = new NamespacedKey(plugin, "icon");
    }

    /** kush:&lt;path&gt; - a model / item definition from our resource pack. */
    public static NamespacedKey model(String path) {
        return NamespacedKey.fromString(PACK_NS + ":" + path);
    }
}
