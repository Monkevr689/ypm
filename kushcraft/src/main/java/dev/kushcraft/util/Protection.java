package dev.kushcraft.util;

import dev.kushcraft.KushCraft;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Asks protection plugins (WorldGuard, GriefPrevention, Towny, ...) whether a
 * player may change a block, by firing a test break event.
 */
public final class Protection {

    private static boolean checking;

    private Protection() {
    }

    /** True while we fire our own test event - our listeners must ignore it. */
    public static boolean isChecking() {
        return checking;
    }

    public static boolean canBuild(Player player, Block block) {
        if (!KushCraft.get().getConfig().getBoolean("growth.respect-protection", true)) {
            return true;
        }
        if (player.hasPermission("kushcraft.admin") && player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return true;
        }
        BlockBreakEvent test = new BlockBreakEvent(block, player);
        test.setDropItems(false);
        test.setExpToDrop(0);
        checking = true;
        try {
            Bukkit.getPluginManager().callEvent(test);
        } finally {
            checking = false;
        }
        return !test.isCancelled();
    }
}
