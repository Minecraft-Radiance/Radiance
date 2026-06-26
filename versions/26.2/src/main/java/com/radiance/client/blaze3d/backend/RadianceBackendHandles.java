package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.textures.GpuTextureView;

public final class RadianceBackendHandles {
    private RadianceBackendHandles() {
    }

    public static long textureViewHandle(GpuTextureView view) {
        return RadianceBackendUtil.textureViewHandle(view);
    }
}
