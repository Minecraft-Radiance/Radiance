package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixins {

    @Final
    @Shadow
    private NativeImageBackedTexture texture;

    @Inject(method = "<init>()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/NativeImageBackedTexture;bindTexture()V", shift = At.Shift.AFTER))
    public void setImageTargetIDBeforeUpload(CallbackInfo ci) {
        NativeImage nativeImage = this.texture.getImage();
        ((INativeImageExt) (Object) nativeImage).radiance$setTarget(
            TextureTracker.captureTarget(this.texture.getGlId()));
    }
}
