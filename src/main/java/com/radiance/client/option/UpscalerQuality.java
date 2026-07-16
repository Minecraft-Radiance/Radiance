package com.radiance.client.option;

import static com.radiance.client.option.Options.UPSCALER_QUALITY_BALANCED;
import static com.radiance.client.option.Options.UPSCALER_QUALITY_NATIVEAA;
import static com.radiance.client.option.Options.UPSCALER_QUALITY_PERFORMANCE;
import static com.radiance.client.option.Options.UPSCALER_QUALITY_QUALITY;

import com.mojang.serialization.Codec;
import com.radiance.client.option.compat.RadianceOptionEnum;

public enum UpscalerQuality implements RadianceOptionEnum {
    NATIVEAA(0, "nativeaa", UPSCALER_QUALITY_NATIVEAA),
    QUALITY(1, "quality", UPSCALER_QUALITY_QUALITY),
    BALANCED(2, "balanced", UPSCALER_QUALITY_BALANCED),
    PERFORMANCE(3, "performance", UPSCALER_QUALITY_PERFORMANCE);

    public static final Codec<UpscalerQuality> Codec =
        RadianceOptionEnum.codec(UpscalerQuality::values);
    private final int ordinal;
    private final String name;
    private final String translationKey;

    UpscalerQuality(final int ordinal, final String name, final String translationKey) {
        this.ordinal = ordinal;
        this.name = name;
        this.translationKey = translationKey;
    }

    public String serializedName() {
        return this.name;
    }

    public int optionId() {
        return this.ordinal;
    }

    public String translationKey() {
        return this.translationKey;
    }
}
