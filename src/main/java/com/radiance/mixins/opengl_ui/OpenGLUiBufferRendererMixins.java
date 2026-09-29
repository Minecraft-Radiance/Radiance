package com.radiance.mixins.opengl_ui;

import com.radiance.client.render.WeatherRenderer;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferRenderer.class)
public class OpenGLUiBufferRendererMixins {

    @Inject(method = "drawWithGlobalProgramInternal(Lnet/minecraft/client/render/BuiltBuffer;)V",
        at = @At("HEAD"), cancellable = true)
    private static void captureWeather(BuiltBuffer buffer, CallbackInfo ci) {
        if (!WeatherRenderer.isCaptureActive()) {
            return;
        }
        if (WeatherRenderer.captureActive(buffer)) {
            ci.cancel();
        }
    }
}
