package com.radiance.mixins.vulkan_render_integration;

import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_NO_API;

import com.mojang.blaze3d.platform.Window;
import com.radiance.client.proxy.vulkan.WindowProxy;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GLCapabilities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixins {

    @Final
    @Shadow
    private long window;

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;"
            +
            "Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 0), remap = false)
    public void cancelGLFWWindowHint0(int hint, int value) {

    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;"
            +
            "Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 1, remap = false))
    public void cancelGLFWWindowHint1(int hint, int value) {

    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;"
            +
            "Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 2, remap = false))
    public void cancelGLFWWindowHint2(int hint, int value) {

    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;"
            +
            "Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 3, remap = false))
    public void cancelGLFWWindowHint3(int hint, int value) {

    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;"
            +
            "Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 4, remap = false))
    public void cancelGLFWWindowHint4(int hint, int value) {

    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;"
            +
            "Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 5, remap = false))
    public void cancelGLFWWindowHint5(int hint, int value) {
        GLFW.glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;"
            +
            "Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwMakeContextCurrent(J)V", remap = false))
    public void cancelGLFWMakeContextCurrent(long window) {

    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;"
            +
            "Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL;createCapabilities()Lorg/lwjgl/opengl/GLCapabilities;",
            remap = false))
    public GLCapabilities cancelGLCreateCapabilities() {
        return null;
    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;"
            +
            "Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;maxSupportedTextureSize()I", remap = false))
    public int cancelGetMaxSupportedTextureSize() {
        return 0;
    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;"
            +
            "Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowSizeLimits(JIIII)V", remap = false))
    public void cancelGetMaxSupportedTextureSize(long window, int minwidth, int minheight,
        int maxwidth, int maxheight) {

    }

    @Inject(method = "Lcom/mojang/blaze3d/platform/Window;onFramebufferResize(JII)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/WindowEventHandler;resizeDisplay()V"))
    public void framebufferSizeChanged(long window, int width, int height, CallbackInfo ci) {
        WindowProxy.onFramebufferSizeChanged();
    }
}
