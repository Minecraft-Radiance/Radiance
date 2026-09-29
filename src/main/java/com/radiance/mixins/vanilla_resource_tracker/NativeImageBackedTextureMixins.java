package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DynamicTexture.class)
public abstract class NativeImageBackedTextureMixins extends AbstractTextureMixins {

    @Shadow
    private NativeImage pixels;

    @Inject(method = "upload()V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;upload(IIIZ)V"))
    public void setTargetIDBeforeUpload(CallbackInfo ci) {
        int id = getId();
        ((INativeImageExt) (Object) pixels).radiance$setTarget(TextureTracker.captureTarget(id));
    }
}
