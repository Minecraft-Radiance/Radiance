package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import java.util.function.Function;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SpecialGlyphs.class)
public abstract class BuiltinEmptyGlyphMixins {

    @Final
    @Shadow
    NativeImage image;





    @Overwrite
    public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> function) {
        return function.apply(new IRenderableGlyphExt() {

            @Override
            public int getPixelWidth() {
                return image.getWidth();
            }

            @Override
            public int getPixelHeight() {
                return image.getHeight();
            }

            @Override
            public float getOversample() {
                return 1.0f;
            }

            @Override
            public void upload(int x, int y) {
                image.upload(0, x, y, false);
            }

            @Override
            public void upload(int id, int x, int y) {
                ((INativeImageExt) (Object) image).radiance$setTarget(TextureTracker.captureTarget(id));
                upload(x, y);
            }

            @Override
            public boolean isColored() {
                return true;
            }
        });
    }
}
