package com.radiance.client.option;

import static com.radiance.client.option.Options.DENOISER_MODE_DLSS;
import static com.radiance.client.option.Options.DENOISER_MODE_NRD;
import static com.radiance.client.option.Options.DENOISER_MODE_SVGF;
import static com.radiance.client.option.Options.DENOISER_MODE_TEMPORAL;

import com.mojang.serialization.Codec;
import net.minecraft.util.OptionEnum;
import net.minecraft.util.StringRepresentable;

public enum DenoiserMode implements OptionEnum, StringRepresentable {
    DLSS(0, "dlss", DENOISER_MODE_DLSS),
    SVGF(1, "svgf", DENOISER_MODE_SVGF),
    NRD(2, "nrd", DENOISER_MODE_NRD),
    TEMPORAL(3, "temporal", DENOISER_MODE_TEMPORAL);

    public static final Codec<DenoiserMode> Codec =
        StringRepresentable.fromEnum(DenoiserMode::values);
    private final int ordinal;
    private final String name;
    private final String translationKey;

    DenoiserMode(final int ordinal, final String name, final String translationKey) {
        this.ordinal = ordinal;
        this.name = name;
        this.translationKey = translationKey;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    @Override
    public int getId() {
        return this.ordinal;
    }

    @Override
    public String getKey() {
        return this.translationKey;
    }
}
