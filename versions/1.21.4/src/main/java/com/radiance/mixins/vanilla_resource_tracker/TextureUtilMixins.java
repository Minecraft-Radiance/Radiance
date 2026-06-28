package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.platform.TextureUtil;
import com.radiance.client.texture.TextureTracker;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureUtil.class)
public abstract class TextureUtilMixins {

    @Inject(method = "prepareImage(Lnet/minecraft/client/texture/NativeImage$InternalFormat;IIII)V", at = @At("HEAD"))
    private static void profileTexture(NativeImage.InternalFormat internalFormat,
        int id,
        int maxLevel,
        int width,
        int height,
        CallbackInfo ci) {
        TextureTracker.Texture texture = new TextureTracker.Texture(width, height, internalFormat,
            maxLevel);
        TextureTracker.GLID2Texture.put(id, texture);
        int textureWidth = texture.width();
        int textureHeight = texture.height();
        int textureChannel = texture.channel();
        int textureMaxLayer = texture.maxLayer();
        String sizeInfo =
            "Size = (" + textureWidth + " x " + textureHeight + " x " + textureChannel + " - "
                + textureMaxLayer + ")";
//        System.out.println("Allocated GL_ID: " + id + " (" + sizeInfo + ")");
    }
}
