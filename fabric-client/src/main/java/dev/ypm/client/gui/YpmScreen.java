package dev.ypm.client.gui;

import dev.ypm.client.YpmClient;
import dev.ypm.client.YpmConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** Tabbed settings screen, opened with Right Ctrl. Changes apply immediately and are saved on close. */
public final class YpmScreen extends Screen {
    private enum Tab {
        AIM_ASSIST("Aim Assist"), AUTO_BRIDGE("Auto Bridge"), SPEED("Speed");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final int ROW_H = 20;
    private static final int ROW_GAP = 2;
    private static final int PAD = 8;
    /** Tab bar + module toggle + 7 rows of two-column settings (Aim Assist has the most). */
    private static final int ROWS = 9;

    private static Tab tab = Tab.AIM_ASSIST;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int colW;
    private int row;
    private int col;

    public YpmScreen() {
        super(Component.literal("YPM Client"));
    }

    @Override
    protected void init() {
        panelW = Math.min(340, width - 16);
        panelH = PAD + 12 + ROWS * (ROW_H + ROW_GAP) + 8 + 4 + ROW_H + PAD;
        panelX = (width - panelW) / 2;
        panelY = Math.max(4, (height - panelH) / 2);
        colW = (panelW - PAD * 3) / 2;

        int tabW = (panelW - PAD * 2 - 4) / Tab.values().length;
        int tabY = panelY + PAD + 12;
        for (int i = 0; i < Tab.values().length; i++) {
            Tab t = Tab.values()[i];
            Button b = Button.builder(Component.literal(t.label), btn -> {
                tab = t;
                rebuildWidgets();
            }).bounds(panelX + PAD + i * (tabW + 2), tabY, tabW, ROW_H).build();
            b.active = t != tab;
            addRenderableWidget(b);
        }

        row = 2;
        col = 0;
        YpmConfig c = YpmConfig.INSTANCE;
        switch (tab) {
            case AIM_ASSIST -> {
                YpmConfig.AimAssist a = c.aimAssist;
                moduleToggle("Aim Assist", a.enabled, v -> a.enabled = v);
                slider("Range", 1, 8, 0.1, "%.1f", () -> a.range, v -> a.range = v);
                slider("FOV", 10, 360, 5, "%.0f°", () -> a.fov, v -> a.fov = v);
                slider("Smoothing", 1, 20, 0.5, "%.1f", () -> a.smoothing, v -> a.smoothing = v);
                slider("Max speed", 30, 720, 10, "%.0f°/s", () -> a.maxSpeed, v -> a.maxSpeed = v);
                toggle("Vertical", a.vertical, v -> a.vertical = v);
                toggle("Hold attack", a.requireAttackKey, v -> a.requireAttackKey = v);
                toggle("Sticky target", a.stickyTarget, v -> a.stickyTarget = v);
                toggle("Line of sight", a.requireLineOfSight, v -> a.requireLineOfSight = v);
                toggle("Players", a.targetPlayers, v -> a.targetPlayers = v);
                toggle("Hostiles", a.targetHostiles, v -> a.targetHostiles = v);
                toggle("Passives", a.targetPassives, v -> a.targetPassives = v);
                toggle("Skip invisible", a.ignoreInvisible, v -> a.ignoreInvisible = v);
                toggle("Skip teammates", a.ignoreTeammates, v -> a.ignoreTeammates = v);
            }
            case AUTO_BRIDGE -> {
                YpmConfig.AutoBridge b = c.autoBridge;
                moduleToggle("Auto Bridge", b.enabled, v -> b.enabled = v);
                slider("Place delay", 0, 10, 1, "%.0f ticks", () -> b.placeDelay, v -> b.placeDelay = (int) v);
                slider("Reach", 2, 6, 0.1, "%.1f", () -> b.reach, v -> b.reach = v);
                toggle("Keep Y", b.keepY, v -> b.keepY = v);
                toggle("Predict", b.predict, v -> b.predict = v);
                toggle("Diagonal", b.diagonal, v -> b.diagonal = v);
                toggle("Use off hand", b.useOffhand, v -> b.useOffhand = v);
            }
            case SPEED -> {
                YpmConfig.Speed s = c.speed;
                moduleToggle("Speed", s.enabled, v -> s.enabled = v);
                slider("Multiplier", 1, 3, 0.05, "%.2fx", () -> s.multiplier, v -> s.multiplier = v);
            }
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(panelX + (panelW - 100) / 2, panelY + panelH - PAD - ROW_H, 100, ROW_H).build());
    }

    private int rowY(int r) {
        return panelY + PAD + 12 + r * (ROW_H + ROW_GAP);
    }

    private void moduleToggle(String label, boolean value, Consumer<Boolean> setter) {
        addRenderableWidget(CycleButton.onOffBuilder(value)
                .create(panelX + PAD, rowY(1) + 4, panelW - PAD * 2, ROW_H, Component.literal(label),
                        (btn, v) -> setter.accept(v)));
    }

    private int[] nextCell() {
        int x = panelX + PAD + col * (colW + PAD);
        int y = rowY(row) + 8;
        if (++col == 2) {
            col = 0;
            row++;
        }
        return new int[]{x, y};
    }

    private void toggle(String label, boolean value, Consumer<Boolean> setter) {
        int[] p = nextCell();
        addRenderableWidget(CycleButton.onOffBuilder(value)
                .create(p[0], p[1], colW, ROW_H, Component.literal(label), (btn, v) -> setter.accept(v)));
    }

    private void slider(String label, double min, double max, double step, String format,
                        DoubleSupplier getter, DoubleConsumer setter) {
        int[] p = nextCell();
        addRenderableWidget(new SettingSlider(p[0], p[1], colW, ROW_H, label, min, max, step, format, getter, setter));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xC0101018);
        g.outline(panelX, panelY, panelW, panelH, 0xFFEB4034);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + PAD + 10, 0x60EB4034);
        g.centeredText(font, title, width / 2, panelY + 5, 0xFFFFFFFF);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (YpmClient.openGuiKey().matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        YpmConfig.save();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
