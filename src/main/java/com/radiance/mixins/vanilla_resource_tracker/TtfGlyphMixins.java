package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ITrueTypeFontExt;
import java.util.function.Function;
import net.minecraft.client.font.GlyphRenderer;
import net.minecraft.client.font.RenderableGlyph;
import net.minecraft.client.font.TrueTypeFont;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.stb.STBTTFontinfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TrueTypeFont.TtfGlyph.class)
public class TtfGlyphMixins {

    @Shadow
    @Final
    TrueTypeFont field_2336;

    @Final
    @Shadow
    int width;

    @Final
    @Shadow
    int height;

    @Final
    @Shadow
    float bearingX;

    @Final
    @Shadow
    float ascent;

    @Final
    @Shadow
    int glyphIndex;

    @Final
    @Shadow
    private float advance;





    @Overwrite
    public GlyphRenderer bake(Function<RenderableGlyph, GlyphRenderer> function) {
        return function.apply(new IRenderableGlyphExt() {

            @Override
            public int getWidth() {
                return width;
            }

            @Override
            public int getHeight() {
                return height;
            }

            @Override
            public float getOversample() {
                return ((ITrueTypeFontExt) field_2336).getOversampleValue();
            }

            @Override
            public float getBearingX() {
                return bearingX;
            }

            @Override
            public float getAscent() {
                return ascent;
            }

            @Override
            public void upload(int x, int y) {
                throw new UnsupportedOperationException("Deprecated");
            }

            @Override
            public void upload(int id, int x, int y) {
                NativeImage nativeImage = new NativeImage(NativeImage.Format.LUMINANCE, width,
                    height, false);
                ITrueTypeFontExt font = (ITrueTypeFontExt) field_2336;
                STBTTFontinfo fontInfo = font.getFontInfo();
                nativeImage.makeGlyphBitmapSubpixel(fontInfo, glyphIndex, width, height,
                    font.getScaleFactorValue(), font.getScaleFactorValue(), font.getShiftXValue(),
                    font.getShiftYValue(), 0, 0);
                ((INativeImageExt) (Object) nativeImage).radiance$setTarget(
                    TextureTracker.captureTarget(id));
                nativeImage.upload(0, x, y, 0, 0, width, height, false, false, false, true);
            }

            @Override
            public boolean hasColor() {
                return false;
            }
        });
    }
}
