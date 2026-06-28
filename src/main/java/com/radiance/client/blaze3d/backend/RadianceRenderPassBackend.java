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
    private IndexType currentIndexType = IndexType.INT;

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
        long pipelineHandle = 0L;
        if (pipeline != null) {
            pipelineHandle = RadianceGpuDeviceBackend.pipelineHandle(pipeline);
        }
        Blaze3DProxy.setPipelineNative(this.handle, pipelineHandle);
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
        this.currentIndexType = indexType;
        Blaze3DProxy.setIndexBufferNative(this.handle, RadianceBackendUtil.bufferHandle(indexBuffer), RadianceBackendUtil.indexType(indexType));
    }

    @Override
    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
        Blaze3DProxy.drawIndexedNative(this.handle, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }

    @Override
    public void multiDrawIndexed(IntBuffer drawParameters, int instanceCount, int firstInstance, int drawCount) {
        int base = drawParameters.position();
        for (int i = 0; i < drawCount; i++) {
            int offset = base + i * 3;
            int firstIndex = drawParameters.get(offset);
            int indexCount = drawParameters.get(offset + 1);
            int vertexOffset = drawParameters.get(offset + 2);
            if (indexCount > 0) {
                this.drawIndexed(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
            }
        }
    }

    @Override
    public void multiDrawIndexed(PointerBuffer firstIndexOffsets, IntBuffer indexCounts, IntBuffer vertexOffsets, int drawCount) {
        int firstIndexBase = firstIndexOffsets.position();
        int indexCountBase = indexCounts.position();
        int vertexOffsetBase = vertexOffsets.position();
        for (int i = 0; i < drawCount; i++) {
            int indexCount = indexCounts.get(indexCountBase + i);
            int firstIndex = firstIndexFromOffset(firstIndexOffsets.get(firstIndexBase + i), this.currentIndexType);
            int vertexOffset = vertexOffsets.get(vertexOffsetBase + i);
            if (indexCount > 0) {
                this.drawIndexed(indexCount, 1, firstIndex, vertexOffset, 0);
            }
        }
    }

    @Override
    public void drawIndexedIndirect(GpuBufferSlice commands, int drawCount) {
        Blaze3DProxy.drawIndexedIndirectNative(this.handle, RadianceBackendUtil.bufferHandle(commands), commands.offset(), commands.length(), drawCount);
    }

    @Override
    public <T> void drawMultipleIndexed(Collection<RenderPass.Draw<T>> draws, @Nullable GpuBuffer defaultIndexBuffer,
                                        @Nullable IndexType defaultIndexType, Collection<String> dynamicUniforms, T uniformArgument) {
        for (RenderPass.Draw<T> draw : draws) {
            if (draw.uniformUploaderConsumer() != null) {
                draw.uniformUploaderConsumer().accept(uniformArgument, this::setUniform);
            }

            GpuBuffer indexBuffer = draw.indexBuffer() == null ? defaultIndexBuffer : draw.indexBuffer();
            IndexType indexType = draw.indexType() == null ? defaultIndexType : draw.indexType();
            if (indexBuffer == null || indexType == null) {
                continue;
            }

            this.setIndexBuffer(indexBuffer, indexType);
            this.setVertexBuffer(draw.slot(), draw.vertexBuffer().slice());
            this.drawIndexed(draw.indexCount(), 1, draw.firstIndex(), draw.baseVertex(), 0);
        }
    }

    @Override
    public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        Blaze3DProxy.drawNative(this.handle, vertexCount, instanceCount, firstVertex, firstInstance);
    }

    @Override
    public void multiDraw(IntBuffer drawParameters, int instanceCount, int firstInstance, int drawCount) {
        int base = drawParameters.position();
        for (int i = 0; i < drawCount; i++) {
            int offset = base + i * 2;
            int firstVertex = drawParameters.get(offset);
            int vertexCount = drawParameters.get(offset + 1);
            if (vertexCount > 0) {
                this.draw(vertexCount, instanceCount, firstVertex, firstInstance);
            }
        }
    }

    @Override
    public void multiDraw(IntBuffer firstVertices, IntBuffer vertexCounts, int drawCount) {
        int firstVertexBase = firstVertices.position();
        int vertexCountBase = vertexCounts.position();
        for (int i = 0; i < drawCount; i++) {
            int firstVertex = firstVertices.get(firstVertexBase + i);
            int vertexCount = vertexCounts.get(vertexCountBase + i);
            if (vertexCount > 0) {
                this.draw(vertexCount, 1, firstVertex, 0);
            }
        }
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

    private static int firstIndexFromOffset(long firstIndexOffset, IndexType indexType) {
        int bytes = indexType == null ? IndexType.INT.bytes : indexType.bytes;
        return Math.toIntExact(firstIndexOffset / bytes);
    }
}
