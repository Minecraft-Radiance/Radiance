package com.radiance.client.blaze3d.backend;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.TransientMemory;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

final class RadianceTransientMemory implements TransientMemory {
    private final RadianceGpuDeviceBackend device;

    RadianceTransientMemory(RadianceGpuDeviceBackend device) {
        this.device = device;
    }

    @Override
    public ByteBuffer allocateCpu(long size, long alignment, long minimumAllocation, long elementSize) {
        return ByteBuffer.allocateDirect(Math.toIntExact(alignedSize(size, alignment, minimumAllocation, elementSize)));
    }

    @Override
    public GpuBufferSlice.MappedView allocateStaging(long size, long alignment, @GpuBuffer.Usage int usage,
                                                     long minimumAllocation, long elementSize) {
        RadianceGpuBuffer buffer = (RadianceGpuBuffer)this.device.createBuffer("Radiance staging", usage | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_SRC,
            alignedSize(size, alignment, minimumAllocation, elementSize));
        return buffer.map(0L, size, false, true);
    }

    @Override
    public GpuBufferSlice allocateGpu(long size, long alignment, @GpuBuffer.Usage int usage, long minimumAllocation, long elementSize) {
        return this.device.createBuffer("Radiance transient gpu", usage, alignedSize(size, alignment, minimumAllocation, elementSize)).slice(0L, size);
    }

    @Override
    public GpuBufferSlice.MappedView allocateGpuMapped(long size, long alignment, @GpuBuffer.Usage int usage,
                                                       long minimumAllocation, long elementSize) {
        RadianceGpuBuffer buffer = (RadianceGpuBuffer)this.device.createBuffer("Radiance transient mapped gpu",
            usage | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_SRC, alignedSize(size, alignment, minimumAllocation, elementSize));
        return buffer.map(0L, size, false, true);
    }

    @Override
    public GpuBufferSlice uploadStaging(List<ByteBuffer> data, long alignment, @GpuBuffer.Usage int usage,
                                        long minimumAllocation, long elementSize) {
        return this.upload(data, alignment, usage | GpuBuffer.USAGE_COPY_SRC, minimumAllocation, elementSize);
    }

    @Override
    public GpuBufferSlice uploadGpu(List<ByteBuffer> data, long alignment, @GpuBuffer.Usage int usage,
                                    long minimumAllocation, long elementSize) {
        return this.upload(data, alignment, usage, minimumAllocation, elementSize);
    }

    @Override
    public List<GpuBufferSlice> multiUploadStaging(List<ByteBuffer> data, long alignment, @GpuBuffer.Usage int usage) {
        return this.multiUpload(data, alignment, usage | GpuBuffer.USAGE_COPY_SRC);
    }

    @Override
    public List<GpuBufferSlice> multiUploadGpu(List<ByteBuffer> data, long alignment, @GpuBuffer.Usage int usage) {
        return this.multiUpload(data, alignment, usage);
    }

    private GpuBufferSlice upload(List<ByteBuffer> data, long alignment, @GpuBuffer.Usage int usage,
                                  long minimumAllocation, long elementSize) {
        long total = 0L;
        for (ByteBuffer item : data) {
            total += item.remaining();
        }
        long allocationSize = alignedSize(total, alignment, minimumAllocation, elementSize);
        ByteBuffer merged = ByteBuffer.allocateDirect(Math.toIntExact(allocationSize));
        for (ByteBuffer item : data) {
            merged.put(item.duplicate());
        }
        merged.flip();
        RadianceGpuBuffer buffer = (RadianceGpuBuffer)this.device.createBuffer("Radiance transient upload", usage, merged);
        return buffer.slice(0L, total);
    }

    private List<GpuBufferSlice> multiUpload(List<ByteBuffer> data, long alignment, @GpuBuffer.Usage int usage) {
        List<GpuBufferSlice> slices = new ArrayList<>(data.size());
        for (ByteBuffer item : data) {
            slices.add(this.upload(List.of(item), alignment, usage, item.remaining(), 1L));
        }
        return slices;
    }

    private static long alignedSize(long size, long alignment, long minimumAllocation, long elementSize) {
        long target = Math.max(size, minimumAllocation);
        long elementAligned = elementSize <= 1L ? target : ((target + elementSize - 1L) / elementSize) * elementSize;
        return alignment <= 1L ? elementAligned : ((elementAligned + alignment - 1L) / alignment) * alignment;
    }
}
