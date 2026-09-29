package com.radiance.client.vista;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.system.MemoryUtil;

public class VistaTerrain {
    private static final AtomicLong nextId = new AtomicLong();
    public static final VistaTerrain EMPTY = new VistaTerrain(new int[0]);
    public final long id = nextId.incrementAndGet();
    private final int[] data;

    private VistaTerrain(int[] data) {
        this.data = data;
    }

    public Upload upload() {
        return new Upload(data);
    }

    public static class Builder {
        private int[] data = new int[256];
        private int size;
        private final int sideShading;

        public Builder() { this(1, 1, 1, 1); }

        public Builder(float west, float east, float north, float south) {
            sideShading = packShade(west) | (packShade(east) << 8)
                | (packShade(north) << 16) | (packShade(south) << 24);
        }

        private static int packShade(float shade) {
            return Float.isFinite(shade) ? Math.round(Math.max(0, Math.min(1, shade)) * 255) : 255;
        }

        public void add(int x, int z, float minY, float maxY, float minX, float maxX,
            float minZ, float maxZ, int color, int material) {
            add(x, z, minY, maxY, minX, maxX, minZ, maxZ, color, material,
                material, material, material, material);
        }

        public void add(int x, int z, float minY, float maxY, float minX, float maxX,
            float minZ, float maxZ, int color, int material, int west, int east, int north, int south) {
            if (minY >= maxY || minX >= maxX || minZ >= maxZ) { return; }
            if (size + 15 > data.length) {
                data = Arrays.copyOf(data, Math.max(size + 15, data.length * 2));
            }
            data[size++] = x;
            data[size++] = z;
            data[size++] = Float.floatToRawIntBits(minY);
            data[size++] = Float.floatToRawIntBits(maxY);
            data[size++] = Float.floatToRawIntBits(minX);
            data[size++] = Float.floatToRawIntBits(maxX);
            data[size++] = Float.floatToRawIntBits(minZ);
            data[size++] = Float.floatToRawIntBits(maxZ);
            data[size++] = color;
            data[size++] = material;
            data[size++] = west;
            data[size++] = east;
            data[size++] = north;
            data[size++] = south;
            data[size++] = sideShading;
        }

        public VistaTerrain build() {
            return size == 0 ? EMPTY : new VistaTerrain(Arrays.copyOf(data, size));
        }
    }

    public static class Upload implements AutoCloseable {
        private final ByteBuffer buffer;
        public final int count;
        public final long address;

        private Upload(int[] data) {
            count = data.length / 15;
            buffer = data.length == 0 ? null
                : MemoryUtil.memAlloc(data.length * Integer.BYTES).order(ByteOrder.nativeOrder());
            if (buffer != null) {
                buffer.asIntBuffer().put(data);
            }
            address = buffer == null ? 0 : MemoryUtil.memAddress(buffer);
        }

        @Override
        public void close() {
            if (buffer != null) { MemoryUtil.memFree(buffer); }
        }
    }
}
