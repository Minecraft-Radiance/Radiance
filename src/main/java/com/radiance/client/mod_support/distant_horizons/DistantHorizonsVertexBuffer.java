package com.radiance.client.mod_support.distant_horizons;

import com.radiance.client.proxy.world.VistaProxy;
import com.radiance.client.profiler.Profiler;
import com.radiance.client.vista.VistaTerrain;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IVertexBufferWrapper;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.system.MemoryUtil;

public class DistantHorizonsVertexBuffer implements IVertexBufferWrapper {
    private static final AtomicLong nextId = new AtomicLong();
    private static final int[] NORMALS = {
        0x80010000, 0x7fff0000, 0x7fff7fff, 0, 0x00008001, 0x00007fff
    };
    public final long id = nextId.incrementAndGet();
    private ByteBuffer vertices;
    private int vertexCount;
    private long submittedWorldVersion;

    @Override
    public void uploadVertexBuffer(ByteBuffer source, int vertexCount) {
        if (vertices != null) { throw new IllegalStateException("Vista buffers are immutable"); }
        if (vertexCount <= 0 || vertexCount % 4 != 0 || source.remaining() != (long) vertexCount * 16) {
            throw new IllegalArgumentException("Unexpected Distant Horizons vertex layout");
        }
        if (Profiler.ENABLED) Profiler.enter("java.DistantHorizonsVertexBuffer.convertVertices");
        try {
            ByteBuffer input = source.duplicate().order(ByteOrder.nativeOrder());
            vertices = MemoryUtil.memAlloc(Math.multiplyExact(vertexCount, 40)).order(ByteOrder.nativeOrder());
            convertVertices(input, vertices, vertexCount);
            this.vertexCount = vertexCount;
        } finally {
            if (Profiler.ENABLED) Profiler.exit();
        }
    }

    static void convertVertices(ByteBuffer input, ByteBuffer output, int vertexCount) {
        final long address = input.isDirect() ? MemoryUtil.memAddress(input) : 0;
        final long outputAddress = MemoryUtil.memAddress(output);
        final boolean packedLoads = (address != 0 ? ByteOrder.nativeOrder() : input.order()) == ByteOrder.LITTLE_ENDIAN;
        for (int i = 0; i < vertexCount; i++) {
            int offset = i * 16;
            long target = outputAddress + (long) i * 40;
            long packedPosition = packedLoads ? (address != 0 ? MemoryUtil.memGetLong(address + offset)
                : input.getLong(input.position() + offset)) : 0;
            long packedMaterial = packedLoads ? (address != 0 ? MemoryUtil.memGetLong(address + offset + 8)
                : input.getLong(input.position() + offset + 8)) : 0;
            int micro = packedLoads ? (int) (packedPosition >>> 56) : unsignedByte(input, address, offset + 7);
            int material = packedLoads ? (int) (packedMaterial >>> 32) & 255 : unsignedByte(input, address, offset + 12);
            boolean water = material == EDhApiBlockMaterial.WATER.index;
            int x = packedLoads ? (int) packedPosition & 65535 : unsignedShort(input, address, offset);
            int y = packedLoads ? (int) (packedPosition >>> 16) & 65535 : unsignedShort(input, address, offset + 2);
            int z = packedLoads ? (int) (packedPosition >>> 32) & 65535 : unsignedShort(input, address, offset + 4);
            MemoryUtil.memPutFloat(target, x + (water ? 0 : microOffset(micro)));
            MemoryUtil.memPutFloat(target + 4, y - (water ? 1.0f / 9.0f + 0.001f : 0));
            MemoryUtil.memPutFloat(target + 8, z + (water ? 0 : microOffset(micro >> 4)));
            int normal = packedLoads ? (int) (packedMaterial >>> 40) & 255 : unsignedByte(input, address, offset + 13);
            MemoryUtil.memPutInt(target + 12, NORMALS[normal < NORMALS.length ? normal : 1]);
            MemoryUtil.memPutFloat(target + 16, switch (normal) {
                case 2 -> -x;
                case 4 -> z;
                case 5 -> -z;
                default -> x;
            });
            MemoryUtil.memPutFloat(target + 20, switch (normal) {
                case 0 -> -z;
                case 1 -> z;
                default -> -y;
            });
            int color = packedLoads ? (int) packedMaterial : address != 0 ? MemoryUtil.memGetInt(address + offset + 8)
                : input.getInt(input.position() + offset + 8);
            MemoryUtil.memPutInt(target + 24, color);
            int light = packedLoads ? (int) (packedPosition >>> 48) & 255 : unsignedByte(input, address, offset + 6);
            MemoryUtil.memPutInt(target + 28, ((light & 15) * 16 << 16) | ((light >>> 4) * 16));
            MemoryUtil.memPutInt(target + 32, DistantHorizonsMaterials.encode(material,
                packedLoads ? (int) (packedMaterial >>> 48) : unsignedShort(input, address, offset + 14)));
            int alphaMode = water || (color >>> 24) < 255 ? 2 : 0;
            MemoryUtil.memPutInt(target + 36, 1 | 16 | 32 | (alphaMode << 8) | DistantHorizonsMaterials.emission(material));
        }
    }

    private static int unsignedByte(ByteBuffer input, long address, int offset) {
        return Byte.toUnsignedInt(address != 0 ? MemoryUtil.memGetByte(address + offset)
            : input.get(input.position() + offset));
    }

    private static int unsignedShort(ByteBuffer input, long address, int offset) {
        return Short.toUnsignedInt(address != 0 ? MemoryUtil.memGetShort(address + offset)
            : input.getShort(input.position() + offset));
    }

    private static float microOffset(int bits) {
        return (bits & 1) == 0 ? 0 : (bits & 2) == 0 ? 0.01f : -0.01f;
    }

    public int getVertexCount() { return vertexCount; }

    @Override
    public void uploadIndexBuffer(ByteBuffer source, int indexCount) {
        throw new UnsupportedOperationException("Vista expects Distant Horizons's shared quad indices");
    }

    public boolean submit(long worldVersion, int x, int y, int z, int width, VistaTerrain terrain) {
        if (submittedWorldVersion == worldVersion) { return true; }
        if (vertices == null || terrain == null) { return false; }
        if (!VistaProxy.rebuildSingle(worldVersion, id, x, y, z, width, vertices, terrain)) { return false; }
        submittedWorldVersion = worldVersion;
        return true;
    }

    @Override
    public void close() {
        vertexCount = 0;
        if (vertices != null) {
            MemoryUtil.memFree(vertices);
            vertices = null;
        }
        if (submittedWorldVersion != 0) {
            VistaProxy.invalidateSingle(submittedWorldVersion, id);
            submittedWorldVersion = 0;
        }
    }
}
