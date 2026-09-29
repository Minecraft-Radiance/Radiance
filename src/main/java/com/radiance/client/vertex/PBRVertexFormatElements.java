package com.radiance.client.vertex;

import com.mojang.blaze3d.vertex.VertexFormatElement;

public class PBRVertexFormatElements {

    public static final VertexFormatElement
        PBR_POS =
        VertexFormatElement.register(VertexFormatElement.findNextId(), 0, VertexFormatElement.Type.FLOAT,
            VertexFormatElement.Usage.GENERIC, 3);

    public static final VertexFormatElement
        PBR_NORM =
        VertexFormatElement.register(VertexFormatElement.findNextId(), 0, VertexFormatElement.Type.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_COLOR_LAYER =
        VertexFormatElement.register(VertexFormatElement.findNextId(), 0, VertexFormatElement.Type.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_TEXTURE_UV =
        VertexFormatElement.register(VertexFormatElement.findNextId(), 0, VertexFormatElement.Type.FLOAT,
            VertexFormatElement.Usage.GENERIC, 2);

    public static final VertexFormatElement
        PBR_OVERLAY_UV =
        VertexFormatElement.register(VertexFormatElement.findNextId(), 0, VertexFormatElement.Type.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_TEXTURE_ID =
        VertexFormatElement.register(VertexFormatElement.findNextId(), 0, VertexFormatElement.Type.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_GLINT_UV =
        VertexFormatElement.register(VertexFormatElement.findNextId(), 0, VertexFormatElement.Type.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_GLINT_TEXTURE =
        VertexFormatElement.register(VertexFormatElement.findNextId(), 0, VertexFormatElement.Type.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_LIGHT_UV =
        VertexFormatElement.register(VertexFormatElement.findNextId(), 0, VertexFormatElement.Type.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_COORDINATE =
        VertexFormatElement.register(VertexFormatElement.findNextId(), 0, VertexFormatElement.Type.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_POST_BASE =
        VertexFormatElement.register(VertexFormatElement.findNextId(), 0, VertexFormatElement.Type.FLOAT,
            VertexFormatElement.Usage.GENERIC, 3);

}
