package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.radiance.client.blaze3d.abi.Blaze3DProxy;
import com.mojang.blaze3d.systems.TransientMemory;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

public final class RadianceTransientMemory implements TransientMemory {
    private static final int RETAINED_GENERATIONS = 4;
    private static final Queue<List<RadianceGpuBuffer>> RETIRED = new ArrayDeque<>();
    private static final List<RadianceGpuBuffer> CURRENT_GENERATION = new ArrayList<>();

    @Override
    public ByteBuffer allocateCpu(long size, long alignment, long minimumAllocation, long elementSize) {
        return RadianceByteBuffers.allocateDirectNative(Math.max(size, minimumAllocation));
    }

    @Override
    public GpuBufferSlice.MappedView allocateStaging(long size, long alignment, @GpuBuffer.Usage int usage,
                                                     long minimumAllocation, long elementSize) {
        RadianceGpuBuffer buffer = createTransientBuffer(
            "transient_staging", usage | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_SRC,
            Math.max(size, minimumAllocation));
        return buffer.map(0L, size, false, true);
    }

    @Override
    public GpuBufferSlice allocateGpu(long size, long alignment, @GpuBuffer.Usage int usage, long minimumAllocation, long elementSize) {
        return createTransientBuffer("transient_gpu", usage, Math.max(size, minimumAllocation)).slice(0L, size);
    }

    @Override
    public GpuBufferSlice.MappedView allocateGpuMapped(long size, long alignment, @GpuBuffer.Usage int usage,
                                                       long minimumAllocation, long elementSize) {
        RadianceGpuBuffer buffer = createTransientBuffer(
            "transient_gpu_mapped", usage | GpuBuffer.USAGE_MAP_WRITE, Math.max(size, minimumAllocation));
        return buffer.map(0L, size, false, true);
    }

    @Override
    public GpuBufferSlice uploadStaging(List<ByteBuffer> data, long alignment, @GpuBuffer.Usage int usage,
                                         long minimumAllocation, long elementSize) {
        return uploadPacked("transient_upload_staging", data, alignment,
            usage | GpuBuffer.USAGE_COPY_SRC, minimumAllocation);
    }

    @Override
    public GpuBufferSlice uploadGpu(List<ByteBuffer> data, long alignment, @GpuBuffer.Usage int usage,
                                     long minimumAllocation, long elementSize) {
        return uploadPacked("transient_upload_gpu", data, alignment, usage, minimumAllocation);
    }

    @Override
    public List<GpuBufferSlice> multiUploadStaging(List<ByteBuffer> data, long alignment, @GpuBuffer.Usage int usage) {
        List<GpuBufferSlice> out = new ArrayList<>(data.size());
        for (ByteBuffer item : data) {
            out.add(this.uploadStaging(List.of(item), alignment, usage, item.remaining(), 1L));
        }
        return out;
    }

    @Override
    public List<GpuBufferSlice> multiUploadGpu(List<ByteBuffer> data, long alignment, @GpuBuffer.Usage int usage) {
        List<GpuBufferSlice> out = new ArrayList<>(data.size());
        for (ByteBuffer item : data) {
            out.add(this.uploadGpu(List.of(item), alignment, usage, item.remaining(), 1L));
        }
        return out;
    }

    private static long totalSize(List<ByteBuffer> data, long minimumAllocation) {
        long total = 0L;
        for (ByteBuffer item : data) {
            total += item.remaining();
        }
        return Math.max(total, minimumAllocation);
    }

    private static RadianceGpuBuffer createTransientBuffer(String label, int usage, long size) {
        RadianceGpuBuffer buffer = new RadianceGpuBuffer(
            Blaze3DProxy.createBufferNative(label, usage, size), usage, size);
        synchronized (RETIRED) {
            CURRENT_GENERATION.add(buffer);
        }
        return buffer;
    }

    private static GpuBufferSlice uploadPacked(String label, List<ByteBuffer> data, long alignment,
                                               int usage, long minimumAllocation) {
        long payloadSize = packedPayloadSize(data, alignment);
        long allocationSize = Math.max(payloadSize, minimumAllocation);
        RadianceGpuBuffer buffer = createTransientBuffer(label, usage, allocationSize);
        long offset = 0L;
        for (ByteBuffer item : data) {
            ByteBuffer bytes = item.duplicate();
            Blaze3DProxy.writeToBufferNative(buffer.handle(), offset, bytes, bytes.position(), bytes.remaining());
            offset = align(offset + bytes.remaining(), alignment);
        }
        return buffer.slice(0L, payloadSize);
    }

    private static long packedPayloadSize(List<ByteBuffer> data, long alignment) {
        long total = 0L;
        for (ByteBuffer item : data) {
            total = align(total + item.remaining(), alignment);
        }
        return total;
    }

    private static long align(long value, long alignment) {
        if (alignment <= 1L) {
            return value;
        }
        long remainder = value % alignment;
        return remainder == 0L ? value : value + alignment - remainder;
    }

    public static void retireFrame() {
        synchronized (RETIRED) {
            if (!CURRENT_GENERATION.isEmpty()) {
                RETIRED.add(new ArrayList<>(CURRENT_GENERATION));
                CURRENT_GENERATION.clear();
            }
            while (RETIRED.size() > RETAINED_GENERATIONS) {
                for (RadianceGpuBuffer buffer : RETIRED.remove()) {
                    buffer.close();
                }
            }
        }
    }

    static void closeAll() {
        synchronized (RETIRED) {
            for (RadianceGpuBuffer buffer : CURRENT_GENERATION) {
                buffer.close();
            }
            CURRENT_GENERATION.clear();
            while (!RETIRED.isEmpty()) {
                for (RadianceGpuBuffer buffer : RETIRED.remove()) {
                    buffer.close();
                }
            }
        }
    }
}
