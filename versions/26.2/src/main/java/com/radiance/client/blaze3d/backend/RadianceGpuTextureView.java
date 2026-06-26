package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.radiance.client.blaze3d.abi.NativeBridge;

public final class RadianceGpuTextureView extends GpuTextureView {
    private final long handle;
    private boolean closed;

    RadianceGpuTextureView(GpuTexture texture, int baseMipLevel, int mipLevels) {
        super(texture, baseMipLevel, mipLevels);
        this.handle = NativeBridge.createTextureViewNative(RadianceBackendUtil.textureHandle(texture), baseMipLevel, mipLevels);
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
