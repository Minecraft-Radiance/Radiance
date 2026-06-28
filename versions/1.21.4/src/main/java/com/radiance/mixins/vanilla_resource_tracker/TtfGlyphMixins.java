package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import java.util.function.Function;
import net.minecraft.client.font.BakedGlyph;
import net.minecraft.client.font.RenderableGlyph;
import net.minecraft.client.font.TrueTypeFont;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.util.freetype.FT_Face;
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

    /**
     * @author LJIONG
     * @reason to pass image targetID
     */
    @Overwrite
    public BakedGlyph bake(Function<RenderableGlyph, BakedGlyph> function) {
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
                return field_2336.oversample;
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
                FT_Face fT_Face = field_2336.getInfo();
                if (nativeImage.makeGlyphBitmapSubpixel(fT_Face, glyphIndex)) {
                    ((INativeImageExt) (Object) nativeImage).radiance$setTargetID(id);
                    nativeImage.upload(0, x, y, 0, 0, width, height, true);
                } else {
                    nativeImage.close();
                }
            }

            @Override
            public boolean hasColor() {
                return false;
            }
        });
    }
}
