package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.TriState;
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
                        Identifier.ofVanilla("textures/block/lightning.png"),
                        TriState.FALSE,
                        false))
                    .build(false));
    }
}
