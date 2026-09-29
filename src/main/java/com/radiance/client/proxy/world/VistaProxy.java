package com.radiance.client.proxy.world;

import com.radiance.client.vista.VistaTerrain;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

public class VistaProxy {
    public static final int MATERIAL_WATER = -2;
    public static native void reset(long worldVersion);

    public static boolean rebuildSingle(long worldVersion, long id, int x, int y, int z, int width,
        ByteBuffer vertices, VistaTerrain terrain) {
        if (!vertices.isDirect() || vertices.remaining() % 160 != 0) {
            throw new IllegalArgumentException("Vista requires direct 40-byte PBR quad vertices");
        }
        try (VistaTerrain.Upload upload = terrain.upload()) {
            return rebuildSingleNative(worldVersion, id, x, y, z, width,
                vertices.remaining() / 40, MemoryUtil.memAddress(vertices),
                terrain.id, upload.count, upload.address);
        }
    }

    private static native boolean rebuildSingleNative(long worldVersion, long id,
        int x, int y, int z, int width, int vertexCount, long vertices,
        long terrainId, int terrainCount, long terrainAddress);

    public static void updateRenderList(long worldVersion, long[] ids) {
        updateRenderListNative(worldVersion, ids);
    }

    private static native void updateRenderListNative(long worldVersion, long[] ids);
    public static native void updateObjectListNative(long worldVersion, long[] ids);
    public static native void moveObject(long worldVersion, long id, double x, double y, double z);
    public static native boolean isReady(long worldVersion, long id);
    public static native void invalidateSingle(long worldVersion, long id);
}
