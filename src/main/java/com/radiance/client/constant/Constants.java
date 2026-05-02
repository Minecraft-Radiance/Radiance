package com.radiance.client.constant;

import com.radiance.client.vertex.PBRVertexFormats;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;

public class Constants {

    public enum IndexTypes {
        SHORT(VertexFormat.IndexType.SHORT, 0),
        INT(VertexFormat.IndexType.INT, 1);

        private static final Map<VertexFormat.IndexType, Integer>
            BY_INDEX_TYPE =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(IndexTypes::getIndexType, IndexTypes::getValue)));

        private final VertexFormat.IndexType indexType;
        private final int value;

        IndexTypes(VertexFormat.IndexType indexType, int value) {
            this.indexType = indexType;
            this.value = value;
        }

        public static int getValue(VertexFormat.IndexType indexType) {
            return BY_INDEX_TYPE.get(indexType);
        }

        public VertexFormat.IndexType getIndexType() {
            return indexType;
        }

        public int getValue() {
            return value;
        }
    }

    public enum DrawModes {
        LINES(VertexFormat.DrawMode.LINES, 0),
        LINE_STRIP(VertexFormat.DrawMode.LINE_STRIP, 1),
        DEBUG_LINES(VertexFormat.DrawMode.DEBUG_LINES, 2),
        DEBUG_LINE_STRIP(VertexFormat.DrawMode.DEBUG_LINE_STRIP, 3),
        TRIANGLES(VertexFormat.DrawMode.TRIANGLES, 4),
        TRIANGLE_STRIP(VertexFormat.DrawMode.TRIANGLE_STRIP, 5),
        TRIANGLE_FAN(VertexFormat.DrawMode.TRIANGLE_FAN, 6),
        QUADS(VertexFormat.DrawMode.QUADS, 7);

        private static final Map<VertexFormat.DrawMode, Integer>
            BY_DRAW_MODE =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(DrawModes::getDrawMode, DrawModes::getValue)));

        private final VertexFormat.DrawMode drawMode;
        private final int value;

        DrawModes(VertexFormat.DrawMode drawMode, int value) {
            this.drawMode = drawMode;
            this.value = value;
        }

        public static int getValue(VertexFormat.DrawMode drawMode) {
            return BY_DRAW_MODE.get(drawMode);
        }

        public VertexFormat.DrawMode getDrawMode() {
            return drawMode;
        }

        public int getValue() {
            return value;
        }
    }

    public enum VertexFormats {
        POSITION_COLOR_TEXTURE_LIGHT_NORMAL(
            net.minecraft.client.render.VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, 0),
        POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL(
            net.minecraft.client.render.VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
            1),
        POSITION_TEXTURE_COLOR_LIGHT(
            net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, 2),
        POSITION(net.minecraft.client.render.VertexFormats.POSITION, 3),
        POSITION_COLOR(net.minecraft.client.render.VertexFormats.POSITION_COLOR, 4),
        LINES(net.minecraft.client.render.VertexFormats.LINES, 5),
        POSITION_COLOR_LIGHT(net.minecraft.client.render.VertexFormats.POSITION_COLOR_LIGHT, 6),
        POSITION_TEXTURE(net.minecraft.client.render.VertexFormats.POSITION_TEXTURE, 7),
        POSITION_TEXTURE_COLOR(net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_COLOR, 8),
        POSITION_COLOR_TEXTURE_LIGHT(
            net.minecraft.client.render.VertexFormats.POSITION_COLOR_TEXTURE_LIGHT, 9),
        POSITION_TEXTURE_LIGHT_COLOR(
            net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_LIGHT_COLOR, 10),
        POSITION_TEXTURE_COLOR_NORMAL(
            net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_COLOR_NORMAL, 11),
        PBR_TRIANGLE(PBRVertexFormats.PBR_TRIANGLE, 12);

        private static final Map<VertexFormat, Integer>
            BY_VERTEX_FORMAT =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(
                    Collectors.toMap(VertexFormats::getVertexFormat, VertexFormats::getValue)));

        private final VertexFormat vertexFormat;
        private final int value;

        VertexFormats(VertexFormat vertexFormat, int value) {
            this.vertexFormat = vertexFormat;
            this.value = value;
        }

        public static int getValue(VertexFormat vertexFormat) {
            return BY_VERTEX_FORMAT.get(vertexFormat);
        }

        public VertexFormat getVertexFormat() {
            return vertexFormat;
        }

        public int getValue() {
            return value;
        }
    }

    public enum GeometryTypes {
        SHADOW(0),
        WORLD_SOLID(1),
        WORLD_TRANSPARENT(2),
        WORLD_NO_REFLECT(3),
        WORLD_CLOUD(4),
        BOAT_WATER_MASK(5),
        END_PORTAL(6),
        END_GATEWAY(7);

        private final int value;

        GeometryTypes(int value) {
            this.value = value;
        }

        public static GeometryTypes getGeometryType(RenderLayer renderLayer, boolean reflect) {
            // single objects
            if (renderLayer.name.contains("water_mask")) {
                return BOAT_WATER_MASK;
            } else if (renderLayer.name.contains("end_portal")) {
                return END_PORTAL;
            } else if (renderLayer.name.contains("end_gateway")) {
                return END_GATEWAY;
            }

            if (renderLayer.name.contains("cloud")) {
                return WORLD_CLOUD;
            }

            if (!reflect) {
                return WORLD_NO_REFLECT;
            }

            RenderLayer.MultiPhase multiPhase = (RenderLayer.MultiPhase) renderLayer;
            if (multiPhase.name.contains("solid")) {
                // solid
                return WORLD_SOLID;
            }

            if (multiPhase.isTranslucent()) {
                // transparent
                if (RenderPhase.NO_TRANSPARENCY.equals(multiPhase.phases.transparency)) {
                    return WORLD_TRANSPARENT;
                } else if (RenderPhase.ADDITIVE_TRANSPARENCY.equals(
                    multiPhase.phases.transparency)) {
                    return WORLD_TRANSPARENT;
                } else if (RenderPhase.LIGHTNING_TRANSPARENCY.equals(
                    multiPhase.phases.transparency)) {
                    return WORLD_TRANSPARENT;
                } else if (RenderPhase.GLINT_TRANSPARENCY.equals(multiPhase.phases.transparency)) {
                    return WORLD_TRANSPARENT;
                } else if (RenderPhase.CRUMBLING_TRANSPARENCY.equals(
                    multiPhase.phases.transparency)) {
                    return WORLD_TRANSPARENT;
                } else if (RenderPhase.OVERLAY_TRANSPARENCY.equals(
                    multiPhase.phases.transparency)) {
                    return WORLD_TRANSPARENT;
                } else if (RenderPhase.TRANSLUCENT_TRANSPARENCY.equals(
                    multiPhase.phases.transparency)) {
                    return WORLD_TRANSPARENT;
                } else {
                    throw new IllegalArgumentException("Invalid render layer " + multiPhase);
                }
            } else {
                // cut out
                return WORLD_TRANSPARENT;
            }
        }

        public int getValue() {
            return value;
        }
    }

    public enum Coordinates {
        WORLD(0),
        CAMERA(1),
        CAMERA_SHIFT(2);

        private final int value;

        Coordinates(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum RayTracingFlags {
        WORLD(0b00000001),
        PLAYER(0b00000010),
        FISHING_BOBBER(0b00000100),
        HAND(0b00001000),
        WEATHER(0b00010000),
        PARTICLE(0b00100000),
        CLOUD(0b01000000),
        BOAT_WATER_MASK(0b10000000);

        private final int value;

        RayTracingFlags(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum PostRenderFlags {
        WEATHER(0b0001),
        PARTICLE(0b0010),
        TEXT(0b0100),
        NAME_TAG(0b1000);

        private final int value;

        PostRenderFlags(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

}
