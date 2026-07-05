package com.radiance.client.scene_import;

import java.util.ArrayList;
import java.util.List;

public final class SceneImportDebugSection {
    private static final long UPDATE_INTERVAL_NS = 250_000_000L;

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
            cachedLines = format(SceneImportDiagnostics.snapshot());
        } catch (Throwable error) {
            cachedLines = List.of("unavailable: " + error.getClass().getSimpleName());
        }
        return cachedLines;
    }

    private static List<String> format(SceneImportDiagnostics.Snapshot diagnostics) {
        ArrayList<String> lines = new ArrayList<>(9);
        SceneImportDiagnostics.EntityPipelineStats entity = diagnostics.entityPipeline();
        lines.add(String.format(
            "res g %d/%dp a %d/%dp post %d/%dp err f%d s%d",
            diagnostics.geometries(),
            diagnostics.pendingGeometries(),
            diagnostics.accelerations(),
            diagnostics.pendingAccelerations(),
            diagnostics.posts(),
            diagnostics.pendingPosts(),
            diagnostics.failedResources(),
            diagnostics.staleResources()));
        lines.add(String.format(
            "work q%d def %d/%d last c%d b%d pub%d st%d f%d async %d tl%d",
            diagnostics.pendingTasks(),
            diagnostics.lastBudgetDeferredTasks(),
            diagnostics.lastBudgetDeferredBlasBatches(),
            diagnostics.lastComputeTasks(),
            diagnostics.lastBlasTasks(),
            diagnostics.lastPublishTasks(),
            diagnostics.lastDroppedStaleTasks(),
            diagnostics.lastFailedTasks(),
            diagnostics.pendingAsyncCompletions(),
            diagnostics.lastAsyncTimelineValue()));
        lines.add(String.format(
            "lat ms tot %s q %s gpu %s pub %s frm %s",
            latency(diagnostics.latency(SceneImportDiagnostics.LATENCY_TOTAL)),
            latency(diagnostics.latency(SceneImportDiagnostics.LATENCY_QUEUE)),
            latency(diagnostics.latency(SceneImportDiagnostics.LATENCY_GPU)),
            latency(diagnostics.latency(SceneImportDiagnostics.LATENCY_PUBLISH)),
            latency(diagnostics.latency(SceneImportDiagnostics.LATENCY_FRAME))));
        lines.add(String.format(
            "src pub/acc chk %s imp %s ent %s b3d %s post %s ext %s",
            group(diagnostics.group(SceneImportDiagnostics.GROUP_CHUNK)),
            group(diagnostics.group(SceneImportDiagnostics.GROUP_IMPORTANT_CHUNK)),
            group(diagnostics.group(SceneImportDiagnostics.GROUP_ENTITY)),
            group(diagnostics.group(SceneImportDiagnostics.GROUP_BLAZE3D)),
            group(diagnostics.group(SceneImportDiagnostics.GROUP_NATIVE_POST)),
            group(diagnostics.group(SceneImportDiagnostics.GROUP_EXTERNAL))));
        lines.add(String.format(
            "prio a/s/d imp %s norm %s bg %s",
            priority(diagnostics.priority(SceneImportDiagnostics.PRIORITY_IMPORTANT)),
            priority(diagnostics.priority(SceneImportDiagnostics.PRIORITY_NORMAL)),
            priority(diagnostics.priority(SceneImportDiagnostics.PRIORITY_BACKGROUND))));
        lines.add(String.format(
            "tlas all/chk/ent/oth %d/%d/%d/%d s%d",
            diagnostics.tlasInstances(),
            diagnostics.tlasChunkInstances(),
            diagnostics.tlasEntityInstances(),
            diagnostics.tlasOtherInstances(),
            diagnostics.tlasInstanceSerial()));
        lines.add(String.format(
            "ent cur g r/p/s/f %d/%d/%d/%d a r/p/s/f %d/%d/%d/%d miss/v/null/snap %d/%d/%d/%d",
            entity.geometryReady(),
            entity.geometryPending(),
            entity.geometryStale(),
            entity.geometryFailed(),
            entity.accelerationReady(),
            entity.accelerationPending(),
            entity.accelerationStale(),
            entity.accelerationFailed(),
            entity.accelerationMissingForReadyGeometry(),
            entity.accelerationVersionMismatch(),
            entity.accelerationBlasNull(),
            entity.snapshotReadyAcceleration()));
        lines.add(String.format(
            "ent blas qsrc %d asyncsrc %d",
            entity.pendingBlasSources(),
            entity.asyncReadyBlasSources()));
        lines.add(String.format(
            "abi k/r/t %d/%d/%d serial %d",
            diagnostics.registeredKernels(),
            diagnostics.registeredPipelineRules(),
            diagnostics.registeredTransformRecipes(),
            diagnostics.serial()));
        return lines;
    }

    private static String latency(SceneImportDiagnostics.LatencyStats stats) {
        return stats.p50Ms() + "/" + stats.p95Ms() + "/" + stats.maxMs();
    }

    private static String group(SceneImportDiagnostics.SourceGroupStats stats) {
        String value = stats.published() + "/" + stats.accepted();
        long errors = stats.stale() + stats.failed();
        if (errors != 0) {
            value += "!" + stats.stale() + "/" + stats.failed();
        }
        if (stats.latestTotalMs() != 0) {
            value += "@" + stats.latestTotalMs();
        }
        return value;
    }

    private static String priority(SceneImportDiagnostics.PriorityStats stats) {
        return stats.accepted() + "/" + stats.submitted() + "/" + stats.deferred();
    }
}
