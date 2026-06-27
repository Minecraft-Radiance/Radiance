package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.radiance.client.blaze3d.abi.Blaze3DProxy;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;

public final class RadianceRenderPassBackend implements RenderPassBackend {
    private final long handle;

    RadianceRenderPassBackend(long handle) {
        this.handle = handle;
    }

    @Override
    public void pushDebugGroup(Supplier<String> label) {
        Blaze3DProxy.pushDebugGroupNative(this.handle, label == null ? "" : label.get());
    }

    @Override
    public void popDebugGroup() {
        Blaze3DProxy.popDebugGroupNative(this.handle);
    }

    @Override
    public void setPipeline(RenderPipeline pipeline) {
        Blaze3DProxy.setPipelineNative(this.handle, 0L);
    }

    @Override
    public void bindTexture(String name, @Nullable GpuTextureView textureView, @Nullable GpuSampler sampler) {
        Blaze3DProxy.bindTextureNative(this.handle, name, RadianceBackendUtil.textureViewHandle(textureView), RadianceBackendUtil.samplerHandle(sampler));
    }

    @Override
    public void setUniform(String name, GpuBuffer value) {
        this.setUniform(name, value.slice());
    }

    @Override
    public void setUniform(String name, GpuBufferSlice value) {
        Blaze3DProxy.setUniformNative(this.handle, name, RadianceBackendUtil.bufferHandle(value), value.offset(), value.length());
    }

    @Override
    public void enableScissor(int x, int y, int width, int height) {
        Blaze3DProxy.enableScissorNative(this.handle, x, y, width, height);
    }

    @Override
    public void disableScissor() {
        Blaze3DProxy.disableScissorNative(this.handle);
    }

    @Override
    public void setVertexBuffer(int slot, @Nullable GpuBufferSlice vertexBuffer) {
        if (vertexBuffer == null) {
            Blaze3DProxy.setVertexBufferNative(this.handle, slot, 0L, 0L, 0L);
        } else {
            Blaze3DProxy.setVertexBufferNative(this.handle, slot, RadianceBackendUtil.bufferHandle(vertexBuffer), vertexBuffer.offset(), vertexBuffer.length());
        }
    }

    @Override
    public void setIndexBuffer(GpuBuffer indexBuffer, IndexType indexType) {
        Blaze3DProxy.setIndexBufferNative(this.handle, RadianceBackendUtil.bufferHandle(indexBuffer), RadianceBackendUtil.indexType(indexType));
    }

    @Override
    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
        Blaze3DProxy.drawIndexedNative(this.handle, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }

    @Override
    public void multiDrawIndexed(IntBuffer drawParameters, int instanceCount, int firstInstance, int drawCount) {
        Blaze3DProxy.multiDrawIndexedNative(this.handle, instanceCount, firstInstance, drawCount);
    }

    @Override
    public void multiDrawIndexed(PointerBuffer firstIndexOffsets, IntBuffer indexCounts, IntBuffer vertexOffsets, int drawCount) {
        Blaze3DProxy.multiDrawIndexedNative(this.handle, 1, 0, drawCount);
    }

    @Override
    public void drawIndexedIndirect(GpuBufferSlice commands, int drawCount) {
        Blaze3DProxy.drawIndexedIndirectNative(this.handle, RadianceBackendUtil.bufferHandle(commands), commands.offset(), commands.length(), drawCount);
    }

    @Override
    public <T> void drawMultipleIndexed(Collection<RenderPass.Draw<T>> draws, @Nullable GpuBuffer defaultIndexBuffer,
                                        @Nullable IndexType defaultIndexType, Collection<String> dynamicUniforms, T uniformArgument) {
        Blaze3DProxy.multiDrawIndexedNative(this.handle, 1, 0, draws.size());
    }

    @Override
    public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        Blaze3DProxy.drawNative(this.handle, vertexCount, instanceCount, firstVertex, firstInstance);
    }

    @Override
    public void multiDraw(IntBuffer drawParameters, int instanceCount, int firstInstance, int drawCount) {
        Blaze3DProxy.multiDrawNative(this.handle, instanceCount, firstInstance, drawCount);
    }

    @Override
    public void multiDraw(IntBuffer firstVertices, IntBuffer vertexCounts, int drawCount) {
        Blaze3DProxy.multiDrawNative(this.handle, 1, 0, drawCount);
    }

    @Override
    public void drawIndirect(GpuBufferSlice commands, int drawCount) {
        Blaze3DProxy.drawIndirectNative(this.handle, RadianceBackendUtil.bufferHandle(commands), commands.offset(), commands.length(), drawCount);
    }

    @Override
    public void writeTimestamp(GpuQueryPool pool, int index) {
        long poolHandle = pool instanceof RadianceGpuQueryPool radiance ? radiance.handle() : 0L;
        Blaze3DProxy.writeTimestampNative(poolHandle, index);
    }
}
