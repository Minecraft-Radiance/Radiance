package com.radiance.mixins.blaze3d_26.sodium;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.caffeinemc.mods.sodium.client.gpu.device.context.DrawContext;
import net.caffeinemc.mods.sodium.client.gpu.device.context.GLDrawContext;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GLDrawContext.class, remap = false)
public abstract class SodiumGlDrawContextMixin extends DrawContext {
    @Inject(method = "setContext", at = @At("HEAD"), cancellable = true)
    private void radiance$useRadianceRenderPass(RenderPass pass, RenderPipeline pipeline,
        CallbackInfo ci) {
        // Sodium treats every non-Mojang-Vulkan backend as OpenGL. Keep its draw
        // batching active without accessing GlRenderPass or issuing GL uniforms.
        this.pass = pass;
        ci.cancel();
    }

    @Inject(method = "updateData", at = @At("HEAD"), cancellable = true)
    private void radiance$skipOpenGlRegionUniforms(RenderRegion region,
        CameraTransform camera, CallbackInfo ci) {
        ci.cancel();
    }
}
