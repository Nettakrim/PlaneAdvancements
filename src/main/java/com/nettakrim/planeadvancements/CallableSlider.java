package com.nettakrim.planeadvancements;

import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public class CallableSlider extends AbstractSliderButton {
    private final DoubleConsumer consumer;
    private final Supplier<Component> textSupplier;

    public CallableSlider(int x, int y, int width, int height, Supplier<Component> textSupplier, double value, DoubleConsumer consumer) {
        super(x, y, width, height, textSupplier.get(), value);
        this.consumer = consumer;
        this.textSupplier = textSupplier;
    }

    @Override
    protected void updateMessage() {
        setMessage(textSupplier.get());
    }

    @Override
    protected void applyValue() {
        consumer.accept(value);
    }
}
