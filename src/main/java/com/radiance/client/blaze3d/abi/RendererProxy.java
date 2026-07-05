package com.radiance.client.blaze3d.abi;

import com.radiance.client.replay.hook.ReplayCaptureHooks;
import com.radiance.loader.neoforge.scene_import.plugin.SceneImportBundleResolver;
import com.radiance.loader.neoforge.scene_import.plugin.SceneImportPipelineClassificationReport;
import java.io.IOException;
import java.nio.file.Path;

public final class RendererProxy {

    private static String folderPath = "";
    private static String sceneImportBundlePath = "";

    private RendererProxy() {
    }

    public static void initFolderPath(String path) {
        folderPath = path == null ? "" : path;
        Blaze3DProxy.initFolderPathNative(folderPath);
        initSceneImportBundlePath(folderPath);
        ReplayCaptureHooks.rendererFolderPath(folderPath);
    }

    public static String getFolderPath() {
        return folderPath;
    }

    public static String getSceneImportBundlePath() {
        return sceneImportBundlePath;
    }

    private static void initSceneImportBundlePath(String radiancePath) {
        if (radiancePath == null || radiancePath.isBlank()) {
            return;
        }
        Path radianceDir = Path.of(radiancePath);
        Path gameDir = radianceDir.getParent();
        if (gameDir == null) {
            gameDir = radianceDir;
        }
        try {
            SceneImportBundleResolver.Bundle bundle = SceneImportBundleResolver.resolve(gameDir);
            SceneImportPipelineClassificationReport.initialize(gameDir);
            sceneImportBundlePath = bundle.directory().toAbsolutePath().toString();
            if (!Blaze3DProxy.initSceneImportBundlePathNative(sceneImportBundlePath)) {
                throw new RuntimeException("Radiance scene import bundle is not compatible: "
                    + sceneImportBundlePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Radiance scene import bundle", e);
        }
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
