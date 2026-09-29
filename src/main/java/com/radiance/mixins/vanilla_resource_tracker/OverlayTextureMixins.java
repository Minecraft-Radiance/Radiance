package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixins {

    @Final
    @Shadow
    private DynamicTexture texture;

    @Redirect(method = "Lnet/minecraft/client/renderer/texture/OverlayTexture;<init>()V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/NativeImage;upload(IIIIIIIZ)V"))
    public void setImageTargetIDBeforeUpload(NativeImage nativeImage, int level,
        int offsetX, int offsetY, int unpackSkipPixels, int unpackSkipRows,
        int width, int height, boolean blur) {
        int id = texture.getId();
        ((INativeImageExt) (Object) nativeImage).radiance$setTarget(TextureTracker.captureTarget(id));
        nativeImage.upload(level, offsetX, offsetY, unpackSkipPixels, unpackSkipRows,
            width, height, blur);
    }
}
