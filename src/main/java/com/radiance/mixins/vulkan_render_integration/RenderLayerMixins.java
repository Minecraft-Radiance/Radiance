package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.VertexSorter;
import com.radiance.client.render.BuiltBuffer;
import com.radiance.client.render.CustomParticleRenderer;
import com.radiance.client.proxy.world.EntityProxy;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderLayer.class)
public class RenderLayerMixins {

    @Shadow
    @Final
    @Mutable
    private static RenderLayer LIGHTNING;

    @Shadow
    @Final
    private boolean translucent;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void replaceLightning(CallbackInfo ci) {
        LIGHTNING =
            RenderLayer.of("lightning",
                VertexFormats.POSITION_TEXTURE_COLOR,
                VertexFormat.DrawMode.QUADS,
                1536,
                false,
                true,
                RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderLayer.LIGHTNING_PROGRAM)
                    .writeMaskState(RenderLayer.ALL_MASK)
                    .transparency(RenderLayer.LIGHTNING_TRANSPARENCY)
                    .target(RenderLayer.WEATHER_TARGET)
                    .texture(new RenderPhase.Texture(
                        new Identifier("minecraft", "textures/block/lightning.png"),
                        false,
                        false))
                    .build(false));
    }

    @Inject(method = "draw(Lnet/minecraft/client/render/BufferBuilder;Lcom/mojang/blaze3d/systems/VertexSorter;)V",
        at = @At("HEAD"),
        cancellable = true)
    private void captureCustomParticleDraw(BufferBuilder buffer, VertexSorter sorter,
        CallbackInfo ci) {
        if ((!CustomParticleRenderer.isCapturing() && !EntityProxy.isWorldRenderCaptureActive()) || !buffer.isBuilding()) {
            return;
        }

        if (this.translucent) {
            buffer.setSorter(sorter);
        }

        BufferBuilder.BuiltBuffer vanillaBuffer = buffer.end();
        BuiltBuffer builtBuffer = BuiltBuffer.wrap(vanillaBuffer);
        if (EntityProxy.captureWorldRenderLayer((RenderLayer) (Object) this, builtBuffer)
            || CustomParticleRenderer.capture((RenderLayer) (Object) this, builtBuffer)) {
            ci.cancel();
            return;
        }

        ((RenderLayer) (Object) this).startDrawing();
        BufferRenderer.drawWithGlobalProgram(vanillaBuffer);
        ((RenderLayer) (Object) this).endDrawing();
        ci.cancel();
    }
}
