package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.proxy.world.EntityProxy;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixins {

    @Shadow
    protected abstract void renderNameTag(EntityRenderState state, Component text,
        PoseStack matrices, MultiBufferSource vertexConsumers, int light);

    @Redirect(method = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;renderNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void redirectRenderLabelIfPresent(EntityRenderer<?, ?> instance,
        EntityRenderState state, Component text, PoseStack matrices,
        MultiBufferSource vertexConsumers, int light) {
        this.renderNameTag(state, text, matrices,
            EntityProxy.postTextVertexConsumerProvider != null ?
                EntityProxy.postTextVertexConsumerProvider :
                vertexConsumers, light);
    }
}
