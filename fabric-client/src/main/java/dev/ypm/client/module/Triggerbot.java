package dev.ypm.client.module;

import dev.ypm.client.YpmConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Attacks the entity under the crosshair once the attack cooldown has recharged. */
public final class Triggerbot {
    private LivingEntity lastTarget;

    public LivingEntity lastTarget() {
        return lastTarget;
    }

    public void onTick(Minecraft mc) {
        YpmConfig.Triggerbot cfg = YpmConfig.INSTANCE.triggerbot;
        LocalPlayer player = mc.player;
        if (!cfg.enabled || player == null || mc.gameMode == null || !mc.mouseHandler.isMouseGrabbed()
                || player.isSpectator() || (cfg.pauseWhileUsing && player.isUsingItem())
                || (cfg.weaponOnly && !TargetFilter.holdingWeapon(player))) {
            lastTarget = null;
            return;
        }

        Entity hovered = mc.crosshairPickEntity;
        if (!TargetFilter.accepts(player, hovered, YpmConfig.INSTANCE.targets)) return;
        if (player.getAttackStrengthScale(0.5f) < cfg.minCooldown) return;

        mc.gameMode.attack(player, hovered);
        Swing.swing(player, InteractionHand.MAIN_HAND);
        lastTarget = (LivingEntity) hovered;
    }
}
