package com.radiance.mixin_related.extensions.vulkan_render_integration;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

public interface IHeldItemRendererExt {

    void radiance$renderItem(float tickDelta,
        MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        ClientPlayerEntity player,
        int light);
}
