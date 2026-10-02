package dev.ypm.client.gui;

import dev.ypm.client.YpmClient;
import dev.ypm.client.YpmConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/** Enabled-module list in a screen corner and a small target readout under the crosshair. */
public final class YpmHud {
    private static final int ACCENT = 0xFFEB4034;

    private YpmHud() {
    }

    public static void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        YpmConfig c = YpmConfig.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        if (!c.hud.enabled || mc.player == null) return;
        Font font = mc.font;

        if (c.hud.moduleList) {
            List<String> lines = new ArrayList<>();
            if (c.aimAssist.enabled) lines.add("Aim Assist");
            if (c.triggerbot.enabled) lines.add("Triggerbot");
            if (c.autoBridge.enabled) lines.add("Auto Bridge");
            if (c.speed.enabled) lines.add(String.format("Speed %.2fx", c.speed.multiplier));
            if (c.autoSprint.enabled) lines.add("Auto Sprint");
            lines.sort((a, b) -> font.width(b) - font.width(a));

            boolean right = c.hud.corner == YpmConfig.Corner.TOP_RIGHT;
            int y = 2;
            for (String line : lines) {
                int w = font.width(line);
                int x = right ? g.guiWidth() - w - 4 : 4;
                g.fill(x - 2, y - 1, x + w + 2, y + font.lineHeight, 0x80000000);
                g.fill(right ? x + w + 2 : x - 3, y - 1, right ? x + w + 3 : x - 2, y + font.lineHeight, ACCENT);
                g.text(font, line, x, y, 0xFFFFFFFF, true);
                y += font.lineHeight + 1;
            }
        }

        if (c.hud.targetInfo) {
            LivingEntity target = YpmClient.AIM_ASSIST.target();
            if (target == null) target = YpmClient.TRIGGERBOT.lastTarget();
            if (target != null && target.isAlive()) {
                String text = target.getName().getString() + "  " + String.format("%.1f", target.getHealth())
                        + "/" + String.format("%.0f", target.getMaxHealth()) + " ❤";
                int w = font.width(text);
                int x = (g.guiWidth() - w) / 2;
                int y = g.guiHeight() / 2 + 12;
                g.fill(x - 3, y - 2, x + w + 3, y + font.lineHeight + 1, 0x80000000);
                float hp = Math.max(0f, Math.min(1f, target.getHealth() / target.getMaxHealth()));
                g.fill(x - 3, y + font.lineHeight, x - 3 + (int) ((w + 6) * hp), y + font.lineHeight + 1, ACCENT);
                g.text(font, text, x, y, 0xFFFFFFFF, true);
            }
        }
    }
}
