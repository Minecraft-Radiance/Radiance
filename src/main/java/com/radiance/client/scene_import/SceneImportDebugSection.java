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
        ArrayList<String> lines = new ArrayList<>(16);
        SceneImportDiagnostics.EntityPipelineStats entity = diagnostics.entityPipeline();
        SceneImportDiagnostics.EntityImportStats entityImport = diagnostics.entityImport();
        SceneImportDiagnostics.ChunkImportStats chunkImport = diagnostics.chunkImport();
        SceneImportDiagnostics.SchedulerStats scheduler = diagnostics.scheduler();
        SceneImportDiagnostics.CpuStats cpu = diagnostics.cpu();
        SceneImportDiagnostics.BlasLatencyStats blasLatency = diagnostics.blasLatency();
        SceneImportDiagnostics.SchedulerWindowStats schedulerWindow = diagnostics.schedulerWindow();
        SceneImportDiagnostics.BlasLaneHealthStats blasLaneHealth = diagnostics.blasLaneHealth();
        SceneImportDiagnostics.RealtimeCpuPublishStats realtimeCpuPublish =
            diagnostics.realtimeCpuPublish();
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
            "work q%d def %d/%d last c%d b%d(%d/%du) pub%d st%d f%d async %d tl%d",
            diagnostics.pendingTasks(),
            diagnostics.lastBudgetDeferredTasks(),
            diagnostics.lastBudgetDeferredBlasBatches(),
            diagnostics.lastComputeTasks(),
            diagnostics.lastBlasTasks(),
            diagnostics.lastBlasBuildBatches(),
            diagnostics.lastBlasUpdateBatches(),
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
            "ent import f%d q%d b%d in %d/%d last %d/%d src c/p/n/r/d %d/%d/%d/%d/%d",
            entityImport.frameSerial(),
            entityImport.queueCallsThisFrame(),
            entityImport.buildCalls(),
            entityImport.inputEntitiesThisFrame(),
            entityImport.inputPostsThisFrame(),
            entityImport.lastInputEntities(),
            entityImport.lastInputPosts(),
            entityImport.lastCurrentSources(),
            entityImport.lastPreviousSources(),
            entityImport.lastCreatedSources(),
            entityImport.lastRetainedSources(),
            entityImport.lastDeletedSources()));
        lines.add(String.format(
            "ent dirty g/def/s/m/i/d %d/%d/%d/%d/%d/%d empty %d churn %d",
            entityImport.dirtyGeometry(),
            entityImport.dirtyDeform(),
            entityImport.dirtySurface(),
            entityImport.dirtyMaterial(),
            entityImport.dirtyInstance(),
            entityImport.dirtyDraw(),
            entityImport.emptyBuildFrames(),
            entityImport.sourceIdChurn()));
        lines.add(String.format(
            "chk import dirty %d submit %d/%d retry %d ready %d block ng/gp/ap/am/ar %d/%d/%d/%d/%d curar %d",
            chunkImport.dirtyGeometry(),
            chunkImport.submits(),
            chunkImport.atomicReplaceSubmits(),
            chunkImport.retriesSubmitted(),
            chunkImport.readyChecks(),
            chunkImport.blockedNoGeometry(),
            chunkImport.blockedGeometryPending(),
            chunkImport.blockedAccelerationPending(),
            chunkImport.blockedAccelerationMissing(),
            chunkImport.blockedAtomicReplacePending(),
            chunkImport.renderingReadyAtomicReplace()));
        lines.add(String.format(
            "sched blas pend %d bud %d/%d/%d/%d infl %d/%d age %d ramp %d press %d thr %d comp %d/%dms",
            scheduler.pendingBlasBatches(),
            scheduler.lastBlasBudget(),
            scheduler.lastImportantBlasBudget(),
            scheduler.lastNormalBlasBudget(),
            scheduler.lastBackgroundBlasBudget(),
            scheduler.lastInFlightSubmits(),
            scheduler.lastInFlightBlasBatches(),
            scheduler.lastOldestInFlightAgeMs(),
            scheduler.rampBlasBatches(),
            scheduler.pressureLevel(),
            scheduler.lastThrottleReason(),
            scheduler.lastCompletionLatencyMs(),
            scheduler.smoothedCompletionLatencyMs()));
        lines.add(String.format(
            "sched win tgt/avail/cap/head %d/%d/%d/%d pend/done/bms %d/%d/%d",
            schedulerWindow.targetInFlight(),
            schedulerWindow.available(),
            schedulerWindow.perSubmitCap(),
            schedulerWindow.criticalHeadroom(),
            schedulerWindow.pendingBatches(),
            schedulerWindow.completedBatches(),
            schedulerWindow.lastBatchLatencyMs()));
        lines.add(String.format(
            "blas lane pend rt/imp/nrm/oth %d/%d/%d/%d last %d/%d/%d/%d",
            blasLatency.pendingRealtime(),
            blasLatency.pendingImportantChunk(),
            blasLatency.pendingNormalChunk(),
            blasLatency.pendingOther(),
            blasLatency.lastRealtime(),
            blasLatency.lastImportantChunk(),
            blasLatency.lastNormalChunk(),
            blasLatency.lastOther()));
        lines.add(String.format(
            "blas lane debt rt/imp/nrm/oth %d/%d/%d/%d age %d/%d/%d/%d",
            blasLaneHealth.debtRealtime(),
            blasLaneHealth.debtImportantChunk(),
            blasLaneHealth.debtNormalChunk(),
            blasLaneHealth.debtOther(),
            blasLaneHealth.oldestRealtimeMs(),
            blasLaneHealth.oldestImportantChunkMs(),
            blasLaneHealth.oldestNormalChunkMs(),
            blasLaneHealth.oldestOtherMs()));
        lines.add(String.format(
            "rtpub pend/proc/coal/def/us %d/%d/%d/%d/%d",
            realtimeCpuPublish.pending(),
            realtimeCpuPublish.processed(),
            realtimeCpuPublish.coalesced(),
            realtimeCpuPublish.deferred(),
            realtimeCpuPublish.cpuUs()));
        lines.add(String.format(
            "cpu us total/poll/select/pub/blas %d/%d/%d/%d/%d",
            cpu.totalUs(),
            cpu.pollBudgetUs(),
            cpu.taskSelectUs(),
            cpu.taskPublishUs(),
            cpu.blasSubmitUs()));
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
