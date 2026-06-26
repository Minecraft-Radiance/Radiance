package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;

final class RadianceBackendUtil {
    private RadianceBackendUtil() {
    }

    static String label(String label) {
        return label == null ? "" : label;
    }

    static ByteBuffer directCopy(ByteBuffer source) {
        ByteBuffer view = source.duplicate();
        ByteBuffer direct = ByteBuffer.allocateDirect(view.remaining());
        direct.put(view);
        direct.flip();
        return direct;
    }

    static ByteBuffer directSlice(ByteBuffer source, int offset, int size) {
        ByteBuffer view = source.duplicate();
        view.position(offset);
        view.limit(offset + size);
        return directCopy(view.slice());
    }

    static void copyInto(ByteBuffer destination, long destinationOffset, ByteBuffer source) {
        if (destination == null) {
            return;
        }
        if (destinationOffset < 0L || destinationOffset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Buffer offset is outside Java shadow range: " + destinationOffset);
        }
        ByteBuffer src = source.duplicate();
        ByteBuffer dst = destination.duplicate();
        int offset = (int)destinationOffset;
        if (offset + src.remaining() > dst.capacity()) {
            throw new IllegalArgumentException("Write overruns Java buffer shadow");
        }
        dst.position(offset);
        dst.put(src);
    }

    static ByteBuffer shadowCopy(ByteBuffer source, long offset, long size) {
        if (source == null) {
            return ByteBuffer.allocateDirect(0);
        }
        if (offset < 0L || size < 0L || offset > Integer.MAX_VALUE || size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Requested shadow slice is outside Java addressable range");
        }
        ByteBuffer view = source.duplicate();
        view.position((int)offset);
        view.limit((int)(offset + size));
        return directCopy(view.slice());
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

    static long textureViewHandle(GpuTextureView view) {
        return view instanceof RadianceGpuTextureView radiance ? radiance.handle() : 0L;
    }

    static long samplerHandle(GpuSampler sampler) {
        return sampler instanceof RadianceGpuSampler radiance ? radiance.handle() : 0L;
    }

    static int gpuFormat(GpuFormat format) {
        return format.ordinal();
    }

    static int polygonMode(PolygonMode mode) {
        return mode == PolygonMode.WIREFRAME ? 1 : 0;
    }

    static int indexType(IndexType type) {
        return type == IndexType.INT ? 1 : 0;
    }

    static int topology(PrimitiveTopology topology) {
        return switch (topology) {
            case POINTS -> 0;
            case LINES -> 1;
            case DEBUG_LINES -> 2;
            case DEBUG_LINE_STRIP -> 3;
            case TRIANGLES -> 4;
            case TRIANGLE_STRIP -> 5;
            case TRIANGLE_FAN -> 6;
            case QUADS -> 7;
        };
    }
}
