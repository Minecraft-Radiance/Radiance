package com.radiance.client.option;

import static com.radiance.client.option.Options.DENOISER_MODE_DLSS;
import static com.radiance.client.option.Options.DENOISER_MODE_NRD;
import static com.radiance.client.option.Options.DENOISER_MODE_SVGF;
import static com.radiance.client.option.Options.DENOISER_MODE_TEMPORAL;

import com.mojang.serialization.Codec;
import com.radiance.client.option.compat.RadianceOptionEnum;

public enum DenoiserMode implements RadianceOptionEnum {
    DLSS(0, "dlss", DENOISER_MODE_DLSS),
    SVGF(1, "svgf", DENOISER_MODE_SVGF),
    NRD(2, "nrd", DENOISER_MODE_NRD),
    TEMPORAL(3, "temporal", DENOISER_MODE_TEMPORAL);

    public static final Codec<DenoiserMode> Codec = RadianceOptionEnum.codec(DenoiserMode::values);
    private final int ordinal;
    private final String name;
    private final String translationKey;

    DenoiserMode(final int ordinal, final String name, final String translationKey) {
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
