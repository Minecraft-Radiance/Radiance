package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.GpuTexture;

public final class RadianceBackendHandles {
    private RadianceBackendHandles() {
    }

    public static long textureViewHandle(GpuTextureView view) {
        return RadianceBackendUtil.textureViewHandle(view);
    }

    public static long textureHandle(GpuTexture texture) {
        return RadianceBackendUtil.textureHandle(texture);
    }
}
