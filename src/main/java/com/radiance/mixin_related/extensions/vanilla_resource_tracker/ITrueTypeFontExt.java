package com.radiance.mixin_related.extensions.vanilla_resource_tracker;

import org.lwjgl.stb.STBTTFontinfo;

public interface ITrueTypeFontExt {

    STBTTFontinfo getFontInfo();

    float getOversampleValue();

    float getShiftXValue();

    float getShiftYValue();

    float getScaleFactorValue();
}
