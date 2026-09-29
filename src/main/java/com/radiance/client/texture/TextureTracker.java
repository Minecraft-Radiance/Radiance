package com.radiance.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.constant.VulkanConstants;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;

public class TextureTracker {

    private static final AtomicLong nextTextureGeneration = new AtomicLong();

    public static Map<ResourceLocation, Integer> textureID2GLID = new ConcurrentHashMap<>();
    public static Map<Integer, Texture> GLID2Texture = new ConcurrentHashMap<>();
    public static Map<Integer, TextureBinding> GLID2SpecularGLID = new ConcurrentHashMap<>();
    public static Map<Integer, TextureBinding> GLID2NormalGLID = new ConcurrentHashMap<>();
    public static Map<Integer, TextureBinding> GLID2FlagGLID = new ConcurrentHashMap<>();

    private static final List<Map<Integer, TextureBinding>> auxiliaryBindings = List.of(
        GLID2SpecularGLID, GLID2NormalGLID, GLID2FlagGLID);

    public record TextureTarget(int id, long generation) {
    }

    public record TextureBinding(TextureTarget base, TextureTarget auxiliary) {
    }

    public static TextureTarget captureTarget(int id) {
        Texture texture = GLID2Texture.get(id);
        if (texture == null) {
            throw new IllegalStateException("Texture " + id + " was not prepared before upload");
        }
        return new TextureTarget(id, texture.generation());
    }

    public static Texture currentTexture(TextureTarget target) {
        if (target == null) {
            return null;
        }
        Texture texture = GLID2Texture.get(target.id());
        return texture != null && texture.generation() == target.generation() ? texture : null;
    }

    public static boolean isCurrent(TextureTarget target) {
        return currentTexture(target) != null;
    }

    public static boolean isCurrent(TextureBinding binding) {
        return binding != null && isCurrent(binding.base()) && isCurrent(binding.auxiliary());
    }

    public static synchronized void registerTexture(TextureTarget target, Texture texture) {
        if (target == null || texture == null) {
            throw new IllegalArgumentException("Texture target and metadata are required");
        }
        if (target.id() < 0) {
            throw new IllegalArgumentException("Invalid texture target id: " + target.id());
        }
        if (target.generation() != texture.generation()) {
            throw new IllegalArgumentException(
                "Texture target generation does not match its metadata: " + target.generation()
                    + " != " + texture.generation());
        }

        Texture previous = GLID2Texture.put(target.id(), texture);
        if (previous != null && previous.generation() != texture.generation()) {
            removeBindingsForId(target.id());
        }
    }

    public static synchronized void releaseTexture(TextureTarget target) {
        if (target == null) {
            return;
        }
        Texture current = GLID2Texture.get(target.id());
        if (current == null || current.generation() != target.generation()) {
            return;
        }
        GLID2Texture.remove(target.id(), current);
        removeBindingsForTarget(target);
    }

    public static synchronized void bindAuxiliary(Map<Integer, TextureBinding> bindings,
        TextureTarget base, TextureTarget auxiliary) {
        if (base == null) {
            return;
        }
        if (!isCurrent(base) || !isCurrent(auxiliary)) {
            bindings.remove(base.id());
            return;
        }
        bindings.put(base.id(), new TextureBinding(base, auxiliary));
    }

    private static void removeBindingsForId(int id) {
        for (Map<Integer, TextureBinding> bindings : auxiliaryBindings) {
            bindings.entrySet().removeIf(entry -> entry.getKey() == id
                || entry.getValue().base().id() == id
                || entry.getValue().auxiliary().id() == id);
        }
    }

    private static void removeBindingsForTarget(TextureTarget target) {
        for (Map<Integer, TextureBinding> bindings : auxiliaryBindings) {
            bindings.entrySet().removeIf(entry -> entry.getValue().base().equals(target)
                || entry.getValue().auxiliary().equals(target));
        }
    }

    public record Texture(int width, int height, int channel, VulkanConstants.VkFormat format,
                          int maxLayer, long generation) {

        public Texture {
            if (width <= 0 || height <= 0 || channel <= 0 || maxLayer < 0) {
                throw new IllegalArgumentException(
                    "Invalid texture width, height, channel, or maxLayer: " + width + ", " + height
                        + ", " + channel + ", " + maxLayer);
            }
        }

        public Texture(int width, int height, NativeImage.InternalGlFormat format, int maxLayer) {
            this(width, height, getChannel(format), getFormat(format), maxLayer,
                nextTextureGeneration.incrementAndGet());
        }

        public Texture(int width, int height, int channel, VulkanConstants.VkFormat format,
            int maxLayer) {
            this(width, height, channel, format, maxLayer,
                nextTextureGeneration.incrementAndGet());
        }

        private static int getChannel(NativeImage.InternalGlFormat internalFormat) {
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
            NativeImage.InternalGlFormat internalFormat) {
            return switch (internalFormat) {
                case RGBA -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM;
                case RGB -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8_UNORM;
                case RG -> VulkanConstants.VkFormat.VK_FORMAT_R8G8_UNORM;
                case RED -> VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM;
            };
        }
    }
}
