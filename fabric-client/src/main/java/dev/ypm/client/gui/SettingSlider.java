package dev.ypm.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** A slider bound to a numeric setting, snapping to {@code step}. */
final class SettingSlider extends AbstractSliderButton {
    private final String label;
    private final double min;
    private final double max;
    private final double step;
    private final String format;
    private final double displayScale;
    private final DoubleConsumer setter;

    SettingSlider(int x, int y, int width, int height, String label, double min, double max, double step,
                  String format, double displayScale, DoubleSupplier getter, DoubleConsumer setter) {
        super(x, y, width, height, Component.empty(), Mth.clamp((getter.getAsDouble() - min) / (max - min), 0.0, 1.0));
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.format = format;
        this.displayScale = displayScale;
        this.setter = setter;
        updateMessage();
    }

    private double current() {
        double raw = min + value * (max - min);
        return Mth.clamp(Math.round(raw / step) * step, min, max);
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(label + ": " + String.format(format, current() * displayScale)));
    }

    @Override
    protected void applyValue() {
        setter.accept(current());
    }
}
