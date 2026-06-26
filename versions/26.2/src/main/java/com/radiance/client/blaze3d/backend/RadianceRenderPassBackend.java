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
import com.radiance.client.blaze3d.abi.NativeBridge;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;

public final class RadianceRenderPassBackend implements RenderPassBackend {
    private static final int VK_DRAW_INDEXED_INDIRECT_COMMAND_SIZE = 20;
    private final RadianceGpuDeviceBackend device;
    private final long handle;
    private RadianceCompiledRenderPipeline pipeline;

    RadianceRenderPassBackend(RadianceGpuDeviceBackend device, long handle) {
        this.device = device;
        this.handle = handle;
    }

    long handle() {
        return this.handle;
    }

    @Override
    public void pushDebugGroup(Supplier<String> label) {
    }

    @Override
    public void popDebugGroup() {
    }

    @Override
    public void setPipeline(RenderPipeline pipeline) {
        this.pipeline = this.device.compilePipeline(pipeline);
        NativeBridge.setPipelineNative(this.pipeline.handle());
    }

    @Override
    public void bindTexture(String name, @Nullable GpuTextureView textureView, @Nullable GpuSampler sampler) {
        long textureViewHandle = textureView == null ? 0L : RadianceBackendUtil.textureViewHandle(textureView);
        long samplerHandle = sampler == null ? 0L : RadianceBackendUtil.samplerHandle(sampler);
        NativeBridge.bindTextureNative(name, textureViewHandle, samplerHandle);
    }

    @Override
    public void setUniform(String name, GpuBuffer value) {
        this.setUniform(name, value.slice());
    }

    @Override
    public void setUniform(String name, GpuBufferSlice value) {
        NativeBridge.setUniformNative(name, RadianceBackendUtil.bufferHandle(value), value.offset(), value.length());
    }

    @Override
    public void enableScissor(int x, int y, int width, int height) {
    }

    @Override
    public void disableScissor() {
    }

    @Override
    public void setVertexBuffer(int slot, @Nullable GpuBufferSlice vertexBuffer) {
        if (vertexBuffer == null) {
            NativeBridge.setVertexBufferNative(slot, 0L, 0L, 0L);
        } else {
            NativeBridge.setVertexBufferNative(slot, RadianceBackendUtil.bufferHandle(vertexBuffer), vertexBuffer.offset(), vertexBuffer.length());
        }
    }

    @Override
    public void setIndexBuffer(GpuBuffer indexBuffer, IndexType indexType) {
        NativeBridge.setIndexBufferNative(RadianceBackendUtil.bufferHandle(indexBuffer), RadianceBackendUtil.indexType(indexType));
    }

    @Override
    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
        NativeBridge.drawIndexedNative(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }

    @Override
    public void multiDrawIndexed(IntBuffer drawParameters, int instanceCount, int firstInstance, int drawCount) {
        IntBuffer params = drawParameters.duplicate();
        for (int i = 0; i < drawCount; i++) {
            int firstIndex = params.get();
            int indexCount = params.get();
            int vertexOffset = params.get();
            this.drawIndexed(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
        }
    }

    @Override
    public void multiDrawIndexed(PointerBuffer firstIndexOffsets, IntBuffer indexCounts, IntBuffer vertexOffsets, int drawCount) {
        PointerBuffer firstIndices = firstIndexOffsets.duplicate();
        IntBuffer counts = indexCounts.duplicate();
        IntBuffer vertices = vertexOffsets.duplicate();
        for (int i = 0; i < drawCount; i++) {
            long firstIndexOffset = firstIndices.get();
            int firstIndex = firstIndexOffset > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)firstIndexOffset;
            this.drawIndexed(counts.get(), 1, firstIndex, vertices.get(), 0);
        }
    }

    @Override
    public void drawIndexedIndirect(GpuBufferSlice commands, int drawCount) {
        if (!(commands.buffer() instanceof RadianceGpuBuffer buffer)) {
            return;
        }
        ByteBuffer indirect = buffer.readShadow(commands.offset(), (long)drawCount * VK_DRAW_INDEXED_INDIRECT_COMMAND_SIZE);
        indirect.order(ByteOrder.nativeOrder());
        for (int i = 0; i < drawCount; i++) {
            int base = i * VK_DRAW_INDEXED_INDIRECT_COMMAND_SIZE;
            int indexCount = indirect.getInt(base);
            int instanceCount = indirect.getInt(base + 4);
            int firstIndex = indirect.getInt(base + 8);
            int vertexOffset = indirect.getInt(base + 12);
            int firstInstance = indirect.getInt(base + 16);
            this.drawIndexed(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
        }
    }

    @Override
    public <T> void drawMultipleIndexed(Collection<RenderPass.Draw<T>> draws, @Nullable GpuBuffer defaultIndexBuffer,
                                        @Nullable IndexType defaultIndexType, Collection<String> dynamicUniforms, T uniformArgument) {
        for (RenderPass.Draw<T> draw : draws) {
            BiConsumer<T, RenderPass.UniformUploader> uniformUploader = draw.uniformUploaderConsumer();
            if (uniformUploader != null) {
                uniformUploader.accept(uniformArgument, this::setUniform);
            }
            GpuBuffer indexBuffer = draw.indexBuffer() == null ? defaultIndexBuffer : draw.indexBuffer();
            IndexType indexType = draw.indexType() == null ? defaultIndexType : draw.indexType();
            if (indexBuffer != null && indexType != null) {
                this.setIndexBuffer(indexBuffer, indexType);
            }
            this.setVertexBuffer(draw.slot(), draw.vertexBuffer().slice());
            this.drawIndexed(draw.indexCount(), 1, draw.firstIndex(), draw.baseVertex(), 0);
        }
    }

    @Override
    public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        // Non-indexed draws do not have enough stable scene payload in the first version.
    }

    @Override
    public void multiDraw(IntBuffer drawParameters, int instanceCount, int firstInstance, int drawCount) {
    }

    @Override
    public void multiDraw(IntBuffer firstVertices, IntBuffer vertexCounts, int drawCount) {
    }

    @Override
    public void drawIndirect(GpuBufferSlice commands, int drawCount) {
    }

    @Override
    public void writeTimestamp(GpuQueryPool pool, int index) {
        if (pool instanceof RadianceGpuQueryPool radiancePool) {
            radiancePool.write(index);
        }
    }
}
