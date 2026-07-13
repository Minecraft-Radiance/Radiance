package com.radiance.client.scene_import;

import java.util.List;

public final class SceneImportDebugSection {
    private static final long UPDATE_INTERVAL_NS = 500_000_000L;

    private static long nextUpdateNs;
    private static List<String> cachedLines = List.of();

    private SceneImportDebugSection() {
    }

    public static List<String> lines() {
        long now = System.nanoTime();
        if (now < nextUpdateNs) {
            return cachedLines;
        }
        nextUpdateNs = now + UPDATE_INTERVAL_NS;
        try {
            SceneImportDiagnostics.Snapshot state = SceneImportDiagnostics.snapshot();
            String status = state.healthy() ? "ok" : "error";
            cachedLines = List.of(
                "status " + status + " serial " + state.serial()
                    + " latency p95 " + state.totalLatencyP95Ms() + "ms",
                "resources g " + state.geometries() + "+" + state.pendingGeometries()
                    + " a " + state.accelerations() + "+" + state.pendingAccelerations()
                    + " post " + state.posts() + "+" + state.pendingPosts(),
                "backlog task " + state.pendingTasks() + " blas " + state.pendingBlasBatches()
                    + " async " + state.pendingAsyncCompletions() + " oldest "
                    + state.oldestTaskAgeMs() + "ms errors " + state.failedResources()
                    + "/" + state.failedTasks() + "/" + state.entityErrors());
        } catch (Throwable error) {
            cachedLines = List.of("status unavailable");
        }
        return cachedLines;
    }
}
