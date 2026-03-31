package com.radiance.client.vertex;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    protected final Map<RenderLayer, VertexConsumer> pending = new ConcurrentHashMap<>();
    protected final Map<RenderLayer, BufferAllocator> allocated = new ConcurrentHashMap<>();

    private int size = 0;

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
            } else {
                vertexConsumer = new BufferBuilder(bufferAllocator, drawMode, vertexFormat);
            }
            this.pending.put(renderLayer, vertexConsumer);
        }
        return vertexConsumer;
    }

    public Map<RenderLayer, VertexConsumer> getLayers() {
        return this.pending;
    }

    public void close() {
        for (Map.Entry<RenderLayer, BufferAllocator> entry : this.allocated.entrySet()) {
            entry.getValue()
                .close();
        }
        this.pending.clear();
    }
}