package com.radiance.client.blaze3d.abi;

import com.radiance.client.replay.hook.ReplayCaptureHooks;

public final class RendererProxy {

    private static String folderPath = "";

    private RendererProxy() {
    }

    public static void initFolderPath(String path) {
        folderPath = path == null ? "" : path;
        NativeBridge.initFolderPathNative(folderPath);
        ReplayCaptureHooks.rendererFolderPath(folderPath);
    }

    public static String getFolderPath() {
        return folderPath;
    }

    public static String[] glfwLibraryCandidates() {
        String mapped = System.mapLibraryName("glfw");
        return new String[] {mapped, "libglfw.so.3", "libglfw.3.dylib", "glfw3.dll"};
    }

    public static int maxSupportedTextureSize() {
        return NativeBridge.maxSupportedTextureSizeNative();
    }

    public static void acquireContext() {
        ReplayCaptureHooks.frameAcquireContext();
        NativeBridge.acquireContextNative();
    }

    public static void submitCommand() {
        ReplayCaptureHooks.frameSubmitCommand();
        NativeBridge.submitCommandNative();
    }

    public static void present() {
        ReplayCaptureHooks.framePresent();
        NativeBridge.presentNative();
    }

    public static void close() {
        NativeBridge.closeNative();
    }
}
