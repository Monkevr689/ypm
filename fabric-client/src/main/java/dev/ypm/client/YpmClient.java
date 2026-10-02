package dev.ypm.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ypm.client.module.AimAssist;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class YpmClient implements ClientModInitializer {
    public static final String MOD_ID = "ypm";
    public static final Logger LOGGER = LoggerFactory.getLogger("YPM Client");

    public static final AimAssist AIM_ASSIST = new AimAssist();

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "ypm"));

    private static KeyMapping toggleAimAssist;
    private static KeyMapping reloadConfig;

    @Override
    public void onInitializeClient() {
        YpmConfig.load();
        AIM_ASSIST.setEnabled(YpmConfig.INSTANCE.aimAssist.enabled);

        toggleAimAssist = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.ypm.toggle_aim_assist", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY));
        reloadConfig = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.ypm.reload_config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(YpmClient::onEndTick);
        LOGGER.info("YPM Client loaded");
    }

    private static void onEndTick(Minecraft mc) {
        while (toggleAimAssist.consumeClick()) {
            AIM_ASSIST.setEnabled(!AIM_ASSIST.isEnabled());
            notify(mc, "Aim Assist", AIM_ASSIST.isEnabled());
        }
        while (reloadConfig.consumeClick()) {
            YpmConfig.load();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("[YPM] Config reloaded").withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    private static void notify(Minecraft mc, String feature, boolean on) {
        if (mc.player == null) return;
        mc.player.displayClientMessage(Component.literal("[YPM] " + feature + ": ")
                .append(Component.literal(on ? "ON" : "OFF")
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
    }
}
