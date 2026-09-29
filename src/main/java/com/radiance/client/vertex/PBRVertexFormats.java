package com.radiance.client.vertex;

import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COORDINATE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_TEXTURE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_LIGHT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_OVERLAY_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POS;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POST_BASE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_ID;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_UV;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.VertexFormat;

public class PBRVertexFormats {

    public static final VertexFormat ENTITY_PBR_TRIANGLE = new VertexFormat(
        ImmutableMap.<String, com.mojang.blaze3d.vertex.VertexFormatElement>builder()
            .put("Pos", PBR_POS)
            .put("NormalOct", PBR_NORM)
            .put("TextureUV", PBR_TEXTURE_UV)
            .put("ColorRGBA8", PBR_COLOR_LAYER)
            .put("LightUV", PBR_LIGHT_UV)
            .put("OverlayUV", PBR_OVERLAY_UV)
            .put("GlintUVHalf", PBR_GLINT_UV)
            .put("TextureID", PBR_TEXTURE_ID)
            .put("GlintTexture", PBR_GLINT_TEXTURE)
            .put("PostBase", PBR_POST_BASE)
            .put("PackedData", PBR_COORDINATE)
            .build());

    public static final VertexFormat CHUNK_PBR_TRIANGLE = new VertexFormat(
        ImmutableMap.<String, com.mojang.blaze3d.vertex.VertexFormatElement>builder()
            .put("Pos", PBR_POS)
            .put("NormalOct", PBR_NORM)
            .put("TextureUV", PBR_TEXTURE_UV)
            .put("ColorRGBA8", PBR_COLOR_LAYER)
            .put("LightUV", PBR_LIGHT_UV)
            .put("TextureID", PBR_TEXTURE_ID)
            .put("PackedData", PBR_COORDINATE)
            .build());
}
