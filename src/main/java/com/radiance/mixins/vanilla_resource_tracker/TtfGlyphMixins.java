package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import java.util.function.Function;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.lwjgl.util.freetype.FT_Face;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TrueTypeGlyphProvider.Glyph.class)
public class TtfGlyphMixins {

    @Shadow
    @Final
    TrueTypeGlyphProvider this$0;

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
    float bearingY;

    @Final
    @Shadow
    int index;

    @Final
    @Shadow
    private float advance;





    @Overwrite
    public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> function) {
        return function.apply(new IRenderableGlyphExt() {

            @Override
            public int getPixelWidth() {
                return width;
            }

            @Override
            public int getPixelHeight() {
                return height;
            }

            @Override
            public float getOversample() {
                return this$0.oversample;
            }

            @Override
            public float getBearingLeft() {
                return bearingX;
            }

            @Override
            public float getBearingTop() {
                return bearingY;
            }

            @Override
            public void upload(int x, int y) {
                throw new UnsupportedOperationException("Deprecated");
            }

            @Override
            public void upload(int id, int x, int y) {
                NativeImage nativeImage = new NativeImage(NativeImage.Format.LUMINANCE, width,
                    height, false);
                FT_Face fT_Face = this$0.validateFontOpen();
                if (nativeImage.copyFromFont(fT_Face, index)) {
                    ((INativeImageExt) (Object) nativeImage).radiance$setTarget(
                        TextureTracker.captureTarget(id));
                    nativeImage.upload(0, x, y, 0, 0, width, height, false, false, false,
                        true);
                } else {
                    nativeImage.close();
                }
            }

            @Override
            public boolean isColored() {
                return false;
            }
        });
    }
}
