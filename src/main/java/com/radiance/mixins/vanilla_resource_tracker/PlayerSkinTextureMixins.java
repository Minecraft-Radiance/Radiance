package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerSkinTexture.class)
public abstract class PlayerSkinTextureMixins extends AbstractTextureMixins {

    @Inject(method = "uploadTexture(Lnet/minecraft/client/texture/NativeImage;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/texture/NativeImage;upload(IIIZ)V"))
    private void captureTargetBeforeUpload(NativeImage image, CallbackInfo ci) {
        ((INativeImageExt) (Object) image).radiance$setTarget(
            TextureTracker.captureTarget(getGlId()));
    }
}
