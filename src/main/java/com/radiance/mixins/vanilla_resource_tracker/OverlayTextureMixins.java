package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixins {

    @Redirect(method = "<init>()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;bind()V"))
    public void setImageTargetIDBeforeUpload(DynamicTexture texture) {
        texture.bind();
        NativeImage nativeImage = texture.getPixels();
        ((INativeImageExt) (Object) nativeImage).radiance$setTarget(
            TextureTracker.captureTarget(texture.getId()));
    }
}
