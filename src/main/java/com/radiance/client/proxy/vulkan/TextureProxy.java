package com.radiance.client.proxy.vulkan;

import static org.lwjgl.system.MemoryUtil.memAddress;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.option.Options;
import com.radiance.client.replay.hook.ReplayCaptureHooks;
import com.radiance.client.texture.EmissionRecorder;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
//#if MC>=260200
//$$ import com.mojang.blaze3d.platform.NativeImage;
//#else
import net.minecraft.client.texture.NativeImage;
//#endif
import org.lwjgl.system.MemoryUtil;

public class TextureProxy {

    private record EmissionTileKey(int textureId, long tileKey) {
    }

    private static final Map<EmissionTileKey, EmissionRecorder.TileUpdate> emissionTileCache =
        new ConcurrentHashMap<>();

    private synchronized static native int generateTextureIdNative();

    public synchronized static int generateTextureId() {
        return ReplayCaptureHooks.textureGenerated(generateTextureIdNative());
    }

    private synchronized static native void prepareImageNative(int id, int mipLevels, int width,
        int height, int format);

    public synchronized static void prepareImage(int id, int mipLevels, int width, int height,
        int format) {
        ReplayCaptureHooks.texturePrepareImage(id, mipLevels, width, height, format);
        prepareImageNative(id, mipLevels, width, height, format);
    }

    public static void prepareImage(int id, int mipLevels, int width, int height,
        VulkanConstants.VkFormat format) {
        clearEmissionTiles(id);
        prepareImage(id, mipLevels, width, height, format.getValue());
    }

    private synchronized static native void setFilterNative(int id, int samplingMode,
        int mipmapMode);

    public synchronized static void setFilter(int id, int samplingMode, int mipmapMode) {
        ReplayCaptureHooks.textureSetFilter(id, samplingMode, mipmapMode);
        setFilterNative(id, samplingMode, mipmapMode);
    }

    private synchronized static native void setClampNative(int id, int addressMode);

    public synchronized static void setClamp(int id, int addressMode) {
        ReplayCaptureHooks.textureSetClamp(id, addressMode);
        setClampNative(id, addressMode);
    }

    private synchronized static native void queueUploadNative(long srcPointer,
        int srcSizeInBytes,
        int srcRowPixels,
        int dstId,
        int srcOffsetX,
        int srcOffsetY,
        int dstOffsetX,
        int dstOffsetY,
        int width,
        int height,
        int level);

    public synchronized static void queueUpload(long srcPointer,
        int srcSizeInBytes,
        int srcRowPixels,
        int dstId,
        int srcOffsetX,
        int srcOffsetY,
        int dstOffsetX,
        int dstOffsetY,
        int width,
        int height,
        int level) {
        ReplayCaptureHooks.textureQueueUpload(srcPointer, srcSizeInBytes, srcRowPixels, dstId,
            srcOffsetX, srcOffsetY, dstOffsetX, dstOffsetY, width, height, level);
        queueUploadNative(srcPointer, srcSizeInBytes, srcRowPixels, dstId, srcOffsetX, srcOffsetY,
            dstOffsetX, dstOffsetY, width, height, level);
    }

    private synchronized static native void uploadEmissionTileNativeInternal(int textureId, long tileKey,
        long cellsPtr, int cellCount);

    private synchronized static void uploadEmissionTileNative(int textureId, long tileKey,
        long cellsPtr, int cellCount) {
        ReplayCaptureHooks.textureEmissionTile(textureId, tileKey, cellsPtr, cellCount);
        uploadEmissionTileNativeInternal(textureId, tileKey, cellsPtr, cellCount);
    }

    public synchronized static void uploadEmissionTileNativeForReplay(int textureId, long tileKey,
        long cellsPtr, int cellCount) {
        uploadEmissionTileNativeInternal(textureId, tileKey, cellsPtr, cellCount);
    }

    public static void uploadEmissionTile(EmissionRecorder.TileUpdate tileUpdate) {
        if (tileUpdate == null) {
            return;
        }

        emissionTileCache.put(new EmissionTileKey(tileUpdate.textureId, tileUpdate.tileKey),
            tileUpdate);
        if (!Options.collectChunkEmission) {
            return;
        }

        uploadEmissionTileToNative(tileUpdate);
    }

    public static void flushEmissionTiles() {
        if (!Options.collectChunkEmission) {
            return;
        }

        for (EmissionRecorder.TileUpdate tileUpdate : emissionTileCache.values()) {
            uploadEmissionTileToNative(tileUpdate);
        }
    }

    public static boolean hasEmissionTile(int textureId, long tileKey) {
        return emissionTileCache.containsKey(new EmissionTileKey(textureId, tileKey));
    }

    private static void clearEmissionTiles(int textureId) {
        emissionTileCache.keySet().removeIf(key -> key.textureId == textureId);
    }

    private static void uploadEmissionTileToNative(EmissionRecorder.TileUpdate tileUpdate) {
        if (tileUpdate == null) {
            return;
        }

        ByteBuffer cellsBuffer = null;
        try {
            int cellCount = tileUpdate.cells.size();
            long cellsAddr = 0L;
            if (cellCount > 0) {
                cellsBuffer = MemoryUtil.memAlloc(cellCount * 8 * Float.BYTES);
                int base = 0;
                for (EmissionRecorder.EmissionCell cell : tileUpdate.cells) {
                    cellsBuffer.putFloat(base, cell.u0);
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, cell.v0);
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, cell.u1);
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, cell.v1);
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, cell.avgEmission);
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, cell.avgR);
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, cell.avgG);
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, cell.avgB);
                    base += Float.BYTES;
                }
                cellsAddr = memAddress(cellsBuffer);
            }

            uploadEmissionTileNative(tileUpdate.textureId, tileUpdate.tileKey, cellsAddr, cellCount);
        } finally {
            if (cellsBuffer != null) {
                MemoryUtil.memFree(cellsBuffer);
            }
        }
    }

    //#if MC>=260200
    //$$ public static void prepareImage(NativeImage.Format internalFormat, int id,
    //#else
    public static void prepareImage(NativeImage.InternalFormat internalFormat, int id,
    //#endif
        int mipLevels, int width, int height) {
        switch (internalFormat) {
            case RGBA:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM);
                break;
            case RGB:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8B8_UNORM);
                break;
            //#if MC>=260200
            //$$ case LUMINANCE_ALPHA:
            //#else
            case RG:
            //#endif
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8_UNORM);
                break;
            //#if MC>=260200
            //$$ case LUMINANCE:
            //#else
            case RED:
            //#endif
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM);
                break;
        }
    }
}
