package com.radiance.mixin_related.extensions.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;

public interface IHeldItemRendererExt {

    void radiance$renderItem(float tickDelta,
        PoseStack matrices,
        MultiBufferSource vertexConsumers,
        LocalPlayer player,
        int light);
}
