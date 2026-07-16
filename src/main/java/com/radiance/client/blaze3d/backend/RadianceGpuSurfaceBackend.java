package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.systems.SurfaceException;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.radiance.client.blaze3d.abi.Blaze3DProxy;
import com.radiance.client.blaze3d.scene.Blaze3DDebugTrace;
import java.util.Collection;
import java.util.List;

public final class RadianceGpuSurfaceBackend implements GpuSurfaceBackend {
    private final long handle;

    RadianceGpuSurfaceBackend(long windowHandle, long handle) {
        this.handle = handle;
    }

    @Override
    public void configure(GpuSurface.Configuration config) throws SurfaceException {
        Blaze3DDebugTrace.trace("surface.configure begin handle=" + this.handle + " size="
            + config.width() + "x" + config.height() + " presentMode=" + config.presentMode());
        try {
            Blaze3DProxy.configureSurfaceNative(this.handle, config.width(), config.height(), 0,
                config.presentMode().ordinal());
            Blaze3DDebugTrace.trace("surface.configure end handle=" + this.handle);
        } catch (Throwable throwable) {
            Blaze3DDebugTrace.failure("surface.configure handle=" + this.handle, throwable);
            throw throwable;
        }
    }

    @Override
    public boolean isSuboptimal() {
        return Blaze3DProxy.isSurfaceSuboptimalNative(this.handle);
    }

    @Override
    public void acquireNextTexture() throws SurfaceException {
        Blaze3DDebugTrace.trace("surface.acquireNextTexture begin handle=" + this.handle);
        try {
            Blaze3DProxy.acquireNextTextureNative(this.handle);
            Blaze3DDebugTrace.trace("surface.acquireNextTexture end handle=" + this.handle);
        } catch (Throwable throwable) {
            Blaze3DDebugTrace.failure("surface.acquireNextTexture handle=" + this.handle, throwable);
            throw throwable;
        }
    }

    @Override
    public void blitFromTexture(CommandEncoderBackend commandEncoder, GpuTextureView textureView) {
        long encoderHandle = commandEncoder instanceof RadianceCommandEncoderBackend radiance ? radiance.handle() : 0L;
        long textureViewHandle = RadianceBackendUtil.textureViewHandle(textureView);
        Blaze3DDebugTrace.trace("surface.blitFromTexture begin handle=" + this.handle
            + " encoder=" + encoderHandle + " textureView=" + textureViewHandle);
        try {
            Blaze3DProxy.blitSurfaceFromTextureNative(this.handle, encoderHandle, textureViewHandle);
            Blaze3DDebugTrace.trace("surface.blitFromTexture end handle=" + this.handle);
        } catch (Throwable throwable) {
            Blaze3DDebugTrace.failure("surface.blitFromTexture handle=" + this.handle, throwable);
            throw throwable;
        }
    }

    @Override
    public void present() {
        Blaze3DDebugTrace.traceAlways("surface.present begin handle=" + this.handle);
        try {
            Blaze3DProxy.presentSurfaceNative(this.handle);
            Blaze3DDebugTrace.traceAlways("surface.present end handle=" + this.handle);
        } catch (Throwable throwable) {
            Blaze3DDebugTrace.failure("surface.present handle=" + this.handle, throwable);
            throw throwable;
        }
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
