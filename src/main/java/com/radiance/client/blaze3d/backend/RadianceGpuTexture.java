package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;
import com.radiance.client.blaze3d.abi.Blaze3DProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.texture.compat.TextureCompat;

public final class RadianceGpuTexture extends GpuTexture {
    private final long handle;
    private final int textureId;
    private final boolean bindlessSampled;
    private boolean closed;

    RadianceGpuTexture(long handle, @GpuTexture.Usage int usage, String label, GpuFormat format,
                       int width, int height, int depthOrLayers, int mipLevels) {
        super(usage, label, format, width, height, depthOrLayers, mipLevels);
        this.handle = handle;
        this.bindlessSampled = canMirrorToBindless(usage, label, format, width, height, depthOrLayers, mipLevels);
        if (this.bindlessSampled) {
            this.textureId = TextureProxy.generateTextureId();
            TextureProxy.prepareImage(this.textureId, mipLevels, width, height, TextureCompat.vkFormat(format));
            Blaze3DProxy.registerTextureRadianceIdNative(this.handle, this.textureId);
            TextureTracker.GLID2Texture.put(this.textureId,
                new TextureTracker.Texture(width, height, format, mipLevels));
        } else {
            this.textureId = 0;
            Blaze3DProxy.registerTextureRadianceIdNative(this.handle, 0);
        }
    }

    long handle() {
        return this.handle;
    }

    int textureId() {
        return this.textureId;
    }

    boolean bindlessSampled() {
        return this.bindlessSampled;
    }

    private static boolean canMirrorToBindless(@GpuTexture.Usage int usage, String label, GpuFormat format,
                                               int width, int height, int depthOrLayers, int mipLevels) {
        if (format == null || !format.hasColorAspect()) {
            return false;
        }
        if (depthOrLayers != 1) {
            return false;
        }
        if ((usage & GpuTexture.USAGE_COPY_DST) == 0) {
            return false;
        }
        if (width <= 0 || height <= 0 || mipLevels <= 0 || mipLevels > maxMipLevels(width, height)) {
            return false;
        }

        String normalizedLabel = label == null ? "" : label;
        if (normalizedLabel.contains(" animation frame ") || normalizedLabel.startsWith("UI ")
            || normalizedLabel.contains(" / Color") || normalizedLabel.contains(" / Depth")
            || normalizedLabel.contains(" depth")) {
            return false;
        }

        return normalizedLabel.contains(":textures/") || normalizedLabel.startsWith("textures/")
            || normalizedLabel.equals("Lightmap");
    }

    private static int maxMipLevels(int width, int height) {
        int levels = 1;
        int size = Math.max(width, height);
        while (size > 1) {
            size >>= 1;
            levels++;
        }
        return levels;
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            Blaze3DProxy.closeTextureNative(this.handle);
        }
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }
}
