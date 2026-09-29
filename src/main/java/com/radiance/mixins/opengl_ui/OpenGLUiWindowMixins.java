package com.radiance.mixins.opengl_ui;

import com.radiance.client.opengl_ui.OpenGLUiBridge;
import com.radiance.client.proxy.vulkan.WindowProxy;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.GLCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class OpenGLUiWindowMixins {

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            + "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwMakeContextCurrent(J)V",
            remap = false))
    public void adoptGLFWContext(long window) {
        OpenGLUiBridge.adoptWindowContext(window);
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            + "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL;createCapabilities()Lorg/lwjgl/opengl/GLCapabilities;",
            remap = false))
    public GLCapabilities cancelGLCreateCapabilities() {
        return null;
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            + "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;maxSupportedTextureSize()I",
            remap = false))
    public int skipMaxSupportedTextureSizeBeforeRendererInitialization() {
        return 0;
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            + "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowSizeLimits(JIIII)V",
            remap = false))
    public void skipWindowSizeLimitsBeforeRendererInitialization(long window, int minWidth,
        int minHeight, int maxWidth, int maxHeight) {
    }

    @Inject(method = "onFramebufferSizeChanged(JII)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/WindowEventHandler;onResolutionChanged()V"))
    public void framebufferSizeChanged(long window, int width, int height, CallbackInfo ci) {
        OpenGLUiBridge.onFramebufferSizeChanged();
        WindowProxy.onFramebufferSizeChanged();
    }
}
