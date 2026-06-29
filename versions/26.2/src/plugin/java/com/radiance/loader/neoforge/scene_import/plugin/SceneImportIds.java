package com.radiance.loader.neoforge.scene_import.plugin;

import java.util.Locale;

public final class SceneImportIds {
    public static final int SCENE_ROUTE_NONE = 0;
    public static final int SCENE_ROUTE_GEOMETRY = 1;
    public static final int SCENE_ROUTE_ACCELERATION = 2;
    public static final int SCENE_ROUTE_POST = 4;
    public static final int SCENE_ROUTE_SUPPORT_TEXTURE = 8;
    public static final int SCENE_ROUTE_OVERLAY = 16;
    public static final int SCENE_ROUTE_UNSPECIFIED = -1;
    public static final int SCENE_ROUTE_RULE_DEPENDENT = -2;

    public static final int FORMAT_UNKNOWN = 0;
    public static final int KERNEL_UNKNOWN = 0;

    public static final int RASTER_UNKNOWN = 0;
    public static final int RASTER_OPAQUE = 1;
    public static final int RASTER_CUTOUT = 2;
    public static final int RASTER_TRANSLUCENT = 3;
    public static final int RASTER_ADDITIVE = 4;
    public static final int RASTER_OVERLAY = 5;

    public static final int MATERIAL_DEFAULT = 0;
    public static final int MATERIAL_VANILLA_TERRAIN = 1;
    public static final int MATERIAL_VANILLA_ENTITY = 2;
    public static final int MATERIAL_PARTICLE = 3;
    public static final int MATERIAL_PORTAL = 4;

    public static final int TRANSFORM_DEFAULT = 0;
    public static final int TRANSFORM_CHUNK_SECTION = 1;
    public static final int TRANSFORM_DYNAMIC_TRANSFORMS = 2;

    private SceneImportIds() {
    }

    public static int routeMask(String value) {
        String text = normalize(value);
        if (text.isBlank() || text.equals("unknown") || text.equals("unspecified")) {
            return SCENE_ROUTE_UNSPECIFIED;
        }
        int mask = 0;
        for (String part : text.split("[+,| ]+")) {
            if (part.isBlank()) {
                continue;
            }
            mask |= switch (part) {
                case "none", "skip", "control_only", "external_readback" -> SCENE_ROUTE_NONE;
                case "geometry", "geometry_translucent" -> SCENE_ROUTE_GEOMETRY;
                case "acceleration" -> SCENE_ROUTE_ACCELERATION;
                case "geometry_acceleration" -> SCENE_ROUTE_GEOMETRY | SCENE_ROUTE_ACCELERATION;
                case "geometry_no_acceleration" -> SCENE_ROUTE_GEOMETRY;
                case "rule_dependent", "pipeline_dependent" -> SCENE_ROUTE_RULE_DEPENDENT;
                case "post", "post_or_background", "sky" -> SCENE_ROUTE_POST;
                case "support_texture", "overlay_support" -> SCENE_ROUTE_SUPPORT_TEXTURE;
                case "overlay" -> SCENE_ROUTE_OVERLAY;
                default -> SCENE_ROUTE_UNSPECIFIED;
            };
            if (mask == SCENE_ROUTE_UNSPECIFIED) {
                return SCENE_ROUTE_UNSPECIFIED;
            }
        }
        return mask;
    }

    public static int formatId(String value) {
        String text = normalize(value);
        return stableCustomId(text);
    }

    public static int kernelId(String value) {
        String text = normalize(value);
        return stableCustomId(text);
    }

    public static int rasterClass(String value) {
        String text = normalize(value);
        return switch (text) {
            case "opaque" -> RASTER_OPAQUE;
            case "cutout" -> RASTER_CUTOUT;
            case "translucent" -> RASTER_TRANSLUCENT;
            case "additive" -> RASTER_ADDITIVE;
            case "overlay" -> RASTER_OVERLAY;
            default -> RASTER_UNKNOWN;
        };
    }

    public static int materialPolicy(String value) {
        String text = normalize(value);
        return switch (text) {
            case "vanilla_terrain" -> MATERIAL_VANILLA_TERRAIN;
            case "vanilla_entity" -> MATERIAL_VANILLA_ENTITY;
            case "particle" -> MATERIAL_PARTICLE;
            case "portal", "portal_translucent" -> MATERIAL_PORTAL;
            default -> MATERIAL_DEFAULT;
        };
    }

    public static int transformPolicy(String value) {
        String text = normalize(value);
        return switch (text) {
            case "chunk_section", "chunksection" -> TRANSFORM_CHUNK_SECTION;
            case "dynamic_transforms", "dynamictransform", "dynamic_transforms_ubo" ->
                TRANSFORM_DYNAMIC_TRANSFORMS;
            default -> TRANSFORM_DEFAULT;
        };
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replace('-', '_').replace(':', '_').replace('/', '_').replace('.', '_')
            .toLowerCase(Locale.ROOT);
    }

    private static int stableCustomId(String normalized) {
        if (normalized.isBlank()
            || "unknown".equals(normalized)
            || "inspect".equals(normalized)
            || "skip".equals(normalized)
            || "no_scene_mesh".equals(normalized)
            || normalized.endsWith("_runtime_split")
            || normalized.endsWith("_definition")
            || normalized.endsWith("_pass")
            || normalized.endsWith("_control")
            || normalized.endsWith("_query")
            || normalized.endsWith("_upload")
            || normalized.endsWith("_readback")
            || normalized.endsWith("_update")) {
            return 0;
        }
        int hash = 0x811c9dc5;
        for (int i = 0; i < normalized.length(); i++) {
            hash ^= normalized.charAt(i) & 0xff;
            hash *= 0x01000193;
        }
        return 0x80000000 | (hash & 0x7fffffff);
    }
}

