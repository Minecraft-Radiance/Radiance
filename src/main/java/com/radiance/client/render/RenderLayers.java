package com.radiance.client.render;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class RenderLayers extends RenderPhase {

    private static final ShaderProgram PARTICLE_PROGRAM = new ShaderProgram(
        GameRenderer::getParticleProgram);
    private static final ShaderProgram CLOUDS_PROGRAM = new ShaderProgram(
        GameRenderer::getPositionTexColorNormalProgram);

    private RenderLayers() {
        super("radiance_render_layers", () -> {
        }, () -> {
        });
    }

    public static RenderLayer createOpaqueParticleLayer(Identifier texture) {
        return RenderLayer.of("opaque_particle", VertexFormats.POSITION_TEXTURE_COLOR_LIGHT,
            VertexFormat.DrawMode.QUADS, 1536, false, false,
            RenderLayer.MultiPhaseParameters.builder()
                .program(PARTICLE_PROGRAM)
                .texture(new RenderPhase.Texture(texture, false, false))
                .lightmap(RenderLayer.ENABLE_LIGHTMAP)
                .writeMaskState(RenderLayer.ALL_MASK)
                .build(false));
    }

    public static RenderLayer createTranslucentParticleLayer(Identifier texture) {
        return RenderLayer.of("translucent_particle",
            VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS, 1536, false,
            false, RenderLayer.MultiPhaseParameters.builder()
                .program(PARTICLE_PROGRAM)
                .texture(new RenderPhase.Texture(texture, false, false))
                .transparency(RenderLayer.TRANSLUCENT_TRANSPARENCY)
                .target(RenderLayer.PARTICLES_TARGET)
                .lightmap(RenderLayer.ENABLE_LIGHTMAP)
                .writeMaskState(RenderLayer.ALL_MASK)
                .build(false));
    }

    public static RenderLayer createWeatherLayer(String name, Identifier texture,
        boolean allMask) {
        return RenderLayer.of(name, VertexFormats.POSITION_TEXTURE_COLOR_LIGHT,
            VertexFormat.DrawMode.QUADS, 1536, false, false,
            RenderLayer.MultiPhaseParameters.builder()
                .program(PARTICLE_PROGRAM)
                .texture(new RenderPhase.Texture(texture, false, false))
                .transparency(RenderLayer.TRANSLUCENT_TRANSPARENCY)
                .target(RenderLayer.WEATHER_TARGET)
                .lightmap(RenderLayer.ENABLE_LIGHTMAP)
                .writeMaskState(allMask ? RenderLayer.ALL_MASK : RenderLayer.COLOR_MASK)
                .cull(RenderLayer.DISABLE_CULLING)
                .build(false));
    }

    public static RenderLayer createFastCloudsLayer(Identifier texture) {
        return RenderLayer.of("clouds", VertexFormats.POSITION_TEXTURE_COLOR_NORMAL,
            VertexFormat.DrawMode.QUADS, 786432, false, false,
            RenderLayer.MultiPhaseParameters.builder()
                .program(CLOUDS_PROGRAM)
                .texture(new RenderPhase.Texture(texture, false, false))
                .transparency(RenderLayer.TRANSLUCENT_TRANSPARENCY)
                .cull(RenderLayer.DISABLE_CULLING)
                .writeMaskState(RenderLayer.ALL_MASK)
                .target(RenderLayer.CLOUDS_TARGET)
                .build(true));
    }
}
