package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixins {

    @Redirect(method = "applyBlur()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    public void cancelFrameBufferInApplyBlur(Framebuffer instance, boolean setViewport) {

    }

    @Inject(method = "renderDarkening(Lnet/minecraft/client/gui/DrawContext;)V",
        at = @At("HEAD"), cancellable = true)
    private void skipDarkeningForRadianceScreens(DrawContext context, CallbackInfo ci) {
        if (((Object) this).getClass().getPackageName().startsWith("com.radiance.client.gui")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderDarkening(Lnet/minecraft/client/gui/DrawContext;IIII)V",
        at = @At("HEAD"), cancellable = true)
    private void skipDarkening4ArgForRadianceScreens(DrawContext context, int x, int y, int w,
        int h, CallbackInfo ci) {
        if (((Object) this).getClass().getPackageName().startsWith("com.radiance.client.gui")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderInGameBackground", at = @At("HEAD"), cancellable = true)
    private void skipInGameBackgroundForRadianceScreens(DrawContext context, CallbackInfo ci) {
        if (((Object) this).getClass().getPackageName().startsWith("com.radiance.client.gui")) {
            ci.cancel();
        }
    }
}
