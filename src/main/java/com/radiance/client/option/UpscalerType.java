package com.radiance.client.option;

import static com.radiance.client.option.Options.UPSCALER_TYPE_FSR3;
import static com.radiance.client.option.Options.UPSCALER_TYPE_NATIVE;

import com.mojang.serialization.Codec;
import com.radiance.client.option.compat.RadianceOptionEnum;

public enum UpscalerType implements RadianceOptionEnum {
    NATIVE(0, "native", UPSCALER_TYPE_NATIVE),
    FSR3(1, "fsr3", UPSCALER_TYPE_FSR3);

    public static final Codec<UpscalerType> Codec = RadianceOptionEnum.codec(UpscalerType::values);
    private final int ordinal;
    private final String name;
    private final String translationKey;

    UpscalerType(final int ordinal, final String name, final String translationKey) {
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
