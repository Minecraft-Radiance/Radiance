package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;

final class RadianceBackendUtil {
    private RadianceBackendUtil() {
    }

    static String label(String label) {
        return label == null ? "" : label;
    }

    static int gpuFormat(GpuFormat format) {
        return format == null ? 0 : format.ordinal();
    }

    static int addressMode(AddressMode mode) {
        return mode == null ? 0 : mode.ordinal();
    }

    static int filterMode(FilterMode mode) {
        return mode == null ? 0 : mode.ordinal();
    }

    static int topology(PrimitiveTopology topology) {
        return topology == null ? 0 : topology.ordinal();
    }

    static int polygonMode(PolygonMode mode) {
        return mode == null ? 0 : mode.ordinal();
    }

    static int indexType(IndexType type) {
        return type == IndexType.INT ? 1 : 0;
    }

    static long bufferHandle(GpuBuffer buffer) {
        return buffer instanceof RadianceGpuBuffer radiance ? radiance.handle() : 0L;
    }

    static long bufferHandle(GpuBufferSlice slice) {
        return slice == null ? 0L : bufferHandle(slice.buffer());
    }

    static long textureHandle(GpuTexture texture) {
        return texture instanceof RadianceGpuTexture radiance ? radiance.handle() : 0L;
    }

    static int textureId(GpuTexture texture) {
        return texture instanceof RadianceGpuTexture radiance ? radiance.textureId() : 0;
    }

    static long textureViewHandle(GpuTextureView view) {
        return view instanceof RadianceGpuTextureView radiance ? radiance.handle() : 0L;
    }

    static long samplerHandle(GpuSampler sampler) {
        return sampler instanceof RadianceGpuSampler radiance ? radiance.handle() : 0L;
    }
}
