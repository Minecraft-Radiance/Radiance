package com.radiance.client.option;

import static com.radiance.client.option.Options.DLSS_MODE_BALANCED;
import static com.radiance.client.option.Options.DLSS_MODE_DLAA;
import static com.radiance.client.option.Options.DLSS_MODE_PERFORMANCE;
import static com.radiance.client.option.Options.DLSS_MODE_QUALITY;
import static com.radiance.client.option.Options.DLSS_MODE_ULTRA_PERFORMANCE;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.TranslatableOption;

public enum DLSSMode implements TranslatableOption, StringIdentifiable {
    ULTRA_PERFORMANCE(0, "ultra_performance", DLSS_MODE_ULTRA_PERFORMANCE),
    PERFORMANCE(1, "performance", DLSS_MODE_PERFORMANCE),
    BALANCED(2, "balanced", DLSS_MODE_BALANCED),
    QUALITY(3, "quality", DLSS_MODE_QUALITY),
    DLAA(4, "dlaa", DLSS_MODE_DLAA);

    public static final Codec<DLSSMode> Codec = StringIdentifiable.createCodec(DLSSMode::values);
    private final int ordinal;
    private final String name;
    private final String translationKey;

    DLSSMode(final int ordinal, final String name, final String translationKey) {
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
