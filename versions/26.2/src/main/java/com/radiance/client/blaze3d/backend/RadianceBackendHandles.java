package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

public final class RadianceBackendHandles {
    private RadianceBackendHandles() {
    }

    public static long bufferHandle(GpuBuffer buffer) {
        return RadianceBackendUtil.bufferHandle(buffer);
    }

    public static long bufferHandle(GpuBufferSlice slice) {
        return RadianceBackendUtil.bufferHandle(slice);
    }

    public static long textureHandle(GpuTexture texture) {
        return RadianceBackendUtil.textureHandle(texture);
    }

    public static long textureViewHandle(GpuTextureView view) {
        return RadianceBackendUtil.textureViewHandle(view);
    }
}
