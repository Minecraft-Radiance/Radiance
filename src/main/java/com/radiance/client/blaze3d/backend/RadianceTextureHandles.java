package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

public final class RadianceTextureHandles {
    private RadianceTextureHandles() {
    }

    public static int textureId(GpuTexture texture) {
        return RadianceBackendUtil.textureId(texture);
    }

    public static long textureViewHandle(GpuTextureView view) {
        return RadianceBackendUtil.textureViewHandle(view);
    }
}
