package com.radiance.client.constant;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.vertex.PBRVertexFormats;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.client.renderer.RenderType;

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
        LINES(VertexFormat.Mode.LINES, 0),
        LINE_STRIP(VertexFormat.Mode.LINE_STRIP, 1),
        DEBUG_LINES(VertexFormat.Mode.DEBUG_LINES, 2),
        DEBUG_LINE_STRIP(VertexFormat.Mode.DEBUG_LINE_STRIP, 3),
        TRIANGLES(VertexFormat.Mode.TRIANGLES, 4),
        TRIANGLE_STRIP(VertexFormat.Mode.TRIANGLE_STRIP, 5),
        TRIANGLE_FAN(VertexFormat.Mode.TRIANGLE_FAN, 6),
        QUADS(VertexFormat.Mode.QUADS, 7);

        private static final Map<VertexFormat.Mode, Integer>
            BY_DRAW_MODE =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(DrawModes::getDrawMode, DrawModes::getValue)));

        private final VertexFormat.Mode drawMode;
        private final int value;

        DrawModes(VertexFormat.Mode drawMode, int value) {
            this.drawMode = drawMode;
            this.value = value;
        }

        public static int getValue(VertexFormat.Mode drawMode) {
            return BY_DRAW_MODE.get(drawMode);
        }

        public VertexFormat.Mode getDrawMode() {
            return drawMode;
        }

        public int getValue() {
            return value;
        }
    }

    public enum VertexFormats {
        POSITION_COLOR_TEXTURE_LIGHT_NORMAL(
            com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK, 0),
        POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL(
            com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
            1),
        POSITION_TEXTURE_COLOR_LIGHT(
            com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE, 2),
        POSITION(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION, 3),
        POSITION_COLOR(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR, 4),
        LINES(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL, 5),
        POSITION_COLOR_LIGHT(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, 6),
        POSITION_TEXTURE(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX, 7),
        POSITION_TEXTURE_COLOR(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR, 8),
        POSITION_COLOR_TEXTURE_LIGHT(
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, 9),
        POSITION_TEXTURE_LIGHT_COLOR(
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR, 10),
        POSITION_TEXTURE_COLOR_NORMAL(
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, 11),
        ENTITY_PBR_TRIANGLE(PBRVertexFormats.ENTITY_PBR_TRIANGLE, 12),
        CHUNK_PBR_TRIANGLE(PBRVertexFormats.CHUNK_PBR_TRIANGLE, 13);

        private final VertexFormat vertexFormat;
        private final int value;

        VertexFormats(VertexFormat vertexFormat, int value) {
            this.vertexFormat = vertexFormat;
            this.value = value;
        }

        public static int getValue(VertexFormat vertexFormat) {
            for (VertexFormats value : values()) {
                if (value.vertexFormat == vertexFormat) {
                    return value.value;
                }
            }

            throw new IllegalArgumentException("Unknown vertex format: " + vertexFormat);
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

        public static GeometryTypes getGeometryType(RenderType renderLayer, boolean reflect) {

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

            RenderType.CompositeRenderType multiPhase = (RenderType.CompositeRenderType) renderLayer;
            if (multiPhase.name.contains("solid")) {

                return WORLD_SOLID;
            }

            return WORLD_TRANSPARENT;
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
        NAME_TAG(0b1000),
        WORLD_OVERLAY(0b10000);

        private final int value;

        PostRenderFlags(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

}
