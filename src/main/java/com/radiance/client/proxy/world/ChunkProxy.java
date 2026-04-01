package com.radiance.client.proxy.world;

import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.mojang.blaze3d.systems.VertexSorter;
import com.mojang.logging.LogUtils;
import com.radiance.client.RadianceClient;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.util.ChunkLightCollector;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderBuiltChunkExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IBlockColorsExt;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.system.MemoryUtil;

public class ChunkProxy {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private static final long FNV64_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV64_PRIME = 0x100000001b3L;

    public static final ChunkBuilder.ChunkData PROCESSED = new ChunkBuilder.ChunkData() {
        @Override
        public boolean isVisibleThrough(Direction from, Direction to) {
            return false;
        }
    };
    public static final ChunkBuilder.ChunkData TERRAIN_EMPTY = new ChunkBuilder.ChunkData() {
        @Override
        public boolean isVisibleThrough(Direction from, Direction to) {
            return false;
        }
    };
    private static final Map<Integer, ChunkBuilder.BuiltChunk> rebuildQueue = new ConcurrentHashMap<>();
    private static final List<Future<?>> rebuildTasks = new ArrayList<>();
    private static final Map<Long, ChunkSpecialRenderData> specialChunkGeometry =
        new ConcurrentHashMap<>();
    private static final Map<Integer, Long> builtChunkBuildOrigins = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> visibleChunkFrames = new ConcurrentHashMap<>();
    private static final Map<Long, ChunkTraversalData> chunkTraversalData =
        new ConcurrentHashMap<>();
    private static final Object specialChunkGeometryLock = new Object();
    private static final int numNormalChunkRebuildThreads = 1;
    private static final int numImportantChunkRebuildThreads = 1;
    private static final double CHUNK_BOUNDING_RADIUS_BLOCKS = 13.856406460551018;
    private static final double VIEW_CONTRIBUTION_NEAR_DISTANCE_MULTIPLIER = 1.5;
    private static final double VIEW_CONTRIBUTION_BASE_HALF_ANGLE_DEGREES = 100.0;
    private static final ExecutorService
        importantChunkRebuildExecutor =
        new ThreadPoolExecutor(numImportantChunkRebuildThreads, numImportantChunkRebuildThreads,
            0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
            r -> {
                Thread thread = new Thread(r);
                thread.setPriority(Thread.NORM_PRIORITY);
                thread.setName("radiance-chunk-important");
                return thread;
            });
    private static final ThreadLocal<BlockBufferAllocatorStorage>
        blockBufferAllocatorStorageThreadLocal =
        ThreadLocal.withInitial(BlockBufferAllocatorStorage::new);
    public static int builtChunkNum = 0;
    private static long rebuildFrameCounter = 0L;
    private static final long VISIBLE_REBUILD_GRACE_FRAMES = 45L;
    private static ExecutorService backgroundChunkRebuildExecutor = new ThreadPoolExecutor(
        numNormalChunkRebuildThreads, numNormalChunkRebuildThreads,
        0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
        r -> {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.setName("radiance-chunk-background");
            return thread;
        });

    public static native void initNative(int numChunks);

    public static void init(int numChunks) {
        clear();
        initNative(numChunks);
    }

    public static AutoCloseable scopedBlockBufferAllocatorStorage() {
        final BlockBufferAllocatorStorage s = blockBufferAllocatorStorageThreadLocal.get();
        s.reset();
        return s::clear;
    }

    public static void clear() {
        waitImportantChunkRebuild();

        // Create new executor BEFORE shutting down the old one to minimize task rejection window
        ThreadPoolExecutor newBackgroundExecutor = new ThreadPoolExecutor(numNormalChunkRebuildThreads,
            numNormalChunkRebuildThreads, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
            r -> {
                Thread thread = new Thread(r);
                thread.setPriority(Thread.NORM_PRIORITY);
                thread.setName("radiance-chunk-background");
                return thread;
            });

        ExecutorService oldExecutor = backgroundChunkRebuildExecutor;
        backgroundChunkRebuildExecutor = newBackgroundExecutor;

        oldExecutor.shutdown();
        try {
            if (!oldExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                LOGGER.warn("Background chunk rebuild executor did not terminate in time, forcing shutdown");
                oldExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while waiting for background chunk rebuild executor", e);
            oldExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        rebuildQueue.clear();
        rebuildTasks.clear();
        synchronized (specialChunkGeometryLock) {
            for (ChunkSpecialRenderData chunkSpecialRenderData : specialChunkGeometry.values()) {
                chunkSpecialRenderData.close();
            }
            specialChunkGeometry.clear();
        }
        builtChunkBuildOrigins.clear();
        visibleChunkFrames.clear();
        chunkTraversalData.clear();
    }

    public static void enqueueRebuild(ChunkBuilder.BuiltChunk chunk) {
        rebuildQueue.put(chunk.index, chunk);
    }

    public static void markLightDirtySection(ChunkSectionPos sectionPos, int lightTypeOrdinal) {
        markLightDirtySection(sectionPos.getSectionX(), sectionPos.getSectionY(),
            sectionPos.getSectionZ(), lightTypeOrdinal);
    }

    public static void rebuild(Camera camera, List<ChunkBuilder.BuiltChunk> visibleBuiltChunks) {
        rebuildFrameCounter++;
        if (visibleBuiltChunks != null) {
            for (ChunkBuilder.BuiltChunk visibleBuiltChunk : visibleBuiltChunks) {
                if (visibleBuiltChunk != null) {
                    visibleChunkFrames.put(visibleBuiltChunk.index, rebuildFrameCounter);
                }
            }
        }

        BlockPos cameraBlockPos = camera.getBlockPos();
        Vec3d cameraPos = camera.getPos();
        Vec3d cameraForward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).normalize();
        List<Integer> processedChunks = new ArrayList<>();
        for (Map.Entry<Integer, ChunkBuilder.BuiltChunk> rebuildEntry : rebuildQueue.entrySet()) {
            ChunkBuilder.BuiltChunk builtChunk = rebuildEntry.getValue();
            if (builtChunk == null) {
                processedChunks.add(rebuildEntry.getKey());
                continue;
            }

            if (!builtChunk.needsRebuild() || !builtChunk.shouldBuild()) {
                if (!builtChunk.needsRebuild()) {
                    processedChunks.add(rebuildEntry.getKey());
                }
                continue;
            }

            BlockPos chunkCenterPos = builtChunk.getOrigin().add(8, 8, 8);
            double distanceChunks = Math.sqrt(chunkCenterPos.getSquaredDistance(cameraBlockPos))
                / 16.0;
            boolean isImportant = chunkCenterPos.getSquaredDistance(cameraBlockPos) < 768.0
                || builtChunk.needsImportantRebuild();
            long chunkOriginKey = builtChunk.getOrigin().asLong();
            long lastVisibleFrame = visibleChunkFrames.getOrDefault(builtChunk.index,
                Long.MIN_VALUE);
            boolean recentlyVisible = lastVisibleFrame != Long.MIN_VALUE
                && rebuildFrameCounter - lastVisibleFrame <= VISIBLE_REBUILD_GRACE_FRAMES;
            boolean potentiallyViewContributing = isPotentiallyViewContributing(cameraPos,
                cameraForward, chunkCenterPos, distanceChunks);

            if (!isImportant) {
                if (!recentlyVisible && !potentiallyViewContributing) {
                    continue;
                }
                if (!potentiallyViewContributing
                    && chunkOriginKey == builtChunkBuildOrigins.getOrDefault(builtChunk.index,
                    Long.MIN_VALUE)) {
                    int updateInterval = RayTracingTuning.terrainUpdateIntervalFrames(
                        distanceChunks);
                    long scheduleBucket = Math.floorMod(rebuildFrameCounter + builtChunk.index,
                        updateInterval);
                    if (scheduleBucket != 0L) {
                        continue;
                    }
                }
            }

            boolean scheduled = false;
            if (isImportant) {
                try {
                    Future<?> rebuildTask = importantChunkRebuildExecutor.submit(() ->
                        rebuildSingle(builtChunk, true));
                    rebuildTasks.add(rebuildTask);
                    scheduled = true;
                } catch (RejectedExecutionException e) {
                    RadianceClient.LOGGER.warn("Important chunk rebuild task rejected for chunk {}, will retry",
                        builtChunk.index);
                    // Do NOT add to processedChunks - chunk remains in rebuildQueue for retry
                    continue;
                }
            } else {
                try {
                    backgroundChunkRebuildExecutor.execute(() -> rebuildSingle(builtChunk, false));
                    scheduled = true;
                } catch (RejectedExecutionException e) {
                    RadianceClient.LOGGER.warn("Background chunk rebuild task rejected for chunk {}, will retry",
                        builtChunk.index);
                    // Do NOT add to processedChunks - chunk remains in rebuildQueue for retry
                    continue;
                }
            }

            builtChunk.cancelRebuild();
            processedChunks.add(rebuildEntry.getKey());
        }

        for (Integer processedChunk : processedChunks) {
            rebuildQueue.remove(processedChunk);
        }
    }

    private static boolean isPotentiallyViewContributing(Vec3d cameraPos, Vec3d cameraForward,
        BlockPos chunkCenterPos, double distanceChunks) {
        double farFieldStartDistanceChunks = RayTracingTuning.getFarFieldStartDistanceChunks();
        if (distanceChunks <= farFieldStartDistanceChunks
            * VIEW_CONTRIBUTION_NEAR_DISTANCE_MULTIPLIER) {
            return true;
        }

        Vec3d toChunk = new Vec3d(
            chunkCenterPos.getX() - cameraPos.x,
            chunkCenterPos.getY() - cameraPos.y,
            chunkCenterPos.getZ() - cameraPos.z);
        double distanceBlocks = toChunk.length();
        if (distanceBlocks <= 1e-6) {
            return true;
        }

        double angularRadiusDegrees = Math.toDegrees(Math.asin(Math.min(1.0,
            CHUNK_BOUNDING_RADIUS_BLOCKS / Math.max(distanceBlocks, CHUNK_BOUNDING_RADIUS_BLOCKS))));
        double halfAngleDegrees = Math.min(179.0,
            VIEW_CONTRIBUTION_BASE_HALF_ANGLE_DEGREES + angularRadiusDegrees);
        double dotThreshold = Math.cos(Math.toRadians(halfAngleDegrees));
        return toChunk.multiply(1.0 / distanceBlocks).dotProduct(cameraForward) >= dotThreshold;
    }

    public static void waitImportantChunkRebuild() {
        if (rebuildTasks.isEmpty()) {
            return;
        }

        for (Future<?> rebuildTask : rebuildTasks) {
            try {
                rebuildTask.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        rebuildTasks.clear();
    }

    private static void rebuildSingle(ChunkBuilder.BuiltChunk builtChunk, boolean important) {
        try (var scope = scopedBlockBufferAllocatorStorage()) {
            ChunkRendererRegionBuilder chunkRendererRegionBuilder = new ChunkRendererRegionBuilder();
            IChunkBuilderBuiltChunkExt builtChunkExt = (IChunkBuilderBuiltChunkExt) builtChunk;
            ChunkBuilder chunkBuilder = builtChunkExt.radiance$getChunkBuilder();
            IChunkBuilderExt chunkBuilderExt = (IChunkBuilderExt) chunkBuilder;
            ChunkRendererRegion
                chunkRendererRegion =
                chunkRendererRegionBuilder.build(chunkBuilderExt.radiance$getWorld(),
                    ChunkSectionPos.from(builtChunk.getSectionPos()));

            if (chunkRendererRegion == null) {
                clearSpecialChunkGeometry(builtChunk.index);
                builtChunkBuildOrigins.remove(builtChunk.index);
                chunkTraversalData.remove(builtChunk.index);
                invalidateSingle(builtChunk.index);
                builtChunk.data.set(ChunkBuilder.ChunkData.EMPTY);
                return;
            }

            BlockBufferAllocatorStorage storage = blockBufferAllocatorStorageThreadLocal.get();
            rebuildSingle(chunkRendererRegion, chunkBuilder, chunkBuilderExt, builtChunk, storage,
                important);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void rebuildSingle(ChunkRendererRegion chunkRendererRegion,
        ChunkBuilder chunkBuilder,
        IChunkBuilderExt chunkBuilderExt,
        ChunkBuilder.BuiltChunk builtChunk,
        BlockBufferAllocatorStorage storage,
        boolean important) {

        ChunkSectionPos chunkSectionPos = ChunkSectionPos.from(builtChunk.getOrigin());
        if (RayTracingTuning.shouldCaptureTraversalMetadata()) {
            chunkTraversalData.put((long) builtChunk.index,
                captureChunkTraversalData(chunkRendererRegion, chunkSectionPos));
        } else {
            chunkTraversalData.remove(builtChunk.index);
        }

        Vec3d vec3d = chunkBuilder.getCameraPosition();
        // TODO: cancel out the sort operation in section builder
        VertexSorter
            vertexSorter =
            VertexSorter.byDistance((float) (vec3d.x - builtChunk.getOrigin()
                    .getX()),
                (float) (vec3d.y - builtChunk.getOrigin()
                    .getY()),
                (float) (vec3d.z - builtChunk.getOrigin()
                    .getZ()));

        SectionBuilder.RenderData renderData;
        synchronized (ChunkBuilder.class) {
            renderData =
                ((IChunkBuilderExt) chunkBuilder).radiance$getSectionBuilder()
                    .build(chunkSectionPos, chunkRendererRegion, vertexSorter, storage);
        }

        Map<RenderLayer, BuiltBuffer> buffers = preprocessChunkBuffers(renderData.buffers);
        builtChunk.setNoCullingBlockEntities(renderData.noCullingBlockEntities);
        long lightStateHash = computeLightStateHash(chunkRendererRegion, chunkSectionPos);
        double distanceChunks = Math.sqrt(
            builtChunk.getOrigin().add(8, 8, 8).getSquaredDistance(vec3d.x, vec3d.y, vec3d.z))
            / 16.0;

        Map<RenderLayer, BuiltBuffer> blasBuffers = new HashMap<>();
        List<BuiltBuffer> specialBuffers = new ArrayList<>();
        EntityProxy.EntityRenderData specialRenderData = new EntityProxy.EntityRenderData(
            Long.hashCode(builtChunk.index),
            builtChunk.getOrigin().getX(),
            builtChunk.getOrigin().getY(),
            builtChunk.getOrigin().getZ(),
            Constants.RayTracingFlags.WORLD.getValue(),
            -1,
            false);

        for (Map.Entry<RenderLayer, BuiltBuffer> entry : buffers.entrySet()) {
            RenderLayer renderLayer = entry.getKey();
            BuiltBuffer vertexBuffer = entry.getValue();
            RayTracingTuning.ChunkGeometryRoute chunkGeometryRoute =
                RayTracingTuning.classifyChunkLayer(renderLayer, distanceChunks);

            if (chunkGeometryRoute == RayTracingTuning.ChunkGeometryRoute.BLAS) {
                blasBuffers.put(renderLayer, vertexBuffer);
                continue;
            }

            if (chunkGeometryRoute == RayTracingTuning.ChunkGeometryRoute.DROP) {
                continue;
            }

            BuiltBuffer specialBuffer = cloneBuiltBuffer(vertexBuffer);
            specialBuffers.add(specialBuffer);
            specialRenderData.add(new EntityProxy.EntityRenderLayer(renderLayer, specialBuffer,
                chunkGeometryRoute == RayTracingTuning.ChunkGeometryRoute.SPECIAL_REFLECTIVE));
        }

        ChunkSpecialRenderData chunkSpecialRenderData =
            specialRenderData.isEmpty() ? null : new ChunkSpecialRenderData(specialRenderData,
                specialBuffers);
        replaceSpecialChunkGeometry(builtChunk.index, chunkSpecialRenderData);

        boolean hasSubmittedGeometry = !blasBuffers.isEmpty() || chunkSpecialRenderData != null;
        ChunkBuilder.ChunkData chunkData = new ChunkBuilder.ChunkData() {
            @Override
            public List<BlockEntity> getBlockEntities() {
                return renderData.blockEntities;
            }

            @Override
            public boolean isVisibleThrough(Direction from, Direction to) {
                return renderData.chunkOcclusionData.isVisibleThrough(from, to);
            }

            @Override
            public boolean isEmpty(RenderLayer layer) {
                return !hasSubmittedGeometry;
            }
        };
        builtChunk.data.set(chunkData);
        builtChunkBuildOrigins.put(builtChunk.index, builtChunk.getOrigin().asLong());
        builtChunkNum++;

        if (blasBuffers.isEmpty()) {
            invalidateSingle(builtChunk.index);
        } else {
            ByteBuffer geometryTypeBB = null;
            ByteBuffer geometryGroupNameBB = null;
            ByteBuffer geometryTextureBB = null;
            ByteBuffer vertexFormatBB = null;
            ByteBuffer vertexCountBB = null;
            ByteBuffer verticesBB = null;
            List<ByteBuffer> geometryGroupNameBuffers = new ArrayList<>(blasBuffers.size());

            try {
                int geometryTypeSize = blasBuffers.size() * Integer.BYTES;
                geometryTypeBB = MemoryUtil.memAlloc(geometryTypeSize);
                long geometryTypeAddr = memAddress(geometryTypeBB);
                int geometryTypeBaseAddr = 0;

                int geometryGroupNameSize = blasBuffers.size() * Long.BYTES;
                geometryGroupNameBB = MemoryUtil.memAlloc(geometryGroupNameSize);
                long geometryGroupNameAddr = memAddress(geometryGroupNameBB);
                int geometryGroupNameBaseAddr = 0;

                int geometryTextureSize = blasBuffers.size() * Integer.BYTES;
                geometryTextureBB = MemoryUtil.memAlloc(geometryTextureSize);
                long geometryTextureAddr = memAddress(geometryTextureBB);
                int geometryTextureBaseAddr = 0;

                int vertexFormatSize = blasBuffers.size() * Integer.BYTES;
                vertexFormatBB = MemoryUtil.memAlloc(vertexFormatSize);
                long vertexFormatAddr = memAddress(vertexFormatBB);
                int vertexFormatBaseAddr = 0;

                int vertexCountSize = blasBuffers.size() * Integer.BYTES;
                vertexCountBB = MemoryUtil.memAlloc(vertexCountSize);
                long vertexCountAddr = memAddress(vertexCountBB);
                int vertexCountBaseAddr = 0;

                int verticesSize = blasBuffers.size() * Long.BYTES;
                verticesBB = MemoryUtil.memAlloc(verticesSize);
                long verticesAddr = memAddress(verticesBB);
                int verticesBaseAddr = 0;

                for (Map.Entry<RenderLayer, BuiltBuffer> entry : blasBuffers.entrySet()) {
                    RenderLayer renderLayer = entry.getKey();
                    assert renderLayer.getDrawMode() == QUADS;

                    BuiltBuffer vertexBuffer = entry.getValue();
                    BufferProxy.BufferInfo vertexBufferInfo = BufferProxy.getBufferInfo(
                        vertexBuffer.getBuffer());
                    assert vertexBuffer.getDrawParameters().indexCount()
                        == vertexBuffer.getDrawParameters().vertexCount() / 4 * 6;

                    TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();

                    int geometryTypeID = Constants.GeometryTypes.getGeometryType(renderLayer,
                        RayTracingTuning.shouldReflectBlasLayer(renderLayer)).getValue();
                    int geometryTextureID = TextureTracker.getRenderLayerTextureGlId(renderLayer,
                        textureManager, MissingSprite.getMissingSpriteId());
                    int vertexFormatID = Constants.VertexFormats.getValue(
                        vertexBuffer.getDrawParameters().format());

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

                    vertexCountBB.putInt(vertexCountBaseAddr,
                        vertexBuffer.getDrawParameters().vertexCount());
                    vertexCountBaseAddr += Integer.BYTES;

                    verticesBB.putLong(verticesBaseAddr, vertexBufferInfo.addr());
                    verticesBaseAddr += Long.BYTES;
                }

                rebuildSingle(
                    builtChunk.getOrigin().getX(),
                    builtChunk.getOrigin().getY(),
                    builtChunk.getOrigin().getZ(),
                    builtChunk.index,
                    blasBuffers.size(),
                    geometryTypeAddr,
                    geometryGroupNameAddr,
                    geometryTextureAddr,
                    vertexFormatAddr,
                    vertexCountAddr,
                    verticesAddr,
                    lightStateHash,
                    important);
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
                if (vertexCountBB != null) {
                    MemoryUtil.memFree(vertexCountBB);
                }
                if (verticesBB != null) {
                    MemoryUtil.memFree(verticesBB);
                }
                for (ByteBuffer geometryGroupNameBuffer : geometryGroupNameBuffers) {
                    MemoryUtil.memFree(geometryGroupNameBuffer);
                }
            }
        }

        for (Map.Entry<RenderLayer, BuiltBuffer> entry : buffers.entrySet()) {
            entry.getValue()
                .close();
        }
    }

    public static void queueSpecialGeometry(List<ChunkBuilder.BuiltChunk> builtChunks, Camera camera) {
        if (builtChunks == null || builtChunks.isEmpty()) {
            return;
        }

        synchronized (specialChunkGeometryLock) {
            EntityProxy.EntityRenderDataList entityRenderDataList = new EntityProxy.EntityRenderDataList();
            for (ChunkBuilder.BuiltChunk builtChunk : builtChunks) {
                ChunkSpecialRenderData chunkSpecialRenderData = specialChunkGeometry.get(
                    builtChunk.index);
                if (chunkSpecialRenderData == null) {
                    continue;
                }

                entityRenderDataList.add(chunkSpecialRenderData.entityRenderData);
            }

            if (entityRenderDataList.isEmpty()) {
                return;
            }

            EntityProxy.queueBuildWithoutClose(entityRenderDataList);
        }
    }

    private static void replaceSpecialChunkGeometry(long index,
        ChunkSpecialRenderData chunkSpecialRenderData) {
        synchronized (specialChunkGeometryLock) {
            ChunkSpecialRenderData previous = specialChunkGeometry.remove(index);
            if (previous != null) {
                previous.close();
            }

            if (chunkSpecialRenderData != null) {
                specialChunkGeometry.put(index, chunkSpecialRenderData);
            }
        }
    }

    private static void clearSpecialChunkGeometry(long index) {
        synchronized (specialChunkGeometryLock) {
            ChunkSpecialRenderData previous = specialChunkGeometry.remove(index);
            if (previous != null) {
                previous.close();
            }
        }
    }

    private static BuiltBuffer cloneBuiltBuffer(BuiltBuffer builtBuffer) {
        ByteBuffer sourceSlice = builtBuffer.getBuffer().slice();
        BufferAllocator bufferAllocator = new BufferAllocator(sourceSlice.remaining());
        long targetAddress = bufferAllocator.allocate(sourceSlice.remaining());
        MemoryUtil.memCopy(memAddress(sourceSlice), targetAddress, sourceSlice.remaining());

        BufferAllocator.CloseableBuffer closeableBuffer = bufferAllocator.getAllocated();
        if (closeableBuffer == null) {
            bufferAllocator.close();
            throw new IllegalStateException("Failed to clone built buffer");
        }

        BuiltBuffer.DrawParameters drawParameters = builtBuffer.getDrawParameters();
        return new BuiltBuffer(closeableBuffer,
            new BuiltBuffer.DrawParameters(
                drawParameters.format(),
                drawParameters.vertexCount(),
                drawParameters.indexCount(),
                drawParameters.mode(),
                drawParameters.indexType()));
    }

    private static ChunkTraversalData captureChunkTraversalData(
        ChunkRendererRegion chunkRendererRegion,
        ChunkSectionPos chunkSectionPos) {
        BlockPos minPos = chunkSectionPos.getMinPos();
        boolean captureMaterialPalette = RayTracingTuning.shouldCaptureMaterialPalette();
        boolean captureFaceMask = RayTracingTuning.shouldCaptureFaceMask();
        int macrocellSize = RayTracingTuning.effectiveMacrocellSize();
        ChunkTraversalData chunkTraversalData = new ChunkTraversalData(
            RayTracingTuning.getChunkDataLayout(), RayTracingTuning.getChunkTraversalMode(),
            captureMaterialPalette, captureFaceMask, macrocellSize);
        Map<Integer, Short> paletteLookup = captureMaterialPalette ? new HashMap<>() : null;
        List<Integer> paletteValues = captureMaterialPalette ? new ArrayList<>() : null;

        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localY = 0; localY < 16; localY++) {
                for (int localX = 0; localX < 16; localX++) {
                    BlockPos blockPos = minPos.add(localX, localY, localZ);
                    BlockState blockState = chunkRendererRegion.getBlockState(blockPos);
                    if (blockState.isAir()) {
                        continue;
                    }

                    int voxelIndex = localX | (localY << 4) | (localZ << 8);
                    chunkTraversalData.markOccupied(voxelIndex);
                    chunkTraversalData.markMacrocell(localX, localY, localZ);

                    if (captureMaterialPalette) {
                        int stateId = Block.getRawIdFromState(blockState);
                        short paletteIndex = paletteLookup.computeIfAbsent(stateId, unused -> {
                            short nextIndex = (short) paletteValues.size();
                            paletteValues.add(stateId);
                            return nextIndex;
                        });
                        chunkTraversalData.materialIndices[voxelIndex] = paletteIndex;
                    }

                    if (captureFaceMask) {
                        byte faceMask = 0;
                        for (Direction direction : Direction.values()) {
                            BlockState neighborState = chunkRendererRegion.getBlockState(
                                blockPos.offset(direction));
                            if (neighborState.isAir() || !neighborState.isOpaqueFullCube()) {
                                faceMask |= (byte) (1 << direction.getId());
                            }
                        }
                        chunkTraversalData.faceMask[voxelIndex] = faceMask;
                    }
                }
            }
        }

        if (captureMaterialPalette) {
            chunkTraversalData.materialPalette = paletteValues.stream()
                .mapToInt(Integer::intValue)
                .toArray();
        }
        return chunkTraversalData;
    }

    private static long computeLightStateHash(ChunkRendererRegion chunkRendererRegion,
        ChunkSectionPos chunkSectionPos) {
        BlockColors blockColors = MinecraftClient.getInstance().getBlockColors();
        IBlockColorsExt blockColorsExt =
            blockColors instanceof IBlockColorsExt ext ? ext : null;
        return ChunkLightCollector.collectHash(chunkRendererRegion, chunkSectionPos, blockColorsExt);
    }

    private static long mixLightHash(long hash, int value) {
        hash ^= (value & 0xFF);
        hash *= FNV64_PRIME;
        hash ^= ((value >>> 8) & 0xFF);
        hash *= FNV64_PRIME;
        hash ^= ((value >>> 16) & 0xFF);
        hash *= FNV64_PRIME;
        hash ^= ((value >>> 24) & 0xFF);
        hash *= FNV64_PRIME;
        return hash;
    }

    private static Map<RenderLayer, BuiltBuffer> preprocessChunkBuffers(
        Map<RenderLayer, BuiltBuffer> buffers) {
        if (!RayTracingTuning.shouldAttemptTerrainMeshingOptimization() || buffers.isEmpty()) {
            return buffers;
        }

        RayTracingTuning.TerrainMeshingMode terrainMeshingMode =
            RayTracingTuning.getEffectiveTerrainMeshingMode();
        int greedyMergeMaxSpan = RayTracingTuning.terrainGreedyMergeMaxSpan();
        if (RayTracingTuning.getWorldRepresentationMode()
            == RayTracingTuning.WorldRepresentationMode.CHUNK_AABB) {
            greedyMergeMaxSpan = Math.min(64, greedyMergeMaxSpan * 2);
        }
        switch (RayTracingTuning.getChunkTraversalMode()) {
            case BRICK -> greedyMergeMaxSpan = Math.min(64, greedyMergeMaxSpan * 2);
            case MACROCELL -> greedyMergeMaxSpan = 64;
            default -> {
            }
        }
        switch (RayTracingTuning.getChunkMacrocellSize()) {
            case SIZE_4 -> greedyMergeMaxSpan = Math.min(64, greedyMergeMaxSpan * 2);
            case SIZE_8 -> greedyMergeMaxSpan = 64;
            case DISABLED -> {
            }
        }
        Map<RenderLayer, BuiltBuffer> optimizedBuffers = new HashMap<>(buffers.size());
        for (Map.Entry<RenderLayer, BuiltBuffer> entry : buffers.entrySet()) {
            BuiltBuffer originalBuffer = entry.getValue();
            BuiltBuffer optimizedBuffer = AtlasSafeGreedyMesher.optimize(originalBuffer,
                terrainMeshingMode, greedyMergeMaxSpan);
            if (optimizedBuffer != originalBuffer) {
                originalBuffer.close();
            }
            optimizedBuffers.put(entry.getKey(), optimizedBuffer);
        }
        return optimizedBuffers;
    }

    private static native void rebuildSingle(int originX,
        int originY,
        int originZ,
        long index,
        int size,
        long geometryTypes,
        long geometryGroupNames,
        long geometryTextures,
        long vertexFormats,
        long vertexCounts,
        long vertices,
        long lightStateHash,
        boolean important);

    public static native boolean isChunkReady(long index);

    public static boolean isChunkReady(ChunkBuilder.BuiltChunk builtChunk) {
        return isChunkReady(builtChunk.index);
    }

    public static native void invalidateSingle(long index);

    public static native void markLightDirtySection(int sectionX, int sectionY, int sectionZ,
        int lightType);

    private static final class ChunkSpecialRenderData {

        private final EntityProxy.EntityRenderData entityRenderData;
        private final List<BuiltBuffer> ownedBuffers;

        private ChunkSpecialRenderData(EntityProxy.EntityRenderData entityRenderData,
            List<BuiltBuffer> ownedBuffers) {
            this.entityRenderData = entityRenderData;
            this.ownedBuffers = ownedBuffers;
        }

        private void close() {
            for (BuiltBuffer ownedBuffer : ownedBuffers) {
                ownedBuffer.close();
            }
        }
    }

    /**
     * Java-side staging data for route-B traversal. Native consumption is still pending, but the
     * chunk rebuild now materializes occupancy, palette, face-mask, and macrocell masks so the
     * data path exists instead of living only on paper.
     */
    private static final class ChunkTraversalData {

        private final long[] occupancyBitmask = new long[64];
        private final byte[] faceMask = new byte[16 * 16 * 16];
        private final short[] materialIndices = new short[16 * 16 * 16];
        private final int dataLayoutOrdinal;
        private final int traversalModeOrdinal;
        private final boolean capturesMaterialPalette;
        private final boolean capturesFaceMask;
        private final int macrocellSize;
        private int[] materialPalette = new int[0];
        private long macrocell4Mask = 0L;
        private byte macrocell8Mask = 0;
        private int occupiedVoxelCount = 0;

        private ChunkTraversalData(RayTracingTuning.ChunkDataLayout chunkDataLayout,
            RayTracingTuning.ChunkTraversalMode chunkTraversalMode,
            boolean capturesMaterialPalette,
            boolean capturesFaceMask,
            int macrocellSize) {
            this.dataLayoutOrdinal = chunkDataLayout.ordinal();
            this.traversalModeOrdinal = chunkTraversalMode.ordinal();
            this.capturesMaterialPalette = capturesMaterialPalette;
            this.capturesFaceMask = capturesFaceMask;
            this.macrocellSize = macrocellSize;
        }

        private void markOccupied(int voxelIndex) {
            occupancyBitmask[voxelIndex >> 6] |= 1L << (voxelIndex & 63);
            occupiedVoxelCount++;
        }

        private void markMacrocell(int localX, int localY, int localZ) {
            if (macrocellSize == 4) {
                macrocell4Mask |= 1L << (
                    (localX >> 2) | ((localY >> 2) << 2) | ((localZ >> 2) << 4));
            } else if (macrocellSize == 8) {
                macrocell8Mask |= (byte) (1 << (
                    (localX >> 3) | ((localY >> 3) << 1) | ((localZ >> 3) << 2)));
            }
        }
    }
}
