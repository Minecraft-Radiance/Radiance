package com.radiance.client.option;

import static com.radiance.client.option.Options.DENOISER_MODE_DLSS;
import static com.radiance.client.option.Options.DENOISER_MODE_NRD;
import static com.radiance.client.option.Options.DENOISER_MODE_SVGF;
import static com.radiance.client.option.Options.DENOISER_MODE_TEMPORAL;

import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.TranslatableOption;

public enum DenoiserMode implements TranslatableOption, StringIdentifiable {
    DLSS(0, "dlss", DENOISER_MODE_DLSS),
    SVGF(1, "svgf", DENOISER_MODE_SVGF),
    NRD(2, "nrd", DENOISER_MODE_NRD),
    TEMPORAL(3, "temporal", DENOISER_MODE_TEMPORAL);

    private final int ordinal;
    private final String name;
    private final String translationKey;

    DenoiserMode(final int ordinal, final String name, final String translationKey) {
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
