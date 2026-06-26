package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.systems.SurfaceException;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.radiance.client.blaze3d.abi.NativeBridge;
import java.util.Collection;
import java.util.List;

public final class RadianceGpuSurfaceBackend implements GpuSurfaceBackend {
    private final long windowHandle;
    private GpuSurface.Configuration configuration;
    private boolean closed;

    RadianceGpuSurfaceBackend(long windowHandle) {
        this.windowHandle = windowHandle;
    }

    @Override
    public void configure(GpuSurface.Configuration config) throws SurfaceException {
        if (this.closed) {
            throw new SurfaceException("Radiance surface is closed");
        }
        this.configuration = config;
    }

    @Override
    public boolean isSuboptimal() {
        return false;
    }

    @Override
    public void acquireNextTexture() throws SurfaceException {
        if (this.closed) {
            throw new SurfaceException("Radiance surface is closed");
        }
        NativeBridge.acquireContextNative();
    }

    @Override
    public void blitFromTexture(CommandEncoderBackend commandEncoder, GpuTextureView textureView) {
        // First version presents the Radiance renderer output; this call only satisfies GpuSurface lifecycle.
    }

    @Override
    public void present() {
        NativeBridge.presentNative();
    }

    @Override
    public void close() {
        this.closed = true;
    }

    @Override
    public Collection<GpuSurface.PresentMode> supportedPresentModes() {
        return List.of(GpuSurface.PresentMode.FIFO, GpuSurface.PresentMode.MAILBOX, GpuSurface.PresentMode.IMMEDIATE);
    }

    long windowHandle() {
        return this.windowHandle;
    }

    GpuSurface.Configuration configuration() {
        return this.configuration;
    }
}
