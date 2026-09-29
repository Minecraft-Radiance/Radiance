package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import java.util.function.Function;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BitmapProvider.Glyph.class)
public abstract class BitmapFontGlyphMixins {

    @Shadow
    @Final
    float scale;

    @Shadow
    @Final
    int offsetX;

    @Shadow
    @Final
    int offsetY;

    @Shadow
    @Final
    int width;

    @Shadow
    @Final
    int height;

    @Shadow
    @Final
    int ascent;

    @Shadow
    @Final
    NativeImage image;





    @Overwrite
    public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> function) {
        return function.apply(new IRenderableGlyphExt() {
            @Override
            public float getOversample() {
                return 1.0f / scale;
            }

            @Override
            public int getPixelWidth() {
                return width;
            }

            @Override
            public int getPixelHeight() {
                return height;
            }

            @Override
            public float getBearingTop() {
                return ascent;
            }

            @Override
            public void upload(int u, int v) {


                image.upload(0, u, v, offsetX, offsetY, width, height, false);
            }

            @Override
            public void upload(int id, int u, int v) {
                ((INativeImageExt) (Object) image).radiance$setTarget(TextureTracker.captureTarget(id));
                upload(u, v);
            }

            @Override
            public boolean isColored() {
                return image.format()
                    .components() > 1;
            }
        });
    }
}
