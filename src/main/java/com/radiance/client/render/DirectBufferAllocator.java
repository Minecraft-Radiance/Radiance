package com.radiance.client.render;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

public final class DirectBufferAllocator implements AutoCloseable {

    private long pointer;
    private int capacity;
    private int used;
    private boolean closed;

    public DirectBufferAllocator(int capacity) {
        this.capacity = Math.max(capacity, 1);
        this.pointer = MemoryUtil.nmemAlloc(this.capacity);
        if (this.pointer == 0L) {
            throw new OutOfMemoryError("Failed to allocate " + this.capacity + " bytes");
        }
    }

    public long allocate(int size) {
        ensureOpen();
        int nextUsed = this.used + size;
        if (nextUsed > this.capacity) {
            grow(nextUsed);
        }
        long result = this.pointer + this.used;
        this.used = nextUsed;
        return result;
    }

    public ByteBuffer takeBuffer() {
        ensureOpen();
        return this.used == 0 ? null : MemoryUtil.memByteBuffer(this.pointer, this.used);
    }

    private void grow(int target) {
        int newCapacity = this.capacity;
        while (newCapacity < target) {
            newCapacity = Math.max(newCapacity * 2, 1);
        }

        long newPointer = MemoryUtil.nmemRealloc(this.pointer, newCapacity);
        if (newPointer == 0L) {
            throw new OutOfMemoryError(
                "Failed to resize buffer from " + this.capacity + " bytes to " + newCapacity
                    + " bytes");
        }

        this.pointer = newPointer;
        this.capacity = newCapacity;
    }

    private void ensureOpen() {
        if (this.closed) {
            throw new IllegalStateException("Allocator already closed");
        }
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            MemoryUtil.nmemFree(this.pointer);
            this.pointer = 0L;
        }
    }
}
