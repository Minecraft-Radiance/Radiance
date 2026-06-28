package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import java.util.function.Function;
import net.minecraft.client.font.BakedGlyph;
import net.minecraft.client.font.BitmapFont;
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

    /**
     * @author LJIONG
     * @reason to pass image targetID
     */
    @Overwrite
    public BakedGlyph bake(Function<RenderableGlyph, BakedGlyph> function) {
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
                return ascent;
            }

            @Override
            public void upload(int u, int v) {
                // 这里的反编译有坑！
                // u,v 是写入到目标纹理图集的坐标；x, y 是从字形位图中取像素的起点
                image.upload(0, u, v, x, y, width, height, false);
            }

            @Override
            public void upload(int id, int u, int v) {
                ((INativeImageExt) (Object) image).radiance$setTargetID(id);
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
