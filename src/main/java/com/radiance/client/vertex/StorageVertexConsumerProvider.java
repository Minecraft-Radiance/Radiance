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

@Environment(EnvType.CLIENT)
public class StorageVertexConsumerProvider implements VertexConsumerProvider {

    protected final Map<RenderLayer, VertexConsumer> pending = new HashMap<>();

    private int size = 0;

    public StorageVertexConsumerProvider(int size) {
        this.size = size;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer renderLayer) {
        VertexConsumer vertexConsumer = this.pending.get(renderLayer);

        if (vertexConsumer == null) {
            VertexFormat.DrawMode drawMode = renderLayer.getDrawMode();
            VertexFormat vertexFormat = renderLayer.getVertexFormat();
            int initialSize = Math.max(256,
                Math.min(this.size, renderLayer.getExpectedBufferSize()));

            if (drawMode == VertexFormat.DrawMode.QUADS) {
                vertexConsumer = new PBRVertexConsumer(initialSize, renderLayer);
            } else {
                BufferBuilder bufferBuilder = new BufferBuilder(initialSize);
                bufferBuilder.begin(drawMode, vertexFormat);
                vertexConsumer = bufferBuilder;
            }
            this.pending.put(renderLayer, vertexConsumer);
        }
        return vertexConsumer;
    }

    public Map<RenderLayer, VertexConsumer> getLayers() {
        return this.pending;
    }

    public void close() {
        for (VertexConsumer vertexConsumer : this.pending.values()) {
            if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
                pbrVertexConsumer.close();
            } else if (vertexConsumer instanceof BufferBuilder bufferBuilder) {
                bufferBuilder.clear();
            }
        }
        this.pending.clear();
    }
}
