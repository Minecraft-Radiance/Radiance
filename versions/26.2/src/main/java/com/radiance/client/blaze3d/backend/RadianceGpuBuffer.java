package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.radiance.client.blaze3d.abi.NativeBridge;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RadianceGpuBuffer extends GpuBuffer {
    private final long handle;
    private final ByteBuffer shadow;
    private boolean closed;
    private int mappingRefCount;

    RadianceGpuBuffer(String label, @GpuBuffer.Usage int usage, long size) {
        super(usage, size);
        this.shadow = allocateShadow(size);
        this.handle = NativeBridge.createBufferNative(RadianceBackendUtil.label(label), usage, size);
    }

    RadianceGpuBuffer(String label, @GpuBuffer.Usage int usage, ByteBuffer data) {
        super(usage, data.remaining());
        this.shadow = RadianceBackendUtil.directCopy(data);
        this.handle = NativeBridge.createBufferWithDataNative(
            RadianceBackendUtil.label(label), usage, this.shadow.duplicate(), 0, this.shadow.capacity());
    }

    long handle() {
        return this.handle;
    }

    ByteBuffer shadow() {
        return this.shadow;
    }

    void write(long offset, ByteBuffer data) {
        ByteBuffer direct = data.isDirect() ? data.duplicate() : RadianceBackendUtil.directCopy(data);
        int size = direct.remaining();
        RadianceBackendUtil.copyInto(this.shadow, offset, direct.duplicate());
        NativeBridge.writeBufferNative(this.handle, offset, direct, direct.position(), size);
    }

    void copyFrom(RadianceGpuBuffer source, long sourceOffset, long targetOffset, long size) {
        if (size > Integer.MAX_VALUE) {
            NativeBridge.copyBufferNative(source.handle, sourceOffset, this.handle, targetOffset, size);
            return;
        }
        if (source.shadow != null && this.shadow != null) {
            ByteBuffer copied = RadianceBackendUtil.shadowCopy(source.shadow, sourceOffset, size);
            RadianceBackendUtil.copyInto(this.shadow, targetOffset, copied);
        }
        NativeBridge.copyBufferNative(source.handle, sourceOffset, this.handle, targetOffset, size);
    }

    ByteBuffer readShadow(long offset, long length) {
        return RadianceBackendUtil.shadowCopy(this.shadow, offset, length);
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    @Override
    public void close() {
        if (!this.closed) {
            if (this.mappingRefCount != 0) {
                throw new IllegalStateException("Attempt to close a mapped Radiance buffer");
            }
            this.closed = true;
        }
    }

    @Override
    public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
        if (this.closed) {
            throw new IllegalStateException("Buffer already closed");
        }
        if (!read && !write) {
            throw new IllegalArgumentException("At least read or write must be true");
        }
        if (read && (this.usage() & GpuBuffer.USAGE_MAP_READ) == 0) {
            throw new IllegalStateException("Buffer is not readable");
        }
        if (write && (this.usage() & GpuBuffer.USAGE_MAP_WRITE) == 0) {
            throw new IllegalStateException("Buffer is not writable");
        }
        if (length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Mapping buffer slices larger than 2GB is not supported");
        }
        ByteBuffer mapped = read ? this.readShadow(offset, length) : ByteBuffer.allocateDirect((int)length);
        this.mappingRefCount++;
        AtomicBoolean closedMapping = new AtomicBoolean(false);
        return new GpuBufferSlice.MappedView(this.slice(offset, length), mapped, () -> {
            if (closedMapping.compareAndSet(false, true)) {
                try {
                    if (write) {
                        mapped.position(0);
                        this.write(offset, mapped);
                    }
                } finally {
                    this.mappingRefCount--;
                }
            }
        });
    }

    private static ByteBuffer allocateShadow(long size) {
        if (size > Integer.MAX_VALUE) {
            return null;
        }
        return ByteBuffer.allocateDirect((int)size);
    }
}
