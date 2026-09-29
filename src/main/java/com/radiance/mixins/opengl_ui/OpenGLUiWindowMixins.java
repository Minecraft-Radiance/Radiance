package com.radiance.mixins.opengl_ui;

import com.mojang.blaze3d.platform.Window;
import com.radiance.client.opengl_ui.OpenGLUiBridge;
import com.radiance.client.proxy.vulkan.WindowProxy;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraftforge.fml.loading.ImmediateWindowHandler;
import org.lwjgl.opengl.GLCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class OpenGLUiWindowMixins {

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V",
            ordinal = 0), remap = false)
    public void cancelGLFWWindowHint0(int hint, int value) {
    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V",
            ordinal = 1, remap = false))
    public void cancelGLFWWindowHint1(int hint, int value) {
    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V",
            ordinal = 2, remap = false))
    public void cancelGLFWWindowHint2(int hint, int value) {
    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V",
            ordinal = 3, remap = false))
    public void cancelGLFWWindowHint3(int hint, int value) {
    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V",
            ordinal = 4, remap = false))
    public void cancelGLFWWindowHint4(int hint, int value) {
    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V",
            ordinal = 5, remap = false))
    public void cancelGLFWWindowHint5(int hint, int value) {
    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J",
            remap = false))
    public long adoptEarlyDisplayWindow(IntSupplier width, IntSupplier height,
        Supplier<String> title, LongSupplier monitor) {
        long window = ImmediateWindowHandler.setupMinecraftWindow(width, height, title, monitor);
        if (window != 0L) {
            OpenGLUiBridge.adoptEarlyDisplayContext(window);
        }
        return window;
    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwMakeContextCurrent(J)V",
            remap = false))
    public void cancelGLFWMakeContextCurrent(long window) {
    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL;createCapabilities()Lorg/lwjgl/opengl/GLCapabilities;",
            remap = false))
    public GLCapabilities cancelGLCreateCapabilities() {
        return null;
    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;maxSupportedTextureSize()I",
            remap = false))
    public int cancelGetMaxSupportedTextureSize() {
        return 0;
    }

    @Redirect(method =
        "<init>(Lcom/mojang/blaze3d/platform/WindowEventHandler;Lcom/mojang/blaze3d/platform/ScreenManager;Lcom/mojang/blaze3d/platform/DisplayData;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowSizeLimits(JIIII)V",
            remap = false))
    public void cancelWindowSizeLimits(long window, int minwidth, int minheight,
        int maxwidth, int maxheight) {
    }

    @Inject(method = "onFramebufferResize(JII)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/WindowEventHandler;resizeDisplay()V"))
    public void framebufferSizeChanged(long window, int width, int height, CallbackInfo ci) {
        OpenGLUiBridge.onFramebufferSizeChanged();
        WindowProxy.onFramebufferSizeChanged();
    }
}
