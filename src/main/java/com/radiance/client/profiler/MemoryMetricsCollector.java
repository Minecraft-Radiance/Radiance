package com.radiance.client.profiler;

import com.radiance.client.texture.TextureTracker;
import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MemoryMetricsCollector {

    private static final long LINUX_PAGE_SIZE_BYTES = 4096L;
    private static final long SAMPLE_INTERVAL_NANOS = 1_000_000_000L;
    private static final MemoryMXBean MEMORY_MX_BEAN = ManagementFactory.getMemoryMXBean();
    private static final List<BufferPoolMXBean> BUFFER_POOL_MX_BEANS =
        ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
    private static long nextSampleNanos = 0L;
    private static long sampleCount = 0L;

    private MemoryMetricsCollector() {
    }

    public static void sampleFrame() {
        long now = System.nanoTime();
        if (now < nextSampleNanos) {
            return;
        }

        nextSampleNanos = now + SAMPLE_INTERVAL_NANOS;
        Profiler.enter("java.MemoryMetricsCollector.sampleFrame");
        try {
            Profiler.metric("memory.java.sample_count", ++sampleCount);

            Runtime runtime = Runtime.getRuntime();
            long heapTotal = runtime.totalMemory();
            long heapFree = runtime.freeMemory();
            Profiler.metric("memory.java.heap_used_bytes", heapTotal - heapFree);
            Profiler.metric("memory.java.heap_total_bytes", heapTotal);
            Profiler.metric("memory.java.heap_max_bytes", runtime.maxMemory());

            MemoryUsage nonHeap = MEMORY_MX_BEAN.getNonHeapMemoryUsage();
            if (nonHeap.getUsed() >= 0) {
                Profiler.metric("memory.java.non_heap_used_bytes", nonHeap.getUsed());
            }

            for (BufferPoolMXBean pool : BUFFER_POOL_MX_BEANS) {
                String name = pool.getName();
                if ("direct".equals(name)) {
                    recordBufferPool("memory.java.direct", pool);
                } else if (name != null && name.startsWith("mapped")) {
                    recordBufferPool("memory.java.mapped", pool);
                }
            }

            recordTextureTrackerMetrics();

            long residentSetBytes = readLinuxResidentSetBytes();
            if (residentSetBytes >= 0) {
                Profiler.metric("memory.process.rss_bytes.java", residentSetBytes);
            }
        } finally {
            Profiler.exit();
        }
    }

    private static void recordBufferPool(String prefix, BufferPoolMXBean pool) {
        Profiler.metric(prefix + "_count", pool.getCount());
        Profiler.metric(prefix + "_used_bytes", pool.getMemoryUsed());
        Profiler.metric(prefix + "_capacity_bytes", pool.getTotalCapacity());
    }

    private static void recordTextureTrackerMetrics() {
        long textureCount = 0;
        long logicalBytes = 0;
        for (TextureTracker.Texture texture : TextureTracker.GLID2Texture.values()) {
            if (texture == null) {
                continue;
            }
            textureCount++;
            logicalBytes = saturatedAdd(logicalBytes, logicalTextureBytes(texture));
        }

        Profiler.metric("memory.java.texture_tracker.count", textureCount);
        Profiler.metric("memory.java.texture_tracker.logical_mip_bytes", logicalBytes);
    }

    private static long logicalTextureBytes(TextureTracker.Texture texture) {
        int width = texture.width();
        int height = texture.height();
        int levels = texture.maxLayer() + 1;
        long bytes = 0;
        for (int level = 0; level < levels; level++) {
            long levelBytes = saturatedMultiply(saturatedMultiply(width, height), texture.channel());
            bytes = saturatedAdd(bytes, levelBytes);
            width = Math.max(1, width / 2);
            height = Math.max(1, height / 2);
        }
        return bytes;
    }

    private static long readLinuxResidentSetBytes() {
        try {
            String statm = Files.readString(Path.of("/proc/self/statm"))
                .trim();
            if (statm.isEmpty()) {
                return -1;
            }
            String[] fields = statm.split("\\s+");
            if (fields.length < 2) {
                return -1;
            }
            return saturatedMultiply(Long.parseLong(fields[1]), LINUX_PAGE_SIZE_BYTES);
        } catch (IOException | NumberFormatException e) {
            return -1;
        }
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left == 0 || right == 0) {
            return 0;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }
}
