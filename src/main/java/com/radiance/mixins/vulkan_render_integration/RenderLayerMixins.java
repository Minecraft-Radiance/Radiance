package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.TriState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderType.class)
public class RenderLayerMixins {

    @Shadow
    @Final
    @Mutable
    private static RenderType LIGHTNING;

    @Inject(method = "Lnet/minecraft/client/renderer/RenderType;<clinit>()V", at = @At("TAIL"))
    private static void replaceLightning(CallbackInfo ci) {
        LIGHTNING =
            RenderType.create("lightning",
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                    .setShaderState(RenderType.RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(RenderType.COLOR_DEPTH_WRITE)
                    .setTransparencyState(RenderType.LIGHTNING_TRANSPARENCY)
                    .setOutputState(RenderType.WEATHER_TARGET)
                    .setTextureState(new RenderStateShard.TextureStateShard(
                        ResourceLocation.withDefaultNamespace("textures/block/lightning.png"),
                        TriState.FALSE,
                        false))
                    .createCompositeState(false));
    }
}
