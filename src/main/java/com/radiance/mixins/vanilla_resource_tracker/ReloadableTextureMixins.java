package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.renderer.texture.SimpleTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SimpleTexture.class)
public abstract class ReloadableTextureMixins extends AbstractTextureMixins {

    @Inject(method = "doLoad(Lcom/mojang/blaze3d/platform/NativeImage;ZZ)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;upload(IIIIIIIZZZZ)V"))
    public void setTargetIDBeforeUpload(NativeImage image, boolean blur, boolean clamp,
        CallbackInfo ci) {
        int id = getId();
        ((INativeImageExt) (Object) image).radiance$setTarget(TextureTracker.captureTarget(id));
    }
}
