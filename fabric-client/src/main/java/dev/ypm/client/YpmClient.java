package dev.ypm.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ypm.client.gui.YpmScreen;
import dev.ypm.client.module.AimAssist;
import dev.ypm.client.gui.YpmHud;
import dev.ypm.client.module.AutoBridge;
import dev.ypm.client.module.AutoSprint;
import dev.ypm.client.module.Triggerbot;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class YpmClient implements ClientModInitializer {
    public static final String MOD_ID = "ypm";
    public static final Logger LOGGER = LoggerFactory.getLogger("YPM Client");

    public static final AimAssist AIM_ASSIST = new AimAssist();
    public static final AutoBridge AUTO_BRIDGE = new AutoBridge();
    public static final Triggerbot TRIGGERBOT = new Triggerbot();
    public static final AutoSprint AUTO_SPRINT = new AutoSprint();

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "ypm"));

    private static KeyMapping openGui;
    private static KeyMapping toggleAimAssist;
    private static KeyMapping toggleAutoBridge;
    private static KeyMapping toggleSpeed;
    private static KeyMapping toggleTriggerbot;
    private static KeyMapping toggleAutoSprint;

    @Override
    public void onInitializeClient() {
        YpmConfig.load();

        openGui = register("open_gui", InputConstants.KEY_RCONTROL);
        toggleAimAssist = register("toggle_aim_assist", InputConstants.KEY_R);
        toggleAutoBridge = register("toggle_auto_bridge", InputConstants.UNKNOWN.getValue());
        toggleSpeed = register("toggle_speed", InputConstants.UNKNOWN.getValue());
        toggleTriggerbot = register("toggle_triggerbot", InputConstants.UNKNOWN.getValue());
        toggleAutoSprint = register("toggle_auto_sprint", InputConstants.UNKNOWN.getValue());

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), YpmHud::extractRenderState);

        ClientTickEvents.END_CLIENT_TICK.register(YpmClient::onEndTick);
        LOGGER.info("YPM Client loaded");
    }

    public static KeyMapping openGuiKey() {
        return openGui;
    }

    private static KeyMapping register(String name, int key) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.ypm." + name, InputConstants.Type.KEYBOARD, key, CATEGORY));
    }

    private static void onEndTick(Minecraft mc) {
        YpmConfig c = YpmConfig.INSTANCE;
        while (openGui.consumeClick()) {
            mc.setScreenAndShow(new YpmScreen());
        }
        handleToggle(mc, toggleAimAssist, "Aim Assist", () -> c.aimAssist.enabled, v -> c.aimAssist.enabled = v);
        handleToggle(mc, toggleAutoBridge, "Auto Bridge", () -> c.autoBridge.enabled, v -> c.autoBridge.enabled = v);
        handleToggle(mc, toggleSpeed, "Speed", () -> c.speed.enabled, v -> c.speed.enabled = v);
        handleToggle(mc, toggleTriggerbot, "Triggerbot", () -> c.triggerbot.enabled, v -> c.triggerbot.enabled = v);
        handleToggle(mc, toggleAutoSprint, "Auto Sprint", () -> c.autoSprint.enabled, v -> c.autoSprint.enabled = v);

        AUTO_SPRINT.onTick(mc);
        AUTO_BRIDGE.onTick(mc);
        TRIGGERBOT.onTick(mc);
    }

    private static void handleToggle(Minecraft mc, KeyMapping key, String name, Supplier<Boolean> get, Consumer<Boolean> set) {
        boolean changed = false;
        while (key.consumeClick()) {
            set.accept(!get.get());
            changed = true;
        }
        if (!changed) return;
        YpmConfig.save();
        boolean on = get.get();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal("[YPM] " + name + ": ")
                    .append(Component.literal(on ? "ON" : "OFF")
                            .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }
    }
}
