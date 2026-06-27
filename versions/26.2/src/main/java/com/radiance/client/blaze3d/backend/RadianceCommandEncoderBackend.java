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
import com.radiance.client.blaze3d.abi.Blaze3DProxy;
import java.nio.ByteBuffer;
import org.joml.Vector4fc;

public final class RadianceCommandEncoderBackend implements CommandEncoderBackend {
    private final long handle;
    private final RadianceTransientMemory transientMemory = new RadianceTransientMemory();

    RadianceCommandEncoderBackend(long handle) {
        this.handle = handle;
    }

    long handle() {
        return this.handle;
    }

    @Override
    public void submit() {
        Blaze3DProxy.submitCommandEncoderNative(this.handle);
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
        long renderPass = Blaze3DProxy.createRenderPassNative(descriptor.label().get(), colorView, hasClearColor,
            clearR, clearG, clearB, clearA, depthView, hasClearDepth, clearDepth,
            area == null ? 0 : area.x(), area == null ? 0 : area.y(), area == null ? 0 : area.width(), area == null ? 0 : area.height());
        return new RadianceRenderPassBackend(renderPass);
    }

    @Override
    public void submitRenderPass() {
        Blaze3DProxy.submitRenderPassNative(this.handle);
    }

    @Override
    public void clearColorTexture(GpuTexture colorTexture, Vector4fc clearColor) {
        Blaze3DProxy.clearColorTextureNative(RadianceBackendUtil.textureHandle(colorTexture), clearColor.x(), clearColor.y(), clearColor.z(), clearColor.w());
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture colorTexture, Vector4fc clearColor, GpuTexture depthTexture, double clearDepth) {
        Blaze3DProxy.clearColorAndDepthTexturesNative(RadianceBackendUtil.textureHandle(colorTexture), clearColor.x(), clearColor.y(), clearColor.z(), clearColor.w(),
            RadianceBackendUtil.textureHandle(depthTexture), clearDepth);
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture colorTexture, Vector4fc clearColor, GpuTexture depthTexture,
                                           double clearDepth, int regionX, int regionY, int regionWidth, int regionHeight) {
        Blaze3DProxy.clearColorAndDepthTexturesRegionNative(RadianceBackendUtil.textureHandle(colorTexture), clearColor.x(), clearColor.y(), clearColor.z(), clearColor.w(),
            RadianceBackendUtil.textureHandle(depthTexture), clearDepth, regionX, regionY, regionWidth, regionHeight);
    }

    @Override
    public void clearDepthTexture(GpuTexture depthTexture, double clearDepth) {
        Blaze3DProxy.clearDepthTextureNative(RadianceBackendUtil.textureHandle(depthTexture), clearDepth);
    }

    @Override
    public void writeToBuffer(GpuBufferSlice destination, ByteBuffer data) {
        ByteBuffer bytes = data.duplicate();
        Blaze3DProxy.writeToBufferNative(RadianceBackendUtil.bufferHandle(destination), destination.offset(), bytes, bytes.position(), bytes.remaining());
    }

    @Override
    public void copyToBuffer(GpuBufferSlice source, GpuBufferSlice target) {
        Blaze3DProxy.copyToBufferNative(RadianceBackendUtil.bufferHandle(source), source.offset(), RadianceBackendUtil.bufferHandle(target), target.offset(), source.length());
    }

    @Override
    public void writeToTexture(GpuTexture destination, ByteBuffer source, int mipLevel, int depthOrLayer,
                               int destX, int destY, int width, int height) {
        ByteBuffer bytes = source.duplicate();
        Blaze3DProxy.writeToTextureNative(RadianceBackendUtil.textureHandle(destination), bytes, mipLevel, depthOrLayer, destX, destY, width, height, bytes.position());
    }

    @Override
    public void copyBufferToTexture(GpuBufferSlice source, int sourceX, int sourceY, int sourceWidth, int sourceHeight,
                                    GpuTexture destination, int destinationX, int destinationY, int copyWidth,
                                    int copyHeight, int mipLevel, int arrayLayer) {
        Blaze3DProxy.copyBufferToTextureNative(RadianceBackendUtil.bufferHandle(source), source.offset(), sourceX, sourceY, sourceWidth, sourceHeight,
            RadianceBackendUtil.textureHandle(destination), destinationX, destinationY, copyWidth, copyHeight, mipLevel, arrayLayer);
    }

    @Override
    public void copyTextureToBuffer(GpuTexture source, GpuBuffer destination, long offset, Runnable callback, int mipLevel) {
        Blaze3DProxy.copyTextureToBufferNative(RadianceBackendUtil.textureHandle(source), RadianceBackendUtil.bufferHandle(destination), offset, mipLevel, 0, 0,
            source.getWidth(mipLevel), source.getHeight(mipLevel));
        callback.run();
    }

    @Override
    public void copyTextureToBuffer(GpuTexture source, GpuBuffer destination, long offset, Runnable callback,
                                    int mipLevel, int x, int y, int width, int height) {
        Blaze3DProxy.copyTextureToBufferNative(RadianceBackendUtil.textureHandle(source), RadianceBackendUtil.bufferHandle(destination), offset, mipLevel, x, y, width, height);
        callback.run();
    }

    @Override
    public void copyTextureToTexture(GpuTexture source, GpuTexture destination, int mipLevel,
                                     int destX, int destY, int sourceX, int sourceY, int width, int height) {
        Blaze3DProxy.copyTextureToTextureNative(RadianceBackendUtil.textureHandle(source), RadianceBackendUtil.textureHandle(destination), mipLevel,
            destX, destY, sourceX, sourceY, width, height);
    }

    @Override
    public GpuFence createFence() {
        return new RadianceGpuFence(Blaze3DProxy.createFenceNative());
    }

    @Override
    public void writeTimestamp(GpuQueryPool pool, int index) {
        long poolHandle = pool instanceof RadianceGpuQueryPool radiance ? radiance.handle() : 0L;
        Blaze3DProxy.writeTimestampNative(poolHandle, index);
    }
}
