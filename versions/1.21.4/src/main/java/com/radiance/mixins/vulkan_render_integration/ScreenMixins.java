package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Screen.class)
public class ScreenMixins {

    @Redirect(method = "applyBlur()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    public void cancelFrameBufferInApplyBlur(Framebuffer instance, boolean setViewport) {

    }
}
