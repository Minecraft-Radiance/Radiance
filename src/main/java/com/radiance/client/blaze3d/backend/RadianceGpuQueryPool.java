package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.systems.GpuQueryPool;
import com.radiance.client.blaze3d.abi.Blaze3DProxy;
import java.util.OptionalLong;

public final class RadianceGpuQueryPool implements GpuQueryPool {
    private final long handle;
    private final int size;

    RadianceGpuQueryPool(long handle, int size) {
        this.handle = handle;
        this.size = size;
    }

    long handle() {
        return this.handle;
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public OptionalLong getValue(int index) {
        return OptionalLong.empty();
    }

    @Override
    public OptionalLong[] getValues(int index, int count) {
        OptionalLong[] values = new OptionalLong[count];
        for (int i = 0; i < count; i++) {
            values[i] = OptionalLong.empty();
        }
        return values;
    }

    @Override
    public void close() {
        Blaze3DProxy.closeTimestampQueryPoolNative(this.handle);
    }
}
