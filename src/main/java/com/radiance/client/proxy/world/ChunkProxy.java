package com.radiance.client.proxy.world;

import static com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS;
import static org.lwjgl.system.MemoryUtil.memAddress;
import com.radiance.client.constant.Constants;
import com.radiance.client.vista.ChunkTerrainBuilder;
import com.radiance.client.vista.Vista;
import com.radiance.client.vista.VistaTerrain;
import com.radiance.client.option.Options;
import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.client.vertex.TransferredBuildBuffer;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderBuiltChunkExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import java.nio.ByteBuffer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;
import com.radiance.client.profiler.Profiler;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import com.mojang.blaze3d.vertex.VertexSorting;

public class ChunkProxy {

    public static final SectionRenderDispatcher.CompiledSection PROCESSED = new SectionRenderDispatcher.CompiledSection() {
        @Override
        public boolean facesCanSeeEachother(Direction from, Direction to) {
            return false;
        }
    };
    public static final SectionRenderDispatcher.CompiledSection TERRAIN_EMPTY = new SectionRenderDispatcher.CompiledSection() {
        @Override
        public boolean facesCanSeeEachother(Direction from, Direction to) {
            return true;
        }
    };
    private static final Map<Integer, SectionRenderDispatcher.RenderSection> rebuildQueue = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field> visibleChunkFieldCache = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<SectionRenderDispatcher.RenderSection> backgroundRebuildQueue =
        new ConcurrentLinkedQueue<>();
    private static final java.util.Set<Integer> forcedRebuildIndices = ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Integer> backgroundRebuildIndices = ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Integer> inFlightRebuildIndices = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger importantRebuildsInFlight = new AtomicInteger();
    private static final AtomicInteger normalRebuildsInFlight = new AtomicInteger();
    private static final AtomicInteger chunkRebuildThreadIds = new AtomicInteger();
    private static final List<Future<?>> rebuildTasks = new ArrayList<>();
    private static final Object rebuildTasksLock = new Object();
    private static final Object chunkLifecycleLock = new Object();
    private static final Map<Integer, ChunkSlot> chunkSlots = new HashMap<>();
    private static long nextChunkGeneration;
    private static final ThreadLocal<IntOpenHashSet> candidateIndexScratch =
        ThreadLocal.withInitial(() -> new IntOpenHashSet(1024));
    private static ViewArea currentStorage = null;
    private static int storageSizeX;
    private static int storageSizeZ;
    private static boolean pendingRebuildAll = false;
    private static int chunkRebuildThreadCount = getChunkRebuildThreadCount();
    private static final int IMPORTANT_CHUNK_REBUILD_THREAD_COUNT = 1;
    private static int normalChunkRebuildThreadCount = Math.max(1,
        chunkRebuildThreadCount - IMPORTANT_CHUNK_REBUILD_THREAD_COUNT);
    private static final int NEARBY_COVERAGE_SAMPLE_INTERVAL_FRAMES = 10;
    private static final int NEARBY_COVERAGE_INNER_RADIUS_SECTIONS = 2;
    private static final int NEARBY_COVERAGE_NEAR_RADIUS_SECTIONS = 4;
    private static final int RAY_TRACING_NEAR_HORIZONTAL_RADIUS_SECTIONS =
        NEARBY_COVERAGE_NEAR_RADIUS_SECTIONS;
    private static final int RAY_TRACING_NEAR_VERTICAL_RADIUS_SECTIONS = 2;
    private static final int BACKGROUND_REBUILD_SCAN_LIMIT_PER_FRAME = 256;
    private static final int STEADY_NORMAL_SUBMISSION_INTERVAL_FRAMES = 3;
    private static final int INITIAL_NEARBY_COVERAGE_BOOST_FRAMES = 240;
    private static final int NEARBY_COVERAGE_BOOST_FRAMES_AFTER_MISSING = 180;
    private static int rebuildFrameCounter = 0;
    private static int emptySectionScanIndex = 0;
    private static int nearbyCoverageFrameCounter = 0;
    private static long lastNearbyCoverageSampleNanos = 0L;
    private static double lastNearbyCoverageCameraX = 0.0;
    private static double lastNearbyCoverageCameraZ = 0.0;
    private static boolean hasLastNearbyCoverageCameraPos = false;
    private static int innerMissingStreakSamples = 0;
    private static int nearMissingStreakSamples = 0;
    private static int nearbyCoverageBoostFramesRemaining = INITIAL_NEARBY_COVERAGE_BOOST_FRAMES;
    private static final ExecutorService
        importantChunkRebuildExecutor =
        Executors.newFixedThreadPool(IMPORTANT_CHUNK_REBUILD_THREAD_COUNT, r -> {
            return newChunkRebuildThread(r, "Radiance Important Chunk Rebuild",
                Thread.NORM_PRIORITY);
        });
    private static final ThreadLocal<SectionBufferBuilderPack>
        blockBufferAllocatorStorage =
        ThreadLocal.withInitial(SectionBufferBuilderPack::new);
    public static int builtChunkCount = 0;
    private static ExecutorService backgroundChunkRebuildExecutor = Executors.newFixedThreadPool(
        normalChunkRebuildThreadCount, r -> {
            return newChunkRebuildThread(r, "Radiance Chunk Rebuild", Thread.NORM_PRIORITY - 2);
        });

    public static native void initNative(int numChunks, int sizeX, int sizeY, int sizeZ,
        int bottomSectionCoord);

    public static native void updateSectionPosNative(int sectionX, int sectionY, int sectionZ);

    public static void init(int numChunks, int sizeX, int sizeY, int sizeZ,
        int bottomSectionCoord) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.ChunkProxy.init");
        }
        try {
            clear();
            storageSizeX = sizeX;
            storageSizeZ = sizeZ;
            initNative(numChunks, sizeX, sizeY, sizeZ, bottomSectionCoord);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static void updateSectionPos(SectionPos sectionPos) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.ChunkProxy.updateSectionPos");
        }
        try {
            updateSectionPosNative(sectionPos.x(), sectionPos.y(),
                sectionPos.z());
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static void setStorage(ViewArea storage) {
        currentStorage = storage;
        if (currentStorage != null && pendingRebuildAll) {
            pendingRebuildAll = false;
            queueRebuildAll(currentStorage);
        }
    }

    public static List<SectionRenderDispatcher.RenderSection> extractVisibleChunks(Iterable<?> chunkInfos) {
        List<SectionRenderDispatcher.RenderSection> visibleChunks = new ArrayList<>();
        if (chunkInfos == null) {
            return visibleChunks;
        }

        for (Object chunkInfo : chunkInfos) {
            if (chunkInfo == null) {
                continue;
            }

            Field chunkField = visibleChunkFieldCache.get(chunkInfo.getClass());
            if (chunkField == null) {
                chunkField = findVisibleChunkField(chunkInfo.getClass());
                Field previousField = visibleChunkFieldCache.putIfAbsent(chunkInfo.getClass(),
                    chunkField);
                if (previousField != null) {
                    chunkField = previousField;
                }
            }

            try {
                SectionRenderDispatcher.RenderSection builtChunk =
                    (SectionRenderDispatcher.RenderSection) chunkField.get(chunkInfo);
                if (builtChunk != null) {
                    visibleChunks.add(builtChunk);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Unable to read the visible chunk", e);
            }
        }
        return visibleChunks;
    }

    private static Field findVisibleChunkField(Class<?> infoClass) {
        for (Class<?> type = infoClass; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != SectionRenderDispatcher.RenderSection.class) {
                    continue;
                }
                field.setAccessible(true);
                return field;
            }
        }
        throw new IllegalStateException("No built chunk field found on " + infoClass.getName());
    }

    private static int getChunkRebuildThreadCount() {
        int expectedBufferTotal = RenderType.chunkBufferLayers()
            .stream()
            .mapToInt(RenderType::bufferSize)
            .sum();
        int memoryLimited = Math.max(1,
            (int) (Runtime.getRuntime().maxMemory() * 0.3) / (expectedBufferTotal * 4) - 1);
        int userThreads = Options.chunkBuildingThreads;
        return Math.max(2,
            Math.min(userThreads, Math.min(Options.getMaxChunkBuildingThreads(), memoryLimited)));
    }

    public static AutoCloseable scopedBlockBufferAllocatorStorage() {
        final SectionBufferBuilderPack storage = blockBufferAllocatorStorage.get();
        storage.discardAll();
        return storage::clearAll;
    }

    public static void clear() {
        if (Profiler.ENABLED) {
            Profiler.enter("java.ChunkProxy.clear");
        }
        try {
            synchronized (chunkLifecycleLock) {
                chunkSlots.clear();
                BlockEntitySectionCache.clear();
            }
            waitAllImportantChunkRebuild();

            backgroundChunkRebuildExecutor.shutdown();
            try {
                backgroundChunkRebuildExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            chunkRebuildThreadCount = getChunkRebuildThreadCount();
            normalChunkRebuildThreadCount = Math.max(1,
                chunkRebuildThreadCount - IMPORTANT_CHUNK_REBUILD_THREAD_COUNT);
            backgroundChunkRebuildExecutor = Executors.newFixedThreadPool(normalChunkRebuildThreadCount,
                r -> {
                    return newChunkRebuildThread(r, "Radiance Chunk Rebuild", Thread.NORM_PRIORITY - 2);
                });

            rebuildQueue.clear();
            backgroundRebuildQueue.clear();
            forcedRebuildIndices.clear();
            backgroundRebuildIndices.clear();
            inFlightRebuildIndices.clear();
            importantRebuildsInFlight.set(0);
            normalRebuildsInFlight.set(0);
            synchronized (rebuildTasksLock) {
                rebuildTasks.clear();
            }
            currentStorage = null;
            storageSizeX = 0;
            storageSizeZ = 0;
            emptySectionScanIndex = 0;
            pendingRebuildAll = false;
            resetNearbyCoverageMetrics();
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static void enqueueRebuild(SectionRenderDispatcher.RenderSection chunk) {
        rebuildQueue.put(chunk.index, chunk);
        if (backgroundRebuildIndices.add(chunk.index)) {
            backgroundRebuildQueue.offer(chunk);
        }
    }

    public static void rebuildAll() {
        if (Profiler.ENABLED) {
            Profiler.enter("java.ChunkProxy.rebuildAll");
        }
        try {
            if (currentStorage == null || currentStorage.sections == null) {
                pendingRebuildAll = true;
                return;
            }

            queueRebuildAll(currentStorage);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static void queueRebuildAll(ViewArea storage) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.ChunkProxy.queueRebuildAll");
        }
        try {
            if (storage == null || storage.sections == null) {
                pendingRebuildAll = true;
                return;
            }

            for (SectionRenderDispatcher.RenderSection builtChunk : storage.sections) {
                if (builtChunk == null) {
                    continue;
                }
                forcedRebuildIndices.add(builtChunk.index);
                builtChunk.setDirty(true);
                enqueueRebuild(builtChunk);
            }
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static void rebuild(Camera camera) {
        rebuild(camera, null, null);
    }

    public static void rebuild(Camera camera, List<SectionRenderDispatcher.RenderSection> visibleChunks,
        List<SectionRenderDispatcher.RenderSection> nearbyChunks) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.ChunkProxy.rebuild");
        }
        try {
            BlockPos blockPos = camera.getBlockPosition();
            boolean completeColumns = Vista.distanceBlocks() > 0;
            if (completeColumns) {
                completeEmptySections();
                discardCompletedBackgroundRebuilds();
            }
            int rebuildFrame = rebuildFrameCounter++;
            boolean nearbyCoverageBoost = consumeNearbyCoverageBoost();
            int normalInFlightLimit = normalRebuildInFlightLimit();
            int importantInFlightLimit = importantRebuildInFlightLimit();
            int normalSubmissionBudget = normalRebuildSubmissionBudgetPerFrame(nearbyCoverageBoost);
            if (!nearbyCoverageBoost
                && rebuildFrame % STEADY_NORMAL_SUBMISSION_INTERVAL_FRAMES != 0) {
                normalSubmissionBudget = 0;
            }
            int remainingNormalSubmissions = Math.min(normalSubmissionBudget,
                Math.max(0, normalInFlightLimit - normalRebuildsInFlight.get()));
            int remainingImportantSubmissions = Math.min(importantRebuildSubmissionBudgetPerFrame(),
                Math.max(0, importantInFlightLimit - importantRebuildsInFlight.get()));
            int candidateLimit = remainingNormalSubmissions + remainingImportantSubmissions;
            int staleCount = 0;
            int notReadyCount = 0;
            int skippedInFlightCount = 0;
            int scannedCount = 0;
            int visibleScannedCount = 0;
            int nearbyScannedCount = 0;
            int rayTracingNearbyScannedCount = 0;
            int backgroundScannedCount = 0;
            int eligibleCount = 0;
            int budgetSkippedCount = 0;
            int submittedTotal = 0;
            int submittedImportant = 0;
            int submittedNormal = 0;
            int submittedImportantExecutor = 0;
            int submittedNormalExecutor = 0;
            int foregroundCandidateCount = 0;
            int rayTracingNearbyCandidateCount = 0;
            int backgroundCandidateCount = 0;
            List<RebuildCandidate> foregroundCandidates = new ArrayList<>(candidateLimit);
            List<RebuildCandidate> rayTracingNearbyCandidates = new ArrayList<>(candidateLimit);
            List<RebuildCandidate> backgroundCandidates = new ArrayList<>(1);
            List<SectionRenderDispatcher.RenderSection> polledBackgroundChunks = new ArrayList<>();
            IntOpenHashSet candidateIndices = candidateIndexScratch.get();
            candidateIndices.clear();
            RebuildScanStats foregroundScanStats = new RebuildScanStats();
            RebuildScanStats rayTracingNearbyScanStats = new RebuildScanStats();
            RebuildScanStats backgroundScanStats = new RebuildScanStats();
            SubmissionBudget submissionBudget = new SubmissionBudget(remainingNormalSubmissions,
                remainingImportantSubmissions);
            SubmissionStats submissionStats = new SubmissionStats();

            if (candidateLimit > 0) {
                if (completeColumns) {
                    rayTracingNearbyScannedCount = collectVistaRebuildCandidates(blockPos,
                        rayTracingNearbyCandidates, candidateLimit, candidateIndices,
                        rayTracingNearbyScanStats);
                    submitRebuildCandidates(rayTracingNearbyCandidates, submissionBudget,
                        submissionStats, true, Integer.MAX_VALUE);
                } else {
                    if (visibleChunks != null) {
                        for (SectionRenderDispatcher.RenderSection builtChunk : visibleChunks) {
                            visibleScannedCount++;
                            collectRebuildCandidate(builtChunk, blockPos, foregroundCandidates,
                                candidateLimit, candidateIndices, foregroundScanStats);
                        }
                    }
                    if (nearbyChunks != null) {
                        for (SectionRenderDispatcher.RenderSection builtChunk : nearbyChunks) {
                            nearbyScannedCount++;
                            collectRebuildCandidate(builtChunk, blockPos, foregroundCandidates,
                                candidateLimit, candidateIndices, foregroundScanStats);
                        }
                    }
                    rayTracingNearbyScannedCount = collectRayTracingNearbyRebuildCandidates(camera,
                        blockPos, rayTracingNearbyCandidates, candidateLimit, candidateIndices,
                        rayTracingNearbyScanStats);

                    int rayTracingNearbyReservedSubmissions =
                        rayTracingNearbyCandidates.isEmpty() || candidateLimit <= 1 ? 0
                            : nearbyCoverageBoost
                                ? Math.min(candidateLimit - 1, Math.max(2, (candidateLimit + 1) / 2))
                                : 1;
                    submitRebuildCandidates(foregroundCandidates, submissionBudget, submissionStats,
                        nearbyCoverageBoost, candidateLimit - rayTracingNearbyReservedSubmissions);
                    submitRebuildCandidates(rayTracingNearbyCandidates, submissionBudget, submissionStats,
                        nearbyCoverageBoost, Integer.MAX_VALUE);
                }

                int backgroundCandidateLimit =
                    completeColumns || backgroundRebuildQueue.isEmpty()
                        || submissionBudget.remainingNormalSubmissions <= 0
                        ? 0 : 1;
                if (backgroundCandidateLimit > 0) {
                    for (int i = 0; i < BACKGROUND_REBUILD_SCAN_LIMIT_PER_FRAME
                        && backgroundCandidates.size() < backgroundCandidateLimit; i++) {
                        SectionRenderDispatcher.RenderSection builtChunk = backgroundRebuildQueue.poll();
                        if (builtChunk == null) {
                            break;
                        }
                        if (!backgroundRebuildIndices.remove(builtChunk.index)) {
                            continue;
                        }
                        polledBackgroundChunks.add(builtChunk);
                        backgroundScannedCount++;
                        collectRebuildCandidate(builtChunk, blockPos, backgroundCandidates,
                            backgroundCandidateLimit, candidateIndices, backgroundScanStats);
                    }
                    submitRebuildCandidates(backgroundCandidates, submissionBudget, submissionStats,
                        false, backgroundCandidateLimit);
                }

                scannedCount = visibleScannedCount + nearbyScannedCount + rayTracingNearbyScannedCount
                    + backgroundScannedCount;
                staleCount = foregroundScanStats.staleCount + rayTracingNearbyScanStats.staleCount
                    + backgroundScanStats.staleCount;
                notReadyCount = foregroundScanStats.notReadyCount + rayTracingNearbyScanStats.notReadyCount
                    + backgroundScanStats.notReadyCount;
                skippedInFlightCount = foregroundScanStats.skippedInFlightCount
                    + rayTracingNearbyScanStats.skippedInFlightCount
                    + backgroundScanStats.skippedInFlightCount;
                eligibleCount = foregroundScanStats.eligibleCount
                    + rayTracingNearbyScanStats.eligibleCount + backgroundScanStats.eligibleCount;

                skippedInFlightCount += submissionStats.skippedInFlightCount;
                submittedTotal = submissionStats.submittedTotal;
                submittedImportant = submissionStats.submittedImportant;
                submittedNormal = submissionStats.submittedNormal;
                submittedImportantExecutor = submissionStats.submittedImportantExecutor;
                submittedNormalExecutor = submissionStats.submittedNormalExecutor;
                foregroundCandidateCount = foregroundCandidates.size();
                rayTracingNearbyCandidateCount = rayTracingNearbyCandidates.size();
                backgroundCandidateCount = backgroundCandidates.size();
                budgetSkippedCount = Math.max(0, foregroundScanStats.eligibleCount - foregroundCandidates.size())
                    + Math.max(0, rayTracingNearbyScanStats.eligibleCount - rayTracingNearbyCandidates.size())
                    + Math.max(0, backgroundScanStats.eligibleCount - backgroundCandidates.size())
                    + submissionStats.budgetSkippedCount;
                for (SectionRenderDispatcher.RenderSection builtChunk : polledBackgroundChunks) {
                    int chunkIndex = builtChunk.index;
                    if (rebuildQueue.containsKey(chunkIndex) && builtChunk.isDirty()) {
                        enqueueBackgroundRebuild(builtChunk);
                    }
                }
            }

            if (Profiler.ENABLED) {
                Profiler.metric("java.chunk_rebuild.queue_size", rebuildQueue.size());
                Profiler.metric("java.chunk_rebuild.scanned_count", scannedCount);
                Profiler.metric("java.chunk_rebuild.visible_scanned_count", visibleScannedCount);
                Profiler.metric("java.chunk_rebuild.nearby_scanned_count", nearbyScannedCount);
                Profiler.metric("java.chunk_rebuild.ray_tracing_nearby_scanned_count",
                    rayTracingNearbyScannedCount);
                Profiler.metric("java.chunk_rebuild.background_scanned_count", backgroundScannedCount);
                Profiler.metric("java.chunk_rebuild.foreground_candidate_count", foregroundCandidateCount);
                Profiler.metric("java.chunk_rebuild.ray_tracing_nearby_candidate_count",
                    rayTracingNearbyCandidateCount);
                Profiler.metric("java.chunk_rebuild.background_candidate_count", backgroundCandidateCount);
                Profiler.metric("java.chunk_rebuild.stale_count", staleCount);
                Profiler.metric("java.chunk_rebuild.not_ready_count", notReadyCount);
                Profiler.metric("java.chunk_rebuild.skipped_in_flight_count", skippedInFlightCount);
                Profiler.metric("java.chunk_rebuild.duplicate_candidate_count",
                    foregroundScanStats.duplicateCandidateCount
                        + rayTracingNearbyScanStats.duplicateCandidateCount
                        + backgroundScanStats.duplicateCandidateCount);
                Profiler.metric("java.chunk_rebuild.ranking_rejected_count",
                    foregroundScanStats.rankingRejectedCount
                        + rayTracingNearbyScanStats.rankingRejectedCount
                        + backgroundScanStats.rankingRejectedCount);
                Profiler.metric("java.chunk_rebuild.eligible_count", eligibleCount);
                Profiler.metric("java.chunk_rebuild.budget_skipped_count", budgetSkippedCount);
                Profiler.metric("java.chunk_rebuild.submitted_total", submittedTotal);
                Profiler.metric("java.chunk_rebuild.submitted_important", submittedImportant);
                Profiler.metric("java.chunk_rebuild.submitted_normal", submittedNormal);
                Profiler.metric("java.chunk_rebuild.submitted_important_executor", submittedImportantExecutor);
                Profiler.metric("java.chunk_rebuild.submitted_normal_executor", submittedNormalExecutor);
                Profiler.metric("java.chunk_rebuild.normal_budget_per_frame",
                    normalSubmissionBudget);
                Profiler.metric("java.chunk_rebuild.nearby_coverage_boost",
                    nearbyCoverageBoost ? 1 : 0);
                Profiler.metric("java.chunk_rebuild.nearby_coverage_boost_frames_remaining",
                    nearbyCoverageBoostFramesRemaining);
                Profiler.metric("java.chunk_rebuild.normal_in_flight_limit", normalInFlightLimit);
                Profiler.metric("java.chunk_rebuild.important_in_flight_limit", importantInFlightLimit);
                Profiler.metric("java.chunk_rebuild.in_flight_important", importantRebuildsInFlight.get());
                Profiler.metric("java.chunk_rebuild.in_flight_normal", normalRebuildsInFlight.get());
            }
            recordNearbyCoverageMetrics(camera);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static void collectRebuildCandidate(SectionRenderDispatcher.RenderSection builtChunk, BlockPos blockPos,
        List<RebuildCandidate> candidates, int candidateLimit, IntSet candidateIndices,
        RebuildScanStats stats) {
        collectRebuildCandidate(builtChunk, blockPos, candidates, candidateLimit, candidateIndices,
            stats, false);
    }

    private static void collectRebuildCandidate(SectionRenderDispatcher.RenderSection builtChunk, BlockPos blockPos,
        List<RebuildCandidate> candidates, int candidateLimit, IntSet candidateIndices,
        RebuildScanStats stats, boolean completeColumns) {
        if (builtChunk == null) {
            return;
        }

        int chunkIndex = builtChunk.index;
        if (!candidateIndices.add(chunkIndex)) {
            stats.duplicateCandidateCount++;
            return;
        }

        boolean forced = forcedRebuildIndices.contains(chunkIndex);
        if (!builtChunk.isDirty()) {
            rebuildQueue.remove(chunkIndex, builtChunk);
            forcedRebuildIndices.remove(chunkIndex);
            backgroundRebuildIndices.remove(chunkIndex);
            stats.staleCount++;
            return;
        }
        if (!rebuildQueue.containsKey(chunkIndex)) {
            rebuildQueue.put(chunkIndex, builtChunk);
            enqueueBackgroundRebuild(builtChunk);
        }
        BlockPos origin = builtChunk.getOrigin();
        double dx = (double) (origin.getX() + 8) - blockPos.getX();
        double dy = (double) (origin.getY() + 8) - blockPos.getY();
        double dz = (double) (origin.getZ() + 8) - blockPos.getZ();
        double squaredDistanceToCamera = dx * dx + dy * dy + dz * dz;
        double priorityDistance = completeColumns ? dx * dx + dz * dz : squaredDistanceToCamera;
        if (completeColumns && candidateLimit > 0 && candidates.size() == candidateLimit
            && candidates.get(candidates.size() - 1).compareTo(priorityDistance,
                origin.getX(), origin.getZ(), squaredDistanceToCamera) <= 0) {
            stats.rankingRejectedCount++;
            return;
        }
        if (!forced && !builtChunk.hasAllNeighbors()) {
            stats.notReadyCount++;
            return;
        }
        if (inFlightRebuildIndices.contains(chunkIndex)) {
            stats.skippedInFlightCount++;
            return;
        }

        boolean isImportant = squaredDistanceToCamera < 768.0 || builtChunk.isDirtyFromPlayer();
        stats.eligibleCount++;
        insertNearestCandidate(candidates, builtChunk, squaredDistanceToCamera,
            priorityDistance, isImportant, candidateLimit);
    }

    private static int collectVistaRebuildCandidates(BlockPos blockPos,
        List<RebuildCandidate> candidates, int candidateLimit, IntSet candidateIndices,
        RebuildScanStats stats) {
        ViewArea storage = currentStorage;
        ClientLevel world = getStorageWorld(storage);
        if (world == null || rebuildQueue.isEmpty()) {
            return 0;
        }
        if (Profiler.ENABLED) Profiler.enter("java.ChunkProxy.collectPendingVistaRebuilds");
        try {
            int scannedCount = 0;
            for (SectionRenderDispatcher.RenderSection builtChunk : rebuildQueue.values()) {
                scannedCount++;
                BlockPos origin = builtChunk.getOrigin();
                if (origin.getY() < world.getMinY() || origin.getY() >= (world.getMinY() + world.getHeight())
                    || storage.getRenderSectionAt(origin) != builtChunk) {
                    continue;
                }
                if (!builtChunk.isDirty()) {
                    rebuildQueue.remove(builtChunk.index, builtChunk);
                    forcedRebuildIndices.remove(builtChunk.index);
                    backgroundRebuildIndices.remove(builtChunk.index);
                    continue;
                }
                if (getLoadedSourceChunk(world, origin) == null) continue;
                collectRebuildCandidate(builtChunk, blockPos, candidates, candidateLimit,
                    candidateIndices, stats, true);
            }
            return scannedCount;
        } finally {
            if (Profiler.ENABLED) Profiler.exit();
        }
    }

    private static int collectRayTracingNearbyRebuildCandidates(Camera camera, BlockPos blockPos,
        List<RebuildCandidate> candidates, int candidateLimit, IntSet candidateIndices,
        RebuildScanStats stats) {
        if (currentStorage == null || currentStorage.sections == null) {
            return 0;
        }

        SectionPos cameraSectionPos = SectionPos.of(camera.getBlockPosition());
        BlockPos.MutableBlockPos sampleBlockPos = new BlockPos.MutableBlockPos();
        int centerSectionX = cameraSectionPos.x();
        int centerSectionY = cameraSectionPos.y();
        int centerSectionZ = cameraSectionPos.z();
        int minSectionY = Math.max(getStorageBottomSectionCoord(currentStorage),
            centerSectionY - RAY_TRACING_NEAR_VERTICAL_RADIUS_SECTIONS);
        int maxSectionY = Math.min(getStorageTopSectionCoord(currentStorage),
            centerSectionY + RAY_TRACING_NEAR_VERTICAL_RADIUS_SECTIONS);
        int scannedCount = 0;

        for (int sectionX = centerSectionX - RAY_TRACING_NEAR_HORIZONTAL_RADIUS_SECTIONS;
            sectionX <= centerSectionX + RAY_TRACING_NEAR_HORIZONTAL_RADIUS_SECTIONS; sectionX++) {
            for (int sectionZ = centerSectionZ - RAY_TRACING_NEAR_HORIZONTAL_RADIUS_SECTIONS;
                sectionZ <= centerSectionZ + RAY_TRACING_NEAR_HORIZONTAL_RADIUS_SECTIONS; sectionZ++) {
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    sampleBlockPos.set(SectionPos.sectionToBlockCoord(sectionX) + 8,
                        SectionPos.sectionToBlockCoord(sectionY) + 8,
                        SectionPos.sectionToBlockCoord(sectionZ) + 8);
                    SectionRenderDispatcher.RenderSection builtChunk = currentStorage.getRenderSectionAt(sampleBlockPos);
                    if (builtChunk == null) {
                        continue;
                    }

                    scannedCount++;
                    collectRebuildCandidate(builtChunk, blockPos, candidates, candidateLimit,
                        candidateIndices, stats);
                }
            }
        }
        return scannedCount;
    }

    private static void enqueueBackgroundRebuild(SectionRenderDispatcher.RenderSection chunk) {
        if (backgroundRebuildIndices.add(chunk.index)) {
            backgroundRebuildQueue.offer(chunk);
        }
    }

    private static void discardCompletedBackgroundRebuilds() {
        for (int i = 0; i < BACKGROUND_REBUILD_SCAN_LIMIT_PER_FRAME; i++) {
            SectionRenderDispatcher.RenderSection builtChunk = backgroundRebuildQueue.poll();
            if (builtChunk == null) {
                break;
            }
            if (backgroundRebuildIndices.contains(builtChunk.index)
                && rebuildQueue.get(builtChunk.index) == builtChunk && builtChunk.isDirty()) {
                backgroundRebuildQueue.offer(builtChunk);
            }
        }
    }

    private static void submitRebuildCandidates(List<RebuildCandidate> candidates,
        SubmissionBudget submissionBudget, SubmissionStats stats, boolean allowImportantFallback,
        int maxSubmissions) {
        int submittedInCall = 0;
        for (RebuildCandidate candidate : candidates) {
            if (submittedInCall >= maxSubmissions) {
                stats.budgetSkippedCount++;
                continue;
            }

            boolean useImportantExecutor = false;
            if (candidate.important && submissionBudget.remainingImportantSubmissions > 0) {
                submissionBudget.remainingImportantSubmissions--;
                useImportantExecutor = true;
            } else if (submissionBudget.remainingNormalSubmissions > 0) {
                submissionBudget.remainingNormalSubmissions--;
            } else if (allowImportantFallback && submissionBudget.remainingImportantSubmissions > 0) {
                submissionBudget.remainingImportantSubmissions--;
                useImportantExecutor = true;
            } else {
                stats.budgetSkippedCount++;
                continue;
            }

            int chunkIndex = candidate.builtChunk.index;
            if (!submitRebuild(candidate.builtChunk, useImportantExecutor, candidate.important)) {
                stats.skippedInFlightCount++;
                continue;
            }

            forcedRebuildIndices.remove(chunkIndex);
            backgroundRebuildIndices.remove(chunkIndex);
            rebuildQueue.remove(chunkIndex, candidate.builtChunk);
            stats.submittedTotal++;
            if (candidate.important) {
                stats.submittedImportant++;
            } else {
                stats.submittedNormal++;
            }
            if (useImportantExecutor) {
                stats.submittedImportantExecutor++;
            } else {
                stats.submittedNormalExecutor++;
            }
            submittedInCall++;
        }
    }

    private static final class SubmissionBudget {

        private int remainingNormalSubmissions;
        private int remainingImportantSubmissions;

        private SubmissionBudget(int remainingNormalSubmissions, int remainingImportantSubmissions) {
            this.remainingNormalSubmissions = remainingNormalSubmissions;
            this.remainingImportantSubmissions = remainingImportantSubmissions;
        }
    }

    private static final class SubmissionStats {

        private int submittedTotal = 0;
        private int submittedImportant = 0;
        private int submittedNormal = 0;
        private int submittedImportantExecutor = 0;
        private int submittedNormalExecutor = 0;
        private int skippedInFlightCount = 0;
        private int budgetSkippedCount = 0;
    }

    private static final class RebuildScanStats {

        private int staleCount = 0;
        private int notReadyCount = 0;
        private int skippedInFlightCount = 0;
        private int eligibleCount = 0;
        private int duplicateCandidateCount = 0;
        private int rankingRejectedCount = 0;
    }

    public static void waitImportantChunkRebuild() {
        if (Profiler.ENABLED) {
            Profiler.enter("java.ChunkProxy.waitImportantChunkRebuild");
        }
        try {
            int completedCount = 0;
            synchronized (rebuildTasksLock) {
                for (int i = rebuildTasks.size() - 1; i >= 0; i--) {
                    Future<?> rebuildTask = rebuildTasks.get(i);
                    if (!rebuildTask.isDone()) {
                        continue;
                    }

                    try {
                        rebuildTask.get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                    rebuildTasks.remove(i);
                    completedCount++;
                }
            }

            if (Profiler.ENABLED) {
                Profiler.metric("java.chunk_rebuild.important_completed", completedCount);
                synchronized (rebuildTasksLock) {
                    Profiler.metric("java.chunk_rebuild.important_pending_futures", rebuildTasks.size());
                }
            }
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static void waitAllImportantChunkRebuild() {
        if (Profiler.ENABLED) {
            Profiler.enter("java.ChunkProxy.waitAllImportantChunkRebuild");
        }
        try {
            List<Future<?>> pendingTasks;
            synchronized (rebuildTasksLock) {
                pendingTasks = new ArrayList<>(rebuildTasks);
                rebuildTasks.clear();
            }

            for (Future<?> rebuildTask : pendingTasks) {
                try {
                    rebuildTask.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static void resetNearbyCoverageMetrics() {
        rebuildFrameCounter = 0;
        nearbyCoverageFrameCounter = 0;
        lastNearbyCoverageSampleNanos = 0L;
        hasLastNearbyCoverageCameraPos = false;
        innerMissingStreakSamples = 0;
        nearMissingStreakSamples = 0;
        nearbyCoverageBoostFramesRemaining = INITIAL_NEARBY_COVERAGE_BOOST_FRAMES;
    }

    private static boolean consumeNearbyCoverageBoost() {
        if (nearbyCoverageBoostFramesRemaining <= 0) {
            return false;
        }
        nearbyCoverageBoostFramesRemaining--;
        return true;
    }

    private static void recordNearbyCoverageMetrics(Camera camera) {
        if (!Profiler.ENABLED) {
            return;
        }
        if (currentStorage == null || currentStorage.sections == null) {
            return;
        }
        nearbyCoverageFrameCounter++;
        if (nearbyCoverageFrameCounter % NEARBY_COVERAGE_SAMPLE_INTERVAL_FRAMES != 0) {
            return;
        }

        Vec3 cameraPos = camera.getPosition();
        BlockPos cameraBlockPos = camera.getBlockPosition();
        SectionPos cameraSectionPos = SectionPos.of(cameraBlockPos);
        long now = System.nanoTime();
        long sampleIntervalMillis =
            lastNearbyCoverageSampleNanos == 0L ? 0L
                : (now - lastNearbyCoverageSampleNanos) / 1_000_000L;
        long cameraHorizontalSpeedMilliBlocksPerSecond = 0L;
        if (hasLastNearbyCoverageCameraPos && now > lastNearbyCoverageSampleNanos) {
            double dx = cameraPos.x - lastNearbyCoverageCameraX;
            double dz = cameraPos.z - lastNearbyCoverageCameraZ;
            double seconds = (now - lastNearbyCoverageSampleNanos) / 1_000_000_000.0;
            cameraHorizontalSpeedMilliBlocksPerSecond =
                Math.round(Math.sqrt(dx * dx + dz * dz) / seconds * 1000.0);
        }
        lastNearbyCoverageSampleNanos = now;
        lastNearbyCoverageCameraX = cameraPos.x;
        lastNearbyCoverageCameraZ = cameraPos.z;
        hasLastNearbyCoverageCameraPos = true;

        NearbyCoverageStats innerStats = sampleNearbyCoverageRadius(currentStorage, cameraPos,
            cameraSectionPos, NEARBY_COVERAGE_INNER_RADIUS_SECTIONS);
        NearbyCoverageStats nearStats = sampleNearbyCoverageRadius(currentStorage, cameraPos,
            cameraSectionPos, NEARBY_COVERAGE_NEAR_RADIUS_SECTIONS);

        innerMissingStreakSamples =
            innerStats.missingCount > 0 ? innerMissingStreakSamples + 1 : 0;
        nearMissingStreakSamples =
            nearStats.missingCount > 0 ? nearMissingStreakSamples + 1 : 0;
        if (innerStats.missingCount > 0 || nearStats.missingCount > 0) {
            nearbyCoverageBoostFramesRemaining = Math.max(nearbyCoverageBoostFramesRemaining,
                NEARBY_COVERAGE_BOOST_FRAMES_AFTER_MISSING);
        }

        Profiler.metric("java.chunk_nearby_coverage.sample_interval_ms", sampleIntervalMillis);
        Profiler.metric("java.chunk_nearby_coverage.camera_section_x", cameraSectionPos.x());
        Profiler.metric("java.chunk_nearby_coverage.camera_section_y", cameraSectionPos.y());
        Profiler.metric("java.chunk_nearby_coverage.camera_section_z", cameraSectionPos.z());
        Profiler.metric("java.chunk_nearby_coverage.camera_horizontal_speed_milliblocks_per_second",
            cameraHorizontalSpeedMilliBlocksPerSecond);
        recordNearbyCoverageStats("java.chunk_nearby_coverage.inner", innerStats,
            innerMissingStreakSamples);
        recordNearbyCoverageStats("java.chunk_nearby_coverage.near", nearStats,
            nearMissingStreakSamples);
    }

    private static NearbyCoverageStats sampleNearbyCoverageRadius(ViewArea storage,
        Vec3 cameraPos, SectionPos cameraSectionPos, int radiusSections) {
        NearbyCoverageStats stats = new NearbyCoverageStats();
        BlockPos.MutableBlockPos sampleBlockPos = new BlockPos.MutableBlockPos();

        int centerSectionX = cameraSectionPos.x();
        int centerSectionZ = cameraSectionPos.z();

        for (int sectionX = centerSectionX - radiusSections;
            sectionX <= centerSectionX + radiusSections; sectionX++) {
            for (int sectionZ = centerSectionZ - radiusSections;
                sectionZ <= centerSectionZ + radiusSections; sectionZ++) {
                sampleNearbyCoverageColumn(storage, cameraPos, sampleBlockPos, sectionX, sectionZ,
                    stats);
            }
        }
        return stats;
    }

    private static void sampleNearbyCoverageColumn(ViewArea storage, Vec3 cameraPos,
        BlockPos.MutableBlockPos sampleBlockPos, int sectionX, int sectionZ,
        NearbyCoverageStats stats) {
        stats.sampledCount++;
        int blockX = SectionPos.sectionToBlockCoord(sectionX) + 8;
        int blockZ = SectionPos.sectionToBlockCoord(sectionZ) + 8;
        double distanceBlocks = Math.sqrt(squaredHorizontalDistance(blockX, blockZ,
            cameraPos.x, cameraPos.z));
        int minSectionY = getStorageBottomSectionCoord(storage);
        int maxSectionY = getStorageTopSectionCoord(storage);
        boolean hasReadyNative = false;
        boolean hasReadyEmptyData = false;
        boolean hasReadySourceEmpty = false;
        boolean hasPendingRebuild = false;
        boolean hasInFlight = false;
        boolean hasNativeNotReady = false;
        LevelChunk worldChunk = null;
        ClientLevel world = null;

        for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
            int blockY = SectionPos.sectionToBlockCoord(sectionY) + 8;
            sampleBlockPos.set(blockX, blockY, blockZ);
            SectionRenderDispatcher.RenderSection builtChunk = storage.getRenderSectionAt(sampleBlockPos);
            if (builtChunk == null) {
                stats.storageMissingCount++;
                stats.recordNotDrawableDistance(distanceBlocks);
                return;
            }

            if (worldChunk == null) {
                world = getChunkWorld(builtChunk);
                int sourceChunkX = SectionPos.blockToSectionCoord(builtChunk.getOrigin().getX());
                int sourceChunkZ = SectionPos.blockToSectionCoord(builtChunk.getOrigin().getZ());
                if (world == null || !world.hasChunk(sourceChunkX, sourceChunkZ)) {
                    stats.sourceUnloadedCount++;
                    stats.recordNotDrawableDistance(distanceBlocks);
                    return;
                }
                worldChunk = world.getChunk(sourceChunkX, sourceChunkZ);
            }

            NearbyCoverageChunkState state = classifyNearbyCoverageChunk(builtChunk, worldChunk,
                world, sectionY);
            switch (state) {
                case READY_NATIVE -> hasReadyNative = true;
                case READY_EMPTY_DATA -> hasReadyEmptyData = true;
                case READY_SOURCE_EMPTY -> hasReadySourceEmpty = true;
                case PENDING_REBUILD -> hasPendingRebuild = true;
                case IN_FLIGHT -> hasInFlight = true;
                case NATIVE_NOT_READY -> hasNativeNotReady = true;
            }
        }

        if (hasPendingRebuild) {
            stats.pendingRebuildCount++;
            stats.recordMissingDistance(distanceBlocks);
        } else if (hasInFlight) {
            stats.inFlightCount++;
            stats.recordMissingDistance(distanceBlocks);
        } else if (hasNativeNotReady) {
            stats.nativeNotReadyCount++;
            stats.recordMissingDistance(distanceBlocks);
        } else if (hasReadyNative) {
            stats.readyNativeCount++;
        } else if (hasReadyEmptyData) {
            stats.readyEmptyDataCount++;
        } else if (hasReadySourceEmpty) {
            stats.readySourceEmptyCount++;
        }
    }

    private static NearbyCoverageChunkState classifyNearbyCoverageChunk(
        SectionRenderDispatcher.RenderSection builtChunk, LevelChunk worldChunk, ClientLevel world,
        int sectionY) {
        if (worldChunk.getSection(sectionY - world.getMinSectionY()).hasOnlyAir()) {
            return NearbyCoverageChunkState.READY_SOURCE_EMPTY;
        }

        SectionRenderDispatcher.CompiledSection data = builtChunk.compiled.get();
        if (inFlightRebuildIndices.contains(builtChunk.index)) {
            return NearbyCoverageChunkState.IN_FLIGHT;
        }
        if (builtChunk.isDirty()) {
            return NearbyCoverageChunkState.PENDING_REBUILD;
        }
        if (data.isEmpty(null)) {
            return NearbyCoverageChunkState.READY_EMPTY_DATA;
        }
        if (isChunkReady(builtChunk)) {
            return NearbyCoverageChunkState.READY_NATIVE;
        }
        return NearbyCoverageChunkState.NATIVE_NOT_READY;
    }

    private static ClientLevel getStorageWorld(ViewArea storage) {
        if (storage == null || storage.sections == null) {
            return null;
        }
        for (SectionRenderDispatcher.RenderSection builtChunk : storage.sections) {
            if (builtChunk == null) {
                continue;
            }
            ClientLevel world = getChunkWorld(builtChunk);
            if (world != null) {
                return world;
            }
        }
        return null;
    }

    private static int getStorageBottomSectionCoord(ViewArea storage) {
        ClientLevel world = getStorageWorld(storage);
        return world == null ? 0 : world.getMinSectionY();
    }

    private static int getStorageTopSectionCoord(ViewArea storage) {
        ClientLevel world = getStorageWorld(storage);
        return world == null ? -1 : world.getMaxSectionY();
    }

    private static ClientLevel getChunkWorld(SectionRenderDispatcher.RenderSection builtChunk) {
        IChunkBuilderBuiltChunkExt builtChunkExt = (IChunkBuilderBuiltChunkExt) builtChunk;
        IChunkBuilderExt chunkBuilderExt =
            (IChunkBuilderExt) builtChunkExt.radiance$getChunkBuilder();
        return chunkBuilderExt.radiance$getWorld();
    }

    private static double squaredHorizontalDistance(double x0, double z0, double x1,
        double z1) {
        double dx = x0 - x1;
        double dz = z0 - z1;
        return dx * dx + dz * dz;
    }

    private static void recordNearbyCoverageStats(String prefix, NearbyCoverageStats stats,
        int missingStreakSamples) {
        if (!Profiler.ENABLED) {
            return;
        }
        Profiler.metric(prefix + ".sampled_count", stats.sampledCount);
        Profiler.metric(prefix + ".ready_count", stats.readyCount());
        Profiler.metric(prefix + ".ready_native_count", stats.readyNativeCount);
        Profiler.metric(prefix + ".ready_empty_data_count", stats.readyEmptyDataCount);
        Profiler.metric(prefix + ".ready_source_empty_count", stats.readySourceEmptyCount);
        Profiler.metric(prefix + ".missing_count", stats.missingCount);
        Profiler.metric(prefix + ".pending_rebuild_count", stats.pendingRebuildCount);
        Profiler.metric(prefix + ".in_flight_count", stats.inFlightCount);
        Profiler.metric(prefix + ".native_not_ready_count", stats.nativeNotReadyCount);
        Profiler.metric(prefix + ".source_unloaded_count", stats.sourceUnloadedCount);
        Profiler.metric(prefix + ".storage_missing_count", stats.storageMissingCount);
        Profiler.metric(prefix + ".not_drawable_count", stats.notDrawableCount());
        Profiler.metric(prefix + ".nearest_missing_distance_blocks",
            Math.round(stats.nearestMissingDistanceBlocks()));
        Profiler.metric(prefix + ".furthest_missing_distance_blocks",
            Math.round(stats.furthestMissingDistanceBlocks()));
        Profiler.metric(prefix + ".nearest_not_drawable_distance_blocks",
            Math.round(stats.nearestNotDrawableDistanceBlocks()));
        Profiler.metric(prefix + ".missing_streak_samples", missingStreakSamples);
    }

    private enum NearbyCoverageChunkState {
        READY_NATIVE,
        READY_EMPTY_DATA,
        READY_SOURCE_EMPTY,
        PENDING_REBUILD,
        IN_FLIGHT,
        NATIVE_NOT_READY
    }

    private static final class NearbyCoverageStats {

        private int sampledCount = 0;
        private int readyNativeCount = 0;
        private int readyEmptyDataCount = 0;
        private int readySourceEmptyCount = 0;
        private int pendingRebuildCount = 0;
        private int inFlightCount = 0;
        private int nativeNotReadyCount = 0;
        private int sourceUnloadedCount = 0;
        private int storageMissingCount = 0;
        private int missingCount = 0;
        private double nearestMissingDistanceBlocks = Double.POSITIVE_INFINITY;
        private double furthestMissingDistanceBlocks = 0.0;
        private double nearestNotDrawableDistanceBlocks = Double.POSITIVE_INFINITY;

        private int readyCount() {
            return readyNativeCount + readyEmptyDataCount + readySourceEmptyCount;
        }

        private int notDrawableCount() {
            return missingCount + sourceUnloadedCount + storageMissingCount;
        }

        private void recordMissingDistance(double distanceBlocks) {
            missingCount++;
            nearestMissingDistanceBlocks = Math.min(nearestMissingDistanceBlocks, distanceBlocks);
            furthestMissingDistanceBlocks = Math.max(furthestMissingDistanceBlocks, distanceBlocks);
            recordNotDrawableDistance(distanceBlocks);
        }

        private void recordNotDrawableDistance(double distanceBlocks) {
            nearestNotDrawableDistanceBlocks =
                Math.min(nearestNotDrawableDistanceBlocks, distanceBlocks);
        }

        private double nearestMissingDistanceBlocks() {
            return missingCount == 0 ? 0.0 : nearestMissingDistanceBlocks;
        }

        private double furthestMissingDistanceBlocks() {
            return missingCount == 0 ? 0.0 : furthestMissingDistanceBlocks;
        }

        private double nearestNotDrawableDistanceBlocks() {
            return notDrawableCount() == 0 ? 0.0 : nearestNotDrawableDistanceBlocks;
        }
    }

    private static boolean submitRebuild(SectionRenderDispatcher.RenderSection builtChunk, boolean importantExecutor,
        boolean importantBuild) {
        ChunkBuild build;
        synchronized (chunkLifecycleLock) {
            ChunkSlot slot = chunkSlots.get(builtChunk.index);
            ClientLevel world = getChunkWorld(builtChunk);
            if (slot == null || slot.builtChunk != builtChunk || slot.relocating || world == null
                || world != Minecraft.getInstance().level
                || !slot.origin.equals(builtChunk.getOrigin())) {
                return false;
            }
            LevelChunk sourceChunk = getLoadedSourceChunk(world, slot.origin);
            if (sourceChunk == null || !inFlightRebuildIndices.add(builtChunk.index)) {
                return false;
            }
            try {
                RenderChunkRegion region = new RenderRegionCache().createRegion(world, SectionPos.of(slot.origin));
                build = new ChunkBuild(slot, world, sourceChunk, region);
            } catch (RuntimeException | Error e) {
                inFlightRebuildIndices.remove(builtChunk.index);
                throw e;
            }
            builtChunk.setNotDirty();
        }
        if (importantExecutor) {
            importantRebuildsInFlight.incrementAndGet();
            Future<?> rebuildTask = importantChunkRebuildExecutor.submit(() -> {
                runRebuildTask(build, true, importantBuild);
            });
            synchronized (rebuildTasksLock) {
                rebuildTasks.add(rebuildTask);
            }
        } else {
            normalRebuildsInFlight.incrementAndGet();
            backgroundChunkRebuildExecutor.execute(() -> {
                runRebuildTask(build, false, importantBuild);
            });
        }
        return true;
    }

    private static void runRebuildTask(ChunkBuild build, boolean importantExecutor,
        boolean importantBuild) {
        try {
            rebuildSingle(build, importantBuild);
        } finally {
            inFlightRebuildIndices.remove(build.slot.builtChunk.index);
            if (importantExecutor) {
                importantRebuildsInFlight.decrementAndGet();
            } else {
                normalRebuildsInFlight.decrementAndGet();
            }
        }
    }

    private static Thread newChunkRebuildThread(Runnable runnable, String name, int priority) {
        Thread thread = new Thread(runnable, name + " " + chunkRebuildThreadIds.incrementAndGet());
        thread.setPriority(Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, priority)));
        return thread;
    }

    private static int normalRebuildSubmissionBudgetPerFrame(boolean nearbyCoverageBoost) {
        int budgetFromThreads = Math.max(1,
            Math.min(normalChunkRebuildThreadCount,
                nearbyCoverageBoost
                    ? (normalChunkRebuildThreadCount + 1) / 2
                    : (normalChunkRebuildThreadCount + 7) / 8));
        return Math.max(1, Math.min(Math.max(1, Options.chunkBuildingBatchSize), budgetFromThreads));
    }

    private static int importantRebuildSubmissionBudgetPerFrame() {
        return IMPORTANT_CHUNK_REBUILD_THREAD_COUNT;
    }

    private static int normalRebuildInFlightLimit() {
        return Math.max(1, normalChunkRebuildThreadCount);
    }

    private static int importantRebuildInFlightLimit() {
        return IMPORTANT_CHUNK_REBUILD_THREAD_COUNT;
    }

    private static void insertNearestCandidate(List<RebuildCandidate> candidates,
        SectionRenderDispatcher.RenderSection builtChunk,
        double squaredDistanceToCamera,
        double priorityDistance,
        boolean important,
        int limit) {
        if (limit <= 0) {
            return;
        }
        RebuildCandidate candidate = new RebuildCandidate(builtChunk, squaredDistanceToCamera,
            priorityDistance, important);
        if (candidates.size() == limit
            && candidate.compareTo(candidates.get(candidates.size() - 1)) >= 0) {
            return;
        }

        int insertIndex = candidates.size();
        while (insertIndex > 0
            && candidate.compareTo(candidates.get(insertIndex - 1)) < 0) {
            insertIndex--;
        }

        candidates.add(insertIndex, candidate);
        if (candidates.size() > limit) {
            candidates.remove(candidates.size() - 1);
        }
    }

    private static final class RebuildCandidate implements Comparable<RebuildCandidate> {

        private final SectionRenderDispatcher.RenderSection builtChunk;
        private final double squaredDistanceToCamera;
        private final double priorityDistance;
        private final int columnX;
        private final int columnZ;
        private final boolean important;

        private RebuildCandidate(SectionRenderDispatcher.RenderSection builtChunk,
            double squaredDistanceToCamera,
            double priorityDistance,
            boolean important) {
            this.builtChunk = builtChunk;
            this.squaredDistanceToCamera = squaredDistanceToCamera;
            this.priorityDistance = priorityDistance;
            this.columnX = builtChunk.getOrigin().getX();
            this.columnZ = builtChunk.getOrigin().getZ();
            this.important = important;
        }

        @Override
        public int compareTo(RebuildCandidate other) {
            return compareTo(other.priorityDistance, other.columnX, other.columnZ,
                other.squaredDistanceToCamera);
        }

        private int compareTo(double priorityDistance, int columnX, int columnZ,
            double squaredDistanceToCamera) {
            int comparison = Double.compare(this.priorityDistance, priorityDistance);
            if (comparison == 0) {
                comparison = Integer.compare(this.columnX, columnX);
            }
            if (comparison == 0) {
                comparison = Integer.compare(this.columnZ, columnZ);
            }
            return comparison == 0
                ? Double.compare(this.squaredDistanceToCamera, squaredDistanceToCamera)
                : comparison;
        }
    }

    private static final class ChunkSlot {
        private final SectionRenderDispatcher.RenderSection builtChunk;
        private final BlockPos origin;
        private final long generation;
        private boolean relocating = true;

        private ChunkSlot(SectionRenderDispatcher.RenderSection builtChunk, BlockPos origin, long generation) {
            this.builtChunk = builtChunk;
            this.origin = origin;
            this.generation = generation;
        }
    }

    private record ChunkBuild(ChunkSlot slot, ClientLevel world, LevelChunk sourceChunk,
        RenderChunkRegion region) {
        private ChunkBuild(ChunkSlot slot, ClientLevel world, LevelChunk sourceChunk) {
            this(slot, world, sourceChunk, null);
        }
    }

    public static void beginRelocation(SectionRenderDispatcher.RenderSection builtChunk, int x, int y, int z) {
        synchronized (chunkLifecycleLock) {
            ChunkSlot slot = new ChunkSlot(builtChunk, new BlockPos(x, y, z), ++nextChunkGeneration);
            chunkSlots.put(builtChunk.index, slot);
            relocateSingle(builtChunk.index, x, y, z, slot.generation);
        }
    }

    public static void finishRelocation(SectionRenderDispatcher.RenderSection builtChunk) {
        synchronized (chunkLifecycleLock) {
            ChunkSlot slot = chunkSlots.get(builtChunk.index);
            if (slot != null && slot.builtChunk == builtChunk && slot.origin.equals(builtChunk.getOrigin())) {
                slot.relocating = false;
            }
        }
    }

    private static boolean isCurrentBuild(ChunkBuild build) {
        ChunkSlot slot = build.slot;
        return chunkSlots.get(slot.builtChunk.index) == slot && !slot.relocating
            && slot.origin.equals(slot.builtChunk.getOrigin())
            && build.world == Minecraft.getInstance().level;
    }

    private static LevelChunk getLoadedSourceChunk(ClientLevel world, BlockPos origin) {
        return world.getChunkSource().getChunk(SectionPos.blockToSectionCoord(origin.getX()),
            SectionPos.blockToSectionCoord(origin.getZ()), ChunkStatus.FULL, false);
    }

    private static boolean isBuildSourceLoaded(ChunkBuild build) {
        return getLoadedSourceChunk(build.world, build.slot.origin) == build.sourceChunk;
    }

    private static boolean isBuildSourceEmpty(ChunkBuild build) {
        if (!isBuildSourceLoaded(build)) {
            return false;
        }
        BlockPos origin = build.slot.origin;
        LevelChunk chunk = build.sourceChunk;
        int section = SectionPos.blockToSectionCoord(origin.getY()) - build.world.getMinSectionY();
        return section >= 0 && section < chunk.getSections().length && chunk.getSection(section).hasOnlyAir();
    }

    private static void completeEmptySections() {
        ViewArea storage = currentStorage;
        if (storage == null || storage.sections == null || storage.sections.length == 0) {
            return;
        }
        int count = Math.min(storage.sections.length, BACKGROUND_REBUILD_SCAN_LIMIT_PER_FRAME);
        for (int i = 0; i < count; i++) {
            emptySectionScanIndex %= storage.sections.length;
            SectionRenderDispatcher.RenderSection builtChunk = storage.sections[emptySectionScanIndex++];
            if (builtChunk == null || !builtChunk.isDirty()
                || inFlightRebuildIndices.contains(builtChunk.index)) {
                continue;
            }
            synchronized (chunkLifecycleLock) {
                ChunkSlot slot = chunkSlots.get(builtChunk.index);
                ClientLevel world = getChunkWorld(builtChunk);
                if (slot == null || slot.builtChunk != builtChunk || slot.relocating || world == null) {
                    continue;
                }
                LevelChunk sourceChunk = getLoadedSourceChunk(world, slot.origin);
                if (sourceChunk == null) {
                    continue;
                }
                ChunkBuild build = new ChunkBuild(slot, world, sourceChunk);
                if (!isCurrentBuild(build) || !isBuildSourceEmpty(build)) {
                    continue;
                }
                BlockPos origin = slot.origin;
                builtChunk.setNotDirty();
                invalidateSingle(builtChunk.index, origin.getX(), origin.getY(), origin.getZ(),
                    slot.generation, 0, 0);
                IChunkBuilderBuiltChunkExt builtChunkExt = (IChunkBuilderBuiltChunkExt) builtChunk;
                builtChunk.updateGlobalBlockEntities(List.of());
                publishCompiledSection(build, (IChunkBuilderExt) builtChunkExt.radiance$getChunkBuilder(), TERRAIN_EMPTY);
                rebuildQueue.remove(builtChunk.index, builtChunk);
                forcedRebuildIndices.remove(builtChunk.index);
                backgroundRebuildIndices.remove(builtChunk.index);
            }
        }
    }

    private static void retryBuild(ChunkBuild build) {
        Minecraft.getInstance().execute(() -> {
            synchronized (chunkLifecycleLock) {
                if (isCurrentBuild(build)) {
                    build.slot.builtChunk.setDirty(false);
                }
            }
        });
    }

    private static void rebuildSingle(ChunkBuild build, boolean important) {
        try (var scope = scopedBlockBufferAllocatorStorage()) {
            synchronized (chunkLifecycleLock) {
                if (!isCurrentBuild(build)) {
                    return;
                }
            }
            if (!isBuildSourceLoaded(build)) {
                retryBuild(build);
                return;
            }
            SectionRenderDispatcher.RenderSection builtChunk = build.slot.builtChunk;
            IChunkBuilderBuiltChunkExt builtChunkExt = (IChunkBuilderBuiltChunkExt) builtChunk;
            SectionRenderDispatcher chunkBuilder = builtChunkExt.radiance$getChunkBuilder();
            IChunkBuilderExt chunkBuilderExt = (IChunkBuilderExt) chunkBuilder;
            BlockPos origin = build.slot.origin;
            RenderChunkRegion chunkRendererRegion = build.region;

            if (chunkRendererRegion == null) {
                synchronized (chunkLifecycleLock) {
                    if (!isCurrentBuild(build)) {
                        return;
                    }
                    if (isBuildSourceEmpty(build)) {
                        invalidateSingle(builtChunk.index, origin.getX(), origin.getY(), origin.getZ(),
                            build.slot.generation, 0, 0);
                        builtChunk.updateGlobalBlockEntities(List.of());
                        publishCompiledSection(build, chunkBuilderExt, TERRAIN_EMPTY);
                    } else {
                        retryBuild(build);
                    }
                }
                return;
            }

            SectionBufferBuilderPack storage = blockBufferAllocatorStorage.get();
            rebuildSingle(chunkRendererRegion, chunkBuilder, chunkBuilderExt, build, storage,
                important);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void rebuildSingle(RenderChunkRegion chunkRendererRegion,
        SectionRenderDispatcher chunkBuilder, IChunkBuilderExt chunkBuilderExt,
        ChunkBuild build,
        SectionBufferBuilderPack storage, boolean important) {
        SectionRenderDispatcher.RenderSection builtChunk = build.slot.builtChunk;
        BlockPos origin = build.slot.origin;
        IChunkBuilderBuiltChunkExt builtChunkExt = (IChunkBuilderBuiltChunkExt) builtChunk;
        ChunkRenderData renderData = buildRenderData(chunkBuilderExt, chunkRendererRegion, origin, storage,
            chunkBuilder.getCameraPosition());
        Map<RenderType, MeshData> buffers = renderData.buffers;
        synchronized (chunkLifecycleLock) {
            if (!isCurrentBuild(build) || !isBuildSourceLoaded(build)) {
                closeUntransferredBuiltBuffers(buffers, new TransferredBuildBuffer[0]);
                retryBuild(build);
                return;
            }
        }

        if (buffers.isEmpty()) {
            SectionRenderDispatcher.CompiledSection chunkData = new SectionRenderDispatcher.CompiledSection() {
                @Override
                public List<BlockEntity> getRenderableBlockEntities() {
                    return renderData.blockEntities;
                }

                @Override
                public boolean facesCanSeeEachother(Direction from, Direction to) {
                    return renderData.chunkOcclusionData.visibilityBetween(from, to);
                }

                @Override
                public boolean isEmpty(RenderType layer) {
                    return true;
                }
            };
            try (VistaTerrain.Upload terrain = renderData.terrain.upload()) {
                synchronized (chunkLifecycleLock) {
                    if (isCurrentBuild(build) && isBuildSourceLoaded(build)) {
                        invalidateSingle(builtChunk.index, origin.getX(), origin.getY(), origin.getZ(),
                            build.slot.generation, terrain.count, terrain.address);
                        builtChunk.updateGlobalBlockEntities(renderData.noCullingBlockEntities);
                        publishCompiledSection(build, chunkBuilderExt, chunkData);
                        builtChunkCount++;
                    } else {
                        retryBuild(build);
                    }
                }
            }
        } else {
            SectionRenderDispatcher.CompiledSection chunkData = new SectionRenderDispatcher.CompiledSection() {
                @Override
                public List<BlockEntity> getRenderableBlockEntities() {
                    return renderData.blockEntities;
                }

                @Override
                public boolean facesCanSeeEachother(Direction from, Direction to) {
                    return renderData.chunkOcclusionData.visibilityBetween(from, to);
                }

                @Override
                public boolean isEmpty(RenderType layer) {
                    return layer == null ? false : !buffers.containsKey(layer);
                }
            };
            ByteBuffer geometryTypeBB = null;
            ByteBuffer geometryGroupNameBB = null;
            ByteBuffer geometryTextureBB = null;
            ByteBuffer vertexFormatBB = null;
            ByteBuffer indexTypeBB = null;
            ByteBuffer vertexCountBB = null;
            ByteBuffer verticesBB = null;
            ByteBuffer indicesBB = null;
            List<ByteBuffer> geometryGroupNameBuffers = new ArrayList<>(buffers.size());
            TransferredBuildBuffer[] transferredBuffers = new TransferredBuildBuffer[buffers.size()];
            boolean submittedNative = false;

            try {
                int geometryTypeSize = buffers.size() * Integer.BYTES;
                geometryTypeBB = MemoryUtil.memAlloc(geometryTypeSize);
                long geometryTypeAddr = memAddress(geometryTypeBB);
                int geometryTypeBaseAddr = 0;

                int geometryGroupNameSize = buffers.size() * Long.BYTES;
                geometryGroupNameBB = MemoryUtil.memAlloc(geometryGroupNameSize);
                long geometryGroupNameAddr = memAddress(geometryGroupNameBB);
                int geometryGroupNameBaseAddr = 0;

                int geometryTextureSize = buffers.size() * Integer.BYTES;
                geometryTextureBB = MemoryUtil.memAlloc(geometryTextureSize);
                long geometryTextureAddr = memAddress(geometryTextureBB);
                int geometryTextureBaseAddr = 0;

                int vertexFormatSize = buffers.size() * Integer.BYTES;
                vertexFormatBB = MemoryUtil.memAlloc(vertexFormatSize);
                long vertexFormatAddr = memAddress(vertexFormatBB);
                int vertexFormatBaseAddr = 0;

                int indexTypeSize = buffers.size() * Integer.BYTES;
                indexTypeBB = MemoryUtil.memAlloc(indexTypeSize);
                long indexTypeAddr = memAddress(indexTypeBB);
                int indexTypeBaseAddr = 0;

                int vertexCountSize = buffers.size() * Integer.BYTES;
                vertexCountBB = MemoryUtil.memAlloc(vertexCountSize);
                long vertexCountAddr = memAddress(vertexCountBB);
                int vertexCountBaseAddr = 0;

                int verticesSize = buffers.size() * Long.BYTES;
                verticesBB = MemoryUtil.memAlloc(verticesSize);
                long verticesAddr = memAddress(verticesBB);
                int verticesBaseAddr = 0;

                int indicesSize = buffers.size() * Long.BYTES;
                indicesBB = MemoryUtil.memAlloc(indicesSize);
                long indicesAddr = memAddress(indicesBB);
                int indicesBaseAddr = 0;

                int geometryIndex = 0;
                for (Map.Entry<RenderType, MeshData> entry : buffers.entrySet()) {
                    RenderType renderLayer = entry.getKey();
                    assert renderLayer.mode() == QUADS;

                    MeshData vertexBuffer = entry.getValue();
                    TransferredBuildBuffer transferredBuffer = TransferredBuildBuffer.takeChunk(storage, renderLayer, vertexBuffer);
                    transferredBuffers[geometryIndex] = transferredBuffer;
                    assert vertexBuffer.drawState().indexCount()
                        == vertexBuffer.drawState().vertexCount() / 4 * 6;

                    TextureManager textureManager = Minecraft.getInstance()
                        .getTextureManager();

                    int geometryTypeID = Constants.GeometryTypes.getGeometryType(renderLayer, true)
                        .getValue();
                    int geometryTextureID = textureManager.getTexture(
                            ((RenderType.CompositeRenderType) renderLayer).state.textureState.cutoutTexture()
                                .orElse(MissingTextureAtlasSprite.getLocation()))
                        .getId();
                    int vertexFormatID = Constants.VertexFormats.getValue(
                        vertexBuffer.drawState().format());

                    geometryTypeBB.putInt(geometryTypeBaseAddr, geometryTypeID);
                    geometryTypeBaseAddr += Integer.BYTES;

                    ByteBuffer geometryGroupNameBuffer = MemoryUtil.memUTF8(renderLayer.name, true);
                    geometryGroupNameBuffers.add(geometryGroupNameBuffer);
                    geometryGroupNameBB.putLong(geometryGroupNameBaseAddr,
                        memAddress(geometryGroupNameBuffer));
                    geometryGroupNameBaseAddr += Long.BYTES;

                    geometryTextureBB.putInt(geometryTextureBaseAddr, geometryTextureID);
                    geometryTextureBaseAddr += Integer.BYTES;

                    vertexFormatBB.putInt(vertexFormatBaseAddr, vertexFormatID);
                    vertexFormatBaseAddr += Integer.BYTES;

                    indexTypeBB.putInt(indexTypeBaseAddr,
                        Constants.IndexTypes.getValue(vertexBuffer.drawState().indexType()));
                    indexTypeBaseAddr += Integer.BYTES;

                    vertexCountBB.putInt(vertexCountBaseAddr,
                        vertexBuffer.drawState().vertexCount());
                    vertexCountBaseAddr += Integer.BYTES;

                    verticesBB.putLong(verticesBaseAddr, transferredBuffer.vertexAddress());
                    verticesBaseAddr += Long.BYTES;
                    indicesBB.putLong(indicesBaseAddr, transferredBuffer.sortedIndexAddress());
                    indicesBaseAddr += Long.BYTES;
                    geometryIndex++;
                }

                try (VistaTerrain.Upload terrain = renderData.terrain.upload()) {
                    synchronized (chunkLifecycleLock) {
                        if (isCurrentBuild(build) && isBuildSourceLoaded(build)) {
                            rebuildSingleNative(origin.getX(), origin.getY(), origin.getZ(),
                                builtChunk.index, buffers.size(), geometryTypeAddr, geometryGroupNameAddr,
                                geometryTextureAddr, vertexFormatAddr, indexTypeAddr, vertexCountAddr,
                                verticesAddr, indicesAddr, transferredBuffers, important, build.slot.generation,
                                terrain.count, terrain.address);
                            submittedNative = true;
                        } else {
                            retryBuild(build);
                        }
                    }
                }
            } finally {
                if (geometryTypeBB != null) {
                    MemoryUtil.memFree(geometryTypeBB);
                }
                if (geometryGroupNameBB != null) {
                    MemoryUtil.memFree(geometryGroupNameBB);
                }
                if (geometryTextureBB != null) {
                    MemoryUtil.memFree(geometryTextureBB);
                }
                if (vertexFormatBB != null) {
                    MemoryUtil.memFree(vertexFormatBB);
                }
                if (indexTypeBB != null) {
                    MemoryUtil.memFree(indexTypeBB);
                }
                if (vertexCountBB != null) {
                    MemoryUtil.memFree(vertexCountBB);
                }
                if (verticesBB != null) {
                    MemoryUtil.memFree(verticesBB);
                }
                if (indicesBB != null) {
                    MemoryUtil.memFree(indicesBB);
                }
                for (ByteBuffer geometryGroupNameBuffer : geometryGroupNameBuffers) {
                    MemoryUtil.memFree(geometryGroupNameBuffer);
                }
            }
            if (submittedNative) {
                synchronized (chunkLifecycleLock) {
                    if (isCurrentBuild(build) && isBuildSourceLoaded(build)) {
                        builtChunk.updateGlobalBlockEntities(renderData.noCullingBlockEntities);
                        publishCompiledSection(build, chunkBuilderExt, chunkData);
                        builtChunkCount++;
                    } else {
                        retryBuild(build);
                    }
                }
            } else {
                for (TransferredBuildBuffer transferredBuffer : transferredBuffers) {
                    if (transferredBuffer != null) {
                        transferredBuffer.close();
                    }
                }
                closeUntransferredBuiltBuffers(buffers, transferredBuffers);
            }
        }
    }

    private static void publishCompiledSection(ChunkBuild build,
        IChunkBuilderExt chunkBuilderExt, SectionRenderDispatcher.CompiledSection chunkData) {
        SectionRenderDispatcher.RenderSection builtChunk = build.slot.builtChunk;
        builtChunk.compiled.set(chunkData);
        BlockEntitySectionCache.changed(builtChunk);
        chunkBuilderExt.radiance$getWorldRenderer().addRecentlyCompiledSection(builtChunk);
    }

    private static ChunkRenderData buildRenderData(IChunkBuilderExt chunkBuilderExt,
        RenderChunkRegion chunkRendererRegion, BlockPos blockPos,
        SectionBufferBuilderPack storage, Vec3 cameraPos) {
        VertexSorting sorter = VertexSorting.byDistance((float) (cameraPos.x - blockPos.getX()),
            (float) (cameraPos.y - blockPos.getY()), (float) (cameraPos.z - blockPos.getZ()));
        SectionCompiler.Results section = chunkBuilderExt.radiance$getSectionBuilder()
            .compile(SectionPos.of(blockPos), chunkRendererRegion, sorter, storage);
        ChunkRenderData renderData = new ChunkRenderData();
        renderData.buffers.putAll(section.renderedLayers);
        renderData.blockEntities.addAll(section.blockEntities);
        renderData.noCullingBlockEntities.addAll(section.globalBlockEntities);
        renderData.chunkOcclusionData = section.visibilitySet;
        if (Vista.isAvailable()) {
            ChunkTerrainBuilder terrain = new ChunkTerrainBuilder();
            try {
                for (BlockPos pos : BlockPos.betweenClosed(blockPos, blockPos.offset(15, 15, 15))) {
                    if ((pos.getX() & 15) == 0 || (pos.getX() & 15) == 15
                        || (pos.getZ() & 15) == 0 || (pos.getZ() & 15) == 15) {
                        terrain.add(chunkRendererRegion, pos, chunkRendererRegion.getBlockState(pos));
                    }
                }
                renderData.terrain = terrain.build();
            } catch (RuntimeException | Error e) {
                section.release();
                throw e;
            }
        }
        return renderData;
    }

    private static void closeUntransferredBuiltBuffers(Map<RenderType, MeshData> buffers,
        TransferredBuildBuffer[] transferredBuffers) {
        for (MeshData builtBuffer : buffers.values()) {
            if (isTransferred(builtBuffer, transferredBuffers)) {
                continue;
            }
            builtBuffer.close();
        }
    }

    private static boolean isTransferred(MeshData builtBuffer,
        TransferredBuildBuffer[] transferredBuffers) {
        if (transferredBuffers == null) {
            return false;
        }

        for (TransferredBuildBuffer transferredBuffer : transferredBuffers) {
            if (transferredBuffer != null && transferredBuffer.builtBuffer() == builtBuffer) {
                return true;
            }
        }
        return false;
    }

    private static native void rebuildSingleNative(int originX, int originY, int originZ, long index,
        int size, long geometryTypes, long geometryGroupNames, long geometryTextures,
        long vertexFormats, long indexTypes, long vertexCounts, long vertices, long indices,
        TransferredBuildBuffer[] transferredBuffers, boolean important, long generation,
        int terrainCount, long terrainAddress);

    public static native boolean isChunkReady(long index);

    public static boolean isChunkReady(SectionRenderDispatcher.RenderSection builtChunk) {
        return isChunkReady(builtChunk.index);
    }

    private static native void invalidateSingle(long index, int originX, int originY, int originZ,
        long generation, int terrainCount, long terrainAddress);

    private static native void relocateSingle(long index, int originX, int originY, int originZ,
        long generation);

    private static final class ChunkRenderData {
        private final List<BlockEntity> noCullingBlockEntities = new ArrayList<>();
        private final List<BlockEntity> blockEntities = new ArrayList<>();
        private final Map<RenderType, MeshData> buffers = new ConcurrentHashMap<>();
        private VisibilitySet chunkOcclusionData = new VisibilitySet();
        private VistaTerrain terrain = VistaTerrain.EMPTY;
    }
}
