package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;

final class RadianceCompiledRenderPipeline implements CompiledRenderPipeline {
    private final long handle;

    RadianceCompiledRenderPipeline(long handle) {
        this.handle = handle;
    }

    long handle() {
        return this.handle;
    }

    @Override
    public boolean isValid() {
        return this.handle != 0L;
    }
}
