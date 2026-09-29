package com.radiance.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class RenderLayers extends RenderStateShard {

    private static final ShaderStateShard PARTICLE_PROGRAM = new ShaderStateShard(
        GameRenderer::getParticleShader);
    private static final ShaderStateShard CLOUDS_PROGRAM = new ShaderStateShard(
        GameRenderer::getPositionTexColorNormalShader);

    private RenderLayers() {
        super("radiance_render_layers", () -> {
        }, () -> {
        });
    }

    public static RenderType createOpaqueParticleLayer(ResourceLocation texture) {
        return RenderType.create("opaque_particle", DefaultVertexFormat.PARTICLE,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                .setShaderState(PARTICLE_PROGRAM)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setLightmapState(RenderType.LIGHTMAP)
                .setWriteMaskState(RenderType.COLOR_DEPTH_WRITE)
                .createCompositeState(false));
    }

    public static RenderType createTranslucentParticleLayer(ResourceLocation texture) {
        return RenderType.create("translucent_particle",
            DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, 1536, false,
            false, RenderType.CompositeState.builder()
                .setShaderState(PARTICLE_PROGRAM)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                .setOutputState(RenderType.PARTICLES_TARGET)
                .setLightmapState(RenderType.LIGHTMAP)
                .setWriteMaskState(RenderType.COLOR_DEPTH_WRITE)
                .createCompositeState(false));
    }

    public static RenderType createWeatherLayer(String name, ResourceLocation texture,
        boolean allMask) {
        return RenderType.create(name, DefaultVertexFormat.PARTICLE,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                .setShaderState(PARTICLE_PROGRAM)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                .setOutputState(RenderType.WEATHER_TARGET)
                .setLightmapState(RenderType.LIGHTMAP)
                .setWriteMaskState(allMask ? RenderType.COLOR_DEPTH_WRITE : RenderType.COLOR_WRITE)
                .setCullState(RenderType.NO_CULL)
                .createCompositeState(false));
    }

    public static RenderType createFastCloudsLayer(ResourceLocation texture) {
        return RenderType.create("clouds", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
            VertexFormat.Mode.QUADS, 786432, false, false,
            RenderType.CompositeState.builder()
                .setShaderState(CLOUDS_PROGRAM)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                .setCullState(RenderType.NO_CULL)
                .setWriteMaskState(RenderType.COLOR_DEPTH_WRITE)
                .setOutputState(RenderType.CLOUDS_TARGET)
                .createCompositeState(true));
    }
}
