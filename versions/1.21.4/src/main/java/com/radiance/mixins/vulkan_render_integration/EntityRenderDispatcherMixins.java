package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixins {

    @Inject(method =
        "renderShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;"
            +
            "Lnet/minecraft/client/render/entity/state/EntityRenderState;FFLnet/minecraft/world/WorldView;F)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    private static void cancelRenderShadow(MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        EntityRenderState renderState,
        float opacity,
        float tickDelta,
        WorldView world,
        float radius,
        CallbackInfo ci) {
        ci.cancel();
    }
}
