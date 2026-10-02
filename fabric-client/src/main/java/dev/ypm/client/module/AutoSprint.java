package dev.ypm.client.module;

import dev.ypm.client.YpmConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Keeps you sprinting while you move forward, under the same conditions vanilla allows sprinting. */
public final class AutoSprint {
    public void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (!YpmConfig.INSTANCE.autoSprint.enabled || player == null || player.isSprinting()) return;
        boolean canSprint = player.input.hasForwardImpulse()
                && !player.isShiftKeyDown()
                && !player.isUsingItem()
                && !player.isPassenger()
                && (player.getFoodData().getFoodLevel() > 6 || player.getAbilities().mayfly);
        if (canSprint) player.setSprinting(true);
    }
}
