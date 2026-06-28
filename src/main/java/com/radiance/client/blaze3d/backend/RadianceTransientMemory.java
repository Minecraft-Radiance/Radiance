package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.TransientMemory;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

final class RadianceTransientMemory implements TransientMemory {
    @Override
    public ByteBuffer allocateCpu(long size, long alignment, long minimumAllocation, long elementSize) {
        return ByteBuffer.allocateDirect(Math.toIntExact(Math.max(size, minimumAllocation)));
    }

    @Override
    public GpuBufferSlice.MappedView allocateStaging(long size, long alignment, @GpuBuffer.Usage int usage,
                                                     long minimumAllocation, long elementSize) {
        RadianceGpuBuffer buffer = new RadianceGpuBuffer(0L, usage | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_SRC, Math.max(size, minimumAllocation));
        return buffer.map(0L, size, false, true);
    }

    @Override
    public GpuBufferSlice allocateGpu(long size, long alignment, @GpuBuffer.Usage int usage, long minimumAllocation, long elementSize) {
        return new RadianceGpuBuffer(0L, usage, Math.max(size, minimumAllocation)).slice(0L, size);
    }

    @Override
    public GpuBufferSlice.MappedView allocateGpuMapped(long size, long alignment, @GpuBuffer.Usage int usage,
                                                       long minimumAllocation, long elementSize) {
        RadianceGpuBuffer buffer = new RadianceGpuBuffer(0L, usage | GpuBuffer.USAGE_MAP_WRITE, Math.max(size, minimumAllocation));
        return buffer.map(0L, size, false, true);
    }

    @Override
    public GpuBufferSlice uploadStaging(List<ByteBuffer> data, long alignment, @GpuBuffer.Usage int usage,
                                        long minimumAllocation, long elementSize) {
        return new RadianceGpuBuffer(0L, usage | GpuBuffer.USAGE_COPY_SRC, totalSize(data, minimumAllocation)).slice(0L, totalSize(data, 0L));
    }

    @Override
    public GpuBufferSlice uploadGpu(List<ByteBuffer> data, long alignment, @GpuBuffer.Usage int usage,
                                    long minimumAllocation, long elementSize) {
        return new RadianceGpuBuffer(0L, usage, totalSize(data, minimumAllocation)).slice(0L, totalSize(data, 0L));
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
}
