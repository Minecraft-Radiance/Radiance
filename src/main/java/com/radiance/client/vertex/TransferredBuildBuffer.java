package com.radiance.client.vertex;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ICloseableBufferExt;

public final class TransferredBuildBuffer implements AutoCloseable {

    private final MeshData builtBuffer;
    private final ByteBufferBuilder allocator;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private TransferredBuildBuffer(MeshData builtBuffer, ByteBufferBuilder allocator) {
        this.builtBuffer = Objects.requireNonNull(builtBuffer, "builtBuffer");
        this.allocator = Objects.requireNonNull(allocator, "allocator");
    }

    public static TransferredBuildBuffer takeChunk(
        SectionBufferBuilderPack storage,
        RenderType layer,
        MeshData builtBuffer) {
        ByteBufferBuilder allocator = ownerOf(builtBuffer);
        ByteBufferBuilder currentAllocator = storage.buffers.get(layer);
        if (currentAllocator != allocator) {
            throw new IllegalStateException("Chunk build buffer owner does not match storage allocator");
        }

        storage.buffers.put(layer, new ByteBufferBuilder(layer.bufferSize()));
        return new TransferredBuildBuffer(builtBuffer, allocator);
    }

    public static TransferredBuildBuffer takeEntity(
        StorageVertexConsumerProvider provider,
        RenderType layer,
        MeshData builtBuffer) {
        ByteBufferBuilder allocator = ownerOf(builtBuffer);
        if (!provider.detachTransferredAllocator(layer, allocator)) {
            throw new IllegalStateException("Entity build buffer owner does not match provider allocator");
        }

        return new TransferredBuildBuffer(builtBuffer, allocator);
    }

    public MeshData builtBuffer() {
        return builtBuffer;
    }

    public long vertexAddress() {
        return org.lwjgl.system.MemoryUtil.memAddress(builtBuffer.vertexBuffer());
    }

    public long sortedIndexAddress() {
        java.nio.ByteBuffer sortedBuffer = builtBuffer.indexBuffer();
        return sortedBuffer == null ? 0L : org.lwjgl.system.MemoryUtil.memAddress(sortedBuffer);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        builtBuffer.close();
        allocator.close();
    }

    private static ByteBufferBuilder ownerOf(MeshData builtBuffer) {
        return ((ICloseableBufferExt) builtBuffer.vertexBuffer).radiance$getOwnerAllocator();
    }
}
