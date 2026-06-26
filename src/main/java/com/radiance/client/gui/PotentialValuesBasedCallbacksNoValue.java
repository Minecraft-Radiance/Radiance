package com.radiance.client.gui;

//#if MC<260200
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.option.SimpleOption.TooltipFactory;

@Environment(EnvType.CLIENT)
public record PotentialValuesBasedCallbacksNoValue<T>(List<T> values, Codec<T> codec) implements
    SimpleOption.CyclingCallbacks<T> {

    @Override
    public Optional<T> validate(T value) {
        return this.values.contains(value) ? Optional.of(value) : Optional.empty();
    }

    @Override
    public CyclingButtonWidget.Values<T> getValues() {
        return CyclingButtonWidget.Values.of(this.values);
    }

    @Override
    public Function<SimpleOption<T>, ClickableWidget> getWidgetCreator(
        TooltipFactory<T> tooltipFactory, GameOptions gameOptions, int x, int y, int width,
        Consumer<T> changeCallback) {
        return option -> CyclingButtonWidget.<T>builder(option.textGetter)
            .values(this.getValues())
            .tooltip(tooltipFactory)
            .initially(option.getValue())
            .omitKeyText()
            .build(x, y, width, 20, option.text, (button, value) -> {
                this.valueSetter().set(option, value);
                gameOptions.write();
                changeCallback.accept(value);
            });
    }
}
//#endif
