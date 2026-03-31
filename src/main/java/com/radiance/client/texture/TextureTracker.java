package com.radiance.client.texture;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

public class TextureTracker {

    public static final int MAX_TEXTURE_SLOTS = 4096;
    public static Map<Identifier, Integer> textureID2GLID = new ConcurrentHashMap<>();
    public static Map<Integer, Texture> GLID2Texture = new ConcurrentHashMap<>();
    public static Map<Integer, Integer> GLID2SpecularGLID = new ConcurrentHashMap<>();
    public static Map<Integer, Integer> GLID2NormalGLID = new ConcurrentHashMap<>();
    public static Map<Integer, Integer> GLID2FlagGLID = new ConcurrentHashMap<>();
    private static final AtomicInteger RENDER_LAYER_TEXTURE_CACHE_EPOCH = new AtomicInteger();
    private static final Map<RenderLayer, CachedLayerTexture> RENDER_LAYER_TEXTURES =
        new ConcurrentHashMap<>();

    private record CachedLayerTexture(int glId, int epoch) {

    }

    public static void trackTextureRegistration(Identifier id, int glId) {
        textureID2GLID.put(id, glId);
        invalidateRenderLayerTextureCache();
    }

    public static void invalidateRenderLayerTextureCache() {
        RENDER_LAYER_TEXTURE_CACHE_EPOCH.incrementAndGet();
        RENDER_LAYER_TEXTURES.clear();
    }

    public static void releaseTextureRegistration(int glId) {
        releaseTextureRegistration(glId, new HashSet<>());
    }

    private static void releaseTextureRegistration(int glId, HashSet<Integer> released) {
        if (glId < 0 || !released.add(glId)) {
            return;
        }

        textureID2GLID.entrySet().removeIf(entry -> entry.getValue() != null && entry.getValue() == glId);

        Integer specularId = GLID2SpecularGLID.remove(glId);
        Integer normalId = GLID2NormalGLID.remove(glId);
        Integer flagId = GLID2FlagGLID.remove(glId);

        GLID2Texture.remove(glId);

        GLID2SpecularGLID.entrySet().removeIf(entry -> entry.getValue() != null && entry.getValue() == glId);
        GLID2NormalGLID.entrySet().removeIf(entry -> entry.getValue() != null && entry.getValue() == glId);
        GLID2FlagGLID.entrySet().removeIf(entry -> entry.getValue() != null && entry.getValue() == glId);

        invalidateRenderLayerTextureCache();

        if (specularId != null) {
            releaseTextureRegistration(specularId, released);
        }
        if (normalId != null) {
            releaseTextureRegistration(normalId, released);
        }
        if (flagId != null) {
            releaseTextureRegistration(flagId, released);
        }

        TextureProxy.releaseTextureId(glId);
    }

    public static int getTextureGlId(Identifier identifier, TextureManager textureManager) {
        Integer trackedGlId = textureID2GLID.get(identifier);
        if (trackedGlId != null) {
            return trackedGlId;
        }

        int glId = textureManager.getTexture(identifier).getGlId();
        textureID2GLID.put(identifier, glId);
        return glId;
    }

    public static int getRenderLayerTextureGlId(RenderLayer renderLayer,
        TextureManager textureManager, Identifier fallbackIdentifier) {
        int epoch = RENDER_LAYER_TEXTURE_CACHE_EPOCH.get();
        CachedLayerTexture cachedLayerTexture = RENDER_LAYER_TEXTURES.get(renderLayer);
        if (cachedLayerTexture != null && cachedLayerTexture.epoch() == epoch) {
            return cachedLayerTexture.glId();
        }

        Identifier identifier = fallbackIdentifier;
        if (renderLayer instanceof RenderLayer.MultiPhase multiPhase) {
            identifier = multiPhase.phases.texture.getId().orElse(fallbackIdentifier);
        }

        int glId = getTextureGlId(identifier, textureManager);
        RENDER_LAYER_TEXTURES.put(renderLayer, new CachedLayerTexture(glId, epoch));
        return glId;
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

        public Texture(int width, int height, NativeImage.InternalFormat format, int maxLayer) {
            this(width, height, getChannel(format), getFormat(format), maxLayer);
        }

        private static int getChannel(NativeImage.InternalFormat internalFormat) {
            return switch (internalFormat) {
                case RGBA -> 4;
                case RGB -> 3;
                case RG -> 2;
                case RED -> 1;
                default -> throw new IllegalArgumentException(
                    "Unknown internal format: " + internalFormat);
            };
        }

        private static VulkanConstants.VkFormat getFormat(
            NativeImage.InternalFormat internalFormat) {
            return switch (internalFormat) {
                case RGBA -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_SRGB;
                case RGB -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8_SRGB;
                case RG -> VulkanConstants.VkFormat.VK_FORMAT_R8G8_SRGB;
                case RED -> VulkanConstants.VkFormat.VK_FORMAT_R8_SRGB;
            };
        }
    }
}
