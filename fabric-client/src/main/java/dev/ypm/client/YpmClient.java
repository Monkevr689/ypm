package dev.ypm.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ypm.client.module.AimAssist;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class YpmClient implements ClientModInitializer {
    public static final String MOD_ID = "ypm";
    public static final Logger LOGGER = LoggerFactory.getLogger("YPM Client");

    public static final AimAssist AIM_ASSIST = new AimAssist();

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "ypm"));

    private static KeyMapping toggleAimAssist;

    @Override
    public void onInitializeClient() {
        YpmConfig.load();
        AIM_ASSIST.setEnabled(YpmConfig.INSTANCE.aimAssist.enabled);

        toggleAimAssist = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ypm.toggle_aim_assist", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(YpmClient::onEndTick);
        LOGGER.info("YPM Client loaded");
    }

    private static void onEndTick(Minecraft mc) {
        while (toggleAimAssist.consumeClick()) {
            boolean enable = !AIM_ASSIST.isEnabled();
            // Re-read the config on every enable so edits to the file apply without a restart.
            if (enable) YpmConfig.load();
            AIM_ASSIST.setEnabled(enable);
            notify(mc, "Aim Assist", enable);
        }
    }

    private static void notify(Minecraft mc, String feature, boolean on) {
        if (mc.player == null) return;
        mc.player.sendOverlayMessage(Component.literal("[YPM] " + feature + ": ")
                .append(Component.literal(on ? "ON" : "OFF")
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)));
    }
}
