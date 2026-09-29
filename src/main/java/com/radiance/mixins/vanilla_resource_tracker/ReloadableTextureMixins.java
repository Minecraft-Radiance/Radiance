package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReloadableTexture.class)
public abstract class ReloadableTextureMixins extends AbstractTextureMixins {

    @Inject(method = "Lnet/minecraft/client/renderer/texture/ReloadableTexture;doLoad(Lcom/mojang/blaze3d/platform/NativeImage;ZZ)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;upload(IIIIIIIZ)V"))
    public void setTargetIDBeforeUpload(NativeImage image, boolean blur, boolean clamp,
        CallbackInfo ci) {
        int id = getId();
        ((INativeImageExt) (Object) image).radiance$setTarget(TextureTracker.captureTarget(id));
    }
}
