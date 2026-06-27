package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;
import com.radiance.client.blaze3d.abi.Blaze3DProxy;

public final class RadianceGpuTexture extends GpuTexture {
    private final long handle;
    private boolean closed;

    RadianceGpuTexture(long handle, @GpuTexture.Usage int usage, String label, GpuFormat format,
                       int width, int height, int depthOrLayers, int mipLevels) {
        super(usage, label, format, width, height, depthOrLayers, mipLevels);
        this.handle = handle;
    }

    long handle() {
        return this.handle;
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
