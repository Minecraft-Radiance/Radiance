package com.radiance.mixins.opengl_ui;

import com.mojang.blaze3d.platform.TextureUtil;
import com.radiance.client.proxy.vulkan.TextureProxy;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureUtil.class)
public class OpenGLUiTextureUtilMixins {

    @Inject(method = "prepareImage(Lnet/minecraft/client/texture/NativeImage$InternalFormat;IIII)V",
        at = @At(value = "TAIL"))
    private static void prepareNativeImage(NativeImage.InternalFormat internalFormat,
        int id,
        int maxLevel,
        int width,
        int height,
        CallbackInfo ci) {
        TextureProxy.prepareImage(internalFormat, id, maxLevel + 1, width, height);
    }
}
