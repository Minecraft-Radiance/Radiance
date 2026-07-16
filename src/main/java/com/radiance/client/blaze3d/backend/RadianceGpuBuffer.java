package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.radiance.client.blaze3d.abi.Blaze3DProxy;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RadianceGpuBuffer extends GpuBuffer {
    private final long handle;
    private boolean closed;

    RadianceGpuBuffer(long handle, @GpuBuffer.Usage int usage, long size) {
        super(usage, size);
        this.handle = handle;
    }

    long handle() {
        return this.handle;
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            Blaze3DProxy.closeBufferNative(this.handle);
        }
    }

    @Override
    public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
        ByteBuffer mapped = RadianceByteBuffers.allocateDirectNative(length);
        AtomicBoolean closedMapping = new AtomicBoolean(false);
        return new GpuBufferSlice.MappedView(this.slice(offset, length), mapped, () -> {
            if (closedMapping.compareAndSet(false, true) && write) {
                ByteBuffer data = mapped.duplicate();
                data.position(0);
                Blaze3DProxy.writeToBufferNative(this.handle, offset, data, 0, data.remaining());
            }
        });
    }
}
