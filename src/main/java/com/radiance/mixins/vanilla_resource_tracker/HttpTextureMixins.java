package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.renderer.texture.HttpTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HttpTexture.class)
public abstract class HttpTextureMixins extends AbstractTextureMixins {

    @Inject(method = "upload(Lcom/mojang/blaze3d/platform/NativeImage;)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/NativeImage;upload(IIIZ)V"))
    private void captureTargetBeforeUpload(NativeImage image, CallbackInfo ci) {
        ((INativeImageExt) (Object) image).radiance$setTarget(
            TextureTracker.captureTarget(getId()));
    }
}
