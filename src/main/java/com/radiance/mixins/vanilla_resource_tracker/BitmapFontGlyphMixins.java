package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import java.util.function.Function;
import net.minecraft.client.font.BitmapFont;
import net.minecraft.client.font.GlyphRenderer;
import net.minecraft.client.font.RenderableGlyph;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BitmapFont.BitmapFontGlyph.class)
public abstract class BitmapFontGlyphMixins {

    @Shadow
    @Final
    float scaleFactor;

    @Shadow
    @Final
    int x;

    @Shadow
    @Final
    int y;

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
    public GlyphRenderer bake(Function<RenderableGlyph, GlyphRenderer> function) {
        return function.apply(new IRenderableGlyphExt() {
            @Override
            public float getOversample() {
                return 1.0f / scaleFactor;
            }

            @Override
            public int getWidth() {
                return width;
            }

            @Override
            public int getHeight() {
                return height;
            }

            @Override
            public float getAscent() {
                return 10.0F - ascent;
            }

            @Override
            public void upload(int u, int v) {


                image.upload(0, u, v, x, y, width, height, false, false, false, false);
            }

            @Override
            public void upload(int id, int u, int v) {
                ((INativeImageExt) (Object) image).radiance$setTarget(TextureTracker.captureTarget(id));
                upload(u, v);
            }

            @Override
            public boolean hasColor() {
                return image.getFormat()
                    .getChannelCount() > 1;
            }
        });
    }
}
