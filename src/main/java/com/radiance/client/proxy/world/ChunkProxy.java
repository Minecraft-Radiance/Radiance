package com.radiance.client.proxy.world;

import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.mojang.blaze3d.systems.VertexSorter;
import com.radiance.client.constant.Constants;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.replay.hook.ChunkRebuildCapture;
import com.radiance.client.replay.hook.ReplayCaptureHooks;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderBuiltChunkExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.system.MemoryUtil;

public class ChunkProxy {

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
    private static final java.util.Set<Integer> forcedRebuildIndices = ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Integer> nativeScenePendingIndices = ConcurrentHashMap.newKeySet();
    private static final List<Future<?>> rebuildTasks = new ArrayList<>();
    private static BuiltChunkStorage currentStorage = null;
    private static boolean pendingRebuildAll = false;
    private static int numChunkRebuildThreads = getChunkRebuildThreadCount();
    private static final int numImportantChunkRebuildThreads = 1;
    private static int numNormalChunkRebuildThreads = Math.max(1,
        numChunkRebuildThreads - numImportantChunkRebuildThreads);
    private static final ExecutorService
        importantChunkRebuildExecutor =
        Executors.newFixedThreadPool(numImportantChunkRebuildThreads, r -> {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });
    private static final ThreadLocal<BlockBufferAllocatorStorage>
        blockBufferAllocatorStorageThreadLocal =
        ThreadLocal.withInitial(BlockBufferAllocatorStorage::new);
    public static int builtChunkNum = 0;
    private static ExecutorService backgroundChunkRebuildExecutor = Executors.newFixedThreadPool(
        numNormalChunkRebuildThreads, r -> {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });

    public static native void initNative(int numChunks, int sizeX, int sizeY, int sizeZ,
        int bottomSectionCoord);

    public static native void updateSectionPosNative(int sectionX, int sectionY, int sectionZ);

    public static void init(int numChunks, int sizeX, int sizeY, int sizeZ,
        int bottomSectionCoord) {
        clear();
        ReplayCaptureHooks.chunkInit(numChunks, sizeX, sizeY, sizeZ, bottomSectionCoord);
        initNative(numChunks, sizeX, sizeY, sizeZ, bottomSectionCoord);
    }

    public static void updateSectionPos(ChunkSectionPos sectionPos) {
        ReplayCaptureHooks.chunkUpdateSectionPos(sectionPos.getSectionX(), sectionPos.getSectionY(),
            sectionPos.getSectionZ());
        updateSectionPosNative(sectionPos.getSectionX(), sectionPos.getSectionY(),
            sectionPos.getSectionZ());
    }

    public static void setStorage(BuiltChunkStorage storage) {
        currentStorage = storage;
        if (currentStorage != null && pendingRebuildAll) {
            pendingRebuildAll = false;
            queueRebuildAll(currentStorage);
        }
    }

    private static int getChunkRebuildThreadCount() {
        int expectedBufferTotal = RenderLayer.getBlockLayers()
            .stream()
            .mapToInt(RenderLayer::getExpectedBufferSize)
            .sum();
        int memoryLimited = Math.max(1,
            (int) (Runtime.getRuntime().maxMemory() * 0.3) / (expectedBufferTotal * 4) - 1);
        int userThreads = Options.chunkBuildingThreads;
        return Math.max(2,
            Math.min(userThreads, Math.min(Options.getMaxChunkBuildingThreads(), memoryLimited)));
    }

    public static AutoCloseable scopedBlockBufferAllocatorStorage() {
        final BlockBufferAllocatorStorage s = blockBufferAllocatorStorageThreadLocal.get();
        s.reset();
        return s::clear;
    }

    public static void clear() {
        waitImportantChunkRebuild();

        backgroundChunkRebuildExecutor.shutdown();
        try {
            backgroundChunkRebuildExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        numChunkRebuildThreads = getChunkRebuildThreadCount();
        numNormalChunkRebuildThreads = Math.max(1,
            numChunkRebuildThreads - numImportantChunkRebuildThreads);
        backgroundChunkRebuildExecutor = Executors.newFixedThreadPool(numNormalChunkRebuildThreads,
            r -> {
                Thread thread = new Thread(r);
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            });

        rebuildQueue.clear();
        forcedRebuildIndices.clear();
        nativeScenePendingIndices.clear();
        rebuildTasks.clear();
        currentStorage = null;
        pendingRebuildAll = false;
    }

    public static void enqueueRebuild(ChunkBuilder.BuiltChunk chunk) {
        rebuildQueue.put(chunk.index, chunk);
    }

    public static void rebuildAll() {
        if (currentStorage == null || currentStorage.chunks == null) {
            pendingRebuildAll = true;
            return;
        }

        queueRebuildAll(currentStorage);
    }

    private static void queueRebuildAll(BuiltChunkStorage storage) {
        if (storage == null || storage.chunks == null) {
            pendingRebuildAll = true;
            return;
        }

        for (ChunkBuilder.BuiltChunk builtChunk : storage.chunks) {
            if (builtChunk == null) {
                continue;
            }
            forcedRebuildIndices.add(builtChunk.index);
            builtChunk.scheduleRebuild(true);
            enqueueRebuild(builtChunk);
        }
    }

    public static void rebuild(Camera camera) {

        BlockPos blockPos = camera.getBlockPos();
        for (Map.Entry<Integer, ChunkBuilder.BuiltChunk> entry : rebuildQueue.entrySet()) {
            ChunkBuilder.BuiltChunk builtChunk = entry.getValue();
            boolean forced = forcedRebuildIndices.contains(builtChunk.index);
            if (!builtChunk.needsRebuild()) {
                rebuildQueue.remove(entry.getKey(), builtChunk);
                forcedRebuildIndices.remove(builtChunk.index);
                continue;
            }
            if (!forced && !builtChunk.shouldBuild()) {
                continue;
            }
            if (!rebuildQueue.remove(entry.getKey(), builtChunk)) {
                continue;
            }
            forcedRebuildIndices.remove(builtChunk.index);
            builtChunk.cancelRebuild();

            BlockPos
                chunkCenterPos =
                builtChunk.getOrigin()
                    .add(8, 8, 8);
            boolean isImportant = chunkCenterPos.getSquaredDistance(blockPos) < 768.0
                || builtChunk.needsImportantRebuild();

            if (isImportant) {
                Future<?> rebuildTask = importantChunkRebuildExecutor.submit(() -> {
                    rebuildSingle(builtChunk, true);
                });
                rebuildTasks.add(rebuildTask);
            } else {
                backgroundChunkRebuildExecutor.execute(() -> {
                    rebuildSingle(builtChunk, false);
                });
            }
        }
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

        SectionBuilder.RenderData renderData =
            ((IChunkBuilderExt) chunkBuilder).radiance$getSectionBuilder()
                .build(chunkSectionPos, chunkRendererRegion, vertexSorter, storage);

        Map<RenderLayer, BuiltBuffer> buffers = renderData.buffers;
        builtChunk.setNoCullingBlockEntities(renderData.noCullingBlockEntities);

        if (buffers.isEmpty()) {
            nativeScenePendingIndices.remove(builtChunk.index);
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
                    return true;
                }
            };
            builtChunk.data.set(chunkData);
            builtChunkNum++;

            invalidateSingle(builtChunk.index);
        } else {
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
                    return layer != null && !buffers.containsKey(layer);
                }
            };

            ByteBuffer geometryTypeBB = null;
            ByteBuffer geometryGroupNameBB = null;
            ByteBuffer geometryTextureBB = null;
            ByteBuffer rasterMetadataBB = null;
            ByteBuffer sceneTagBB = null;
            ByteBuffer vertexFormatBB = null;
            ByteBuffer vertexCountBB = null;
            ByteBuffer verticesBB = null;
            List<ByteBuffer> geometryGroupNameBuffers = new ArrayList<>(buffers.size());
            boolean replayCaptureActive = ReplayCaptureHooks.isActive();
            List<ChunkRebuildCapture.Geometry> replayGeometries =
                replayCaptureActive ? new ArrayList<>(buffers.size()) : List.of();

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

                int rasterMetadataSize = buffers.size() * Constants.RasterMetadata.INT_STRIDE * Integer.BYTES;
                rasterMetadataBB = MemoryUtil.memAlloc(rasterMetadataSize);
                long rasterMetadataAddr = memAddress(rasterMetadataBB);
                int rasterMetadataBaseIndex = 0;

                int sceneTagSize = buffers.size() * Constants.SceneTags.INT_STRIDE * Integer.BYTES;
                sceneTagBB = MemoryUtil.memAlloc(sceneTagSize);
                long sceneTagAddr = memAddress(sceneTagBB);
                int sceneTagBaseIndex = 0;

                int vertexFormatSize = buffers.size() * Integer.BYTES;
                vertexFormatBB = MemoryUtil.memAlloc(vertexFormatSize);
                long vertexFormatAddr = memAddress(vertexFormatBB);
                int vertexFormatBaseAddr = 0;

                int vertexCountSize = buffers.size() * Integer.BYTES;
                vertexCountBB = MemoryUtil.memAlloc(vertexCountSize);
                long vertexCountAddr = memAddress(vertexCountBB);
                int vertexCountBaseAddr = 0;

                int verticesSize = buffers.size() * Long.BYTES;
                verticesBB = MemoryUtil.memAlloc(verticesSize);
                long verticesAddr = memAddress(verticesBB);
                int verticesBaseAddr = 0;

                int[] rasterMetadata = new int[Constants.RasterMetadata.INT_STRIDE];
                int[] sceneTags = new int[Constants.SceneTags.INT_STRIDE];
                for (Map.Entry<RenderLayer, BuiltBuffer> entry : buffers.entrySet()) {
                    RenderLayer renderLayer = entry.getKey();
                    assert renderLayer.getDrawMode() == QUADS;

                    BuiltBuffer vertexBuffer = entry.getValue();
                    BufferProxy.BufferInfo vertexBufferInfo = BufferProxy.getBufferInfo(
                        vertexBuffer.getBuffer());
                    assert vertexBuffer.getDrawParameters()
                        .indexCount() == vertexBuffer.getDrawParameters()
                        .vertexCount() / 4 * 6;

                    TextureManager
                        textureManager =
                        MinecraftClient.getInstance()
                            .getTextureManager();

                    int
                        geometryTypeID =
                        Constants.GeometryTypes.getGeometryType(renderLayer, true)
                            .getValue();
                    int
                        geometryTextureID =
                        textureManager.getTexture(
                                ((RenderLayer.MultiPhase) renderLayer).phases.texture.getId()
                                    .orElse(MissingSprite.getMissingSpriteId()))
                            .getGlId();
                    int vertexFormatID = Constants.VertexFormats.getValue(
                        vertexBuffer.getDrawParameters()
                            .format());
                    byte[] replayVertexBytes = null;
                    if (replayCaptureActive) {
                        replayVertexBytes = new byte[vertexBufferInfo.size()];
                        ByteBuffer replayVertexView = vertexBuffer.getBuffer().slice();
                        replayVertexView.get(replayVertexBytes);
                    }

                    geometryTypeBB.putInt(geometryTypeBaseAddr, geometryTypeID);
                    geometryTypeBaseAddr += Integer.BYTES;

                    ByteBuffer geometryGroupNameBuffer = MemoryUtil.memUTF8(renderLayer.name, true);
                    geometryGroupNameBuffers.add(geometryGroupNameBuffer);
                    geometryGroupNameBB.putLong(geometryGroupNameBaseAddr,
                        memAddress(geometryGroupNameBuffer));
                    geometryGroupNameBaseAddr += Long.BYTES;

                    geometryTextureBB.putInt(geometryTextureBaseAddr, geometryTextureID);
                    geometryTextureBaseAddr += Integer.BYTES;

                    Constants.RasterMetadata.write(rasterMetadata, 0, renderLayer, true);
                    for (int metadataValue : rasterMetadata) {
                        rasterMetadataBB.putInt(rasterMetadataBaseIndex * Integer.BYTES, metadataValue);
                        rasterMetadataBaseIndex++;
                    }

                    Constants.SceneTags.writeVanillaChunk(sceneTags, 0);
                    for (int tagValue : sceneTags) {
                        sceneTagBB.putInt(sceneTagBaseIndex * Integer.BYTES, tagValue);
                        sceneTagBaseIndex++;
                    }

                    vertexFormatBB.putInt(vertexFormatBaseAddr, vertexFormatID);
                    vertexFormatBaseAddr += Integer.BYTES;

                    vertexCountBB.putInt(vertexCountBaseAddr,
                        vertexBuffer.getDrawParameters()
                            .vertexCount());
                    vertexCountBaseAddr += Integer.BYTES;

                    verticesBB.putLong(verticesBaseAddr, vertexBufferInfo.addr());
                    verticesBaseAddr += Long.BYTES;

                    if (replayCaptureActive) {
                        replayGeometries.add(new ChunkRebuildCapture.Geometry(
                            geometryTypeID,
                            renderLayer.name,
                            geometryTextureID,
                            vertexFormatID,
                            vertexBuffer.getDrawParameters().vertexCount(),
                            replayVertexBytes));
                    }
                }

                if (replayCaptureActive) {
                    ReplayCaptureHooks.chunkRebuild(new ChunkRebuildCapture(
                        builtChunk.getOrigin().getX(),
                        builtChunk.getOrigin().getY(),
                        builtChunk.getOrigin().getZ(),
                        builtChunk.index,
                        important,
                        replayGeometries));
                }
                rebuildSingleWithSceneTags(builtChunk.getOrigin()
                        .getX(),
                    builtChunk.getOrigin()
                        .getY(),
                    builtChunk.getOrigin()
                        .getZ(),
                    builtChunk.index,
                    buffers.size(),
                    geometryTypeAddr,
                    geometryGroupNameAddr,
                    geometryTextureAddr,
                    Constants.RasterMetadata.VERSION,
                    rasterMetadataAddr,
                    Constants.RasterMetadata.INT_STRIDE,
                    Constants.SceneTags.VERSION,
                    sceneTagAddr,
                    Constants.SceneTags.INT_STRIDE,
                    vertexFormatAddr,
                    vertexCountAddr,
                    verticesAddr,
                    important);
                nativeScenePendingIndices.add(builtChunk.index);
                builtChunk.data.set(chunkData);
                builtChunkNum++;
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
                if (rasterMetadataBB != null) {
                    MemoryUtil.memFree(rasterMetadataBB);
                }
                if (sceneTagBB != null) {
                    MemoryUtil.memFree(sceneTagBB);
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
        boolean important);

    private static native void rebuildSingleWithRasterMetadata(int originX,
        int originY,
        int originZ,
        long index,
        int size,
        long geometryTypes,
        long geometryGroupNames,
        long geometryTextures,
        int rasterMetadataVersion,
        long rasterMetadata,
        int rasterMetadataIntStride,
        long vertexFormats,
        long vertexCounts,
        long vertices,
        boolean important);

    private static native void rebuildSingleWithSceneTags(int originX,
        int originY,
        int originZ,
        long index,
        int size,
        long geometryTypes,
        long geometryGroupNames,
        long geometryTextures,
        int rasterMetadataVersion,
        long rasterMetadata,
        int rasterMetadataIntStride,
        int sceneTagVersion,
        long sceneTags,
        int sceneTagIntStride,
        long vertexFormats,
        long vertexCounts,
        long vertices,
        boolean important);

    public static void rebuildSingleForReplay(int originX,
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
        boolean important) {
        rebuildSingle(originX, originY, originZ, index, size, geometryTypes, geometryGroupNames,
            geometryTextures, vertexFormats, vertexCounts, vertices, important);
    }

    public static native boolean isChunkReady(long index);

    public static boolean isChunkReady(ChunkBuilder.BuiltChunk builtChunk) {
        boolean ready = isChunkReady(builtChunk.index);
        if (ready) {
            nativeScenePendingIndices.remove(builtChunk.index);
        }
        return ready;
    }

    public static boolean isChunkRenderingReady(ChunkBuilder.BuiltChunk builtChunk) {
        if (!nativeScenePendingIndices.contains(builtChunk.index)) {
            return true;
        }
        if (!isChunkReady(builtChunk.index)) {
            return false;
        }
        nativeScenePendingIndices.remove(builtChunk.index);
        return true;
    }

    private static native void relocateSingleNative(long index, int originX, int originY,
        int originZ);

    public static void relocateSingle(long index, int originX, int originY, int originZ) {
        ReplayCaptureHooks.chunkRelocate(index, originX, originY, originZ);
        nativeScenePendingIndices.remove((int) index);
        relocateSingleNative(index, originX, originY, originZ);
    }

    private static native void invalidateSingleNative(long index);

    public static void invalidateSingle(long index) {
        ReplayCaptureHooks.chunkInvalidate(index);
        nativeScenePendingIndices.remove((int) index);
        invalidateSingleNative(index);
    }
}
