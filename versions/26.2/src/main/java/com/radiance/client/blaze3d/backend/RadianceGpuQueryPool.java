package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.systems.GpuQueryPool;
import java.util.Arrays;
import java.util.OptionalLong;

public final class RadianceGpuQueryPool implements GpuQueryPool {
    private final OptionalLong[] values;
    private boolean closed;

    RadianceGpuQueryPool(int size) {
        this.values = new OptionalLong[size];
        Arrays.fill(this.values, OptionalLong.empty());
    }

    void write(int index) {
        if (!this.closed && index >= 0 && index < this.values.length) {
            this.values[index] = OptionalLong.of(System.nanoTime());
        }
    }

    @Override
    public int size() {
        return this.values.length;
    }

    @Override
    public OptionalLong getValue(int index) {
        return index >= 0 && index < this.values.length ? this.values[index] : OptionalLong.empty();
    }

    @Override
    public OptionalLong[] getValues(int index, int count) {
        OptionalLong[] out = new OptionalLong[count];
        for (int i = 0; i < count; i++) {
            out[i] = this.getValue(index + i);
        }
        return out;
    }

    @Override
    public void close() {
        this.closed = true;
    }
}
