package dev.smpsuite;

import org.bukkit.NamespacedKey;

/** Persistent data and attribute modifier keys. */
public final class Keys {

    public static NamespacedKey GEM;
    public static NamespacedKey GEM_OWNER;
    public static NamespacedKey GEM_SERIAL;
    public static NamespacedKey PLACED;
    public static NamespacedKey PROJECTILE;
    public static NamespacedKey MOD_MINING;
    public static NamespacedKey MOD_VITALITY;
    public static NamespacedKey MOD_GEM_STRENGTH;

    private Keys() {
    }

    static void init(SMPSuite plugin) {
        GEM = new NamespacedKey(plugin, "gem");
        GEM_OWNER = new NamespacedKey(plugin, "gem_owner");
        GEM_SERIAL = new NamespacedKey(plugin, "gem_serial");
        PLACED = new NamespacedKey(plugin, "placed");
        PROJECTILE = new NamespacedKey(plugin, "projectile");
        MOD_MINING = new NamespacedKey(plugin, "mining_efficiency");
        MOD_VITALITY = new NamespacedKey(plugin, "vitality_hearts");
        MOD_GEM_STRENGTH = new NamespacedKey(plugin, "gem_strength");
    }
}
