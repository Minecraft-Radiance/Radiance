package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteContentsExt;
import com.radiance.client.texture.TextureTracker.TextureTarget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.class)
public class SpriteContentsMixins implements ISpriteContentsExt {

    @Shadow
    @Final
    private NativeImage image;

    @Unique
    private volatile boolean radiance$hasAverageColor;

    @Unique
    private int radiance$averageColor;

    @Unique
    private TextureTarget target;

    @Override
    public TextureTarget radiance$getTarget() {
        return target;
    }

    @Override
    public void radiance$setTarget(TextureTarget target) {
        this.target = target;
    }

    @Override
    public int radiance$getAverageColor() {
        if (!radiance$hasAverageColor) {
            synchronized (this) {
                if (!radiance$hasAverageColor) {
                    long red = 0, green = 0, blue = 0, weight = 0;
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            int color = image.getColor(x, y);
                            int alpha = color >>> 24;
                            red += (color & 255) * alpha;
                            green += ((color >>> 8) & 255) * alpha;
                            blue += ((color >>> 16) & 255) * alpha;
                            weight += alpha;
                        }
                    }
                    int alpha = (int) (weight / ((long) image.getWidth() * image.getHeight()));
                    radiance$averageColor = weight == 0 ? 0 : (alpha << 24)
                        | (int) (red / weight) | ((int) (green / weight) << 8)
                        | ((int) (blue / weight) << 16);
                    radiance$hasAverageColor = true;
                }
            }
        }
        return radiance$averageColor;
    }

    @Inject(method = "upload(IIII[Lnet/minecraft/client/texture/NativeImage;)V",
        at = @At(value = "HEAD"))
    public void setImageTargetIDBeforeUpload(int x,
        int y,
        int unpackSkipPixels,
        int unpackSkipRows,
        NativeImage[] images,
        CallbackInfo ci) {
        for (NativeImage image : images) {
            if (image != null) {
                ((INativeImageExt) (Object) image).radiance$setTarget(this.target);
            }
        }
    }
}
