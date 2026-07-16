package com.radiance.client.scene_import;

import com.radiance.client.pipeline.Pipeline;

public final class SceneImportDiagnostics {
    public static final int SCHEMA = 1;
    public static final int FIELD_COUNT = 18;

    private SceneImportDiagnostics() {
    }

    public static Snapshot snapshot() {
        long[] values = Pipeline.getSceneImportDiagnosticsSnapshot();
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
            value(values, 13),
            value(values, 14),
            value(values, 15),
            value(values, 16),
            value(values, 17));
    }

    private static long value(long[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : 0L;
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
        long pendingBlasBatches,
        long pendingAsyncCompletions,
        long oldestTaskAgeMs,
        long totalLatencyP95Ms,
        long droppedStaleTasks,
        long failedTasks,
        long entityErrors) {

        public boolean healthy() {
            return failedResources == 0 && failedTasks == 0 && entityErrors == 0;
        }
    }
}
