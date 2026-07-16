package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.world.EntityProxy;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixins {

    @Shadow
    protected abstract void renderLabelIfPresent(EntityRenderState state, Text text,
        MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light);

    @Redirect(method = "render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void redirectRenderLabelIfPresent(EntityRenderer<?, ?> instance,
        EntityRenderState state, Text text, MatrixStack matrices,
        VertexConsumerProvider vertexConsumers, int light) {
        this.renderLabelIfPresent(state, text, matrices,
            EntityProxy.postTextVertexConsumerProvider != null ?
                EntityProxy.postTextVertexConsumerProvider :
                vertexConsumers, light);
    }
}
