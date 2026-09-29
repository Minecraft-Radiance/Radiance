package com.radiance.client.proxy.vulkan;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.constant.Constants;
import com.radiance.client.option.Options;
import com.radiance.client.profiler.MemoryMetricsCollector;
import com.radiance.client.profiler.Profiler;
import com.radiance.client.ui.UiBackendRendererHooks;
import com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt;
import net.minecraft.client.Minecraft;

public class RendererProxy {

    private static volatile long openGLPresentationTarget = 0L;

    public static native void initFolderPath(String folderPath);

    public static native void initRenderer(String[] glfwLibCandidates, long windowHandle);

    private static native long prepareOpenGLPresentationTargetNative(
        String[] glfwLibCandidates, long windowHandle);

    private static native void initRendererWithOpenGLDevice(long presentationTargetHandle,
        byte[] deviceUuids, byte[] driverUuid);

    private static native void releaseOpenGLPresentationTargetNative(
        long presentationTargetHandle);

    private static String[] glfwLibCandidates() {
        String mapped = System.mapLibraryName("glfw");
        return new String[] {mapped, "libglfw.so.3", "libglfw.3.dylib", "glfw3.dll"};
    }

    public static void initRenderer(Window window) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.RendererProxy.initRenderer");
        }
        try {
            RendererProxy.initRenderer(glfwLibCandidates(), window.getWindow());
            UiBackendRendererHooks.afterRendererInitialized();
            Options.applyDefaultFrameGeneration();
            RenderSystem.apiDescription = "Vulkan 1.4";
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static synchronized void prepareOpenGLPresentationTarget(Window window) {
        if (openGLPresentationTarget != 0L) {
            throw new IllegalStateException("OpenGL presentation target is already initialized");
        }
        openGLPresentationTarget = prepareOpenGLPresentationTargetNative(
            glfwLibCandidates(), window.getWindow());
        if (openGLPresentationTarget == 0L) {
            throw new IllegalStateException("Failed to create the OpenGL UI presentation target");
        }
    }

    public static void initRendererWithOpenGLDevice(byte[] deviceUuids,
        byte[] driverUuid) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.RendererProxy.initRendererWithOpenGLDevice");
        }
        try {
            long presentationTarget = openGLPresentationTarget;
            if (presentationTarget == 0L) {
                throw new IllegalStateException("OpenGL presentation target was not prepared");
            }
            RendererProxy.initRendererWithOpenGLDevice(
                presentationTarget, deviceUuids, driverUuid);
            UiBackendRendererHooks.afterRendererInitialized();
            Options.applyDefaultFrameGeneration();
            RenderSystem.apiDescription = "Vulkan 1.4 + OpenGL UI";
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static synchronized void releaseOpenGLPresentationTarget() {
        if (openGLPresentationTarget == 0L) {
            return;
        }
        releaseOpenGLPresentationTargetNative(openGLPresentationTarget);
        openGLPresentationTarget = 0L;
    }

    public static native int maxSupportedTextureSize();

    public static native void acquireContext();

    public static native void submitCommand();

    public static native void present();

    public static native int getPresentedFrameRate();

    public static void submitCommandAndPresent() {
        if (Profiler.ENABLED) {
            Profiler.enter("java.RendererProxy.submitCommandAndPresent");
        }
        try {
            if (Profiler.ENABLED) {
                MemoryMetricsCollector.sampleFrame();
            }
            submitCommand();
            present();
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static native void fuseWorld();

    public static native void postBlur();

    public static native void close();

    public static native void shouldRenderWorld(boolean renderWorld);

    public static native void takeScreenshot(boolean withUI, int width, int height, int channel,
        long pointer);

    public static NativeImage takeScreenshotWithoutUI() {
        if (Profiler.ENABLED) {
            Profiler.enter("java.RendererProxy.takeScreenshotWithoutUI");
        }
        try {
            Minecraft minecraftClient = Minecraft.getInstance();
            int
                width =
                minecraftClient.getWindow()
                    .getScreenWidth();
            int
                height =
                minecraftClient.getWindow()
                    .getScreenHeight();
            NativeImage nativeImage = new NativeImage(width, height, false);
            ((INativeImageExt) (Object) nativeImage).radiance$loadFromTextureImageWithoutUI(0, true);
            return nativeImage;
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }
}
