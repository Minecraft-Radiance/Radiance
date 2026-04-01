package com.radiance.client.option;

import static com.radiance.client.option.Options.QUALITY_LEVEL_BALANCED;
import static com.radiance.client.option.Options.QUALITY_LEVEL_EXTREME;
import static com.radiance.client.option.Options.QUALITY_LEVEL_FLUENT;
import static com.radiance.client.option.Options.QUALITY_LEVEL_PERFORMANCE;
import static com.radiance.client.option.Options.QUALITY_LEVEL_QUALITY;
import static com.radiance.client.option.Options.QUALITY_LEVEL_ULTRA;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.TranslatableOption;

public enum QualityLevel implements TranslatableOption, StringIdentifiable {
    FLUENT(5, "fluent", QUALITY_LEVEL_FLUENT),
    PERFORMANCE(0, "performance", QUALITY_LEVEL_PERFORMANCE),
    BALANCED(1, "balanced", QUALITY_LEVEL_BALANCED),
    HIGH(2, "high", QUALITY_LEVEL_QUALITY),
    ULTRA(3, "ultra", QUALITY_LEVEL_ULTRA),
    EXTREME(4, "extreme", QUALITY_LEVEL_EXTREME);

    public static final Codec<QualityLevel> Codec =
        StringIdentifiable.createCodec(QualityLevel::values);

    private final int id;
    private final String name;
    private final String translationKey;

    QualityLevel(int id, String name, String translationKey) {
        this.id = id;
        this.name = name;
        this.translationKey = translationKey;
    }

    public static QualityLevel fromId(int id) {
        for (QualityLevel value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        return BALANCED;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public String getTranslationKey() {
        return translationKey;
    }

    @Override
    public String asString() {
        return name;
    }
}
