package com.radiance.client.blaze3d.abi;

import com.radiance.client.replay.hook.ReplayCaptureHooks;

public final class RendererProxy {

    private static String folderPath = "";

    private RendererProxy() {
    }

    public static void initFolderPath(String path) {
        folderPath = path == null ? "" : path;
        Blaze3DProxy.initFolderPathNative(folderPath);
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
        return Blaze3DProxy.maxSupportedTextureSizeNative();
    }

    public static void acquireContext() {
        ReplayCaptureHooks.frameAcquireContext();
        Blaze3DProxy.acquireContextNative();
    }

    public static void submitCommand() {
        ReplayCaptureHooks.frameSubmitCommand();
        Blaze3DProxy.submitCommandNative();
    }

    public static void present() {
        ReplayCaptureHooks.framePresent();
        Blaze3DProxy.presentNative();
    }

    public static void close() {
        Blaze3DProxy.closeNative();
    }
}
