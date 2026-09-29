package com.radiance.client.proxy.vulkan;

import static org.lwjgl.system.MemoryUtil.memAddress;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.option.Options;
import com.radiance.client.profiler.Profiler;
import com.radiance.client.texture.EmissionRecorder;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.texture.TextureTracker.TextureTarget;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public class TextureProxy {

    private record EmissionUpdateKey(int textureId, long tileKey) {
    }

    private record FilterState(int samplingMode, int mipmapMode) {
    }

    private static final Map<EmissionUpdateKey, EmissionRecorder.TileUpdate> emissionUpdatesByTile =
        new ConcurrentHashMap<>();
    private static final Map<Integer, FilterState> filterStatesByTextureId = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> clampModesByTextureId = new ConcurrentHashMap<>();
    public static final int UPLOAD_KIND_MAIN = 0;
    public static final int UPLOAD_KIND_SPECULAR = 1;
    public static final int UPLOAD_KIND_NORMAL = 2;
    public static final int UPLOAD_KIND_FLAG = 3;
    private static final int UPLOAD_REGION_SIZE_BYTES = Long.BYTES + Integer.BYTES * 12;
    private static final int MAX_BATCH_REGIONS_PER_JNI_CALL = 16_384;
    private static final ThreadLocal<UploadBatch> activeUploadBatch = new ThreadLocal<>();

    private static final class UploadBatch {

        private int depth;
        private final List<TextureUploadRegion> regions = new ArrayList<>();
    }

    public record TextureUploadRegion(
        long sourcePointer,
        int sourceSizeBytes,
        int sourceRowPixels,
        int textureId,
        int sourceOffsetX,
        int sourceOffsetY,
        int destinationOffsetX,
        int destinationOffsetY,
        int width,
        int height,
        int level,
        int uploadKind,
        int sourceBytesPerPixel,
        long textureGeneration) {
    }

    private static native int generateTextureIdNative();

    public synchronized static int generateTextureId() {
        if (Profiler.ENABLED) {
            Profiler.enter("java.TextureProxy.generateTextureId");
        }
        try {
            return generateTextureIdNative();
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static native void prepareImageNative(int textureId, int mipLevels, int width,
        int height, int format, boolean clear, float clearRed, float clearGreen,
        float clearBlue, float clearAlpha);

    private static native void releaseTextureNative(int textureId);

    private static native void queueTextureClearNative(int textureId, float clearRed,
        float clearGreen, float clearBlue, float clearAlpha);

    public synchronized static void prepareImage(int textureId, int mipLevels, int width,
        int height, int format) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.TextureProxy.prepareImage");
        }
        try {
            clearEmissionUpdates(textureId);
            filterStatesByTextureId.remove(textureId);
            clampModesByTextureId.remove(textureId);
            VulkanConstants.VkFormat vkFormat = VulkanConstants.VkFormat.fromValue(format);
            prepareImageNative(textureId, mipLevels, width, height, format, false,
                0.0f, 0.0f, 0.0f, 0.0f);
            TextureTracker.Texture texture = new TextureTracker.Texture(width, height,
                formatToChannelCount(vkFormat), vkFormat, mipLevels - 1);
            TextureTracker.registerTexture(new TextureTarget(textureId, texture.generation()), texture);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public synchronized static void prepareClearedImage(int textureId, int mipLevels, int width,
        int height, VulkanConstants.VkFormat format, float clearRed, float clearGreen,
        float clearBlue, float clearAlpha, long textureGeneration) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.TextureProxy.prepareClearedImage");
        }
        try {
            clearEmissionUpdates(textureId);
            filterStatesByTextureId.remove(textureId);
            clampModesByTextureId.remove(textureId);
            prepareImageNative(textureId, mipLevels, width, height, format.getValue(), true,
                clearRed, clearGreen, clearBlue, clearAlpha);
            TextureTracker.Texture texture = new TextureTracker.Texture(width, height,
                formatToChannelCount(format), format, mipLevels - 1, textureGeneration);
            TextureTracker.registerTexture(new TextureTarget(textureId, texture.generation()), texture);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public synchronized static void releaseTexture(int textureId) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.TextureProxy.releaseTexture");
        }
        try {
            clearEmissionUpdates(textureId);
            filterStatesByTextureId.remove(textureId);
            clampModesByTextureId.remove(textureId);
            TextureTracker.Texture texture = TextureTracker.GLID2Texture.get(textureId);
            TextureTarget target = texture == null ? null
                : new TextureTarget(textureId, texture.generation());
            releaseTextureNative(textureId);
            TextureTracker.releaseTexture(target);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public synchronized static void queueTextureClear(int textureId, float clearRed,
        float clearGreen, float clearBlue, float clearAlpha, long textureGeneration) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.TextureProxy.queueTextureClear");
        }
        try {
            TextureTracker.Texture texture = TextureTracker.GLID2Texture.get(textureId);
            if (texture == null) {
                throw new IllegalStateException("Cannot clear untracked texture " + textureId);
            }
            queueTextureClearNative(textureId, clearRed, clearGreen, clearBlue, clearAlpha);
            TextureTracker.Texture updatedTexture = new TextureTracker.Texture(texture.width(),
                texture.height(), texture.channel(), texture.format(), texture.maxLayer(),
                textureGeneration);
            TextureTracker.registerTexture(new TextureTarget(textureId, updatedTexture.generation()),
                updatedTexture);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static void prepareImage(int textureId, int mipLevels, int width, int height,
        VulkanConstants.VkFormat format) {
        prepareImage(textureId, mipLevels, width, height, format.getValue());
    }

    private static native void setFilterNative(int textureId, int samplingMode, int mipmapMode);

    public synchronized static void setFilter(int textureId, int samplingMode, int mipmapMode) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.TextureProxy.setFilter");
        }
        try {
            FilterState filterState = new FilterState(samplingMode, mipmapMode);
            if (filterState.equals(filterStatesByTextureId.get(textureId))) {
                return;
            }
            setFilterNative(textureId, samplingMode, mipmapMode);
            filterStatesByTextureId.put(textureId, filterState);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static native void setClampNative(int textureId, int addressMode);

    public synchronized static void setClamp(int textureId, int addressMode) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.TextureProxy.setClamp");
        }
        try {
            if (clampModesByTextureId.getOrDefault(textureId, Integer.MIN_VALUE) == addressMode) {
                return;
            }
            setClampNative(textureId, addressMode);
            clampModesByTextureId.put(textureId, addressMode);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static native void queueUploadsNative(long regionsPtr, int regionCount);

    public synchronized static void beginUploadBatch() {
        UploadBatch batch = activeUploadBatch.get();
        if (batch == null) {
            batch = new UploadBatch();
            activeUploadBatch.set(batch);
        }
        batch.depth++;
    }

    public synchronized static void endUploadBatch() {
        UploadBatch batch = activeUploadBatch.get();
        if (batch == null || batch.depth <= 0) {
            throw new IllegalStateException("Texture upload batch is not active");
        }
        batch.depth--;
        if (batch.depth == 0) {
            try {
                submitUploadRegions(batch.regions);
            } finally {
                activeUploadBatch.remove();
            }
        }
    }

    public synchronized static void flushUploadBatch() {
        UploadBatch batch = activeUploadBatch.get();
        if (batch == null || batch.regions.isEmpty()) {
            return;
        }
        try {
            submitUploadRegions(batch.regions);
        } finally {
            batch.regions.clear();
        }
    }

    public synchronized static void queueUploadRegion(TextureUploadRegion uploadRegion) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.TextureProxy.queueUploads");
        }
        try {
            UploadBatch batch = activeUploadBatch.get();
            if (batch != null) {
                appendUploadRegion(batch, uploadRegion);
                return;
            }
            if (!isCurrentUploadRegion(uploadRegion)) {
                return;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer regionBuffer = stack.malloc(UPLOAD_REGION_SIZE_BYTES);
                regionBuffer.order(ByteOrder.nativeOrder());
                writeUploadRegion(regionBuffer, 0, uploadRegion);
                queueUploadsNative(memAddress(regionBuffer), 1);
            }
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public synchronized static void queueUploads(TextureUploadRegion mainRegion,
        List<TextureUploadRegion> auxiliaryRegions) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.TextureProxy.queueUploads");
        }
        try {
            int auxiliaryCount = auxiliaryRegions.size();
            int regionCount = auxiliaryCount + 1;

            UploadBatch batch = activeUploadBatch.get();
            if (batch != null) {
                for (TextureUploadRegion auxiliaryRegion : auxiliaryRegions) {
                    appendUploadRegion(batch, auxiliaryRegion);
                }
                appendUploadRegion(batch, mainRegion);
                return;
            }

            List<TextureUploadRegion> uploadRegions = new ArrayList<>(regionCount);
            uploadRegions.addAll(auxiliaryRegions);
            uploadRegions.add(mainRegion);
            submitUploadRegions(uploadRegions);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static void appendUploadRegion(UploadBatch batch, TextureUploadRegion uploadRegion) {
        batch.regions.add(uploadRegion);
        if (batch.regions.size() >= MAX_BATCH_REGIONS_PER_JNI_CALL) {
            try {
                submitUploadRegions(batch.regions);
            } finally {
                batch.regions.clear();
            }
        }
    }

    private static void submitUploadRegions(List<TextureUploadRegion> uploadRegions) {
        List<TextureUploadRegion> currentUploadRegions = new ArrayList<>(uploadRegions.size());
        for (TextureUploadRegion uploadRegion : uploadRegions) {
            if (isCurrentUploadRegion(uploadRegion)) {
                currentUploadRegions.add(uploadRegion);
            }
        }

        int totalRegions = currentUploadRegions.size();
        for (int firstRegion = 0; firstRegion < totalRegions;
             firstRegion += MAX_BATCH_REGIONS_PER_JNI_CALL) {
            int regionCount = Math.min(MAX_BATCH_REGIONS_PER_JNI_CALL, totalRegions - firstRegion);
            ByteBuffer regionBuffer = MemoryUtil.memAlloc(regionCount * UPLOAD_REGION_SIZE_BYTES);
            try {
                regionBuffer.order(ByteOrder.nativeOrder());
                for (int regionIndex = 0; regionIndex < regionCount; regionIndex++) {
                    writeUploadRegion(regionBuffer, regionIndex * UPLOAD_REGION_SIZE_BYTES,
                        currentUploadRegions.get(firstRegion + regionIndex));
                }
                queueUploadsNative(memAddress(regionBuffer), regionCount);
            } finally {
                MemoryUtil.memFree(regionBuffer);
            }
        }
    }

    private static boolean isCurrentUploadRegion(TextureUploadRegion uploadRegion) {
        TextureTracker.Texture texture = TextureTracker.GLID2Texture.get(uploadRegion.textureId());
        if (texture == null || texture.generation() != uploadRegion.textureGeneration()) {
            return false;
        }

        validateCurrentUploadRegion(uploadRegion, texture);
        return true;
    }

    private static void validateCurrentUploadRegion(TextureUploadRegion uploadRegion,
        TextureTracker.Texture texture) {
        int level = uploadRegion.level();
        if (level < 0 || level >= Integer.SIZE || level > texture.maxLayer()) {
            throw new IllegalStateException(
                "Texture upload targets an unavailable mip level: textureId=" + uploadRegion.textureId()
                    + ", generation=" + uploadRegion.textureGeneration() + ", mip=" + level
                    + ", maxMip=" + texture.maxLayer());
        }

        int levelWidth = Math.max(1, texture.width() >> level);
        int levelHeight = Math.max(1, texture.height() >> level);
        long destinationRight = (long) uploadRegion.destinationOffsetX() + uploadRegion.width();
        long destinationBottom = (long) uploadRegion.destinationOffsetY() + uploadRegion.height();
        if (uploadRegion.destinationOffsetX() < 0 || uploadRegion.destinationOffsetY() < 0
            || uploadRegion.width() <= 0 || uploadRegion.height() <= 0
            || destinationRight > levelWidth || destinationBottom > levelHeight) {
            throw new IllegalStateException(
                "Texture upload region exceeds current texture generation: textureId="
                    + uploadRegion.textureId() + ", generation=" + uploadRegion.textureGeneration()
                    + ", mip=" + level + ", destinationOffset=" + uploadRegion.destinationOffsetX()
                    + "x" + uploadRegion.destinationOffsetY() + ", uploadSize=" + uploadRegion.width()
                    + "x" + uploadRegion.height() + ", destinationMipSize=" + levelWidth + "x"
                    + levelHeight + ", destinationTextureSize=" + texture.width() + "x"
                    + texture.height());
        }
    }

    private static void writeUploadRegion(ByteBuffer buffer, int byteOffset, TextureUploadRegion region) {
        writeUploadRegion(buffer, byteOffset, region.sourcePointer, region.sourceSizeBytes,
            region.sourceRowPixels, region.textureId, region.sourceOffsetX, region.sourceOffsetY,
            region.destinationOffsetX, region.destinationOffsetY, region.width, region.height, region.level,
            region.uploadKind, region.sourceBytesPerPixel);
    }

    private static void writeUploadRegion(ByteBuffer buffer,
        int byteOffset,
        long sourcePointer,
        int sourceSizeBytes,
        int sourceRowPixels,
        int textureId,
        int sourceOffsetX,
        int sourceOffsetY,
        int destinationOffsetX,
        int destinationOffsetY,
        int width,
        int height,
        int level,
        int uploadKind,
        int sourceBytesPerPixel) {
        buffer.putLong(byteOffset, sourcePointer);
        buffer.putInt(byteOffset + 8, sourceSizeBytes);
        buffer.putInt(byteOffset + 12, sourceRowPixels);
        buffer.putInt(byteOffset + 16, textureId);
        buffer.putInt(byteOffset + 20, sourceOffsetX);
        buffer.putInt(byteOffset + 24, sourceOffsetY);
        buffer.putInt(byteOffset + 28, destinationOffsetX);
        buffer.putInt(byteOffset + 32, destinationOffsetY);
        buffer.putInt(byteOffset + 36, width);
        buffer.putInt(byteOffset + 40, height);
        buffer.putInt(byteOffset + 44, level);
        buffer.putInt(byteOffset + 48, uploadKind);
        buffer.putInt(byteOffset + 52, sourceBytesPerPixel);
    }

    private synchronized static native void submitEmissionUpdateNative(int textureId, long tileKey,
        long cellsPointer, int cellCount);

    public static void queueEmissionUpdate(EmissionRecorder.TileUpdate tileUpdate) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.TextureProxy.queueEmissionUpdate");
        }
        try {
            if (tileUpdate == null) {
                return;
            }

            emissionUpdatesByTile.put(new EmissionUpdateKey(tileUpdate.textureId, tileUpdate.tileKey),
                tileUpdate);
            if (!Options.collectChunkEmission) {
                return;
            }

            submitEmissionUpdate(tileUpdate);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static boolean hasEmissionUpdate(int textureId, long tileKey) {
        return emissionUpdatesByTile.containsKey(new EmissionUpdateKey(textureId, tileKey));
    }

    private static void clearEmissionUpdates(int textureId) {
        emissionUpdatesByTile.keySet().removeIf(updateKey -> updateKey.textureId == textureId);
    }

    private static void submitEmissionUpdate(EmissionRecorder.TileUpdate tileUpdate) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.TextureProxy.submitEmissionUpdate");
        }
        try {
            if (tileUpdate == null) {
                return;
            }

            ByteBuffer cellsBuffer = null;
            try {
                int cellCount = tileUpdate.cells.size();
                long cellsAddress = 0L;
                if (cellCount > 0) {
                    if (Profiler.ENABLED) {
                        Profiler.enter("java.TextureProxy.packEmissionCells");
                    }
                    try {
                        cellsBuffer = MemoryUtil.memAlloc(cellCount * 8 * Float.BYTES);
                        int byteOffset = 0;
                        for (EmissionRecorder.EmissionCell cell : tileUpdate.cells) {
                            cellsBuffer.putFloat(byteOffset, cell.u0);
                            byteOffset += Float.BYTES;
                            cellsBuffer.putFloat(byteOffset, cell.v0);
                            byteOffset += Float.BYTES;
                            cellsBuffer.putFloat(byteOffset, cell.u1);
                            byteOffset += Float.BYTES;
                            cellsBuffer.putFloat(byteOffset, cell.v1);
                            byteOffset += Float.BYTES;
                            cellsBuffer.putFloat(byteOffset, cell.avgEmission);
                            byteOffset += Float.BYTES;
                            cellsBuffer.putFloat(byteOffset, cell.avgR);
                            byteOffset += Float.BYTES;
                            cellsBuffer.putFloat(byteOffset, cell.avgG);
                            byteOffset += Float.BYTES;
                            cellsBuffer.putFloat(byteOffset, cell.avgB);
                            byteOffset += Float.BYTES;
                        }
                        cellsAddress = memAddress(cellsBuffer);
                    } finally {
                        if (Profiler.ENABLED) {
                            Profiler.exit();
                        }
                    }
                }

                if (Profiler.ENABLED) {
                    Profiler.enter("java.TextureProxy.submitEmissionUpdateNative");
                }
                try {
                    submitEmissionUpdateNative(tileUpdate.textureId, tileUpdate.tileKey, cellsAddress, cellCount);
                } finally {
                    if (Profiler.ENABLED) {
                        Profiler.exit();
                    }
                }
            } finally {
                if (cellsBuffer != null) {
                    MemoryUtil.memFree(cellsBuffer);
                }
            }
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public synchronized static void prepareImage(NativeImage.InternalGlFormat internalFormat, int textureId,
        int mipLevels, int width, int height) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.TextureProxy.prepareImageInternalFormat");
        }
        try {
            VulkanConstants.VkFormat format = switch (internalFormat) {
                case RGBA:
                    yield VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM;
                case RGB:
                    yield VulkanConstants.VkFormat.VK_FORMAT_R8G8B8_UNORM;
                case RG:
                    yield VulkanConstants.VkFormat.VK_FORMAT_R8G8_UNORM;
                case RED:
                    yield VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM;
            };
            prepareImage(textureId, mipLevels, width, height, format);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static int formatToChannelCount(VulkanConstants.VkFormat format) {
        return switch (format) {
            case VK_FORMAT_R8_UNORM -> 1;
            case VK_FORMAT_R8G8_UNORM -> 2;
            case VK_FORMAT_R8G8B8_UNORM -> 3;
            case VK_FORMAT_R8G8B8A8_UNORM -> 4;
            default -> throw new IllegalArgumentException("Unsupported tracked texture format: " + format);
        };
    }
}
