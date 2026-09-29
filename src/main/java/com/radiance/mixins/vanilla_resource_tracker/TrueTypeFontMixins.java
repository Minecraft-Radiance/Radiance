package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ITrueTypeFontExt;
import org.lwjgl.stb.STBTTFontinfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TrueTypeGlyphProvider.class)
public abstract class TrueTypeFontMixins implements ITrueTypeFontExt {

    @Shadow
    abstract STBTTFontinfo validateFontOpen();

    @Shadow
    @Final
    float oversample;

    @Shadow
    @Final
    float shiftX;

    @Shadow
    @Final
    float shiftY;

    @Shadow
    @Final
    float pointScale;

    @Override
    public STBTTFontinfo getFontInfo() {
        return this.validateFontOpen();
    }

    @Override
    public float getOversampleValue() {
        return this.oversample;
    }

    @Override
    public float getShiftXValue() {
        return this.shiftX;
    }

    @Override
    public float getShiftYValue() {
        return this.shiftY;
    }

    @Override
    public float getScaleFactorValue() {
        return this.pointScale;
    }
}
