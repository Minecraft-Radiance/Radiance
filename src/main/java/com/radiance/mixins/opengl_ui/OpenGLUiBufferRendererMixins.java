package com.radiance.mixins.opengl_ui;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.radiance.client.render.BuiltBuffer;
import com.radiance.client.render.WeatherRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferUploader.class)
public class OpenGLUiBufferRendererMixins {

    @Inject(method = "_drawWithShader(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V",
        at = @At("HEAD"), cancellable = true)
    private static void captureWeather(BufferBuilder.RenderedBuffer vanillaBuffer, CallbackInfo ci) {
        if (!WeatherRenderer.isCaptureActive()) {
            return;
        }
        if (WeatherRenderer.captureActive(BuiltBuffer.wrap(vanillaBuffer))) {
            ci.cancel();
        }
    }
}
