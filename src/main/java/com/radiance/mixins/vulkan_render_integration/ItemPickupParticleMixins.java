package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.render.CustomParticleRenderer;
import net.minecraft.client.particle.ItemPickupParticle;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ItemPickupParticle.class)
public class ItemPickupParticleMixins {

    @ModifyArg(method = "buildGeometry",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"),
        index = 7)
    private VertexConsumerProvider useParticleVertexConsumers(VertexConsumerProvider original) {
        return CustomParticleRenderer.getVertexConsumerProvider(original);
    }
}
