package com.radiance.client.scene_import;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.radiance.client.pipeline.Pipeline;

public final class SceneImportAbi {
    public static final int GEOMETRY_RECORD_STRIDE = 384;

    private SceneImportAbi() {
    }

    public static final int SOURCE_NATIVE_CHUNK = 0;
    public static final int SOURCE_NATIVE_ENTITY = 1;
    public static final int SOURCE_NATIVE_POST = 2;
    public static final int SOURCE_BLAZE3D = 3;
    public static final int SOURCE_EXTERNAL = 4;

    public static final int RESOURCE_SOURCE = 0;
    public static final int RESOURCE_GEOMETRY = 1;
    public static final int RESOURCE_POST = 2;
    public static final int RESOURCE_ACCELERATION = 3;
    public static final int RESOURCE_CUSTOM = 4;

    public static final int FORMAT_NATIVE_PBR = 0;
    public static final int FORMAT_NATIVE_POST_PBR = 1;
    public static final int FORMAT_BLAZE3D_DRAW = 2;
    public static final int FORMAT_EXTERNAL = 3;

    public static final int ROUTE_NONE = 0;
    public static final int ROUTE_GEOMETRY = 1;
    public static final int ROUTE_ACCELERATION = 2;
    public static final int ROUTE_POST = 4;
    public static final int ROUTE_SUPPORT_TEXTURE = 8;
    public static final int ROUTE_OVERLAY = 16;
    public static final int ROUTE_UNSPECIFIED = -1;
    public static final int ROUTE_RULE_DEPENDENT = -2;
    public static final int ROUTE_GEOMETRY_ACCELERATION = ROUTE_GEOMETRY | ROUTE_ACCELERATION;

    public static final int STEP_ALIAS_NATIVE_GEOMETRY = 0;
    public static final int STEP_PUBLISH_EXTERNAL_GEOMETRY = 1;
    public static final int STEP_COMPUTE_IMPORT_GEOMETRY = 2;
    public static final int STEP_COMPUTE_PREPROCESS_GEOMETRY = 3;
    public static final int STEP_COMPUTE_CUSTOM = 4;
    public static final int STEP_BUILD_BLAS = 5;
    public static final int STEP_IMPORT_POST = 6;

    public static final int PRIORITY_BACKGROUND = 0;
    public static final int PRIORITY_NORMAL = 1;
    public static final int PRIORITY_IMPORTANT = 2;

    public static final int DISPATCH_DIRECT = 0;
    public static final int DISPATCH_PER_ELEMENT_1D = 1;

    public static final int DESCRIPTOR_SAMPLER = 0;
    public static final int DESCRIPTOR_COMBINED_IMAGE_SAMPLER = 1;
    public static final int DESCRIPTOR_SAMPLED_IMAGE = 2;
    public static final int DESCRIPTOR_STORAGE_IMAGE = 3;
    public static final int DESCRIPTOR_UNIFORM_TEXEL_BUFFER = 4;
    public static final int DESCRIPTOR_STORAGE_TEXEL_BUFFER = 5;
    public static final int DESCRIPTOR_STORAGE_BUFFER = 7;
    public static final int DESCRIPTOR_UNIFORM_BUFFER = 6;
    public static final int DESCRIPTOR_ACCELERATION_STRUCTURE = 1_000_150_000;

    public static final int IMAGE_LAYOUT_UNDEFINED = 0;
    public static final int IMAGE_LAYOUT_GENERAL = 1;
    public static final int IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL = 5;

    public static final int VIEW_NONE = 0;
    public static final int VIEW_GEOMETRY = 1;
    public static final int VIEW_ACCELERATION = 2;
    public static final int VIEW_POST = 4;

    public static final int SOURCE_TAG_UNKNOWN = 0;
    public static final int SOURCE_TAG_VANILLA_CHUNK = 1;
    public static final int SOURCE_TAG_VANILLA_ENTITY = 2;
    public static final int SOURCE_TAG_VANILLA_POST = 3;

    public static final int DOMAIN_CHUNK = 0;
    public static final int DOMAIN_ENTITY = 1;
    public static final int DOMAIN_WEATHER = 2;
    public static final int DOMAIN_PARTICLE = 3;
    public static final int DOMAIN_TEXT = 4;
    public static final int DOMAIN_NAME_TAG = 5;
    public static final int DOMAIN_STAR = 6;

    public static final int COORDINATE_WORLD = 0;
    public static final int COORDINATE_CAMERA = 1;
    public static final int COORDINATE_CAMERA_SHIFT = 2;

    public static final int GEOMETRY_SHADOW = 0;
    public static final int GEOMETRY_WORLD_SOLID = 1;
    public static final int GEOMETRY_WORLD_TRANSPARENT = 2;
    public static final int GEOMETRY_WORLD_NO_REFLECT = 3;
    public static final int GEOMETRY_WORLD_CLOUD = 4;
    public static final int GEOMETRY_BOAT_WATER_MASK = 5;
    public static final int GEOMETRY_END_PORTAL = 6;
    public static final int GEOMETRY_END_GATEWAY = 7;

    public static final int INDEX_UINT16 = 0;
    public static final int INDEX_UINT32 = 1;

    public static final int VK_BUFFER_USAGE_STORAGE_BUFFER = 0x20;
    public static final int VK_BUFFER_USAGE_UNIFORM_BUFFER = 0x10;
    public static final int VK_BUFFER_USAGE_INDEX_BUFFER = 0x40;
    public static final int VK_BUFFER_USAGE_VERTEX_BUFFER = 0x80;
    public static final int VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY = 0x80000;

    public static ComputeTaskBuilder computeTask(int kernelId) {
        return new ComputeTaskBuilder(kernelId);
    }

    public static ComputeTaskBuilder computeTask(String kernelName) {
        return computeTask(stableCustomId(kernelName));
    }

    public static GeometryBatchBuilder geometryBatch(ComputeTaskBuilder task, int recordCount) {
        return new GeometryBatchBuilder(task, recordCount);
    }

    public static GeometryBatchBuilder geometryBatch(int kernelId, int recordCount) {
        int step = kernelId == 0 ? STEP_PUBLISH_EXTERNAL_GEOMETRY : STEP_COMPUTE_IMPORT_GEOMETRY;
        return geometryBatch(computeTask(kernelId).buildStep(step), recordCount);
    }

    public static GeometryBatchBuilder geometryBatch(String kernelName, int recordCount) {
        return geometryBatch(stableCustomId(kernelName), recordCount);
    }

    public static GeometryRecordBuilder geometryRecord() {
        return new GeometryRecordBuilder();
    }

    public static BuildPlanBuilder buildPlan() {
        return new BuildPlanBuilder();
    }

    public static int stableCustomId(String value) {
        String normalized = normalizeCustomId(value);
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

    public static int routeMask(String value) {
        String text = normalizeCustomId(value);
        if (text.isBlank() || "unknown".equals(text) || "unspecified".equals(text)) {
            return ROUTE_UNSPECIFIED;
        }
        int mask = 0;
        for (String part : text.split("[+,| ]+")) {
            if (part.isBlank()) {
                continue;
            }
            switch (part) {
                case "none", "skip", "control_only", "external_readback" -> mask |= ROUTE_NONE;
                case "geometry", "geometry_translucent" -> mask |= ROUTE_GEOMETRY;
                case "acceleration" -> mask |= ROUTE_ACCELERATION;
                case "geometry_acceleration" -> mask |= ROUTE_GEOMETRY_ACCELERATION;
                case "geometry_no_acceleration" -> mask |= ROUTE_GEOMETRY;
                case "rule_dependent", "pipeline_dependent" -> mask |= ROUTE_RULE_DEPENDENT;
                case "post", "post_or_background", "sky" -> mask |= ROUTE_POST;
                case "support_texture", "overlay_support" -> mask |= ROUTE_SUPPORT_TEXTURE;
                case "overlay" -> mask |= ROUTE_OVERLAY;
                default -> {
                    return ROUTE_UNSPECIFIED;
                }
            }
            if (mask == ROUTE_UNSPECIFIED) {
                return ROUTE_UNSPECIFIED;
            }
        }
        return mask;
    }

    public static ByteBuffer allocateGeometryRecordBuffer(int recordCount) {
        return ByteBuffer.allocateDirect(Math.max(recordCount, 0) * GEOMETRY_RECORD_STRIDE)
            .order(ByteOrder.nativeOrder());
    }

    private static String normalizeCustomId(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replace('-', '_').replace(':', '_').replace('/', '_').replace('.', '_')
            .toLowerCase(Locale.ROOT);
    }

    public static long reserveTaskId() {
        return SceneImportProxy.reserveTaskIdNative();
    }

    public static long markSourceVersion(int sourceKind, long sourceValue, long version) {
        return SceneImportProxy.markSourceVersionNative(sourceKind, sourceValue, version);
    }

    public static void configureWorkBudget(WorkBudget budget) {
        WorkBudget value = budget == null ? WorkBudget.defaults() : budget;
        SceneImportProxy.configureWorkBudgetNative(
            value.maxBuildTasks,
            value.maxImportantBuildTasks,
            value.maxNormalBuildTasks,
            value.maxBackgroundBuildTasks,
            value.maxBlasBatches,
            value.maxImportantBlasBatches,
            value.maxNormalBlasBatches,
            value.maxBackgroundBlasBatches,
            value.adaptive);
    }

    public static long submitComputeTask(ComputeTask task) {
        return SceneImportProxy.submitComputeTaskNative(
            task.taskId,
            task.buildStepKind,
            task.priority,
            task.label,
            task.kernelId,
            task.dispatchMode,
            task.elementCount,
            task.groupCountX,
            task.groupCountY,
            task.groupCountZ,
            task.pushConstants,
            task.pushConstantsOffset,
            task.pushConstantsSize,
            task.bindingSets,
            task.bindingIndices,
            task.bindingArrayElements,
            task.bindingDescriptorTypes,
            task.bindingBuffers,
            task.bindingOffsets,
            task.bindingRanges,
            task.bindingUsages,
            task.bindingImages,
            task.bindingImageLayouts,
            task.bindingImageViewIndices,
            task.bindingSamplers,
            task.bindingTlasHandles,
            task.bindingTexelBuffers,
            task.bindingTexelFormats,
            task.dependencies,
            task.expectedSourceKinds,
            task.expectedSourceValues,
            task.expectedSourceVersions);
    }

    public static long submitGeometryBatch(GeometryBatch batch) {
        return SceneImportProxy.submitGeometryBatchNative(
            batch.task.taskId,
            batch.task.buildStepKind,
            batch.task.priority,
            batch.task.label,
            batch.task.kernelId,
            batch.task.dispatchMode,
            batch.task.elementCount,
            batch.task.groupCountX,
            batch.task.groupCountY,
            batch.task.groupCountZ,
            batch.task.pushConstants,
            batch.task.pushConstantsOffset,
            batch.task.pushConstantsSize,
            batch.task.bindingSets,
            batch.task.bindingIndices,
            batch.task.bindingArrayElements,
            batch.task.bindingDescriptorTypes,
            batch.task.bindingBuffers,
            batch.task.bindingOffsets,
            batch.task.bindingRanges,
            batch.task.bindingUsages,
            batch.task.bindingImages,
            batch.task.bindingImageLayouts,
            batch.task.bindingImageViewIndices,
            batch.task.bindingSamplers,
            batch.task.bindingTlasHandles,
            batch.task.bindingTexelBuffers,
            batch.task.bindingTexelFormats,
            batch.task.dependencies,
            batch.task.expectedSourceKinds,
            batch.task.expectedSourceValues,
            batch.task.expectedSourceVersions,
            batch.geometryRecords,
            batch.geometryRecordOffset,
            batch.geometryRecordStride,
            batch.geometryRecordCount,
            batch.groupNames,
            batch.contentNames);
    }

    public static long submitBuildPlan(BuildPlan plan) {
        return SceneImportProxy.submitBuildPlanNative(
            plan.planId,
            plan.sourceKind,
            plan.sourceValue,
            plan.sourceVersion,
            plan.nodeIds,
            plan.nodeKinds,
            plan.nodePriorities,
            plan.nodeLabels,
            plan.nodeKernels,
            plan.nodeDispatchModes,
            plan.nodeElementCounts,
            plan.nodeGroupCountX,
            plan.nodeGroupCountY,
            plan.nodeGroupCountZ,
            plan.pushConstants,
            plan.pushConstantOffsets,
            plan.pushConstantSizes,
            plan.bindingNodeIndices,
            plan.bindingSets,
            plan.bindingIndices,
            plan.bindingArrayElements,
            plan.bindingDescriptorTypes,
            plan.bindingBuffers,
            plan.bindingOffsets,
            plan.bindingRanges,
            plan.bindingUsages,
            plan.bindingImages,
            plan.bindingImageLayouts,
            plan.bindingImageViewIndices,
            plan.bindingSamplers,
            plan.bindingTlasHandles,
            plan.bindingTexelBuffers,
            plan.bindingTexelFormats,
            plan.dependencyNodeIndices,
            plan.dependencies,
            plan.expectedNodeIndices,
            plan.expectedSourceKinds,
            plan.expectedSourceValues,
            plan.expectedSourceVersions,
            plan.inputNodeIndices,
            plan.inputResourceKinds,
            plan.inputSourceKinds,
            plan.inputSourceValues,
            plan.inputVersions,
            plan.inputValues,
            plan.outputNodeIndices,
            plan.outputResourceKinds,
            plan.outputSourceKinds,
            plan.outputSourceValues,
            plan.outputVersions,
            plan.outputValues);
    }

    public static long[] diagnostics() {
        return SceneImportProxy.diagnosticsNative();
    }

    public static Diagnostics diagnosticsSnapshot() {
        long[] values = diagnostics();
        return new Diagnostics(
            diagnosticValue(values, 0),
            diagnosticValue(values, 1),
            diagnosticValue(values, 2),
            diagnosticValue(values, 3),
            diagnosticValue(values, 4),
            diagnosticValue(values, 5),
            diagnosticValue(values, 6),
            diagnosticValue(values, 7),
            diagnosticValue(values, 8),
            diagnosticValue(values, 9) != 0L,
            diagnosticValue(values, 10),
            diagnosticValue(values, 11),
            diagnosticValue(values, 12),
            diagnosticValue(values, 13),
            diagnosticValue(values, 14),
            diagnosticValue(values, 15),
            diagnosticValue(values, 16),
            diagnosticValue(values, 17),
            diagnosticValue(values, 18),
            diagnosticValue(values, 19),
            diagnosticValue(values, 20),
            diagnosticValue(values, 21),
            diagnosticValue(values, 22),
            diagnosticValue(values, 23),
            diagnosticValue(values, 24));
    }

    public static final int DIAGNOSTICS_V2_FIELD_COUNT = 108;
    public static final int DIAGNOSTICS_V2_LATENCY_BASE = 27;
    public static final int DIAGNOSTICS_V2_PRIORITY_BASE = 47;
    public static final int DIAGNOSTICS_V2_GROUP_BASE = 59;
    public static final int DIAGNOSTICS_V2_TLAS_INSTANCE_BASE = 89;
    public static final int DIAGNOSTICS_V2_ENTITY_PIPELINE_BASE = 94;
    public static final int DIAGNOSTICS_V2_LATENCY_STRIDE = 4;
    public static final int DIAGNOSTICS_V2_PRIORITY_STRIDE = 4;
    public static final int DIAGNOSTICS_V2_GROUP_STRIDE = 5;

    public static final int LATENCY_QUEUE = 0;
    public static final int LATENCY_GPU = 1;
    public static final int LATENCY_PUBLISH = 2;
    public static final int LATENCY_FRAME = 3;
    public static final int LATENCY_TOTAL = 4;
    public static final int LATENCY_COUNT = 5;

    public static final int GROUP_CHUNK = 0;
    public static final int GROUP_IMPORTANT_CHUNK = 1;
    public static final int GROUP_ENTITY = 2;
    public static final int GROUP_NATIVE_POST = 3;
    public static final int GROUP_BLAZE3D = 4;
    public static final int GROUP_EXTERNAL = 5;
    public static final int GROUP_COUNT = 6;

    public static long[] diagnosticsV2() {
        return Pipeline.getSceneImportDiagnosticsV2Snapshot();
    }

    public static DiagnosticsV2 diagnosticsV2Snapshot() {
        long[] values = diagnosticsV2();
        LatencyStats[] latencies = new LatencyStats[LATENCY_COUNT];
        for (int i = 0; i < latencies.length; i++) {
            int base = DIAGNOSTICS_V2_LATENCY_BASE + i * DIAGNOSTICS_V2_LATENCY_STRIDE;
            latencies[i] = new LatencyStats(
                diagnosticValue(values, base),
                diagnosticValue(values, base + 1),
                diagnosticValue(values, base + 2),
                diagnosticValue(values, base + 3));
        }

        PriorityStats[] priorities = new PriorityStats[] {
            priorityStats(values, PRIORITY_BACKGROUND),
            priorityStats(values, PRIORITY_NORMAL),
            priorityStats(values, PRIORITY_IMPORTANT)
        };

        SourceGroupStats[] groups = new SourceGroupStats[GROUP_COUNT];
        for (int i = 0; i < groups.length; i++) {
            int base = DIAGNOSTICS_V2_GROUP_BASE + i * DIAGNOSTICS_V2_GROUP_STRIDE;
            groups[i] = new SourceGroupStats(
                diagnosticValue(values, base),
                diagnosticValue(values, base + 1),
                diagnosticValue(values, base + 2),
                diagnosticValue(values, base + 3),
                diagnosticValue(values, base + 4));
        }

        return new DiagnosticsV2(
            diagnosticValue(values, 0),
            diagnosticValue(values, 1),
            diagnosticValue(values, 2),
            diagnosticValue(values, 3),
            diagnosticValue(values, 4),
            diagnosticValue(values, 5),
            diagnosticValue(values, 6),
            diagnosticValue(values, 7),
            diagnosticValue(values, 8),
            diagnosticValue(values, 9),
            diagnosticValue(values, 10),
            diagnosticValue(values, 11),
            diagnosticValue(values, 12),
            diagnosticValue(values, 13) != 0L,
            diagnosticValue(values, 14),
            diagnosticValue(values, 15),
            diagnosticValue(values, 16),
            diagnosticValue(values, 17),
            diagnosticValue(values, 18),
            diagnosticValue(values, 19),
            diagnosticValue(values, 20),
            diagnosticValue(values, 21),
            diagnosticValue(values, 22),
            diagnosticValue(values, 23),
            diagnosticValue(values, 24),
            diagnosticValue(values, 25),
            diagnosticValue(values, 26),
            diagnosticValue(values, DIAGNOSTICS_V2_TLAS_INSTANCE_BASE),
            diagnosticValue(values, DIAGNOSTICS_V2_TLAS_INSTANCE_BASE + 1),
            diagnosticValue(values, DIAGNOSTICS_V2_TLAS_INSTANCE_BASE + 2),
            diagnosticValue(values, DIAGNOSTICS_V2_TLAS_INSTANCE_BASE + 3),
            diagnosticValue(values, DIAGNOSTICS_V2_TLAS_INSTANCE_BASE + 4),
            entityPipelineStats(values),
            latencies,
            priorities,
            groups);
    }

    private static PriorityStats priorityStats(long[] values, int priority) {
        int base = DIAGNOSTICS_V2_PRIORITY_BASE + priority * DIAGNOSTICS_V2_PRIORITY_STRIDE;
        return new PriorityStats(
            diagnosticValue(values, base),
            diagnosticValue(values, base + 1),
            diagnosticValue(values, base + 2),
            diagnosticValue(values, base + 3));
    }

    private static EntityPipelineStats entityPipelineStats(long[] values) {
        int base = DIAGNOSTICS_V2_ENTITY_PIPELINE_BASE;
        return new EntityPipelineStats(
            diagnosticValue(values, base),
            diagnosticValue(values, base + 1),
            diagnosticValue(values, base + 2),
            diagnosticValue(values, base + 3),
            diagnosticValue(values, base + 4),
            diagnosticValue(values, base + 5),
            diagnosticValue(values, base + 6),
            diagnosticValue(values, base + 7),
            diagnosticValue(values, base + 8),
            diagnosticValue(values, base + 9),
            diagnosticValue(values, base + 10),
            diagnosticValue(values, base + 11),
            diagnosticValue(values, base + 12),
            diagnosticValue(values, base + 13));
    }

    private static long diagnosticValue(long[] values, int index) {
        return values != null && index < values.length ? values[index] : 0L;
    }

    public record Diagnostics(
        long serial,
        long geometries,
        long posts,
        long pendingTasks,
        long completedTasks,
        long failedTasks,
        long registeredKernels,
        long registeredPipelineRules,
        long registeredTransformRecipes,
        boolean lastWorkSubmitted,
        long lastComputeTasks,
        long lastBlasTasks,
        long lastPublishTasks,
        long lastDroppedStaleTasks,
        long lastFailedTasks,
        long lastQueuedTasks,
        long lastBudgetDeferredTasks,
        long lastBudgetDeferredBlasBatches,
        long accelerations,
        long pendingGeometries,
        long pendingPosts,
        long pendingAccelerations,
        long failedResources,
        long staleResources,
        long lastAsyncTimelineValue) {
    }

    public record LatencyStats(long samples, long p50Ms, long p95Ms, long maxMs) {
    }

    public record PriorityStats(long accepted, long selected, long submitted, long deferred) {
    }

    public record SourceGroupStats(
        long accepted,
        long published,
        long stale,
        long failed,
        long latestTotalMs) {
    }

    public record EntityPipelineStats(
        long geometryReady,
        long geometryPending,
        long geometryStale,
        long geometryFailed,
        long accelerationReady,
        long accelerationPending,
        long accelerationStale,
        long accelerationFailed,
        long accelerationMissingForReadyGeometry,
        long accelerationVersionMismatch,
        long accelerationBlasNull,
        long snapshotReadyAcceleration,
        long pendingBlasSources,
        long asyncReadyBlasSources) {
    }

    public record DiagnosticsV2(
        long schema,
        long serial,
        long geometries,
        long pendingGeometries,
        long posts,
        long pendingPosts,
        long accelerations,
        long pendingAccelerations,
        long failedResources,
        long staleResources,
        long pendingTasks,
        long completedTasks,
        long failedTasks,
        boolean lastWorkSubmitted,
        long lastComputeTasks,
        long lastBlasTasks,
        long lastPublishTasks,
        long lastDroppedStaleTasks,
        long lastFailedTasks,
        long lastQueuedTasks,
        long lastBudgetDeferredTasks,
        long lastBudgetDeferredBlasBatches,
        long lastAsyncTimelineValue,
        long pendingAsyncCompletions,
        long registeredKernels,
        long registeredPipelineRules,
        long registeredTransformRecipes,
        long tlasInstanceSerial,
        long tlasInstances,
        long tlasChunkInstances,
        long tlasEntityInstances,
        long tlasOtherInstances,
        EntityPipelineStats entityPipeline,
        LatencyStats[] latencies,
        PriorityStats[] priorities,
        SourceGroupStats[] groups) {

        public LatencyStats latency(int index) {
            return latencies != null && index >= 0 && index < latencies.length
                ? latencies[index]
                : new LatencyStats(0, 0, 0, 0);
        }

        public PriorityStats priority(int index) {
            return priorities != null && index >= 0 && index < priorities.length
                ? priorities[index]
                : new PriorityStats(0, 0, 0, 0);
        }

        public SourceGroupStats group(int index) {
            return groups != null && index >= 0 && index < groups.length
                ? groups[index]
                : new SourceGroupStats(0, 0, 0, 0, 0);
        }
    }

    public record WorkBudget(
        int maxBuildTasks,
        int maxImportantBuildTasks,
        int maxNormalBuildTasks,
        int maxBackgroundBuildTasks,
        int maxBlasBatches,
        int maxImportantBlasBatches,
        int maxNormalBlasBatches,
        int maxBackgroundBlasBatches,
        boolean adaptive) {

        public static WorkBudget defaults() {
            return new WorkBudget(64, 64, 32, 8, 48, 48, 24, 6, true);
        }
    }

    public record ComputeTask(
        long taskId,
        int buildStepKind,
        int priority,
        String label,
        int kernelId,
        int dispatchMode,
        int elementCount,
        int groupCountX,
        int groupCountY,
        int groupCountZ,
        ByteBuffer pushConstants,
        int pushConstantsOffset,
        int pushConstantsSize,
        int[] bindingSets,
        int[] bindingIndices,
        int[] bindingArrayElements,
        int[] bindingDescriptorTypes,
        long[] bindingBuffers,
        long[] bindingOffsets,
        long[] bindingRanges,
        int[] bindingUsages,
        long[] bindingImages,
        int[] bindingImageLayouts,
        int[] bindingImageViewIndices,
        long[] bindingSamplers,
        long[] bindingTlasHandles,
        long[] bindingTexelBuffers,
        int[] bindingTexelFormats,
        long[] dependencies,
        int[] expectedSourceKinds,
        long[] expectedSourceValues,
        long[] expectedSourceVersions) {
    }

    public record GeometryBatch(
        ComputeTask task,
        ByteBuffer geometryRecords,
        int geometryRecordOffset,
        int geometryRecordStride,
        int geometryRecordCount,
        String[] groupNames,
        String[] contentNames) {
    }

    public record ResourceRef(int kind, int sourceKind, long sourceValue, long version, long value) {
    }

    public record BuildPlan(
        long planId,
        int sourceKind,
        long sourceValue,
        long sourceVersion,
        long[] nodeIds,
        int[] nodeKinds,
        int[] nodePriorities,
        String[] nodeLabels,
        int[] nodeKernels,
        int[] nodeDispatchModes,
        int[] nodeElementCounts,
        int[] nodeGroupCountX,
        int[] nodeGroupCountY,
        int[] nodeGroupCountZ,
        ByteBuffer pushConstants,
        int[] pushConstantOffsets,
        int[] pushConstantSizes,
        int[] bindingNodeIndices,
        int[] bindingSets,
        int[] bindingIndices,
        int[] bindingArrayElements,
        int[] bindingDescriptorTypes,
        long[] bindingBuffers,
        long[] bindingOffsets,
        long[] bindingRanges,
        int[] bindingUsages,
        long[] bindingImages,
        int[] bindingImageLayouts,
        int[] bindingImageViewIndices,
        long[] bindingSamplers,
        long[] bindingTlasHandles,
        long[] bindingTexelBuffers,
        int[] bindingTexelFormats,
        int[] dependencyNodeIndices,
        long[] dependencies,
        int[] expectedNodeIndices,
        int[] expectedSourceKinds,
        long[] expectedSourceValues,
        long[] expectedSourceVersions,
        int[] inputNodeIndices,
        int[] inputResourceKinds,
        int[] inputSourceKinds,
        long[] inputSourceValues,
        long[] inputVersions,
        long[] inputValues,
        int[] outputNodeIndices,
        int[] outputResourceKinds,
        int[] outputSourceKinds,
        long[] outputSourceValues,
        long[] outputVersions,
        long[] outputValues) {
    }

    public static final class BuildPlanBuilder {
        private long planId;
        private int sourceKind = SOURCE_EXTERNAL;
        private long sourceValue;
        private long sourceVersion;
        private final List<PlanNode> nodes = new ArrayList<>();

        private BuildPlanBuilder() {
        }

        public BuildPlanBuilder planId(long value) {
            this.planId = value;
            return this;
        }

        public BuildPlanBuilder source(int kind, long value, long version) {
            this.sourceKind = kind;
            this.sourceValue = value;
            this.sourceVersion = version;
            return this;
        }

        public NodeBuilder node(ComputeTaskBuilder task) {
            NodeBuilder builder = new NodeBuilder(this, task == null ? computeTask(0) : task);
            return builder;
        }

        public BuildPlanBuilder add(NodeBuilder builder) {
            if (builder != null) {
                this.nodes.add(builder.buildNode());
            }
            return this;
        }

        public BuildPlan build() {
            int nodeCount = this.nodes.size();
            long[] nodeIds = new long[nodeCount];
            int[] nodeKinds = new int[nodeCount];
            int[] nodePriorities = new int[nodeCount];
            String[] nodeLabels = new String[nodeCount];
            int[] nodeKernels = new int[nodeCount];
            int[] nodeDispatchModes = new int[nodeCount];
            int[] nodeElementCounts = new int[nodeCount];
            int[] nodeGroupCountX = new int[nodeCount];
            int[] nodeGroupCountY = new int[nodeCount];
            int[] nodeGroupCountZ = new int[nodeCount];

            int pushConstantBytes = 0;
            int bindingCount = 0;
            int dependencyCount = 0;
            int expectedCount = 0;
            int inputCount = 0;
            int outputCount = 0;
            for (PlanNode node : this.nodes) {
                ComputeTask task = node.task();
                pushConstantBytes += Math.max(task.pushConstantsSize(), 0);
                bindingCount += task.bindingSets().length;
                dependencyCount += task.dependencies().length;
                expectedCount += task.expectedSourceKinds().length;
                inputCount += node.inputs().size();
                outputCount += node.outputs().size();
            }

            ByteBuffer pushConstants = ByteBuffer.allocateDirect(pushConstantBytes)
                .order(ByteOrder.nativeOrder());
            int[] pushConstantOffsets = new int[nodeCount];
            int[] pushConstantSizes = new int[nodeCount];
            int[] bindingNodeIndices = new int[bindingCount];
            int[] bindingSets = new int[bindingCount];
            int[] bindingIndices = new int[bindingCount];
            int[] bindingArrayElements = new int[bindingCount];
            int[] bindingDescriptorTypes = new int[bindingCount];
            long[] bindingBuffers = new long[bindingCount];
            long[] bindingOffsets = new long[bindingCount];
            long[] bindingRanges = new long[bindingCount];
            int[] bindingUsages = new int[bindingCount];
            long[] bindingImages = new long[bindingCount];
            int[] bindingImageLayouts = new int[bindingCount];
            int[] bindingImageViewIndices = new int[bindingCount];
            long[] bindingSamplers = new long[bindingCount];
            long[] bindingTlasHandles = new long[bindingCount];
            long[] bindingTexelBuffers = new long[bindingCount];
            int[] bindingTexelFormats = new int[bindingCount];
            int[] dependencyNodeIndices = new int[dependencyCount];
            long[] dependencies = new long[dependencyCount];
            int[] expectedNodeIndices = new int[expectedCount];
            int[] expectedSourceKinds = new int[expectedCount];
            long[] expectedSourceValues = new long[expectedCount];
            long[] expectedSourceVersions = new long[expectedCount];
            int[] inputNodeIndices = new int[inputCount];
            int[] inputResourceKinds = new int[inputCount];
            int[] inputSourceKinds = new int[inputCount];
            long[] inputSourceValues = new long[inputCount];
            long[] inputVersions = new long[inputCount];
            long[] inputValues = new long[inputCount];
            int[] outputNodeIndices = new int[outputCount];
            int[] outputResourceKinds = new int[outputCount];
            int[] outputSourceKinds = new int[outputCount];
            long[] outputSourceValues = new long[outputCount];
            long[] outputVersions = new long[outputCount];
            long[] outputValues = new long[outputCount];

            int bindingCursor = 0;
            int dependencyCursor = 0;
            int expectedCursor = 0;
            int inputCursor = 0;
            int outputCursor = 0;
            for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
                PlanNode node = this.nodes.get(nodeIndex);
                ComputeTask task = node.task();
                nodeIds[nodeIndex] = task.taskId();
                nodeKinds[nodeIndex] = task.buildStepKind();
                nodePriorities[nodeIndex] = task.priority();
                nodeLabels[nodeIndex] = task.label();
                nodeKernels[nodeIndex] = task.kernelId();
                nodeDispatchModes[nodeIndex] = task.dispatchMode();
                nodeElementCounts[nodeIndex] = task.elementCount();
                nodeGroupCountX[nodeIndex] = task.groupCountX();
                nodeGroupCountY[nodeIndex] = task.groupCountY();
                nodeGroupCountZ[nodeIndex] = task.groupCountZ();
                pushConstantOffsets[nodeIndex] = pushConstants.position();
                pushConstantSizes[nodeIndex] = Math.max(task.pushConstantsSize(), 0);
                if (task.pushConstants() != null && task.pushConstantsSize() > 0) {
                    ByteBuffer source = task.pushConstants().duplicate().order(ByteOrder.nativeOrder());
                    source.position(task.pushConstantsOffset());
                    source.limit(task.pushConstantsOffset() + task.pushConstantsSize());
                    pushConstants.put(source);
                }

                for (int i = 0; i < task.bindingSets().length; i++, bindingCursor++) {
                    bindingNodeIndices[bindingCursor] = nodeIndex;
                    bindingSets[bindingCursor] = task.bindingSets()[i];
                    bindingIndices[bindingCursor] = task.bindingIndices()[i];
                    bindingArrayElements[bindingCursor] = task.bindingArrayElements()[i];
                    bindingDescriptorTypes[bindingCursor] = task.bindingDescriptorTypes()[i];
                    bindingBuffers[bindingCursor] = task.bindingBuffers()[i];
                    bindingOffsets[bindingCursor] = task.bindingOffsets()[i];
                    bindingRanges[bindingCursor] = task.bindingRanges()[i];
                    bindingUsages[bindingCursor] = task.bindingUsages()[i];
                    bindingImages[bindingCursor] = task.bindingImages()[i];
                    bindingImageLayouts[bindingCursor] = task.bindingImageLayouts()[i];
                    bindingImageViewIndices[bindingCursor] = task.bindingImageViewIndices()[i];
                    bindingSamplers[bindingCursor] = task.bindingSamplers()[i];
                    bindingTlasHandles[bindingCursor] = task.bindingTlasHandles()[i];
                    bindingTexelBuffers[bindingCursor] = task.bindingTexelBuffers()[i];
                    bindingTexelFormats[bindingCursor] = task.bindingTexelFormats()[i];
                }
                for (long dependency : task.dependencies()) {
                    dependencyNodeIndices[dependencyCursor] = nodeIndex;
                    dependencies[dependencyCursor++] = dependency;
                }
                for (int i = 0; i < task.expectedSourceKinds().length; i++, expectedCursor++) {
                    expectedNodeIndices[expectedCursor] = nodeIndex;
                    expectedSourceKinds[expectedCursor] = task.expectedSourceKinds()[i];
                    expectedSourceValues[expectedCursor] = task.expectedSourceValues()[i];
                    expectedSourceVersions[expectedCursor] = task.expectedSourceVersions()[i];
                }
                for (ResourceRef input : node.inputs()) {
                    inputNodeIndices[inputCursor] = nodeIndex;
                    inputResourceKinds[inputCursor] = input.kind();
                    inputSourceKinds[inputCursor] = input.sourceKind();
                    inputSourceValues[inputCursor] = input.sourceValue();
                    inputVersions[inputCursor] = input.version();
                    inputValues[inputCursor++] = input.value();
                }
                for (ResourceRef output : node.outputs()) {
                    outputNodeIndices[outputCursor] = nodeIndex;
                    outputResourceKinds[outputCursor] = output.kind();
                    outputSourceKinds[outputCursor] = output.sourceKind();
                    outputSourceValues[outputCursor] = output.sourceValue();
                    outputVersions[outputCursor] = output.version();
                    outputValues[outputCursor++] = output.value();
                }
            }
            pushConstants.flip();

            return new BuildPlan(this.planId, this.sourceKind, this.sourceValue, this.sourceVersion,
                nodeIds, nodeKinds, nodePriorities, nodeLabels, nodeKernels, nodeDispatchModes,
                nodeElementCounts, nodeGroupCountX, nodeGroupCountY, nodeGroupCountZ,
                pushConstants, pushConstantOffsets, pushConstantSizes, bindingNodeIndices,
                bindingSets, bindingIndices, bindingArrayElements, bindingDescriptorTypes,
                bindingBuffers, bindingOffsets, bindingRanges, bindingUsages, bindingImages,
                bindingImageLayouts, bindingImageViewIndices, bindingSamplers, bindingTlasHandles,
                bindingTexelBuffers, bindingTexelFormats, dependencyNodeIndices, dependencies,
                expectedNodeIndices, expectedSourceKinds, expectedSourceValues, expectedSourceVersions,
                inputNodeIndices, inputResourceKinds, inputSourceKinds, inputSourceValues,
                inputVersions, inputValues, outputNodeIndices, outputResourceKinds,
                outputSourceKinds, outputSourceValues, outputVersions, outputValues);
        }

        public long submit() {
            return submitBuildPlan(build());
        }
    }

    public static final class NodeBuilder {
        private final BuildPlanBuilder parent;
        private final ComputeTaskBuilder task;
        private final List<ResourceRef> inputs = new ArrayList<>();
        private final List<ResourceRef> outputs = new ArrayList<>();

        private NodeBuilder(BuildPlanBuilder parent, ComputeTaskBuilder task) {
            this.parent = parent;
            this.task = task;
        }

        public NodeBuilder input(int kind, int sourceKind, long sourceValue, long version) {
            return input(kind, sourceKind, sourceValue, version, 0L);
        }

        public NodeBuilder input(int kind, int sourceKind, long sourceValue, long version,
            long value) {
            this.inputs.add(new ResourceRef(kind, sourceKind, sourceValue, version, value));
            return this;
        }

        public NodeBuilder output(int kind, int sourceKind, long sourceValue, long version) {
            return output(kind, sourceKind, sourceValue, version, 0L);
        }

        public NodeBuilder output(int kind, int sourceKind, long sourceValue, long version,
            long value) {
            this.outputs.add(new ResourceRef(kind, sourceKind, sourceValue, version, value));
            return this;
        }

        public BuildPlanBuilder endNode() {
            return this.parent.add(this);
        }

        private PlanNode buildNode() {
            return new PlanNode(this.task.build(), List.copyOf(this.inputs), List.copyOf(this.outputs));
        }
    }

    private record PlanNode(ComputeTask task, List<ResourceRef> inputs, List<ResourceRef> outputs) {
    }

    public static final class ComputeTaskBuilder {
        private long taskId;
        private int buildStepKind = STEP_COMPUTE_CUSTOM;
        private int priority = PRIORITY_NORMAL;
        private String label = "";
        private int kernelId;
        private int dispatchMode = DISPATCH_DIRECT;
        private int elementCount;
        private int groupCountX = 1;
        private int groupCountY = 1;
        private int groupCountZ = 1;
        private ByteBuffer pushConstants;
        private int pushConstantsOffset;
        private int pushConstantsSize;
        private final List<Binding> bindings = new ArrayList<>();
        private final List<Long> dependencies = new ArrayList<>();
        private final List<ExpectedVersion> expectedVersions = new ArrayList<>();

        private ComputeTaskBuilder(int kernelId) {
            this.kernelId = kernelId;
        }

        public ComputeTaskBuilder taskId(long value) {
            this.taskId = value;
            return this;
        }

        public ComputeTaskBuilder buildStep(int value) {
            this.buildStepKind = value;
            return this;
        }

        public ComputeTaskBuilder priority(int value) {
            this.priority = value;
            return this;
        }

        public ComputeTaskBuilder label(String value) {
            this.label = value == null ? "" : value;
            return this;
        }

        public ComputeTaskBuilder kernelId(int value) {
            this.kernelId = value;
            return this;
        }

        public ComputeTaskBuilder kernel(String name) {
            return kernelId(stableCustomId(name));
        }

        public ComputeTaskBuilder dispatchDirect(int groupsX, int groupsY, int groupsZ) {
            this.dispatchMode = DISPATCH_DIRECT;
            this.groupCountX = Math.max(groupsX, 0);
            this.groupCountY = Math.max(groupsY, 0);
            this.groupCountZ = Math.max(groupsZ, 0);
            this.elementCount = 0;
            return this;
        }

        public ComputeTaskBuilder dispatchPerElement1D(int elements) {
            this.dispatchMode = DISPATCH_PER_ELEMENT_1D;
            this.elementCount = Math.max(elements, 0);
            this.groupCountX = 1;
            this.groupCountY = 1;
            this.groupCountZ = 1;
            return this;
        }

        public ComputeTaskBuilder pushConstants(ByteBuffer buffer) {
            return pushConstants(buffer, buffer == null ? 0 : buffer.position(),
                buffer == null ? 0 : buffer.remaining());
        }

        public ComputeTaskBuilder pushConstants(ByteBuffer buffer, int offset, int size) {
            this.pushConstants = buffer == null ? null : buffer.order(ByteOrder.nativeOrder());
            this.pushConstantsOffset = Math.max(offset, 0);
            this.pushConstantsSize = Math.max(size, 0);
            return this;
        }

        public ComputeTaskBuilder storageBuffer(int binding, long bufferHandle) {
            return storageBuffer(0, binding, bufferHandle, 0, -1L);
        }

        public ComputeTaskBuilder storageBuffer(int set, int binding, long bufferHandle) {
            return storageBuffer(set, binding, bufferHandle, 0, -1L);
        }

        public ComputeTaskBuilder storageBuffer(int set, int binding, long bufferHandle,
            long offset, long range) {
            return buffer(set, binding, 0, DESCRIPTOR_STORAGE_BUFFER, bufferHandle,
                offset, range, VK_BUFFER_USAGE_STORAGE_BUFFER);
        }

        public ComputeTaskBuilder uniformBuffer(int set, int binding, long bufferHandle,
            long offset, long range) {
            return buffer(set, binding, 0, DESCRIPTOR_UNIFORM_BUFFER, bufferHandle,
                offset, range, VK_BUFFER_USAGE_UNIFORM_BUFFER);
        }

        public ComputeTaskBuilder buffer(int set, int binding, int arrayElement, int descriptorType,
            long bufferHandle, long offset, long range, int usage) {
            if (bufferHandle != 0L) {
                this.bindings.add(new Binding(set, binding, arrayElement, descriptorType,
                    bufferHandle, offset, range, usage, 0L, IMAGE_LAYOUT_UNDEFINED, 0,
                    0L, 0L, 0L, 0));
            }
            return this;
        }

        public ComputeTaskBuilder sampledImage(int set, int binding, long textureViewHandle) {
            return image(set, binding, 0, DESCRIPTOR_SAMPLED_IMAGE, textureViewHandle,
                IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, 0);
        }

        public ComputeTaskBuilder storageImage(int set, int binding, long textureViewHandle) {
            return image(set, binding, 0, DESCRIPTOR_STORAGE_IMAGE, textureViewHandle,
                IMAGE_LAYOUT_GENERAL, 0);
        }

        public ComputeTaskBuilder image(int set, int binding, int arrayElement,
            int descriptorType, long textureViewHandle, int imageLayout, int imageViewIndex) {
            if (textureViewHandle != 0L) {
                this.bindings.add(new Binding(set, binding, arrayElement, descriptorType,
                    0L, 0L, -1L, 0, textureViewHandle, imageLayout, imageViewIndex,
                    0L, 0L, 0L, 0));
            }
            return this;
        }

        public ComputeTaskBuilder combinedImageSampler(int set, int binding,
            long textureViewHandle, long samplerHandle) {
            return combinedImageSampler(set, binding, 0, textureViewHandle, samplerHandle,
                IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, 0);
        }

        public ComputeTaskBuilder combinedImageSampler(int set, int binding, int arrayElement,
            long textureViewHandle, long samplerHandle, int imageLayout, int imageViewIndex) {
            if (textureViewHandle != 0L && samplerHandle != 0L) {
                this.bindings.add(new Binding(set, binding, arrayElement,
                    DESCRIPTOR_COMBINED_IMAGE_SAMPLER, 0L, 0L, -1L, 0,
                    textureViewHandle, imageLayout, imageViewIndex, samplerHandle,
                    0L, 0L, 0));
            }
            return this;
        }

        public ComputeTaskBuilder tlas(int set, int binding, long tlasHandle) {
            if (tlasHandle != 0L) {
                this.bindings.add(new Binding(set, binding, 0, DESCRIPTOR_ACCELERATION_STRUCTURE,
                    0L, 0L, -1L, 0, 0L, IMAGE_LAYOUT_UNDEFINED, 0,
                    0L, tlasHandle, 0L, 0));
            }
            return this;
        }

        public ComputeTaskBuilder uniformTexelBuffer(int set, int binding,
            long bufferHandle, int vkFormat, long offset, long range) {
            return texelBuffer(set, binding, 0, DESCRIPTOR_UNIFORM_TEXEL_BUFFER,
                bufferHandle, vkFormat, offset, range, VK_BUFFER_USAGE_UNIFORM_BUFFER);
        }

        public ComputeTaskBuilder storageTexelBuffer(int set, int binding,
            long bufferHandle, int vkFormat, long offset, long range) {
            return texelBuffer(set, binding, 0, DESCRIPTOR_STORAGE_TEXEL_BUFFER,
                bufferHandle, vkFormat, offset, range, VK_BUFFER_USAGE_STORAGE_BUFFER);
        }

        public ComputeTaskBuilder texelBuffer(int set, int binding, int arrayElement,
            int descriptorType, long bufferHandle, int vkFormat, long offset, long range,
            int usage) {
            if (bufferHandle != 0L) {
                this.bindings.add(new Binding(set, binding, arrayElement, descriptorType,
                    0L, offset, range, usage, 0L, IMAGE_LAYOUT_UNDEFINED, 0,
                    0L, 0L, bufferHandle, vkFormat));
            }
            return this;
        }

        public ComputeTaskBuilder dependsOn(long taskId) {
            if (taskId > 0L) {
                this.dependencies.add(taskId);
            }
            return this;
        }

        public ComputeTaskBuilder expectSourceVersion(int sourceKind, long sourceValue,
            long version) {
            this.expectedVersions.add(new ExpectedVersion(sourceKind, sourceValue, version));
            return this;
        }

        public ComputeTask build() {
            int[] bindingSets = new int[this.bindings.size()];
            int[] bindingIndices = new int[this.bindings.size()];
            int[] bindingArrayElements = new int[this.bindings.size()];
            int[] bindingDescriptorTypes = new int[this.bindings.size()];
            long[] bindingBuffers = new long[this.bindings.size()];
            long[] bindingOffsets = new long[this.bindings.size()];
            long[] bindingRanges = new long[this.bindings.size()];
            int[] bindingUsages = new int[this.bindings.size()];
            long[] bindingImages = new long[this.bindings.size()];
            int[] bindingImageLayouts = new int[this.bindings.size()];
            int[] bindingImageViewIndices = new int[this.bindings.size()];
            long[] bindingSamplers = new long[this.bindings.size()];
            long[] bindingTlasHandles = new long[this.bindings.size()];
            long[] bindingTexelBuffers = new long[this.bindings.size()];
            int[] bindingTexelFormats = new int[this.bindings.size()];
            for (int i = 0; i < this.bindings.size(); i++) {
                Binding binding = this.bindings.get(i);
                bindingSets[i] = Math.max(binding.set(), 0);
                bindingIndices[i] = Math.max(binding.binding(), 0);
                bindingArrayElements[i] = Math.max(binding.arrayElement(), 0);
                bindingDescriptorTypes[i] = binding.descriptorType();
                bindingBuffers[i] = binding.bufferHandle();
                bindingOffsets[i] = Math.max(binding.offset(), 0L);
                bindingRanges[i] = binding.range();
                bindingUsages[i] = Math.max(binding.usage(), 0);
                bindingImages[i] = binding.imageHandle();
                bindingImageLayouts[i] = Math.max(binding.imageLayout(), 0);
                bindingImageViewIndices[i] = Math.max(binding.imageViewIndex(), 0);
                bindingSamplers[i] = binding.samplerHandle();
                bindingTlasHandles[i] = binding.tlasHandle();
                bindingTexelBuffers[i] = binding.texelBufferHandle();
                bindingTexelFormats[i] = Math.max(binding.texelFormat(), 0);
            }

            long[] dependencyArray = new long[this.dependencies.size()];
            for (int i = 0; i < this.dependencies.size(); i++) {
                dependencyArray[i] = this.dependencies.get(i);
            }

            int[] expectedSourceKinds = new int[this.expectedVersions.size()];
            long[] expectedSourceValues = new long[this.expectedVersions.size()];
            long[] expectedSourceVersions = new long[this.expectedVersions.size()];
            for (int i = 0; i < this.expectedVersions.size(); i++) {
                ExpectedVersion expected = this.expectedVersions.get(i);
                expectedSourceKinds[i] = expected.sourceKind();
                expectedSourceValues[i] = expected.sourceValue();
                expectedSourceVersions[i] = expected.version();
            }

            return new ComputeTask(
                this.taskId,
                this.buildStepKind,
                this.priority,
                this.label,
                this.kernelId,
                this.dispatchMode,
                this.elementCount,
                this.groupCountX,
                this.groupCountY,
                this.groupCountZ,
                this.pushConstants,
                this.pushConstantsOffset,
                this.pushConstantsSize,
                bindingSets,
                bindingIndices,
                bindingArrayElements,
                bindingDescriptorTypes,
                bindingBuffers,
                bindingOffsets,
                bindingRanges,
                bindingUsages,
                bindingImages,
                bindingImageLayouts,
                bindingImageViewIndices,
                bindingSamplers,
                bindingTlasHandles,
                bindingTexelBuffers,
                bindingTexelFormats,
                dependencyArray,
                expectedSourceKinds,
                expectedSourceValues,
                expectedSourceVersions);
        }

        private record Binding(int set, int binding, int arrayElement, int descriptorType,
                               long bufferHandle, long offset, long range, int usage,
                               long imageHandle, int imageLayout, int imageViewIndex,
                               long samplerHandle, long tlasHandle, long texelBufferHandle,
                               int texelFormat) {
        }

        private record ExpectedVersion(int sourceKind, long sourceValue, long version) {
        }
    }

    public static final class GeometryBatchBuilder {
        private final ComputeTaskBuilder task;
        private final ByteBuffer geometryRecords;
        private final int recordCount;
        private final List<String> groupNames = new ArrayList<>();
        private final List<String> contentNames = new ArrayList<>();

        private GeometryBatchBuilder(ComputeTaskBuilder task, int recordCount) {
            this.task = task == null ? computeTask(0).buildStep(STEP_PUBLISH_EXTERNAL_GEOMETRY) : task;
            this.recordCount = Math.max(recordCount, 0);
            this.geometryRecords = allocateGeometryRecordBuffer(this.recordCount);
        }

        public ByteBuffer recordsBuffer() {
            return this.geometryRecords;
        }

        public GeometryBatchBuilder put(int index, GeometryRecord record) {
            if (index < 0 || index >= this.recordCount) {
                throw new IndexOutOfBoundsException(index);
            }
            putGeometryRecord(this.geometryRecords, index * GEOMETRY_RECORD_STRIDE, record);
            return this;
        }

        public int groupName(String name) {
            this.groupNames.add(name == null ? "" : name);
            return this.groupNames.size() - 1;
        }

        public int contentName(String name) {
            this.contentNames.add(name == null ? "" : name);
            return this.contentNames.size() - 1;
        }

        public GeometryBatch build() {
            return new GeometryBatch(
                this.task.build(),
                this.geometryRecords,
                0,
                GEOMETRY_RECORD_STRIDE,
                this.recordCount,
                this.groupNames.toArray(String[]::new),
                this.contentNames.toArray(String[]::new));
        }

        public long submit() {
            return submitGeometryBatch(build());
        }
    }

    public static final class GeometryRecordBuilder {
        private int sourceKind = SOURCE_EXTERNAL;
        private long sourceValue;
        private long version;
        private int format = FORMAT_EXTERNAL;
        private int domain = DOMAIN_ENTITY;
        private int routeMask = ROUTE_GEOMETRY;
        private int rayTracingMask = 1;
        private int coordinate = COORDINATE_CAMERA;
        private int stableHistoryId;
        private boolean hasObjectToWorld = true;
        private boolean hasPreviousObjectToWorld = true;
        private float[] objectToWorld;
        private float[] previousObjectToWorld;
        private long indexBufferHandle;
        private int indexBufferUsage = VK_BUFFER_USAGE_INDEX_BUFFER;
        private long indexBufferOffset;
        private long indexBufferRange = -1L;
        private long positionBufferHandle;
        private int positionBufferUsage = VK_BUFFER_USAGE_VERTEX_BUFFER | VK_BUFFER_USAGE_STORAGE_BUFFER;
        private long positionBufferOffset;
        private long positionBufferRange = -1L;
        private long materialBufferHandle;
        private int materialBufferUsage = VK_BUFFER_USAGE_STORAGE_BUFFER;
        private long materialBufferOffset;
        private long materialBufferRange = -1L;
        private int vertexCount;
        private int indexCount;
        private int indexType = INDEX_UINT32;
        private int geometryType = GEOMETRY_WORLD_SOLID;
        private int textureId;
        private int groupNameIndex = -1;
        private int contentNameIndex = -1;
        private int viewMask = VIEW_NONE;
        private int sourceTag = SOURCE_TAG_UNKNOWN;
        private int semanticFlags;
        private int budgetClass;
        private int[] rasterMetadata;
        private int postRenderFlag;

        private GeometryRecordBuilder() {
        }

        public GeometryRecordBuilder source(int kind, long value, long version) {
            this.sourceKind = kind;
            this.sourceValue = value;
            this.version = version;
            return this;
        }

        public GeometryRecordBuilder format(int value) {
            this.format = value;
            return this;
        }

        public GeometryRecordBuilder domain(int value) {
            this.domain = value;
            return this;
        }

        public GeometryRecordBuilder route(int value) {
            this.routeMask = value;
            return this;
        }

        public GeometryRecordBuilder route(String value) {
            return route(routeMask(value));
        }

        public GeometryRecordBuilder rayTracingMask(int value) {
            this.rayTracingMask = value;
            return this;
        }

        public GeometryRecordBuilder coordinate(int value) {
            this.coordinate = value;
            return this;
        }

        public GeometryRecordBuilder stableHistoryId(int value) {
            this.stableHistoryId = value;
            return this;
        }

        public GeometryRecordBuilder objectToWorld(float[] current, float[] previous) {
            this.objectToWorld = current;
            this.previousObjectToWorld = previous;
            this.hasObjectToWorld = current != null;
            this.hasPreviousObjectToWorld = previous != null;
            return this;
        }

        public GeometryRecordBuilder indexBuffer(long handle, long offset, long range) {
            this.indexBufferHandle = handle;
            this.indexBufferOffset = offset;
            this.indexBufferRange = range;
            return this;
        }

        public GeometryRecordBuilder positionBuffer(long handle, long offset, long range) {
            this.positionBufferHandle = handle;
            this.positionBufferOffset = offset;
            this.positionBufferRange = range;
            return this;
        }

        public GeometryRecordBuilder materialBuffer(long handle, long offset, long range) {
            this.materialBufferHandle = handle;
            this.materialBufferOffset = offset;
            this.materialBufferRange = range;
            return this;
        }

        public GeometryRecordBuilder counts(int vertices, int indices) {
            this.vertexCount = Math.max(vertices, 0);
            this.indexCount = Math.max(indices, 0);
            return this;
        }

        public GeometryRecordBuilder indexType(int value) {
            this.indexType = value;
            return this;
        }

        public GeometryRecordBuilder geometryType(int value) {
            this.geometryType = value;
            return this;
        }

        public GeometryRecordBuilder textureId(int value) {
            this.textureId = value;
            return this;
        }

        public GeometryRecordBuilder names(int groupIndex, int contentIndex) {
            this.groupNameIndex = groupIndex;
            this.contentNameIndex = contentIndex;
            return this;
        }

        public GeometryRecordBuilder sceneTag(int viewMask, int sourceTag,
            int semanticFlags, int budgetClass) {
            this.viewMask = viewMask;
            this.sourceTag = sourceTag;
            this.semanticFlags = semanticFlags;
            this.budgetClass = budgetClass;
            return this;
        }

        public GeometryRecordBuilder rasterMetadata(int[] value) {
            this.rasterMetadata = value;
            return this;
        }

        public GeometryRecordBuilder postRenderFlag(int value) {
            this.postRenderFlag = value;
            return this;
        }

        public GeometryRecord build() {
            return new GeometryRecord(
                this.sourceKind,
                this.sourceValue,
                this.version,
                this.format,
                this.domain,
                this.routeMask,
                this.rayTracingMask,
                this.coordinate,
                this.stableHistoryId,
                this.hasObjectToWorld,
                this.hasPreviousObjectToWorld,
                this.objectToWorld,
                this.previousObjectToWorld,
                this.indexBufferHandle,
                this.indexBufferUsage,
                this.indexBufferOffset,
                this.indexBufferRange,
                this.positionBufferHandle,
                this.positionBufferUsage,
                this.positionBufferOffset,
                this.positionBufferRange,
                this.materialBufferHandle,
                this.materialBufferUsage,
                this.materialBufferOffset,
                this.materialBufferRange,
                this.vertexCount,
                this.indexCount,
                this.indexType,
                this.geometryType,
                this.textureId,
                this.groupNameIndex,
                this.contentNameIndex,
                this.viewMask,
                this.sourceTag,
                this.semanticFlags,
                this.budgetClass,
                this.rasterMetadata,
                this.postRenderFlag);
        }
    }

    public static void putGeometryRecord(ByteBuffer buffer, int baseOffset, GeometryRecord record) {
        buffer.order(ByteOrder.nativeOrder());
        buffer.putInt(baseOffset, record.sourceKind);
        buffer.putLong(baseOffset + 8, record.sourceValue);
        buffer.putLong(baseOffset + 16, record.version);
        buffer.putInt(baseOffset + 24, record.format);
        buffer.putInt(baseOffset + 28, record.domain);
        buffer.putInt(baseOffset + 32, record.routeMask);
        buffer.putInt(baseOffset + 36, record.rayTracingMask);
        buffer.putInt(baseOffset + 40, record.coordinate);
        buffer.putInt(baseOffset + 44, record.stableHistoryId);
        buffer.putInt(baseOffset + 48, record.hasObjectToWorld ? 1 : 0);
        buffer.putInt(baseOffset + 52, record.hasPreviousObjectToWorld ? 1 : 0);
        putMat4(buffer, baseOffset + 56, record.objectToWorld);
        putMat4(buffer, baseOffset + 120, record.previousObjectToWorld);
        putBufferRef(buffer, baseOffset + 184, record.indexBufferHandle, record.indexBufferUsage,
            record.indexBufferOffset, record.indexBufferRange);
        putBufferRef(buffer, baseOffset + 216, record.positionBufferHandle,
            record.positionBufferUsage, record.positionBufferOffset, record.positionBufferRange);
        putBufferRef(buffer, baseOffset + 248, record.materialBufferHandle,
            record.materialBufferUsage, record.materialBufferOffset, record.materialBufferRange);
        buffer.putInt(baseOffset + 280, record.vertexCount);
        buffer.putInt(baseOffset + 284, record.indexCount);
        buffer.putInt(baseOffset + 288, record.indexType);
        buffer.putInt(baseOffset + 292, record.geometryType);
        buffer.putInt(baseOffset + 296, record.textureId);
        buffer.putInt(baseOffset + 300, record.groupNameIndex);
        buffer.putInt(baseOffset + 304, record.contentNameIndex);
        putSceneTag(buffer, baseOffset + 308, record.viewMask, record.sourceTag,
            record.semanticFlags, record.budgetClass);
        putRasterMetadata(buffer, baseOffset + 324, record.rasterMetadata);
        buffer.putInt(baseOffset + 380, record.postRenderFlag);
    }

    private static void putBufferRef(ByteBuffer buffer, int offset, long handle, int usage,
        long bufferOffset, long range) {
        buffer.putLong(offset, handle);
        buffer.putInt(offset + 8, usage);
        buffer.putLong(offset + 16, bufferOffset);
        buffer.putLong(offset + 24, range);
    }

    private static void putMat4(ByteBuffer buffer, int offset, float[] matrix) {
        for (int i = 0; i < 16; i++) {
            buffer.putFloat(offset + i * 4, matrix != null && i < matrix.length ? matrix[i] : (i % 5 == 0 ? 1.0F : 0.0F));
        }
    }

    private static void putSceneTag(ByteBuffer buffer, int offset, int viewMask, int sourceTag,
        int semanticFlags, int budgetClass) {
        buffer.putInt(offset, viewMask);
        buffer.putInt(offset + 4, sourceTag);
        buffer.putInt(offset + 8, semanticFlags);
        buffer.putInt(offset + 12, budgetClass);
    }

    private static void putRasterMetadata(ByteBuffer buffer, int offset, int[] metadata) {
        for (int i = 0; i < 14; i++) {
            buffer.putInt(offset + i * 4, metadata != null && i < metadata.length ? metadata[i] : 0);
        }
    }

    public record GeometryRecord(
        int sourceKind,
        long sourceValue,
        long version,
        int format,
        int domain,
        int routeMask,
        int rayTracingMask,
        int coordinate,
        int stableHistoryId,
        boolean hasObjectToWorld,
        boolean hasPreviousObjectToWorld,
        float[] objectToWorld,
        float[] previousObjectToWorld,
        long indexBufferHandle,
        int indexBufferUsage,
        long indexBufferOffset,
        long indexBufferRange,
        long positionBufferHandle,
        int positionBufferUsage,
        long positionBufferOffset,
        long positionBufferRange,
        long materialBufferHandle,
        int materialBufferUsage,
        long materialBufferOffset,
        long materialBufferRange,
        int vertexCount,
        int indexCount,
        int indexType,
        int geometryType,
        int textureId,
        int groupNameIndex,
        int contentNameIndex,
        int viewMask,
        int sourceTag,
        int semanticFlags,
        int budgetClass,
        int[] rasterMetadata,
        int postRenderFlag) {
    }
}
