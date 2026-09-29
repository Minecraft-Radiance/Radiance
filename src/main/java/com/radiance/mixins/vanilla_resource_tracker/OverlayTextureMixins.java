package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixins {

    @Redirect(method = "<init>()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/texture/NativeImageBackedTexture;bindTexture()V"))
    public void setImageTargetIDBeforeUpload(NativeImageBackedTexture texture) {
        texture.bindTexture();
        NativeImage nativeImage = texture.getImage();
        ((INativeImageExt) (Object) nativeImage).radiance$setTarget(
            TextureTracker.captureTarget(texture.getGlId()));
    }
}
