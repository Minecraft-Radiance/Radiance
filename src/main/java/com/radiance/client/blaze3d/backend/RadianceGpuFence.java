package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.buffers.GpuFence;
import com.radiance.client.blaze3d.abi.Blaze3DProxy;

public final class RadianceGpuFence implements GpuFence {
    private final long handle;

    RadianceGpuFence(long handle) {
        this.handle = handle;
    }

    @Override
    public void close() {
        Blaze3DProxy.closeFenceNative(this.handle);
    }

    @Override
    public boolean awaitCompletion(long timeoutNS) {
        return Blaze3DProxy.awaitFenceCompletionNative(this.handle, timeoutNS);
    }
}
