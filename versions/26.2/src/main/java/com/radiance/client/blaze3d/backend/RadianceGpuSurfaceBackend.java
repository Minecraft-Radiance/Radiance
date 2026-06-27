package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.systems.SurfaceException;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.radiance.client.blaze3d.abi.Blaze3DProxy;
import java.util.Collection;
import java.util.List;

public final class RadianceGpuSurfaceBackend implements GpuSurfaceBackend {
    private final long handle;

    RadianceGpuSurfaceBackend(long windowHandle, long handle) {
        this.handle = handle;
    }

    @Override
    public void configure(GpuSurface.Configuration config) throws SurfaceException {
        Blaze3DProxy.configureSurfaceNative(this.handle, config.width(), config.height(), 0, config.presentMode().ordinal());
    }

    @Override
    public boolean isSuboptimal() {
        return Blaze3DProxy.isSurfaceSuboptimalNative(this.handle);
    }

    @Override
    public void acquireNextTexture() throws SurfaceException {
        Blaze3DProxy.acquireNextTextureNative(this.handle);
    }

    @Override
    public void blitFromTexture(CommandEncoderBackend commandEncoder, GpuTextureView textureView) {
        long encoderHandle = commandEncoder instanceof RadianceCommandEncoderBackend radiance ? radiance.handle() : 0L;
        Blaze3DProxy.blitSurfaceFromTextureNative(this.handle, encoderHandle, RadianceBackendUtil.textureViewHandle(textureView));
    }

    @Override
    public void present() {
        Blaze3DProxy.presentSurfaceNative(this.handle);
    }

    @Override
    public void close() {
        Blaze3DProxy.closeSurfaceNative(this.handle);
    }

    @Override
    public Collection<GpuSurface.PresentMode> supportedPresentModes() {
        return List.of(GpuSurface.PresentMode.FIFO, GpuSurface.PresentMode.MAILBOX, GpuSurface.PresentMode.IMMEDIATE);
    }
}
