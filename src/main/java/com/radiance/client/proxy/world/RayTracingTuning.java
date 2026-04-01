package com.radiance.client.proxy.world;

import com.radiance.client.pipeline.Pipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;

final class RayTracingTuning {

    private static final String RAY_TRACING_MODULE = "render_pipeline.module.ray_tracing.name";
    private static final String ATTR_WORLD_REPRESENTATION_MODE =
        "render_pipeline.module.ray_tracing.attribute.world_representation_mode";
    private static final String ATTR_CHUNK_TRAVERSAL_MODE =
        "render_pipeline.module.ray_tracing.attribute.chunk_traversal_mode";
    private static final String ATTR_CHUNK_DATA_LAYOUT =
        "render_pipeline.module.ray_tracing.attribute.chunk_data_layout";
    private static final String ATTR_CHUNK_MACROCELL_SIZE =
        "render_pipeline.module.ray_tracing.attribute.chunk_macrocell_size";
    private static final String ATTR_TERRAIN_MESHING_MODE =
        "render_pipeline.module.ray_tracing.attribute.terrain_meshing_mode";
    private static final String ATTR_GREEDY_MERGE_MAX_SPAN =
        "render_pipeline.module.ray_tracing.attribute.greedy_merge_max_span";
    private static final String ATTR_BLAS_INCLUSION_MODE =
        "render_pipeline.module.ray_tracing.attribute.blas_inclusion_mode";
    private static final String ATTR_GLASS_PATH_MODE =
        "render_pipeline.module.ray_tracing.attribute.glass_path_mode";
    private static final String ATTR_FOLIAGE_PATH_MODE =
        "render_pipeline.module.ray_tracing.attribute.foliage_path_mode";
    private static final String ATTR_DECORATION_PATH_MODE =
        "render_pipeline.module.ray_tracing.attribute.decoration_path_mode";
    private static final String ATTR_FAR_FIELD_GEOMETRY_MODE =
        "render_pipeline.module.ray_tracing.attribute.far_field_geometry_mode";
    private static final String ATTR_FAR_FIELD_START_DISTANCE_CHUNKS =
        "render_pipeline.module.ray_tracing.attribute.far_field_start_distance_chunks";
    private static final String ATTR_REFLECTION_RAY_MATERIAL_MODE =
        "render_pipeline.module.ray_tracing.attribute.reflection_ray_material_mode";
    private static final String ATTR_DIFFUSE_GI_MODE =
        "render_pipeline.module.ray_tracing.attribute.diffuse_gi_mode";
    private static final String ATTR_TERRAIN_UPDATE_INTERVAL_FRAMES =
        "render_pipeline.module.ray_tracing.attribute.terrain_update_interval_frames";
    private static final String ATTR_ENTITY_UPDATE_INTERVAL_FRAMES =
        "render_pipeline.module.ray_tracing.attribute.entity_update_interval_frames";
    private static final String ATTR_BLOCK_ENTITY_UPDATE_INTERVAL_FRAMES =
        "render_pipeline.module.ray_tracing.attribute.block_entity_update_interval_frames";
    private static final String ATTR_PARTICLE_UPDATE_INTERVAL_FRAMES =
        "render_pipeline.module.ray_tracing.attribute.particle_update_interval_frames";
    private static final String ATTR_PARTICLE_CRIT_GLOW =
        "render_pipeline.module.ray_tracing.attribute.particle_crit_glow";
    private static final String ATTR_PARTICLE_DEATH_SMOKE_GLOW =
        "render_pipeline.module.ray_tracing.attribute.particle_death_smoke_glow";
    private static final String ATTR_PARTICLE_CRIT_GLOW_STRENGTH =
        "render_pipeline.module.ray_tracing.attribute.particle_crit_glow_strength";
    private static final String ATTR_PARTICLE_DEATH_SMOKE_GLOW_STRENGTH =
        "render_pipeline.module.ray_tracing.attribute.particle_death_smoke_glow_strength";
    private static final String ATTR_SEPARATE_ENTITY_TERRAIN_ACCEL_STRUCTURES =
        "render_pipeline.module.ray_tracing.attribute.separate_entity_terrain_accel_structures";

    private static final String VALUE_WORLD_REPRESENTATION_CHUNK_AABB =
        "render_pipeline.module.ray_tracing.attribute.world_representation_mode.chunk_aabb";
    private static final String VALUE_TRAVERSAL_TRIANGLE_HIT =
        "render_pipeline.module.ray_tracing.attribute.chunk_traversal_mode.triangle_hit";
    private static final String VALUE_CHUNK_LAYOUT_TRIANGLE_GEOMETRY =
        "render_pipeline.module.ray_tracing.attribute.chunk_data_layout.triangle_geometry";
    private static final String VALUE_CHUNK_MACROCELL_DISABLED =
        "render_pipeline.module.ray_tracing.attribute.chunk_macrocell_size.disabled";
    private static final String VALUE_TERRAIN_MESHING_LEGACY =
        "render_pipeline.module.ray_tracing.attribute.terrain_meshing_mode.legacy_quads";
    private static final String VALUE_TERRAIN_MESHING_GREEDY =
        "render_pipeline.module.ray_tracing.attribute.terrain_meshing_mode.greedy_meshing";
    private static final String VALUE_TERRAIN_MESHING_COPLANAR =
        "render_pipeline.module.ray_tracing.attribute.terrain_meshing_mode.coplanar_merge";
    private static final double FAR_FIELD_VISIBLE_FEATURE_PIXELS = 1.25;
    private static final double FAR_FIELD_CUTOUT_FEATURE_BLOCKS = 2.0;
    private static final double FAR_FIELD_DEFAULT_FEATURE_BLOCKS = 1.0;
    private static final double FAR_FIELD_HALF_FOV_DEGREES = 35.0;
    private static final double FAR_FIELD_INTERVAL_RATIO_MEDIUM = 1.5;
    private static final double FAR_FIELD_INTERVAL_RATIO_FAR = 2.5;
    private static final double FAR_FIELD_INTERVAL_RATIO_VERY_FAR = 4.0;
    private static final double FAR_FIELD_INTERVAL_RATIO_EXTREME = 6.0;

    private RayTracingTuning() {
    }

    static ChunkGeometryRoute classifyChunkLayer(RenderLayer renderLayer, double distanceChunks) {
        if (renderLayer == null) {
            return ChunkGeometryRoute.DROP;
        }

        boolean opaqueLayer = isOpaqueLayer(renderLayer);
        boolean farField = isFarField(distanceChunks);
        boolean coarseWorldRepresentation =
            getWorldRepresentationMode() == WorldRepresentationMode.CHUNK_AABB;

        BlasInclusionMode blasInclusionMode = getBlasInclusionMode();
        if (opaqueLayer) {
            return ChunkGeometryRoute.BLAS;
        }

        GeometryPathMode geometryPathMode = getPathMode(renderLayer);
        if (geometryPathMode == GeometryPathMode.EXCLUDE) {
            return ChunkGeometryRoute.DROP;
        }
        if (geometryPathMode == GeometryPathMode.SPECIAL_PATH
            && shouldForceBlasForTranslucentTerrain(renderLayer)) {
            return ChunkGeometryRoute.BLAS;
        }
        if (geometryPathMode == GeometryPathMode.SPECIAL_PATH
            && !separatesEntityAndTerrainAccelerationStructures()) {
            return ChunkGeometryRoute.BLAS;
        }

        if (blasInclusionMode == BlasInclusionMode.ALL_GEOMETRY
            || geometryPathMode == GeometryPathMode.BLAS) {
            return ChunkGeometryRoute.BLAS;
        }

        boolean preserveVisibleFarFieldSpecialGeometry =
            farField && shouldPreserveVisibleFarFieldSpecialGeometry(renderLayer, distanceChunks);
        if (farField && shouldDropFarFieldSpecialGeometry()) {
            return preserveVisibleFarFieldSpecialGeometry ? ChunkGeometryRoute.BLAS
                : ChunkGeometryRoute.DROP;
        }
        if (coarseWorldRepresentation && farField && !isReflectiveSpecialLayer(renderLayer)) {
            return preserveVisibleFarFieldSpecialGeometry ? ChunkGeometryRoute.BLAS
                : ChunkGeometryRoute.DROP;
        }

        return isReflectiveSpecialLayer(renderLayer) ? ChunkGeometryRoute.SPECIAL_REFLECTIVE
            : ChunkGeometryRoute.SPECIAL_MATTE;
    }

    static boolean shouldReflectBlasLayer(RenderLayer renderLayer) {
        return shouldReflectLayer(renderLayer, true);
    }

    static boolean shouldReflectLayer(RenderLayer renderLayer, boolean defaultReflect) {
        if (!defaultReflect || renderLayer == null) {
            return false;
        }

        DiffuseGiMode diffuseGiMode = getDiffuseGiMode();
        if (diffuseGiMode != DiffuseGiMode.FULL_RAY_TRACING) {
            return isReflectiveSpecialLayer(renderLayer);
        }

        ReflectionRayMaterialMode reflectionRayMaterialMode = getReflectionRayMaterialMode();
        if (reflectionRayMaterialMode == ReflectionRayMaterialMode.ALL_MATERIALS) {
            return true;
        }

        if (reflectionRayMaterialMode == ReflectionRayMaterialMode.REFLECTIVE_ONLY
            || reflectionRayMaterialMode == ReflectionRayMaterialMode.WATER_GLASS_METAL) {
            return isReflectiveSpecialLayer(renderLayer);
        }

        return true;
    }

    static boolean shouldCaptureTraversalMetadata() {
        return getWorldRepresentationMode() == WorldRepresentationMode.CHUNK_AABB
            || getChunkTraversalMode() != ChunkTraversalMode.TRIANGLE_HIT
            || getChunkDataLayout() != ChunkDataLayout.TRIANGLE_GEOMETRY
            || getChunkMacrocellSize() != ChunkMacrocellSize.DISABLED
            || getTerrainMeshingMode() != TerrainMeshingMode.LEGACY_QUADS;
    }

    static boolean shouldAttemptTerrainMeshingOptimization() {
        return getEffectiveTerrainMeshingMode() != TerrainMeshingMode.LEGACY_QUADS;
    }

    static TerrainMeshingMode getTerrainMeshingMode() {
        String value = Pipeline.getModuleAttributeValue(RAY_TRACING_MODULE,
            ATTR_TERRAIN_MESHING_MODE, "");
        if (VALUE_TERRAIN_MESHING_COPLANAR.equals(value) || value.endsWith(".coplanar_merge")) {
            return TerrainMeshingMode.COPLANAR_MERGE;
        }
        if (VALUE_TERRAIN_MESHING_GREEDY.equals(value) || value.endsWith(".greedy_meshing")) {
            return TerrainMeshingMode.GREEDY_MESHING;
        }
        return TerrainMeshingMode.LEGACY_QUADS;
    }

    static TerrainMeshingMode getEffectiveTerrainMeshingMode() {
        TerrainMeshingMode terrainMeshingMode = getTerrainMeshingMode();
        if (terrainMeshingMode != TerrainMeshingMode.LEGACY_QUADS) {
            return terrainMeshingMode;
        }
        if (getWorldRepresentationMode() == WorldRepresentationMode.CHUNK_AABB
            || getChunkTraversalMode() == ChunkTraversalMode.MACROCELL) {
            return TerrainMeshingMode.COPLANAR_MERGE;
        }
        if (getChunkTraversalMode() == ChunkTraversalMode.BRICK
            || getChunkDataLayout() != ChunkDataLayout.TRIANGLE_GEOMETRY) {
            return TerrainMeshingMode.GREEDY_MESHING;
        }
        return TerrainMeshingMode.LEGACY_QUADS;
    }

    static int terrainGreedyMergeMaxSpan() {
        return Math.max(1,
            Pipeline.getModuleAttributeIntValue(RAY_TRACING_MODULE, ATTR_GREEDY_MERGE_MAX_SPAN,
                16));
    }

    static int terrainUpdateIntervalFrames(double distanceChunks) {
        int interval = Math.max(1,
            Pipeline.getModuleAttributeIntValue(RAY_TRACING_MODULE,
                ATTR_TERRAIN_UPDATE_INTERVAL_FRAMES, 1));

        int farFieldStartDistanceChunks = getFarFieldStartDistanceChunks();
        FarFieldGeometryMode farFieldGeometryMode = getFarFieldGeometryMode();
        if (distanceChunks < farFieldStartDistanceChunks) {
            return interval;
        }

        int farFieldInterval = switch (farFieldGeometryMode) {
            case EXACT_CHUNKS -> interval;
            case SIMPLIFIED_SHELL -> interval * 2;
            case CLIPMAP -> interval * 4;
            case SHELL_AND_CLIPMAP -> interval * 6;
        };

        int coarseMultiplier = 1;
        if (getWorldRepresentationMode() == WorldRepresentationMode.CHUNK_AABB) {
            coarseMultiplier *= 2;
        }
        coarseMultiplier *= switch (getChunkTraversalMode()) {
            case TRIANGLE_HIT, VOXEL_DDA -> 1;
            case BRICK -> 2;
            case MACROCELL -> 3;
        };
        coarseMultiplier *= switch (getChunkMacrocellSize()) {
            case DISABLED -> 1;
            case SIZE_4 -> 2;
            case SIZE_8 -> 3;
        };
        int distanceMultiplier = farFieldDistanceUpdateMultiplier(distanceChunks,
            farFieldStartDistanceChunks);
        return Math.max(1, Math.min(1024,
            farFieldInterval * coarseMultiplier * distanceMultiplier));
    }

    static int entityUpdateIntervalFrames() {
        return Math.max(1,
            Pipeline.getModuleAttributeIntValue(RAY_TRACING_MODULE,
                ATTR_ENTITY_UPDATE_INTERVAL_FRAMES, 1));
    }

    static int blockEntityUpdateIntervalFrames() {
        return Math.max(1,
            Pipeline.getModuleAttributeIntValue(RAY_TRACING_MODULE,
                ATTR_BLOCK_ENTITY_UPDATE_INTERVAL_FRAMES, entityUpdateIntervalFrames()));
    }

    static int particleUpdateIntervalFrames() {
        return Math.max(1,
            Pipeline.getModuleAttributeIntValue(RAY_TRACING_MODULE,
                ATTR_PARTICLE_UPDATE_INTERVAL_FRAMES, entityUpdateIntervalFrames()));
    }

    static boolean critParticlesGlowEnabled() {
        return Pipeline.getModuleAttributeBooleanValue(RAY_TRACING_MODULE,
            ATTR_PARTICLE_CRIT_GLOW, true);
    }

    static float critParticlesGlowStrength() {
        if (!critParticlesGlowEnabled()) {
            return 0.0f;
        }
        return Math.max(0.0f,
            Pipeline.getModuleAttributeFloatValue(RAY_TRACING_MODULE,
                ATTR_PARTICLE_CRIT_GLOW_STRENGTH, 0.55f));
    }

    static boolean deathSmokeParticlesGlowEnabled() {
        return Pipeline.getModuleAttributeBooleanValue(RAY_TRACING_MODULE,
            ATTR_PARTICLE_DEATH_SMOKE_GLOW, true);
    }

    static float deathSmokeParticlesGlowStrength() {
        if (!deathSmokeParticlesGlowEnabled()) {
            return 0.0f;
        }
        return Math.max(0.0f,
            Pipeline.getModuleAttributeFloatValue(RAY_TRACING_MODULE,
                ATTR_PARTICLE_DEATH_SMOKE_GLOW_STRENGTH, 0.32f));
    }

    static int getFarFieldStartDistanceChunks() {
        return Math.max(2,
            Pipeline.getModuleAttributeIntValue(RAY_TRACING_MODULE,
                ATTR_FAR_FIELD_START_DISTANCE_CHUNKS, 32));
    }

    static boolean separatesEntityAndTerrainAccelerationStructures() {
        return Pipeline.getModuleAttributeBooleanValue(RAY_TRACING_MODULE,
            ATTR_SEPARATE_ENTITY_TERRAIN_ACCEL_STRUCTURES, true);
    }

    static boolean useLowCostDiffuseGi() {
        return getDiffuseGiMode() != DiffuseGiMode.FULL_RAY_TRACING;
    }

    static WorldRepresentationMode getWorldRepresentationMode() {
        String value = Pipeline.getModuleAttributeValue(RAY_TRACING_MODULE,
            ATTR_WORLD_REPRESENTATION_MODE, "");
        if (VALUE_WORLD_REPRESENTATION_CHUNK_AABB.equals(value) || value.endsWith(
            ".chunk_aabb")) {
            return WorldRepresentationMode.CHUNK_AABB;
        }
        return WorldRepresentationMode.TRIANGLE_BLAS;
    }

    static ChunkTraversalMode getChunkTraversalMode() {
        String value = Pipeline.getModuleAttributeValue(RAY_TRACING_MODULE,
            ATTR_CHUNK_TRAVERSAL_MODE, "");
        if (value.endsWith(".voxel_dda")) {
            return ChunkTraversalMode.VOXEL_DDA;
        }
        if (value.endsWith(".brick")) {
            return ChunkTraversalMode.BRICK;
        }
        if (value.endsWith(".macrocell")) {
            return ChunkTraversalMode.MACROCELL;
        }
        return ChunkTraversalMode.TRIANGLE_HIT;
    }

    static ChunkDataLayout getChunkDataLayout() {
        String value = Pipeline.getModuleAttributeValue(RAY_TRACING_MODULE, ATTR_CHUNK_DATA_LAYOUT,
            "");
        if (value.endsWith(".occupancy_bitmask")) {
            return ChunkDataLayout.OCCUPANCY_BITMASK;
        }
        if (value.endsWith(".occupancy_palette")) {
            return ChunkDataLayout.OCCUPANCY_PALETTE;
        }
        if (value.endsWith(".occupancy_palette_face_mask")) {
            return ChunkDataLayout.OCCUPANCY_PALETTE_FACE_MASK;
        }
        return ChunkDataLayout.TRIANGLE_GEOMETRY;
    }

    static ChunkMacrocellSize getChunkMacrocellSize() {
        String value = Pipeline.getModuleAttributeValue(RAY_TRACING_MODULE,
            ATTR_CHUNK_MACROCELL_SIZE, "");
        if (value.endsWith(".size_4")) {
            return ChunkMacrocellSize.SIZE_4;
        }
        if (value.endsWith(".size_8")) {
            return ChunkMacrocellSize.SIZE_8;
        }
        return ChunkMacrocellSize.DISABLED;
    }

    static int effectiveMacrocellSize() {
        return switch (getChunkMacrocellSize()) {
            case SIZE_4 -> 4;
            case SIZE_8 -> 8;
            case DISABLED -> switch (getChunkTraversalMode()) {
                case MACROCELL -> 8;
                case BRICK -> 4;
                default -> 0;
            };
        };
    }

    static boolean shouldCaptureMaterialPalette() {
        return switch (getChunkDataLayout()) {
            case OCCUPANCY_PALETTE, OCCUPANCY_PALETTE_FACE_MASK -> true;
            default -> false;
        };
    }

    static boolean shouldCaptureFaceMask() {
        if (getChunkDataLayout() == ChunkDataLayout.OCCUPANCY_PALETTE_FACE_MASK) {
            return true;
        }
        return switch (getChunkTraversalMode()) {
            case VOXEL_DDA, BRICK, MACROCELL -> true;
            default -> false;
        };
    }

    static boolean shouldCaptureMacrocell4() {
        return effectiveMacrocellSize() == 4;
    }

    static boolean shouldCaptureMacrocell8() {
        return effectiveMacrocellSize() == 8;
    }

    static DiffuseGiMode getDiffuseGiMode() {
        String value = Pipeline.getModuleAttributeValue(RAY_TRACING_MODULE, ATTR_DIFFUSE_GI_MODE,
            "");
        if (value.endsWith(".probes")) {
            return DiffuseGiMode.PROBES;
        }
        if (value.endsWith(".radiance_cache")) {
            return DiffuseGiMode.RADIANCE_CACHE;
        }
        if (value.endsWith(".low_cost_hybrid")) {
            return DiffuseGiMode.LOW_COST_HYBRID;
        }
        return DiffuseGiMode.FULL_RAY_TRACING;
    }

    static boolean isFarField(double distanceChunks) {
        return distanceChunks >= getFarFieldStartDistanceChunks();
    }

    static boolean isOpaqueLayer(RenderLayer renderLayer) {
        if (!(renderLayer instanceof RenderLayer.MultiPhase multiPhase)) {
            return renderLayer.name.contains("solid");
        }

        if (renderLayer.name.contains("solid")) {
            return true;
        }

        if (isCutoutLayer(renderLayer) || isTranslucentLayer(renderLayer)) {
            return false;
        }

        return RenderPhase.NO_TRANSPARENCY.equals(multiPhase.phases.transparency);
    }

    static boolean isTranslucentLayer(RenderLayer renderLayer) {
        if (!(renderLayer instanceof RenderLayer.MultiPhase multiPhase)) {
            return renderLayer.name.contains("translucent") || renderLayer.name.contains("water");
        }

        if (renderLayer.name.contains("translucent") || renderLayer.name.contains("water")) {
            return true;
        }

        return multiPhase.isTranslucent() && !RenderPhase.NO_TRANSPARENCY.equals(
            multiPhase.phases.transparency);
    }

    static boolean isCutoutLayer(RenderLayer renderLayer) {
        String name = renderLayer.name;
        return name.contains("cutout") || name.contains("tripwire");
    }

    private static boolean shouldPreserveVisibleFarFieldSpecialGeometry(RenderLayer renderLayer,
        double distanceChunks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return true;
        }

        double framebufferHeight = Math.max(1.0, client.getWindow().getFramebufferHeight());
        double distanceBlocks = Math.max(1.0, distanceChunks * 16.0);
        double featureBlocks = isCutoutLayer(renderLayer) ? FAR_FIELD_CUTOUT_FEATURE_BLOCKS
            : FAR_FIELD_DEFAULT_FEATURE_BLOCKS;
        double projectedPixels =
            framebufferHeight * featureBlocks / (2.0 * distanceBlocks * Math.tan(
                Math.toRadians(FAR_FIELD_HALF_FOV_DEGREES)));
        return projectedPixels >= FAR_FIELD_VISIBLE_FEATURE_PIXELS;
    }

    private static int farFieldDistanceUpdateMultiplier(double distanceChunks,
        int farFieldStartDistanceChunks) {
        double distanceRatio = distanceChunks / Math.max(1.0, farFieldStartDistanceChunks);
        if (distanceRatio >= FAR_FIELD_INTERVAL_RATIO_EXTREME) {
            return 12;
        }
        if (distanceRatio >= FAR_FIELD_INTERVAL_RATIO_VERY_FAR) {
            return 8;
        }
        if (distanceRatio >= FAR_FIELD_INTERVAL_RATIO_FAR) {
            return 4;
        }
        if (distanceRatio >= FAR_FIELD_INTERVAL_RATIO_MEDIUM) {
            return 2;
        }
        return 1;
    }

    private static boolean shouldDropFarFieldSpecialGeometry() {
        FarFieldGeometryMode farFieldGeometryMode = getFarFieldGeometryMode();
        return farFieldGeometryMode == FarFieldGeometryMode.SIMPLIFIED_SHELL
            || farFieldGeometryMode == FarFieldGeometryMode.CLIPMAP
            || farFieldGeometryMode == FarFieldGeometryMode.SHELL_AND_CLIPMAP;
    }

    private static boolean isReflectiveSpecialLayer(RenderLayer renderLayer) {
        return isTranslucentLayer(renderLayer) || renderLayer.name.contains("water")
            || renderLayer.name.contains("ice");
    }

    private static boolean shouldForceBlasForTranslucentTerrain(RenderLayer renderLayer) {
        String name = renderLayer.name;
        if (name.contains("water_mask")
            || name.contains("end_portal")
            || name.contains("end_gateway")
            || name.contains("cloud")) {
            return false;
        }
        return isTranslucentLayer(renderLayer) || name.contains("ice");
    }

    private static GeometryPathMode getPathMode(RenderLayer renderLayer) {
        String attributeName = ATTR_DECORATION_PATH_MODE;
        if (isTranslucentLayer(renderLayer)) {
            attributeName = ATTR_GLASS_PATH_MODE;
        } else if (isCutoutLayer(renderLayer)) {
            attributeName = ATTR_FOLIAGE_PATH_MODE;
        }

        String value = Pipeline.getModuleAttributeValue(RAY_TRACING_MODULE, attributeName, "");
        if (value.endsWith(".exclude")) {
            return GeometryPathMode.EXCLUDE;
        }
        if (value.endsWith(".special_path")) {
            return GeometryPathMode.SPECIAL_PATH;
        }
        return GeometryPathMode.BLAS;
    }

    private static BlasInclusionMode getBlasInclusionMode() {
        String value = Pipeline.getModuleAttributeValue(RAY_TRACING_MODULE, ATTR_BLAS_INCLUSION_MODE,
            "");
        if (value.endsWith(".opaque_and_shadow")) {
            return BlasInclusionMode.OPAQUE_AND_SHADOW;
        }
        if (value.endsWith(".opaque_only")) {
            return BlasInclusionMode.OPAQUE_ONLY;
        }
        return BlasInclusionMode.ALL_GEOMETRY;
    }

    private static ReflectionRayMaterialMode getReflectionRayMaterialMode() {
        String value = Pipeline.getModuleAttributeValue(RAY_TRACING_MODULE,
            ATTR_REFLECTION_RAY_MATERIAL_MODE, "");
        if (value.endsWith(".reflective_only")) {
            return ReflectionRayMaterialMode.REFLECTIVE_ONLY;
        }
        if (value.endsWith(".water_glass_metal")) {
            return ReflectionRayMaterialMode.WATER_GLASS_METAL;
        }
        return ReflectionRayMaterialMode.ALL_MATERIALS;
    }

    private static FarFieldGeometryMode getFarFieldGeometryMode() {
        String value = Pipeline.getModuleAttributeValue(RAY_TRACING_MODULE,
            ATTR_FAR_FIELD_GEOMETRY_MODE, "");
        if (value.endsWith(".simplified_shell")) {
            return FarFieldGeometryMode.SIMPLIFIED_SHELL;
        }
        if (value.endsWith(".clipmap")) {
            return FarFieldGeometryMode.CLIPMAP;
        }
        if (value.endsWith(".shell_and_clipmap")) {
            return FarFieldGeometryMode.SHELL_AND_CLIPMAP;
        }
        return FarFieldGeometryMode.EXACT_CHUNKS;
    }

    enum ChunkGeometryRoute {
        BLAS,
        SPECIAL_REFLECTIVE,
        SPECIAL_MATTE,
        DROP
    }

    private enum BlasInclusionMode {
        ALL_GEOMETRY,
        OPAQUE_AND_SHADOW,
        OPAQUE_ONLY
    }

    private enum FarFieldGeometryMode {
        EXACT_CHUNKS,
        SIMPLIFIED_SHELL,
        CLIPMAP,
        SHELL_AND_CLIPMAP
    }

    private enum ReflectionRayMaterialMode {
        ALL_MATERIALS,
        REFLECTIVE_ONLY,
        WATER_GLASS_METAL
    }

    private enum GeometryPathMode {
        BLAS,
        SPECIAL_PATH,
        EXCLUDE
    }

    enum WorldRepresentationMode {
        TRIANGLE_BLAS,
        CHUNK_AABB
    }

    enum ChunkTraversalMode {
        TRIANGLE_HIT,
        VOXEL_DDA,
        BRICK,
        MACROCELL
    }

    enum ChunkDataLayout {
        TRIANGLE_GEOMETRY,
        OCCUPANCY_BITMASK,
        OCCUPANCY_PALETTE,
        OCCUPANCY_PALETTE_FACE_MASK
    }

    enum ChunkMacrocellSize {
        DISABLED,
        SIZE_4,
        SIZE_8
    }

    enum DiffuseGiMode {
        FULL_RAY_TRACING,
        PROBES,
        RADIANCE_CACHE,
        LOW_COST_HYBRID
    }

    enum TerrainMeshingMode {
        LEGACY_QUADS,
        GREEDY_MESHING,
        COPLANAR_MERGE
    }
}
