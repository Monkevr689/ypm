package dev.ypm.client.gui;

import dev.ypm.client.YpmClient;
import dev.ypm.client.YpmConfig;
import net.minecraft.ChatFormatting;
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
        AIM("Aim"), TRIGGER("Trigger"), TARGETS("Targets"), BRIDGE("Bridge"), MOVE("Move"), HUD("HUD");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final int ROW_H = 20;
    private static final int ROW_GAP = 2;
    private static final int PAD = 8;
    /** Tab bar + module toggle + 7 rows of two-column settings (Auto Bridge has the most). */
    private static final int ROWS = 9;

    private static Tab tab = Tab.AIM;

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
            Button b = Button.builder(tabLabel(t), btn -> {
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
            case AIM -> {
                YpmConfig.AimAssist a = c.aimAssist;
                moduleToggle("Aim Assist", a.enabled, v -> a.enabled = v);
                slider("Range", 1, 8, 0.1, "%.1f", () -> a.range, v -> a.range = v);
                slider("FOV", 10, 360, 5, "%.0f°", () -> a.fov, v -> a.fov = v);
                slider("Smoothing", 1, 20, 0.5, "%.1f", () -> a.smoothing, v -> a.smoothing = v);
                slider("Max speed", 30, 720, 10, "%.0f°/s", () -> a.maxSpeed, v -> a.maxSpeed = v);
                toggle("Vertical", a.vertical, v -> a.vertical = v);
                toggle("Hold attack", a.requireAttackKey, v -> a.requireAttackKey = v);
                toggle("Sticky target", a.stickyTarget, v -> a.stickyTarget = v);
                toggle("Stop on target", a.stopOnTarget, v -> a.stopOnTarget = v);
                toggle("Weapon only", a.weaponOnly, v -> a.weaponOnly = v);
            }
            case TRIGGER -> {
                YpmConfig.Triggerbot t = c.triggerbot;
                moduleToggle("Triggerbot", t.enabled, v -> t.enabled = v);
                slider("Min cooldown", 0.5, 1, 0.01, "%.0f%%", () -> t.minCooldown, v -> t.minCooldown = v, 100);
                toggle("Weapon only", t.weaponOnly, v -> t.weaponOnly = v);
                toggle("Pause using item", t.pauseWhileUsing, v -> t.pauseWhileUsing = v);
            }
            case TARGETS -> {
                YpmConfig.Targets t = c.targets;
                row = 1; // no module toggle on this tab
                toggle("Players", t.players, v -> t.players = v);
                toggle("Hostiles", t.hostiles, v -> t.hostiles = v);
                toggle("Passives", t.passives, v -> t.passives = v);
                toggle("Skip invisible", t.ignoreInvisible, v -> t.ignoreInvisible = v);
                toggle("Skip teammates", t.ignoreTeammates, v -> t.ignoreTeammates = v);
                toggle("Line of sight", t.requireLineOfSight, v -> t.requireLineOfSight = v);
                cycle("Priority", YpmConfig.Priority.values(), t.priority, v -> t.priority = v);
            }
            case BRIDGE -> {
                YpmConfig.AutoBridge b = c.autoBridge;
                moduleToggle("Auto Bridge", b.enabled, v -> b.enabled = v);
                slider("Place delay", 0, 10, 1, "%.0f ticks", () -> b.placeDelay, v -> b.placeDelay = (int) v);
                slider("Reach", 2, 6, 0.1, "%.1f", () -> b.reach, v -> b.reach = v);
                toggle("Look down only", b.requireLookDown, v -> b.requireLookDown = v);
                slider("Min pitch", 0, 90, 5, "%.0f°", () -> b.minPitch, v -> b.minPitch = v);
                toggle("Keep Y", b.keepY, v -> b.keepY = v);
                toggle("Predict", b.predict, v -> b.predict = v);
                toggle("Diagonal", b.diagonal, v -> b.diagonal = v);
                toggle("Use off hand", b.useOffhand, v -> b.useOffhand = v);
                toggle("Auto switch", b.autoSwitch, v -> b.autoSwitch = v);
                toggle("Sneak at edge", b.sneakAtEdge, v -> b.sneakAtEdge = v);
                toggle("Swing", b.swing, v -> b.swing = v);
            }
            case MOVE -> {
                YpmConfig.Speed s = c.speed;
                moduleToggle("Speed", s.enabled, v -> s.enabled = v);
                slider("Multiplier", 1, 3, 0.05, "%.2fx", () -> s.multiplier, v -> s.multiplier = v);
                toggle("Off while sneaking", s.notWhileSneaking, v -> s.notWhileSneaking = v);
                toggle("Auto Sprint", c.autoSprint.enabled, v -> c.autoSprint.enabled = v);
            }
            case HUD -> {
                YpmConfig.Hud h = c.hud;
                moduleToggle("HUD", h.enabled, v -> h.enabled = v);
                toggle("Module list", h.moduleList, v -> h.moduleList = v);
                toggle("Target info", h.targetInfo, v -> h.targetInfo = v);
                cycle("Corner", YpmConfig.Corner.values(), h.corner, v -> h.corner = v);
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
                        (btn, v) -> {
                            setter.accept(v);
                            rebuildWidgets(); // refresh the tab's on/off colour
                        }));
    }

    private static Component tabLabel(Tab t) {
        YpmConfig c = YpmConfig.INSTANCE;
        Boolean on = switch (t) {
            case AIM -> c.aimAssist.enabled;
            case TRIGGER -> c.triggerbot.enabled;
            case BRIDGE -> c.autoBridge.enabled;
            case MOVE -> c.speed.enabled || c.autoSprint.enabled;
            case HUD -> c.hud.enabled;
            case TARGETS -> null;
        };
        Component label = Component.literal(t.label);
        return on == null ? label : label.copy().withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private <T extends Enum<T>> void cycle(String label, T[] values, T value, Consumer<T> setter) {
        int[] p = nextCell();
        addRenderableWidget(CycleButton.builder((T v) -> Component.literal(prettify(v.name())), value)
                .withValues(values)
                .create(p[0], p[1], colW, ROW_H, Component.literal(label), (btn, v) -> setter.accept(v)));
    }

    private static String prettify(String enumName) {
        String s = enumName.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
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
        slider(label, min, max, step, format, getter, setter, 1);
    }

    private void slider(String label, double min, double max, double step, String format,
                        DoubleSupplier getter, DoubleConsumer setter, double displayScale) {
        int[] p = nextCell();
        addRenderableWidget(new SettingSlider(p[0], p[1], colW, ROW_H, label, min, max, step, format, displayScale, getter, setter));
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
