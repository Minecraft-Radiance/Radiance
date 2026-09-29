package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.world.EntityProxy;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixins<T extends Entity> {

    @Shadow
    protected abstract void renderLabelIfPresent(T entity, Text text, MatrixStack matrices,
        VertexConsumerProvider vertexConsumers, int light, float tickDelta);

    @Redirect(method = "render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;renderLabelIfPresent(Lnet/minecraft/entity/Entity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IF)V"))
    private void redirectRenderLabelIfPresent(EntityRenderer<T> instance, T entity, Text text,
        MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
        float tickDelta) {
        this.renderLabelIfPresent(entity, text, matrices,
            EntityProxy.postTextVertexConsumerProvider != null ?
                EntityProxy.postTextVertexConsumerProvider :
                vertexConsumers, light,
            tickDelta);
    }
}
