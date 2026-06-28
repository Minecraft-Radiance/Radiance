package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScreenshotRecorder.class)
public class ScreenshotRecorderMixins {

    @Inject(method = "takeScreenshot(Lnet/minecraft/client/gl/Framebuffer;)Lnet/minecraft/client/texture/NativeImage;",
        at = @At(value = "HEAD"),
        cancellable = true)
    private static void redirectTakeScreenshot(Framebuffer framebuffer,
        CallbackInfoReturnable<NativeImage> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int
            width =
            mc.getWindow()
                .getWidth();
        int
            height =
            mc.getWindow()
                .getHeight();
        NativeImage nativeImage = new NativeImage(width, height, false);
        nativeImage.loadFromTextureImage(0, true);
        cir.setReturnValue(nativeImage);
    }
}
