package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.proxy.world.EntityProxy;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixins<T extends Entity> {

    @Shadow
    protected abstract void renderNameTag(T entity, Component text, PoseStack matrices,
        MultiBufferSource vertexConsumers, int light);

    @Redirect(method = "render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void redirectRenderLabelIfPresent(EntityRenderer<T> instance, T entity, Component text,
        PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        this.renderNameTag(entity, text, matrices,
            EntityProxy.postTextVertexConsumerProvider != null ?
                EntityProxy.postTextVertexConsumerProvider :
                vertexConsumers, light);
    }
}
