package com.radiance.client.blaze3d.abi;

public final class RendererProxy {

    private static String folderPath = "";

    private RendererProxy() {
    }

    public static void initFolderPath(String path) {
        folderPath = path == null ? "" : path;
    }

    public static String getFolderPath() {
        return folderPath;
    }

    public static void close() {
    }
}
