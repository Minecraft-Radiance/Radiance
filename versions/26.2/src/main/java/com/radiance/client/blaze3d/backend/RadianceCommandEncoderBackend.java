package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.TransientMemory;
import com.mojang.blaze3d.textures.GpuTexture;
import com.radiance.client.blaze3d.abi.NativeBridge;
import java.nio.ByteBuffer;
import org.joml.Vector4fc;

public final class RadianceCommandEncoderBackend implements CommandEncoderBackend {
    private final RadianceGpuDeviceBackend device;
    private final RadianceTransientMemory transientMemory;
    private RadianceRenderPassBackend activeRenderPass;

    RadianceCommandEncoderBackend(RadianceGpuDeviceBackend device) {
        this.device = device;
        this.transientMemory = new RadianceTransientMemory(device);
    }

    @Override
    public void submit() {
        NativeBridge.submitCommandNative();
    }

    @Override
    public TransientMemory transientMemory() {
        return this.transientMemory;
    }

    @Override
    public RenderPassBackend createRenderPass(RenderPassDescriptor descriptor) {
        long colorView = 0L;
        boolean hasClearColor = false;
        float clearR = 0.0F;
        float clearG = 0.0F;
        float clearB = 0.0F;
        float clearA = 0.0F;
        if (!descriptor.colorAttachments().isEmpty() && descriptor.colorAttachments().get(0) != null) {
            RenderPassDescriptor.Attachment<java.util.Optional<Vector4fc>> color = descriptor.colorAttachments().get(0);
            colorView = RadianceBackendUtil.textureViewHandle(color.textureView());
            if (color.clearValue().isPresent()) {
                Vector4fc value = color.clearValue().get();
                hasClearColor = true;
                clearR = value.x();
                clearG = value.y();
                clearB = value.z();
                clearA = value.w();
            }
        }

        long depthView = 0L;
        boolean hasClearDepth = false;
        double clearDepth = 0.0;
        if (descriptor.depthAttachment() != null) {
            depthView = RadianceBackendUtil.textureViewHandle(descriptor.depthAttachment().textureView());
            hasClearDepth = descriptor.depthAttachment().clearValue().isPresent();
            clearDepth = descriptor.depthAttachment().clearValue().orElse(0.0);
        }

        RenderPass.RenderArea area = descriptor.renderArea;
        long handle = NativeBridge.createRenderPassNative(descriptor.label().get(), colorView, hasClearColor,
            clearR, clearG, clearB, clearA, depthView, hasClearDepth, clearDepth,
            area == null ? 0 : area.x(), area == null ? 0 : area.y(), area == null ? 0 : area.width(), area == null ? 0 : area.height());
        this.activeRenderPass = new RadianceRenderPassBackend(this.device, handle);
        NativeBridge.beginRenderPassNative(handle);
        return this.activeRenderPass;
    }

    @Override
    public void submitRenderPass() {
        if (this.activeRenderPass != null) {
            NativeBridge.endRenderPassNative();
            this.activeRenderPass = null;
        }
    }

    @Override
    public void clearColorTexture(GpuTexture colorTexture, Vector4fc clearColor) {
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture colorTexture, Vector4fc clearColor, GpuTexture depthTexture, double clearDepth) {
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture colorTexture, Vector4fc clearColor, GpuTexture depthTexture,
                                           double clearDepth, int regionX, int regionY, int regionWidth, int regionHeight) {
    }

    @Override
    public void clearDepthTexture(GpuTexture depthTexture, double clearDepth) {
    }

    @Override
    public void writeToBuffer(GpuBufferSlice destination, ByteBuffer data) {
        if (destination.buffer() instanceof RadianceGpuBuffer buffer) {
            buffer.write(destination.offset(), data);
        }
    }

    @Override
    public void copyToBuffer(GpuBufferSlice source, GpuBufferSlice target) {
        if (source.buffer() instanceof RadianceGpuBuffer sourceBuffer && target.buffer() instanceof RadianceGpuBuffer targetBuffer) {
            targetBuffer.copyFrom(sourceBuffer, source.offset(), target.offset(), source.length());
        }
    }

    @Override
    public void writeToTexture(GpuTexture destination, ByteBuffer source, int mipLevel, int depthOrLayer,
                               int destX, int destY, int width, int height) {
    }

    @Override
    public void copyBufferToTexture(GpuBufferSlice source, int sourceX, int sourceY, int sourceWidth, int sourceHeight,
                                    GpuTexture destination, int destinationX, int destinationY, int copyWidth,
                                    int copyHeight, int mipLevel, int arrayLayer) {
    }

    @Override
    public void copyTextureToBuffer(GpuTexture source, GpuBuffer destination, long offset, Runnable callback, int mipLevel) {
        callback.run();
    }

    @Override
    public void copyTextureToBuffer(GpuTexture source, GpuBuffer destination, long offset, Runnable callback,
                                    int mipLevel, int x, int y, int width, int height) {
        callback.run();
    }

    @Override
    public void copyTextureToTexture(GpuTexture source, GpuTexture destination, int mipLevel,
                                     int destX, int destY, int sourceX, int sourceY, int width, int height) {
    }

    @Override
    public GpuFence createFence() {
        return new RadianceGpuFence();
    }

    @Override
    public void writeTimestamp(GpuQueryPool pool, int index) {
        if (pool instanceof RadianceGpuQueryPool radiancePool) {
            radiancePool.write(index);
        }
    }
}
