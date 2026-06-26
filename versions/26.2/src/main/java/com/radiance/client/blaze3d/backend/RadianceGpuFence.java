package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.buffers.GpuFence;

public final class RadianceGpuFence implements GpuFence {
    private boolean closed;

    @Override
    public void close() {
        this.closed = true;
    }

    @Override
    public boolean awaitCompletion(long timeoutNS) {
        return !this.closed;
    }
}
