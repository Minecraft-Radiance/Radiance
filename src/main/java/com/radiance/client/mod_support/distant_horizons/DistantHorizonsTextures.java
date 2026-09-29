package com.radiance.client.mod_support.distant_horizons;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTracker;
import com.mojang.blaze3d.platform.TextureUtil;
import com.radiance.client.ui.UiBackendBuildConfig;
import com.seibel.distanthorizons.core.dataObjects.render.textures.BlockTextureRegistry;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

public class DistantHorizonsTextures {
    private static int texture;
    private static int rows;
    private static int uploaded;

    public static int id() {
        return com.seibel.distanthorizons.core.config.Config.Client.Advanced.Graphics.Texture.enableTexturedLods.get()
            ? texture : 0;
    }

    public static void update() {
        BlockTextureRegistry registry = BlockTextureRegistry.INSTANCE;
        synchronized (registry) {
            update(registry);
        }
    }

    private static void update(BlockTextureRegistry registry) {
        int count = registry.getTileCount();
        if (texture == 0) { texture = TextureUtil.generateTextureId(); }
        if (rows == 0 || count > rows * 256 || count < uploaded) {
            int size = Math.max(16, rows);
            while (count > size * 256) { size *= 2; }
            rows = size;
            TextureProxy.prepareImage(texture, 5, 4096, rows * 16,
                VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM.getValue());
            TextureProxy.setFilter(texture, 1, 1);
            TextureProxy.setClamp(texture, 2);
            registry.resetPendingUploads();
            uploaded = 0;
        }
        BlockTextureRegistry.PendingTiles pending = registry.getAndClearPendingUploadTiles();
        if (pending == null) { return; }
        long generation = TextureTracker.captureTarget(texture).generation();
        ByteBuffer pixels = MemoryUtil.memAlloc(BlockTextureRegistry.TILE_BYTE_COUNT);
        try {
            for (int tile = 0; tile < pending.tilePixels.length; tile++) {
                int id = pending.firstTileId + tile;
                byte[] mip = pending.tilePixels[tile];
                for (int level = 0, size = 16; level < 5; level++, size >>= 1) {
                    pixels.clear().put(mip).flip();
                    TextureProxy.queueUploadRegion(new TextureProxy.TextureUploadRegion(
                        MemoryUtil.memAddress(pixels), mip.length, size, texture, 0, 0,
                        (id % 256) * size, (id / 256) * size, size, size, level,
                        TextureProxy.UPLOAD_KIND_MAIN, 4, generation));
                    if (size == 1) { break; }
                    byte[] next = new byte[size * size];
                    for (int y = 0; y < size / 2; y++) {
                        for (int x = 0; x < size / 2; x++) {
                            int[] sums = new int[4];
                            for (int dy = 0; dy < 2; dy++) {
                                for (int dx = 0; dx < 2; dx++) {
                                    int source = ((y * 2 + dy) * size + x * 2 + dx) * 4;
                                    for (int channel = 0; channel < 4; channel++) {
                                        sums[channel] += Byte.toUnsignedInt(mip[source + channel]) *
                                            (channel == 3 ? 1 : Byte.toUnsignedInt(mip[source + 3]));
                                    }
                                }
                            }
                            int target = (y * size / 2 + x) * 4;
                            for (int channel = 0; channel < 4; channel++) {
                                next[target + channel] = (byte) (channel == 3 ? (sums[channel] + 2) / 4 :
                                    sums[3] == 0 ? 128 : (sums[channel] + sums[3] / 2) / sums[3]);
                            }
                        }
                    }
                    mip = next;
                }
            }
            uploaded = pending.firstTileId + pending.tilePixels.length;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    public static void reset() {
        rows = 0;
        uploaded = 0;
    }

    public static void close() {
        if (texture != 0) { TextureProxy.releaseTexture(texture); }
        if (texture != 0 && UiBackendBuildConfig.OPENGL_UI) { TextureUtil.releaseTextureId(texture); }
        texture = 0;
        reset();
    }
}
