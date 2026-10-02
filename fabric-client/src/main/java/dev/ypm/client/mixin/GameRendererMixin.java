package dev.ypm.client.mixin;

import dev.ypm.client.YpmClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs aim assist once per rendered frame so rotation stays smooth at any frame rate. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void ypm$onRender(CallbackInfo ci) {
        YpmClient.AIM_ASSIST.onFrame();
    }
}
