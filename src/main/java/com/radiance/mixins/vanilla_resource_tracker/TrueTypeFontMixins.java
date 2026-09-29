package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ITrueTypeFontExt;
import net.minecraft.client.font.TrueTypeFont;
import org.lwjgl.stb.STBTTFontinfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TrueTypeFont.class)
public abstract class TrueTypeFontMixins implements ITrueTypeFontExt {

    @Shadow
    abstract STBTTFontinfo getInfo();

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
    float scaleFactor;

    @Override
    public STBTTFontinfo getFontInfo() {
        return this.getInfo();
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
        return this.scaleFactor;
    }
}
