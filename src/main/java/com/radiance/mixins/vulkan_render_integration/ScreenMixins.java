package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Screen.class)
public class ScreenMixins {

    @Redirect(method = "renderBlurredBackground(F)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    public void cancelFrameBufferInApplyBlur(RenderTarget instance, boolean setViewport) {

    }
}
