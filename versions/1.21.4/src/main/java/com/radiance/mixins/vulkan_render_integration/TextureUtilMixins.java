package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.platform.TextureUtil;
import com.radiance.client.proxy.vulkan.TextureProxy;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextureUtil.class)
public class TextureUtilMixins {

    @Inject(method = "generateTextureId()I", at = @At(value = "HEAD"), cancellable = true, remap = false)
    private static void redirectGenerateTextureId(CallbackInfoReturnable<Integer> cir) {
        int textureId = TextureProxy.generateTextureId();
        cir.setReturnValue(textureId);
    }

    @Inject(method = "prepareImage(Lnet/minecraft/client/texture/NativeImage$InternalFormat;IIII)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER,
            remap = false),
        cancellable = true)
    private static void redirectPrepareImage(NativeImage.InternalFormat internalFormat,
        int id,
        int maxLevel,
        int width,
        int height,
        CallbackInfo ci) {
        TextureProxy.prepareImage(internalFormat, id, maxLevel + 1, width, height);
        ci.cancel();
    }
}
