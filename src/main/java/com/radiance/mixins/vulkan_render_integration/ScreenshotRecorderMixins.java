package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screenshot.class)
public class ScreenshotRecorderMixins {

    @Inject(method = "Lnet/minecraft/client/Screenshot;takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lcom/mojang/blaze3d/platform/NativeImage;",
        at = @At(value = "HEAD"),
        cancellable = true)
    private static void redirectTakeScreenshot(RenderTarget framebuffer,
        CallbackInfoReturnable<NativeImage> cir) {
        Minecraft mc = Minecraft.getInstance();
        int
            width =
            mc.getWindow()
                .getScreenWidth();
        int
            height =
            mc.getWindow()
                .getScreenHeight();
        NativeImage nativeImage = new NativeImage(width, height, false);
        nativeImage.downloadTexture(0, true);
        cir.setReturnValue(nativeImage);
    }
}
