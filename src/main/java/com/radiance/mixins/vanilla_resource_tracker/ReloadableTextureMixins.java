package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.ResourceTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResourceTexture.class)
public abstract class ReloadableTextureMixins extends AbstractTextureMixins {

    @Inject(method = "upload(Lnet/minecraft/client/texture/NativeImage;ZZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/NativeImage;upload(IIIIIIIZZZZ)V"))
    public void setTargetIDBeforeUpload(NativeImage image, boolean blur, boolean clamp,
        CallbackInfo ci) {
        int id = getGlId();
        ((INativeImageExt) (Object) image).radiance$setTargetID(id);
    }
}
