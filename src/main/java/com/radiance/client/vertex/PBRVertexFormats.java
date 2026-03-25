package com.radiance.client.vertex;

import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_ALBEDO_EMISSION;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COORDINATE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_TEXTURE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_LIGHT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_OVERLAY_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_PADDING;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POS;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POST_BASE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_ID;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_GLINT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_LIGHT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_OVERLAY;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_TEXTURE;

import com.google.common.collect.ImmutableMap;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;

public class PBRVertexFormats {

    public static final VertexFormat
        PBR_TRIANGLE =
        new VertexFormat(ImmutableMap.<String, VertexFormatElement>builder()
            .put("Pos", PBR_POS)
            .put("UseNorm", PBR_USE_NORM)
            .put("Norm", PBR_NORM)
            .put("UseColorLayer", PBR_USE_COLOR_LAYER)
            .put("ColorLayer", PBR_COLOR_LAYER)
            .put("UseTexture", PBR_USE_TEXTURE)
            .put("UseOverlay", PBR_USE_OVERLAY)
            .put("TextureUV", PBR_TEXTURE_UV)
            .put("OverlayUV", PBR_OVERLAY_UV)
            .put("UseGlint", PBR_USE_GLINT)
            .put("TextureID", PBR_TEXTURE_ID)
            .put("GlintUV", PBR_GLINT_UV)
            .put("GlintTexture", PBR_GLINT_TEXTURE)
            .put("UseLight", PBR_USE_LIGHT)
            .put("LightUV", PBR_LIGHT_UV)
            .put("Coordinate", PBR_COORDINATE)
            .put("AlbedoEmission", PBR_ALBEDO_EMISSION)
            .put("PostBase", PBR_POST_BASE)
            .put("Padding", PBR_PADDING)
            .build());
}
