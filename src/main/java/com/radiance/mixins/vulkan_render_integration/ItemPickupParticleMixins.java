package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.render.CustomParticleRenderer;
import net.minecraft.client.particle.ItemPickupParticle;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ItemPickupParticle.class)
public class ItemPickupParticleMixins {

    @ModifyArg(method = "render",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
        index = 7)
    private MultiBufferSource useParticleVertexConsumers(MultiBufferSource original) {
        return CustomParticleRenderer.getVertexConsumerProvider(original);
    }
}
