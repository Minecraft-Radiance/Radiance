package com.radiance.client.proxy.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.Window;

public class RendererProxy {

    private static native void initFolderPathNative(String folderPath);

    public static void initFolderPath(String folderPath) {
        initFolderPathNative(folderPath);
    }

    private static native void initRendererNative(String[] glfwLibCandidates, long windowHandle);

    public static void initRenderer(Window window) {
        String mapped = System.mapLibraryName("glfw");
        String[] candidates = {mapped, "libglfw.so.3", "libglfw.3.dylib", "glfw3.dll"};
        RendererProxy.initRendererNative(candidates, window.getHandle());
        RenderSystem.apiDescription = "Vulkan 1.4";
    }

    public static native int maxSupportedTextureSize();

    private static native void acquireContextNative();

    public static void acquireContext() {
        acquireContextNative();
    }

    private static native void submitCommandNative();

    public static void submitCommand() {
        submitCommandNative();
    }

    private static native void presentNative();

    public static void present() {
        presentNative();
    }

    public static void submitCommandAndPresent() {
        submitCommand();
        present();
    }

    private static native void fuseWorldNative();

    public static void fuseWorld() {
        fuseWorldNative();
    }

    private static native void postBlurNative();

    public static void postBlur() {
        postBlurNative();
    }

    public static native void close();

    public static native void shouldRenderWorld(boolean renderWorld);

    public static native void takeScreenshot(boolean withUI, int width, int height, int channel,
        long pointer);

    public static NativeImage takeScreenshotWithoutUI() {
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
        ((INativeImageExt) (Object) nativeImage).radiance$loadFromTextureImageWithoutUI(0, true);
        return nativeImage;
    }
}
