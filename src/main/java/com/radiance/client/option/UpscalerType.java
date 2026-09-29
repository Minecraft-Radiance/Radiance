package com.radiance.client.option;

import static com.radiance.client.option.Options.UPSCALER_TYPE_FSR3;
import static com.radiance.client.option.Options.UPSCALER_TYPE_NATIVE;

import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.TranslatableOption;

public enum UpscalerType implements TranslatableOption, StringIdentifiable {
    NATIVE(0, "native", UPSCALER_TYPE_NATIVE),
    FSR3(1, "fsr3", UPSCALER_TYPE_FSR3);

    private final int ordinal;
    private final String name;
    private final String translationKey;

    UpscalerType(final int ordinal, final String name, final String translationKey) {
        this.ordinal = ordinal;
        this.name = name;
        this.translationKey = translationKey;
    }

    @Override
    public String asString() {
        return this.name;
    }

    @Override
    public int getId() {
        return this.ordinal;
    }

    @Override
    public String getTranslationKey() {
        return this.translationKey;
    }
}
