package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IDrawContextExt;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DrawContext.class)
public class DrawContextMixins implements IDrawContextExt {

    @Final
    @Shadow
    private MatrixStack matrices;

    @Final
    @Shadow
    private VertexConsumerProvider.Immediate vertexConsumers;

    @Override
    public void radiance$drawOrientedQuad(RenderLayer layer, float x1, float y1, float x2,
        float y2, float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.1f) {
            return;
        }

        float nx = -dy / len * (thickness / 2f);
        float ny = dx / len * (thickness / 2f);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = vertexConsumers.getBuffer(layer);

        buffer.vertex(matrix, x1 + nx, y1 + ny, 0).color(color);
        buffer.vertex(matrix, x2 + nx, y2 + ny, 0).color(color);
        buffer.vertex(matrix, x2 - nx, y2 - ny, 0).color(color);
        buffer.vertex(matrix, x1 - nx, y1 - ny, 0).color(color);
    }
}
