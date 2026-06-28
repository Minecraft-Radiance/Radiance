package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.radiance.client.blaze3d.abi.Blaze3DProxy;

public final class RadianceGpuTextureView extends GpuTextureView {
    private final long handle;
    private boolean closed;

    RadianceGpuTextureView(long handle, GpuTexture texture, int baseMipLevel, int mipLevels) {
        super(texture, baseMipLevel, mipLevels);
        this.handle = handle;
    }

    long handle() {
        return this.handle;
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            Blaze3DProxy.closeTextureViewNative(this.handle);
        }
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }
}
