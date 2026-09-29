package com.radiance.client.vertex;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ICloseableBufferExt;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.util.BufferAllocator;

public final class TransferredBuildBuffer implements AutoCloseable {

    private final BuiltBuffer builtBuffer;
    private final BufferAllocator allocator;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private TransferredBuildBuffer(BuiltBuffer builtBuffer, BufferAllocator allocator) {
        this.builtBuffer = Objects.requireNonNull(builtBuffer, "builtBuffer");
        this.allocator = Objects.requireNonNull(allocator, "allocator");
    }

    public static TransferredBuildBuffer takeChunk(
        BlockBufferAllocatorStorage storage,
        RenderLayer layer,
        BuiltBuffer builtBuffer) {
        BufferAllocator allocator = ownerOf(builtBuffer);
        BufferAllocator currentAllocator = storage.allocators.get(layer);
        if (currentAllocator != allocator) {
            throw new IllegalStateException("Chunk build buffer owner does not match storage allocator");
        }

        storage.allocators.put(layer, new BufferAllocator(layer.getExpectedBufferSize()));
        return new TransferredBuildBuffer(builtBuffer, allocator);
    }

    public static TransferredBuildBuffer takeEntity(
        StorageVertexConsumerProvider provider,
        RenderLayer layer,
        BuiltBuffer builtBuffer) {
        BufferAllocator allocator = ownerOf(builtBuffer);
        if (!provider.detachTransferredAllocator(layer, allocator)) {
            throw new IllegalStateException("Entity build buffer owner does not match provider allocator");
        }

        return new TransferredBuildBuffer(builtBuffer, allocator);
    }

    public BuiltBuffer builtBuffer() {
        return builtBuffer;
    }

    public long vertexAddress() {
        return org.lwjgl.system.MemoryUtil.memAddress(builtBuffer.getBuffer());
    }

    public long sortedIndexAddress() {
        java.nio.ByteBuffer sortedBuffer = builtBuffer.getSortedBuffer();
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

    private static BufferAllocator ownerOf(BuiltBuffer builtBuffer) {
        return ((ICloseableBufferExt) builtBuffer.buffer).radiance$getOwnerAllocator();
    }
}
