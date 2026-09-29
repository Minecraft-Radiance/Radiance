package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.VertexFormatElement;

public class PBRVertexFormatElements {

    public static final VertexFormatElement PBR_POS = new VertexFormatElement(0,
        VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.POSITION, 3);
    public static final VertexFormatElement PBR_NORM = new VertexFormatElement(0,
        VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);
    public static final VertexFormatElement PBR_TEXTURE_UV = new VertexFormatElement(0,
        VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 2);
    public static final VertexFormatElement PBR_COLOR_LAYER = new VertexFormatElement(1,
        VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);
    public static final VertexFormatElement PBR_LIGHT_UV = new VertexFormatElement(2,
        VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);
    public static final VertexFormatElement PBR_OVERLAY_UV = new VertexFormatElement(3,
        VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);
    public static final VertexFormatElement PBR_GLINT_UV = new VertexFormatElement(4,
        VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);
    public static final VertexFormatElement PBR_TEXTURE_ID = new VertexFormatElement(5,
        VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);
    public static final VertexFormatElement PBR_GLINT_TEXTURE = new VertexFormatElement(6,
        VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);
    public static final VertexFormatElement PBR_POST_BASE = new VertexFormatElement(0,
        VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 3);
    public static final VertexFormatElement PBR_COORDINATE = new VertexFormatElement(7,
        VertexFormatElement.Type.UINT, VertexFormatElement.Usage.UV, 1);
}
