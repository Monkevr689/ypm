package dev.ypm.client.mixin;

import dev.ypm.client.YpmConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scales the local player's ground movement speed when the Speed module is on. */
@Mixin(Player.class)
public abstract class PlayerSpeedMixin {
    @Inject(method = "getSpeed", at = @At("RETURN"), cancellable = true)
    private void ypm$scaleSpeed(CallbackInfoReturnable<Float> cir) {
        YpmConfig.Speed cfg = YpmConfig.INSTANCE.speed;
        Player self = (Player) (Object) this;
        if (!cfg.enabled || self != Minecraft.getInstance().player) return;
        if (cfg.notWhileSneaking && self.isShiftKeyDown()) return;
        cir.setReturnValue((float) (cir.getReturnValueF() * cfg.multiplier));
    }
}
