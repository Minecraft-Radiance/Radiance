package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import java.util.Optional;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;

public class StorageOutlineVertexConsumerProvider implements MultiBufferSource {

    private final StorageVertexConsumerProvider parent;
    private int red = 255;
    private int green = 255;
    private int blue = 255;
    private int alpha = 255;

    public StorageOutlineVertexConsumerProvider(StorageVertexConsumerProvider parent) {
        this.parent = parent;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderLayer) {
        if (renderLayer.isOutline()) {
            VertexConsumer vertexConsumer = this.parent.getBuffer(renderLayer);
            return new OutlineVertexConsumer(vertexConsumer, this.red, this.green, this.blue,
                this.alpha);
        } else {
            VertexConsumer vertexConsumer = this.parent.getBuffer(renderLayer);
            Optional<RenderType> optional = renderLayer.outline();
            if (optional.isPresent()) {
                VertexConsumer vertexConsumer2 = this.parent.getBuffer(
                    optional.get());
                OutlineVertexConsumer
                    outlineVertexConsumer =
                    new OutlineVertexConsumer(vertexConsumer2, this.red, this.green, this.blue,
                        this.alpha);
                return VertexMultiConsumer.create(outlineVertexConsumer, vertexConsumer);
            } else {
                return vertexConsumer;
            }
        }
    }

    public void setColor(int red, int green, int blue, int alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    record OutlineVertexConsumer(VertexConsumer delegate, int color) implements VertexConsumer {

        public OutlineVertexConsumer(VertexConsumer delegate, int red, int green, int blue,
            int alpha) {
            this(delegate, FastColor.ARGB32.color(alpha, red, green, blue));
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z)
                .setColor(this.color);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
