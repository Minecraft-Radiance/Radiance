package com.radiance.client.proxy.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.constant.Constants;
import com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.Window;

public class RendererProxy {

    public static native void initFolderPath(String folderPath);

    public static native void initRenderer(String[] glfwLibCandidates, long windowHandle);

    public static void initRenderer(Window window) {
        String mapped = System.mapLibraryName("glfw");
        String[] candidates = {mapped, "libglfw.so.3", "libglfw.3.dylib", "glfw3.dll"};
        RendererProxy.initRenderer(candidates, window.getHandle());
        RenderSystem.apiDescription = "Vulkan 1.4";
    }

    public static native int maxSupportedTextureSize();

    public static native void acquireContext();

    public static native void submitCommand();

    public static native void present();

    public static void submitCommandAndPresent() {
        submitCommand();
        present();
    }

    public static native void fuseWorld();

    public static native void postBlur();

    public static native void close();

    public static native void shouldRenderWorld(boolean renderWorld);

    public static native void updateScenarioSceneContext(String dimensionKey, String biomeKey, long timeOfDay,
        boolean raining, boolean thundering, String submersion, boolean indoors, boolean cave);

    public static native boolean saveScenarioColorGrading(String scenarioName, int priority, boolean saveWorld,
        boolean saveTime, boolean saveWeather, boolean saveBiome, boolean saveSubmersion, boolean saveIndoor,
        boolean saveCave, int timeStart, int timeEnd, String[] attributePairs);

    public static native String getActiveScenarioColorGradingName();

    public static native String[] getActiveScenarioColorGradingValues(String[] attributeKeys);

    public static native void applyPreviewScenarioColorGrading(String[] attributePairs);

    public static native void clearPreviewScenarioColorGrading();

    public static native void setScenarioGradingIsolation(boolean enabled);

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
