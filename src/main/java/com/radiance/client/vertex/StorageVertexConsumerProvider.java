package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public class StorageVertexConsumerProvider implements MultiBufferSource {

    protected final Map<RenderType, VertexConsumer> pending = new HashMap<>();
    protected final Map<RenderType, ByteBufferBuilder> allocated = new HashMap<>();

    private int size = 0;
    private int entityCoordinate = 0;
    private boolean entityNormalOffset = false;
    private float entityBaseX = 0.0f;
    private float entityBaseY = 0.0f;
    private float entityBaseZ = 0.0f;
    private boolean entityContextChangedAfterPbrWrite = false;

    public StorageVertexConsumerProvider(int size) {
        this.size = size;
    }

    private static void assignBufferBuilder(
        Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder> builderStorage,
        RenderType layer) {
        builderStorage.put(layer, new ByteBufferBuilder(layer.bufferSize()));
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderLayer) {
        VertexConsumer vertexConsumer = this.pending.get(renderLayer);

        if (vertexConsumer == null) {
            ByteBufferBuilder bufferAllocator = new ByteBufferBuilder(size);
            allocated.put(renderLayer, bufferAllocator);

            VertexFormat.Mode drawMode = renderLayer.mode();
            VertexFormat vertexFormat = renderLayer.format();

            if (drawMode == VertexFormat.Mode.QUADS) {
                vertexConsumer = new PBRVertexConsumer(bufferAllocator, renderLayer);
                ((PBRVertexConsumer) vertexConsumer).setEntityBuildContext(
                    entityCoordinate,
                    entityNormalOffset,
                    entityBaseX,
                    entityBaseY,
                    entityBaseZ);
            } else {
                vertexConsumer = new BufferBuilder(bufferAllocator, drawMode, vertexFormat);
            }
            this.pending.put(renderLayer, vertexConsumer);
        }
        return vertexConsumer;
    }

    public StorageVertexConsumerProvider setEntityBuildContext(
        int coordinate,
        boolean normalOffset,
        double baseX,
        double baseY,
        double baseZ) {
        float nextBaseX = (float) baseX;
        float nextBaseY = (float) baseY;
        float nextBaseZ = (float) baseZ;
        if (entityCoordinate == coordinate && entityNormalOffset == normalOffset &&
            Float.compare(entityBaseX, nextBaseX) == 0 && Float.compare(entityBaseY, nextBaseY) == 0 &&
            Float.compare(entityBaseZ, nextBaseZ) == 0) {
            return this;
        }

        for (VertexConsumer vertexConsumer : pending.values()) {
            if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
                if (pbrVertexConsumer.getVertexCount() > 0) {
                    entityContextChangedAfterPbrWrite = true;
                }
                pbrVertexConsumer.setEntityBuildContext(
                    coordinate,
                    normalOffset,
                    nextBaseX,
                    nextBaseY,
                    nextBaseZ);
            }
        }

        entityCoordinate = coordinate;
        entityNormalOffset = normalOffset;
        entityBaseX = nextBaseX;
        entityBaseY = nextBaseY;
        entityBaseZ = nextBaseZ;
        return this;
    }

    public boolean hasFinalEntityBuildContext(
        int coordinate,
        boolean normalOffset,
        float baseX,
        float baseY,
        float baseZ) {
        return !entityContextChangedAfterPbrWrite &&
            entityCoordinate == coordinate &&
            entityNormalOffset == normalOffset &&
            Float.compare(entityBaseX, baseX) == 0 &&
            Float.compare(entityBaseY, baseY) == 0 &&
            Float.compare(entityBaseZ, baseZ) == 0;
    }

    public Map<RenderType, VertexConsumer> getLayers() {
        return this.pending;
    }

    boolean detachTransferredAllocator(RenderType renderLayer, ByteBufferBuilder allocator) {
        ByteBufferBuilder current = this.allocated.get(renderLayer);
        if (current != allocator) {
            return false;
        }

        this.allocated.remove(renderLayer);
        return true;
    }

    public void close() {
        for (Map.Entry<RenderType, ByteBufferBuilder> entry : this.allocated.entrySet()) {
            entry.getValue()
                .close();
        }
        this.allocated.clear();
        this.pending.clear();
    }
}
