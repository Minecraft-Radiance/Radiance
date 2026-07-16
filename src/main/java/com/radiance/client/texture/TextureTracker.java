package com.radiance.client.texture;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.texture.compat.TextureCompat;
import com.radiance.client.texture.compat.TextureMaterialCompat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TextureTracker {

    public static Map<Object, Integer> textureID2GLID = new ConcurrentHashMap<>();
    public static Map<Integer, Texture> GLID2Texture = new ConcurrentHashMap<>();
    public static Map<Integer, Integer> GLID2SpecularGLID = new ConcurrentHashMap<>();
    public static Map<Integer, Integer> GLID2NormalGLID = new ConcurrentHashMap<>();
    public static Map<Integer, Integer> GLID2FlagGLID = new ConcurrentHashMap<>();

    public static void clearPbrMaterials() {
        TextureMaterialCompat.clearPbrMaterials();
    }

    public static void registerPbrMaterial(Object spriteId, Object albedoPath, Object normalPath,
        Object specularPath, Object flagPath) {
        TextureMaterialCompat.registerPbrMaterial(spriteId, albedoPath, normalPath, specularPath,
            flagPath);
    }

    public static void clearAtlas(long atlasTexture) {
        TextureMaterialCompat.clearAtlas(atlasTexture);
    }

    public static void syncAtlasSprite(long atlasTexture, long atlasTextureView, Object atlasId,
        Object spriteId, float u0, float v0, float u1, float v1, int pixelX, int pixelY,
        int pixelWidth, int pixelHeight) {
        TextureMaterialCompat.syncAtlasSprite(atlasTexture, atlasTextureView, atlasId, spriteId,
            u0, v0, u1, v1, pixelX, pixelY, pixelWidth, pixelHeight);
    }

    public record Texture(int width, int height, int channel, VulkanConstants.VkFormat format,
                          int maxLayer) {

        public Texture {
            if (width <= 0 || height <= 0 || channel <= 0 || maxLayer < 0) {
                throw new IllegalArgumentException(
                    "Invalid texture width, height, channel, or maxLayer: " + width + ", " + height
                        + ", " + channel + ", " + maxLayer);
            }
        }

        public Texture(int width, int height, Object format, int maxLayer) {
            this(width, height, TextureCompat.channel(format), TextureCompat.vkFormat(format),
                maxLayer);
        }
    }
}
