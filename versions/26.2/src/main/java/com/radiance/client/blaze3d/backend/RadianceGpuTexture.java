package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;
import com.radiance.client.blaze3d.abi.NativeBridge;

public final class RadianceGpuTexture extends GpuTexture {
    private final long handle;
    private boolean closed;

    RadianceGpuTexture(@GpuTexture.Usage int usage, String label, GpuFormat format, int width, int height, int depthOrLayers, int mipLevels) {
        super(usage, RadianceBackendUtil.label(label), format, width, height, depthOrLayers, mipLevels);
        this.handle = NativeBridge.createTextureNative(
            RadianceBackendUtil.label(label), usage, RadianceBackendUtil.gpuFormat(format), width, height, depthOrLayers, mipLevels);
    }

    long handle() {
        return this.handle;
    }

    @Override
    public void close() {
        this.closed = true;
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }
}
