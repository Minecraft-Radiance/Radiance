package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import java.util.Optional;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
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

    @OnlyIn(Dist.CLIENT)
    record OutlineVertexConsumer(VertexConsumer delegate, int color) implements VertexConsumer {

        public OutlineVertexConsumer(VertexConsumer delegate, int red, int green, int blue,
            int alpha) {
            this(delegate, FastColor.ARGB32.color(alpha, red, green, blue));
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.delegate.vertex(x, y, z)
                .color(this.color);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            this.delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
            this.delegate.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            this.delegate.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            this.delegate.unsetDefaultColor();
        }
    }
}
