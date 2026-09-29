package com.radiance.client.vertex;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.BufferAllocator;

@Environment(EnvType.CLIENT)
public class StorageVertexConsumerProvider implements VertexConsumerProvider {

    protected final Map<RenderLayer, VertexConsumer> pending = new HashMap<>();
    protected final Map<RenderLayer, BufferAllocator> allocated = new HashMap<>();

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
        Object2ObjectLinkedOpenHashMap<RenderLayer, BufferAllocator> builderStorage,
        RenderLayer layer) {
        builderStorage.put(layer, new BufferAllocator(layer.getExpectedBufferSize()));
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer renderLayer) {
        VertexConsumer vertexConsumer = this.pending.get(renderLayer);

        if (vertexConsumer == null) {
            BufferAllocator bufferAllocator = new BufferAllocator(size);
            allocated.put(renderLayer, bufferAllocator);

            VertexFormat.DrawMode drawMode = renderLayer.getDrawMode();
            VertexFormat vertexFormat = renderLayer.getVertexFormat();

            if (drawMode == VertexFormat.DrawMode.QUADS) {
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

    public Map<RenderLayer, VertexConsumer> getLayers() {
        return this.pending;
    }

    boolean detachTransferredAllocator(RenderLayer renderLayer, BufferAllocator allocator) {
        BufferAllocator current = this.allocated.get(renderLayer);
        if (current != allocator) {
            return false;
        }

        this.allocated.remove(renderLayer);
        return true;
    }

    public void close() {
        for (Map.Entry<RenderLayer, BufferAllocator> entry : this.allocated.entrySet()) {
            entry.getValue()
                .close();
        }
        this.allocated.clear();
        this.pending.clear();
    }
}
