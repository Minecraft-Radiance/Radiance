package com.radiance.client.vertex;

import net.minecraft.client.render.VertexFormatElement;

public class PBRVertexFormatElements {

    private static int nextId() {
        for (int id = 0; id < VertexFormatElement.field_52106; id++) {
            if (VertexFormatElement.get(id) == null) return id;
        }
        throw new IllegalStateException("Vertex format element limit exceeded while registering Radiance");
    }

    public static final VertexFormatElement
        PBR_POS =
        VertexFormatElement.register(nextId(), 0, VertexFormatElement.ComponentType.FLOAT,
            VertexFormatElement.Usage.GENERIC, 3);

    public static final VertexFormatElement
        PBR_NORM =
        VertexFormatElement.register(nextId(), 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_COLOR_LAYER =
        VertexFormatElement.register(nextId(), 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_TEXTURE_UV =
        VertexFormatElement.register(nextId(), 0, VertexFormatElement.ComponentType.FLOAT,
            VertexFormatElement.Usage.GENERIC, 2);

    public static final VertexFormatElement
        PBR_OVERLAY_UV =
        VertexFormatElement.register(nextId(), 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_TEXTURE_ID =
        VertexFormatElement.register(nextId(), 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_GLINT_UV =
        VertexFormatElement.register(nextId(), 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_GLINT_TEXTURE =
        VertexFormatElement.register(nextId(), 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_LIGHT_UV =
        VertexFormatElement.register(nextId(), 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_COORDINATE =
        VertexFormatElement.register(nextId(), 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_POST_BASE =
        VertexFormatElement.register(nextId(), 0, VertexFormatElement.ComponentType.FLOAT,
            VertexFormatElement.Usage.GENERIC, 3);

}
