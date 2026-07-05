package com.radiance.client.scene_import;

import com.radiance.client.pipeline.Pipeline;

public final class SceneImportDiagnostics {
    public static final int FIELD_COUNT = 108;
    public static final int LATENCY_BASE = 27;
    public static final int PRIORITY_BASE = 47;
    public static final int GROUP_BASE = 59;
    public static final int TLAS_INSTANCE_BASE = 89;
    public static final int ENTITY_PIPELINE_BASE = 94;
    public static final int LATENCY_STRIDE = 4;
    public static final int PRIORITY_STRIDE = 4;
    public static final int GROUP_STRIDE = 5;

    public static final int PRIORITY_BACKGROUND = 0;
    public static final int PRIORITY_NORMAL = 1;
    public static final int PRIORITY_IMPORTANT = 2;

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

    private SceneImportDiagnostics() {
    }

    public static long[] raw() {
        return Pipeline.getSceneImportDiagnosticsV2Snapshot();
    }

    public static Snapshot snapshot() {
        long[] values = raw();
        LatencyStats[] latencies = new LatencyStats[LATENCY_COUNT];
        for (int i = 0; i < latencies.length; i++) {
            int base = LATENCY_BASE + i * LATENCY_STRIDE;
            latencies[i] = new LatencyStats(
                value(values, base),
                value(values, base + 1),
                value(values, base + 2),
                value(values, base + 3));
        }

        PriorityStats[] priorities = new PriorityStats[] {
            priority(values, PRIORITY_BACKGROUND),
            priority(values, PRIORITY_NORMAL),
            priority(values, PRIORITY_IMPORTANT)
        };

        SourceGroupStats[] groups = new SourceGroupStats[GROUP_COUNT];
        for (int i = 0; i < groups.length; i++) {
            int base = GROUP_BASE + i * GROUP_STRIDE;
            groups[i] = new SourceGroupStats(
                value(values, base),
                value(values, base + 1),
                value(values, base + 2),
                value(values, base + 3),
                value(values, base + 4));
        }

        return new Snapshot(
            value(values, 0),
            value(values, 1),
            value(values, 2),
            value(values, 3),
            value(values, 4),
            value(values, 5),
            value(values, 6),
            value(values, 7),
            value(values, 8),
            value(values, 9),
            value(values, 10),
            value(values, 11),
            value(values, 12),
            value(values, 13) != 0L,
            value(values, 14),
            value(values, 15),
            value(values, 16),
            value(values, 17),
            value(values, 18),
            value(values, 19),
            value(values, 20),
            value(values, 21),
            value(values, 22),
            value(values, 23),
            value(values, 24),
            value(values, 25),
            value(values, 26),
            value(values, TLAS_INSTANCE_BASE),
            value(values, TLAS_INSTANCE_BASE + 1),
            value(values, TLAS_INSTANCE_BASE + 2),
            value(values, TLAS_INSTANCE_BASE + 3),
            value(values, TLAS_INSTANCE_BASE + 4),
            entityPipeline(values),
            latencies,
            priorities,
            groups);
    }

    private static PriorityStats priority(long[] values, int priority) {
        int base = PRIORITY_BASE + priority * PRIORITY_STRIDE;
        return new PriorityStats(
            value(values, base),
            value(values, base + 1),
            value(values, base + 2),
            value(values, base + 3));
    }

    private static EntityPipelineStats entityPipeline(long[] values) {
        int base = ENTITY_PIPELINE_BASE;
        return new EntityPipelineStats(
            value(values, base),
            value(values, base + 1),
            value(values, base + 2),
            value(values, base + 3),
            value(values, base + 4),
            value(values, base + 5),
            value(values, base + 6),
            value(values, base + 7),
            value(values, base + 8),
            value(values, base + 9),
            value(values, base + 10),
            value(values, base + 11),
            value(values, base + 12),
            value(values, base + 13));
    }

    private static long value(long[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : 0L;
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

    public record Snapshot(
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
}
