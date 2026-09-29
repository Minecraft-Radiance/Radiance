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

import net.minecraft.client.render.VertexFormat;

public class PBRVertexFormats {

    public static final VertexFormat
        ENTITY_PBR_TRIANGLE =
        VertexFormat.builder()
            .add("Pos", PBR_POS)
            .add("NormalOct", PBR_NORM)
            .add("TextureUV", PBR_TEXTURE_UV)
            .add("ColorRGBA8", PBR_COLOR_LAYER)
            .add("LightUV", PBR_LIGHT_UV)
            .add("OverlayUV", PBR_OVERLAY_UV)
            .add("GlintUVHalf", PBR_GLINT_UV)
            .add("TextureID", PBR_TEXTURE_ID)
            .add("GlintTexture", PBR_GLINT_TEXTURE)
            .add("PostBase", PBR_POST_BASE)
            .add("PackedData", PBR_COORDINATE)
            .build();

    public static final VertexFormat
        CHUNK_PBR_TRIANGLE =
        VertexFormat.builder()
            .add("Pos", PBR_POS)
            .add("NormalOct", PBR_NORM)
            .add("TextureUV", PBR_TEXTURE_UV)
            .add("ColorRGBA8", PBR_COLOR_LAYER)
            .add("LightUV", PBR_LIGHT_UV)
            .add("TextureID", PBR_TEXTURE_ID)
            .add("PackedData", PBR_COORDINATE)
            .build();
}
