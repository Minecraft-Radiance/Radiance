package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.texture.MipmapHelper;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MipmapHelper.class)
public class MipmapHelperMixins {

    @Inject(method = "getMipmapLevelsImages([Lnet/minecraft/client/texture/NativeImage;I)[Lnet/minecraft/client/texture/NativeImage;", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/NativeImage;getWidth()I", ordinal = 1))
    private static void addIdentifier(NativeImage[] originals, int mipmap,
        CallbackInfoReturnable<NativeImage[]> cir, @Local(ordinal = 0) NativeImage nativeImage,
        @Local(ordinal = 1) NativeImage nativeImage2) {

        ((INativeImageExt) (Object) nativeImage2).radiance$setIdentifier(
            ((INativeImageExt) (Object) nativeImage).radiance$getIdentifier());
    }
}
