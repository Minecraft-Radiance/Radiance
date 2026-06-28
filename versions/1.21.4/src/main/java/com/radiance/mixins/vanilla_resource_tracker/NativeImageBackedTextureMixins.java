package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NativeImageBackedTexture.class)
public abstract class NativeImageBackedTextureMixins extends AbstractTextureMixins {

    @Shadow
    private NativeImage image;

    @Inject(method = "upload()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/NativeImage;upload(IIIZ)V"))
    public void setTargetIDBeforeUpload(CallbackInfo ci) {
        int id = getGlId();
        ((INativeImageExt) (Object) image).radiance$setTargetID(id);
    }
}
