package com.radiance.client.constant;

//#if MC>=260200
//$$ import com.mojang.blaze3d.IndexType;
//$$ import com.mojang.blaze3d.PrimitiveTopology;
//$$ import com.mojang.blaze3d.vertex.DefaultVertexFormat;
//$$ import com.mojang.blaze3d.vertex.VertexFormat;
//#else
import com.radiance.client.vertex.PBRVertexFormats;
//#endif
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
//#if MC<260200
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
//#endif

public class Constants {

    public static class SceneTags {

        public static final int VERSION = 1;
        public static final int INT_STRIDE = 4;

        public static final int VIEW_GEOMETRY = 1 << 0;
        public static final int VIEW_ACCELERATION = 1 << 1;
        public static final int VIEW_POST = 1 << 2;

        public static final int SOURCE_UNKNOWN = 0;
        public static final int SOURCE_VANILLA_CHUNK = 1;
        public static final int SOURCE_VANILLA_ENTITY = 2;
        public static final int SOURCE_VANILLA_POST = 3;

        private SceneTags() {
        }

        public static void write(int[] tags, int geometryIndex, int viewMask, int sourceTag) {
            int base = geometryIndex * INT_STRIDE;
            tags[base] = viewMask;
            tags[base + 1] = sourceTag;
            tags[base + 2] = 0;
            tags[base + 3] = 0;
        }

        public static void writeVanillaChunk(int[] tags, int geometryIndex) {
            write(tags, geometryIndex, VIEW_GEOMETRY | VIEW_ACCELERATION, SOURCE_VANILLA_CHUNK);
        }

        public static void writeVanillaEntity(int[] tags, int geometryIndex) {
            write(tags, geometryIndex, VIEW_GEOMETRY | VIEW_ACCELERATION, SOURCE_VANILLA_ENTITY);
        }

        public static void writeVanillaPost(int[] tags, int geometryIndex) {
            write(tags, geometryIndex, VIEW_POST, SOURCE_VANILLA_POST);
        }
    }

    public static class RasterMetadata {

        public static final int VERSION = 1;
        public static final int INT_STRIDE = 14;

        public static final int ALPHA_UNKNOWN = 0;
        public static final int ALPHA_OPAQUE = 1;
        public static final int ALPHA_CUTOUT = 2;
        public static final int ALPHA_TRANSLUCENT = 3;

        public static final int BLEND_UNKNOWN = 0;
        public static final int BLEND_OPAQUE = 1;
        public static final int BLEND_ALPHA = 2;
        public static final int BLEND_ADDITIVE = 3;
        public static final int BLEND_LIGHTNING = 4;
        public static final int BLEND_GLINT = 5;
        public static final int BLEND_CRUMBLING = 6;
        public static final int BLEND_OVERLAY = 7;

        public static final int DEPTH_UNKNOWN = 0;
        public static final int DEPTH_TEST_WRITE = 1;
        public static final int DEPTH_TEST_ONLY = 2;
        public static final int DEPTH_ALWAYS = 3;
        public static final int DEPTH_DISABLED = 4;

        public static final int CULL_UNKNOWN = 0;
        public static final int CULL_BACK = 1;
        public static final int CULL_FRONT = 2;
        public static final int CULL_NONE = 3;

        public static final int WRITE_COLOR = 1 << 0;
        public static final int WRITE_DEPTH = 1 << 1;

        public static final int STATE_LIGHTMAP = 1 << 0;
        public static final int STATE_OVERLAY = 1 << 1;
        public static final int STATE_OUTLINE = 1 << 2;
        public static final int STATE_CRUMBLING = 1 << 3;
        public static final int STATE_SORT_ON_UPLOAD = 1 << 4;
        public static final int STATE_NO_REFLECT = 1 << 5;

        public static final int TARGET_UNKNOWN = 0;
        public static final int TARGET_MAIN = 1;
        public static final int TARGET_TRANSLUCENT = 2;
        public static final int TARGET_WEATHER = 3;
        public static final int TARGET_CLOUDS = 4;
        public static final int TARGET_PARTICLES = 5;
        public static final int TARGET_OUTLINE = 6;
        public static final int TARGET_OVERLAY = 7;

        public static final int LAYERING_UNKNOWN = 0;
        public static final int LAYERING_NONE = 1;
        public static final int LAYERING_POLYGON_OFFSET = 2;
        public static final int LAYERING_VIEW_OFFSET_Z = 3;
        public static final int LAYERING_VIEW_OFFSET_Z_FORWARD = 4;
        public static final int LAYERING_WORLD_BORDER = 5;

        public static final int SEMANTIC_WATER = 1 << 0;
        public static final int SEMANTIC_GLASS = 1 << 1;
        public static final int SEMANTIC_REFRACTION = 1 << 2;
        public static final int SEMANTIC_TRANSMISSION = 1 << 3;
        public static final int SEMANTIC_PORTAL = 1 << 4;
        public static final int SEMANTIC_WEATHER = 1 << 5;
        public static final int SEMANTIC_TEXT = 1 << 6;
        public static final int SEMANTIC_PARTICLE = 1 << 7;
        public static final int SEMANTIC_OVERLAY = 1 << 8;

        private RasterMetadata() {
        }

        //#if MC<260200
        public static void write(int[] metadata, int geometryIndex, RenderLayer renderLayer,
            boolean reflect) {
            int base = geometryIndex * INT_STRIDE;
            RenderLayer.MultiPhase multiPhase = renderLayer instanceof RenderLayer.MultiPhase phase
                ? phase
                : null;
            String name = renderLayer.name;
            int alphaMode = alphaMode(name, multiPhase);
            int blendMode = blendMode(multiPhase);
            int depthPolicy = depthPolicy(alphaMode, blendMode);
            int cullMode = cullMode(multiPhase);
            int stateFlags = stateFlags(name, reflect);
            int writeMask = writeMask(depthPolicy);
            int outputTarget = outputTarget(name, blendMode);
            int layering = layering(name);
            int semanticFlags = semanticFlags(name, outputTarget);
            int sortKey = sortKey(outputTarget, blendMode, alphaMode, name);

            metadata[base] = alphaMode;
            metadata[base + 1] = blendMode;
            metadata[base + 2] = depthPolicy;
            metadata[base + 3] = cullMode;
            metadata[base + 4] = stateFlags;
            metadata[base + 5] = writeMask;
            metadata[base + 6] = outputTarget;
            metadata[base + 7] = layering;
            metadata[base + 8] = semanticFlags;
            metadata[base + 9] = sortKey;
            metadata[base + 10] = Float.floatToRawIntBits(polygonOffsetFactor(layering));
            metadata[base + 11] = Float.floatToRawIntBits(polygonOffsetUnits(layering));
            metadata[base + 12] = 0;
            metadata[base + 13] = 0;
        }
        //#endif

        public static void write(int[] metadata, int geometryIndex, String layerName,
            int blendMode, boolean depthWrite, boolean cull, boolean reflect) {
            String name = layerName == null ? "" : layerName;
            int alphaMode = alphaMode(name);
            int resolvedBlendMode = blendMode == BLEND_UNKNOWN ? blendMode(name) : blendMode;
            int depthPolicy = depthWrite && alphaMode != ALPHA_TRANSLUCENT
                && resolvedBlendMode == BLEND_OPAQUE ? DEPTH_TEST_WRITE : DEPTH_TEST_ONLY;
            int cullMode = cull ? CULL_BACK : CULL_NONE;
            int base = geometryIndex * INT_STRIDE;
            int stateFlags = stateFlags(name, reflect);
            int writeMask = writeMask(depthPolicy);
            int outputTarget = outputTarget(name, resolvedBlendMode);
            int layering = layering(name);
            int semanticFlags = semanticFlags(name, outputTarget);
            int sortKey = sortKey(outputTarget, resolvedBlendMode, alphaMode, name);

            metadata[base] = alphaMode;
            metadata[base + 1] = resolvedBlendMode;
            metadata[base + 2] = depthPolicy;
            metadata[base + 3] = cullMode;
            metadata[base + 4] = stateFlags;
            metadata[base + 5] = writeMask;
            metadata[base + 6] = outputTarget;
            metadata[base + 7] = layering;
            metadata[base + 8] = semanticFlags;
            metadata[base + 9] = sortKey;
            metadata[base + 10] = Float.floatToRawIntBits(polygonOffsetFactor(layering));
            metadata[base + 11] = Float.floatToRawIntBits(polygonOffsetUnits(layering));
            metadata[base + 12] = 0;
            metadata[base + 13] = 0;
        }

        //#if MC<260200
        private static int alphaMode(String name, RenderLayer.MultiPhase multiPhase) {
            if (name.contains("solid")) {
                return ALPHA_OPAQUE;
            }
            if (name.contains("cutout")) {
                return ALPHA_CUTOUT;
            }
            if (name.contains("translucent") || name.contains("water") || name.contains("glass")
                || name.contains("portal") || name.contains("cloud") || name.contains("weather")
                || name.contains("particle") || name.contains("text")) {
                return ALPHA_TRANSLUCENT;
            }
            if (multiPhase != null && RenderPhase.NO_TRANSPARENCY.equals(multiPhase.phases.transparency)) {
                return ALPHA_CUTOUT;
            }
            if (multiPhase != null && multiPhase.isTranslucent()) {
                return ALPHA_TRANSLUCENT;
            }
            return ALPHA_OPAQUE;
        }
        //#endif

        private static int alphaMode(String name) {
            if (name.contains("solid")) {
                return ALPHA_OPAQUE;
            }
            if (name.contains("cutout")) {
                return ALPHA_CUTOUT;
            }
            if (name.contains("translucent") || name.contains("water") || name.contains("glass")
                || name.contains("portal") || name.contains("cloud") || name.contains("weather")
                || name.contains("particle") || name.contains("text")) {
                return ALPHA_TRANSLUCENT;
            }
            return ALPHA_OPAQUE;
        }

        //#if MC<260200
        private static int blendMode(RenderLayer.MultiPhase multiPhase) {
            if (multiPhase == null || RenderPhase.NO_TRANSPARENCY.equals(multiPhase.phases.transparency)) {
                return BLEND_OPAQUE;
            }
            if (RenderPhase.ADDITIVE_TRANSPARENCY.equals(multiPhase.phases.transparency)) {
                return BLEND_ADDITIVE;
            }
            if (RenderPhase.LIGHTNING_TRANSPARENCY.equals(multiPhase.phases.transparency)) {
                return BLEND_LIGHTNING;
            }
            if (RenderPhase.GLINT_TRANSPARENCY.equals(multiPhase.phases.transparency)) {
                return BLEND_GLINT;
            }
            if (RenderPhase.CRUMBLING_TRANSPARENCY.equals(multiPhase.phases.transparency)) {
                return BLEND_CRUMBLING;
            }
            if (RenderPhase.OVERLAY_TRANSPARENCY.equals(multiPhase.phases.transparency)) {
                return BLEND_OVERLAY;
            }
            if (RenderPhase.TRANSLUCENT_TRANSPARENCY.equals(multiPhase.phases.transparency)) {
                return BLEND_ALPHA;
            }
            return BLEND_ALPHA;
        }
        //#endif

        private static int blendMode(String name) {
            if (name.contains("lightning")) {
                return BLEND_LIGHTNING;
            }
            if (name.contains("glint")) {
                return BLEND_GLINT;
            }
            if (name.contains("crumbling")) {
                return BLEND_CRUMBLING;
            }
            if (name.contains("overlay")) {
                return BLEND_OVERLAY;
            }
            if (name.contains("additive")) {
                return BLEND_ADDITIVE;
            }
            return alphaMode(name) == ALPHA_TRANSLUCENT ? BLEND_ALPHA : BLEND_OPAQUE;
        }

        private static int depthPolicy(int alphaMode, int blendMode) {
            if (blendMode == BLEND_OVERLAY || blendMode == BLEND_GLINT || blendMode == BLEND_CRUMBLING) {
                return DEPTH_TEST_ONLY;
            }
            if (alphaMode == ALPHA_TRANSLUCENT || blendMode != BLEND_OPAQUE) {
                return DEPTH_TEST_ONLY;
            }
            return DEPTH_TEST_WRITE;
        }

        //#if MC<260200
        private static int cullMode(RenderLayer.MultiPhase multiPhase) {
            if (multiPhase == null) {
                return CULL_BACK;
            }
            return RenderPhase.DISABLE_CULLING.equals(multiPhase.phases.cull) ? CULL_NONE : CULL_BACK;
        }
        //#endif

        private static int stateFlags(String name, boolean reflect) {
            int flags = STATE_LIGHTMAP;
            if (!reflect) {
                flags |= STATE_NO_REFLECT;
            }
            if (name.contains("overlay")) {
                flags |= STATE_OVERLAY;
            }
            if (name.contains("outline")) {
                flags |= STATE_OUTLINE;
            }
            if (name.contains("crumbling")) {
                flags |= STATE_CRUMBLING;
            }
            if (name.contains("translucent") || name.contains("particle")) {
                flags |= STATE_SORT_ON_UPLOAD;
            }
            return flags;
        }

        private static int writeMask(int depthPolicy) {
            return depthPolicy == DEPTH_TEST_WRITE ? WRITE_COLOR | WRITE_DEPTH : WRITE_COLOR;
        }

        private static int outputTarget(String name, int blendMode) {
            if (name.contains("weather") || name.contains("rain") || name.contains("snow")
                || blendMode == BLEND_LIGHTNING) {
                return TARGET_WEATHER;
            }
            if (name.contains("cloud")) {
                return TARGET_CLOUDS;
            }
            if (name.contains("particle")) {
                return TARGET_PARTICLES;
            }
            if (name.contains("outline")) {
                return TARGET_OUTLINE;
            }
            if (name.contains("overlay")) {
                return TARGET_OVERLAY;
            }
            if (blendMode != BLEND_OPAQUE || name.contains("translucent")) {
                return TARGET_TRANSLUCENT;
            }
            return TARGET_MAIN;
        }

        private static int layering(String name) {
            if (name.contains("polygon_offset")) {
                return LAYERING_POLYGON_OFFSET;
            }
            if (name.contains("view_offset_z") || name.contains("see_through")) {
                return LAYERING_VIEW_OFFSET_Z;
            }
            if (name.contains("world_border")) {
                return LAYERING_WORLD_BORDER;
            }
            return LAYERING_NONE;
        }

        private static int semanticFlags(String name, int outputTarget) {
            int flags = 0;
            if (name.contains("water")) {
                flags |= SEMANTIC_WATER | SEMANTIC_REFRACTION | SEMANTIC_TRANSMISSION;
            }
            if (name.contains("glass")) {
                flags |= SEMANTIC_GLASS | SEMANTIC_REFRACTION | SEMANTIC_TRANSMISSION;
            }
            if (name.contains("portal") || name.contains("gateway")) {
                flags |= SEMANTIC_PORTAL;
            }
            if (outputTarget == TARGET_WEATHER) {
                flags |= SEMANTIC_WEATHER;
            }
            if (name.contains("text")) {
                flags |= SEMANTIC_TEXT;
            }
            if (outputTarget == TARGET_PARTICLES) {
                flags |= SEMANTIC_PARTICLE;
            }
            if (outputTarget == TARGET_OVERLAY || name.contains("overlay")) {
                flags |= SEMANTIC_OVERLAY;
            }
            return flags;
        }

        private static int sortKey(int outputTarget, int blendMode, int alphaMode, String name) {
            int key = outputTarget << 24 | blendMode << 16 | alphaMode << 8;
            return key | (name.hashCode() & 0xFF);
        }

        private static float polygonOffsetFactor(int layering) {
            return layering == LAYERING_POLYGON_OFFSET ? -1.0F : 0.0F;
        }

        private static float polygonOffsetUnits(int layering) {
            return layering == LAYERING_POLYGON_OFFSET ? -10.0F : 0.0F;
        }
    }

    public enum IndexTypes {
        //#if MC>=260200
        //$$ SHORT(IndexType.SHORT, 0),
        //$$ INT(IndexType.INT, 1);
        //$$
        //$$ private static final Map<IndexType, Integer>
        //$$     BY_INDEX_TYPE =
        //$$     Collections.unmodifiableMap(Arrays.stream(values())
        //$$         .collect(Collectors.toMap(IndexTypes::getIndexType, IndexTypes::getValue)));
        //$$
        //$$ private final IndexType indexType;
        //$$ private final int value;
        //$$
        //$$ IndexTypes(IndexType indexType, int value) {
        //$$     this.indexType = indexType;
        //$$     this.value = value;
        //$$ }
        //$$
        //$$ public static int getValue(IndexType indexType) {
        //$$     return BY_INDEX_TYPE.get(indexType);
        //$$ }
        //$$
        //$$ public IndexType getIndexType() {
        //$$     return indexType;
        //$$ }
        //#else
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
        //#endif

        public int getValue() {
            return value;
        }
    }

    public enum DrawModes {
        //#if MC>=260200
        //$$ LINES(PrimitiveTopology.LINES, 0),
        //$$ LINE_STRIP(null, 1),
        //$$ DEBUG_LINES(PrimitiveTopology.DEBUG_LINES, 2),
        //$$ DEBUG_LINE_STRIP(PrimitiveTopology.DEBUG_LINE_STRIP, 3),
        //$$ TRIANGLES(PrimitiveTopology.TRIANGLES, 4),
        //$$ TRIANGLE_STRIP(PrimitiveTopology.TRIANGLE_STRIP, 5),
        //$$ TRIANGLE_FAN(PrimitiveTopology.TRIANGLE_FAN, 6),
        //$$ QUADS(PrimitiveTopology.QUADS, 7),
        //$$ POINTS(PrimitiveTopology.POINTS, 8);
        //$$
        //$$ private static final Map<PrimitiveTopology, Integer>
        //$$     BY_DRAW_MODE =
        //$$     Collections.unmodifiableMap(Arrays.stream(values())
        //$$         .filter(value -> value.topology != null)
        //$$         .collect(Collectors.toMap(DrawModes::getDrawMode, DrawModes::getValue)));
        //$$
        //$$ private final PrimitiveTopology topology;
        //$$ private final int value;
        //$$
        //$$ DrawModes(PrimitiveTopology topology, int value) {
        //$$     this.topology = topology;
        //$$     this.value = value;
        //$$ }
        //$$
        //$$ public static int getValue(PrimitiveTopology topology) {
        //$$     return BY_DRAW_MODE.get(topology);
        //$$ }
        //$$
        //$$ public PrimitiveTopology getDrawMode() {
        //$$     return topology;
        //$$ }
        //#else
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
        //#endif

        public int getValue() {
            return value;
        }
    }

    public enum VertexFormats {
        //#if MC>=260200
        //$$ BLOCK(DefaultVertexFormat.BLOCK, 0),
        //$$ ENTITY(DefaultVertexFormat.ENTITY, 1),
        //$$ PARTICLE(DefaultVertexFormat.PARTICLE, 2),
        //$$ POSITION(DefaultVertexFormat.POSITION, 3),
        //$$ POSITION_COLOR(DefaultVertexFormat.POSITION_COLOR, 4),
        //$$ POSITION_COLOR_NORMAL(DefaultVertexFormat.POSITION_COLOR_NORMAL, 5),
        //$$ POSITION_COLOR_LIGHTMAP(DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, 6),
        //$$ POSITION_TEX(DefaultVertexFormat.POSITION_TEX, 7),
        //$$ POSITION_TEX_COLOR(DefaultVertexFormat.POSITION_TEX_COLOR, 8),
        //$$ POSITION_COLOR_TEX_LIGHTMAP(DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, 9),
        //$$ POSITION_TEX_LIGHTMAP_COLOR(DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR, 10),
        //$$ POSITION_TEX_COLOR_NORMAL(DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, 11);
        //$$
        //$$ private static final Map<VertexFormat, Integer>
        //$$     BY_VERTEX_FORMAT =
        //$$     Collections.unmodifiableMap(Arrays.stream(values())
        //$$         .collect(Collectors.toMap(VertexFormats::getVertexFormat, VertexFormats::getValue)));
        //$$
        //$$ private final VertexFormat vertexFormat;
        //$$ private final int value;
        //$$
        //$$ VertexFormats(VertexFormat vertexFormat, int value) {
        //$$     this.vertexFormat = vertexFormat;
        //$$     this.value = value;
        //$$ }
        //$$
        //$$ public static int getValue(VertexFormat vertexFormat) {
        //$$     return BY_VERTEX_FORMAT.getOrDefault(vertexFormat, -1);
        //$$ }
        //$$
        //$$ public VertexFormat getVertexFormat() {
        //$$     return vertexFormat;
        //$$ }
        //#else
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
        //#endif

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

        public static GeometryTypes getGeometryType(String layerName, boolean reflect) {
            String name = layerName == null ? "" : layerName;
            if (name.contains("water_mask")) {
                return BOAT_WATER_MASK;
            } else if (name.contains("end_portal")) {
                return END_PORTAL;
            } else if (name.contains("end_gateway")) {
                return END_GATEWAY;
            }
            if (name.contains("cloud")) {
                return WORLD_CLOUD;
            }
            if (!reflect) {
                return WORLD_NO_REFLECT;
            }
            if (name.contains("solid")) {
                return WORLD_SOLID;
            }
            return WORLD_TRANSPARENT;
        }

        //#if MC<260200
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
        //#endif

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
