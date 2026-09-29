package com.radiance.client.option;

import static com.radiance.client.option.Options.UPSCALER_QUALITY_BALANCED;
import static com.radiance.client.option.Options.UPSCALER_QUALITY_NATIVEAA;
import static com.radiance.client.option.Options.UPSCALER_QUALITY_PERFORMANCE;
import static com.radiance.client.option.Options.UPSCALER_QUALITY_QUALITY;

import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.TranslatableOption;

public enum UpscalerQuality implements TranslatableOption, StringIdentifiable {
    NATIVEAA(0, "nativeaa", UPSCALER_QUALITY_NATIVEAA),
    QUALITY(1, "quality", UPSCALER_QUALITY_QUALITY),
    BALANCED(2, "balanced", UPSCALER_QUALITY_BALANCED),
    PERFORMANCE(3, "performance", UPSCALER_QUALITY_PERFORMANCE);

    private final int ordinal;
    private final String name;
    private final String translationKey;

    UpscalerQuality(final int ordinal, final String name, final String translationKey) {
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
