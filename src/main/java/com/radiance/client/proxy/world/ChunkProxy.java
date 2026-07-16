package com.radiance.client.proxy.world;

//#if MC>=260200
//$$ import com.radiance.client.constant.Constants;
//$$ import java.util.Arrays;
//$$ import java.util.Set;
//$$ import java.util.concurrent.ConcurrentHashMap;
//$$
//$$ public final class ChunkProxy {
//$$     private static final int UNASSIGNED_SECTION = Integer.MIN_VALUE;
//$$     private static final Set<Integer> nativeScenePendingIndices = ConcurrentHashMap.newKeySet();
//$$     private static int sizeX;
//$$     private static int sizeY;
//$$     private static int sizeZ;
//$$     private static int bottomSectionCoord;
//$$     private static int[] slotSectionX = new int[0];
//$$     private static int[] slotSectionY = new int[0];
//$$     private static int[] slotSectionZ = new int[0];
//$$     private static int currentSectionX;
//$$     private static int currentSectionY;
//$$     private static int currentSectionZ;
//$$     private static boolean hasCurrentSection;
//$$
//$$     private ChunkProxy() {
//$$     }
//$$
//$$     public static native void initNative(int numChunks, int sizeX, int sizeY, int sizeZ,
//$$         int bottomSectionCoord);
//$$
//$$     public static native void updateSectionPosNative(int sectionX, int sectionY, int sectionZ);
//$$
//$$     public static native void rebuildSingleWithSceneTags(int originX,
//$$         int originY,
//$$         int originZ,
//$$         long index,
//$$         int size,
//$$         long geometryTypes,
//$$         long geometryGroupNames,
//$$         long geometryTextures,
//$$         int rasterMetadataVersion,
//$$         long rasterMetadata,
//$$         int rasterMetadataIntStride,
//$$         int sceneTagVersion,
//$$         long sceneTags,
//$$         int sceneTagIntStride,
//$$         long vertexFormats,
//$$         long vertexCounts,
//$$         long vertices,
//$$         boolean important);
//$$
//$$     public static native boolean isChunkReady(long index);
//$$
//$$     private static native void relocateSingleNative(long index, int originX, int originY,
//$$         int originZ);
//$$
//$$     private static native void invalidateSingleNative(long index);
//$$
//$$     public static void initSodium(int renderDistance, int minSectionY, int maxSectionY) {
//$$         int resolvedSizeX = Math.max(1, renderDistance * 2 + 1);
//$$         int resolvedSizeY = Math.max(1, maxSectionY - minSectionY + 1);
//$$         int resolvedSizeZ = resolvedSizeX;
//$$         init(resolvedSizeX * resolvedSizeY * resolvedSizeZ, resolvedSizeX, resolvedSizeY,
//$$             resolvedSizeZ, minSectionY);
//$$     }
//$$
//$$     public static synchronized void init(int numChunks, int sizeX, int sizeY, int sizeZ,
//$$         int bottomSectionCoord) {
//$$         clear();
//$$         ChunkProxy.sizeX = sizeX;
//$$         ChunkProxy.sizeY = sizeY;
//$$         ChunkProxy.sizeZ = sizeZ;
//$$         ChunkProxy.bottomSectionCoord = bottomSectionCoord;
//$$         ChunkProxy.slotSectionX = filledSlots(numChunks);
//$$         ChunkProxy.slotSectionY = filledSlots(numChunks);
//$$         ChunkProxy.slotSectionZ = filledSlots(numChunks);
//$$         ChunkProxy.currentSectionX = 0;
//$$         ChunkProxy.currentSectionY = bottomSectionCoord;
//$$         ChunkProxy.currentSectionZ = 0;
//$$         ChunkProxy.hasCurrentSection = false;
//$$         initNative(numChunks, sizeX, sizeY, sizeZ, bottomSectionCoord);
//$$     }
//$$
//$$     public static synchronized void updateSodiumCameraSection(int sectionX, int sectionY,
//$$         int sectionZ) {
//$$         if (hasCurrentSection && currentSectionX == sectionX && currentSectionY == sectionY
//$$             && currentSectionZ == sectionZ) {
//$$             return;
//$$         }
//$$         currentSectionX = sectionX;
//$$         currentSectionY = sectionY;
//$$         currentSectionZ = sectionZ;
//$$         hasCurrentSection = true;
//$$         syncSodiumSlotOrigins();
//$$         updateSectionPosNative(sectionX, sectionY, sectionZ);
//$$     }
//$$
//$$     public static synchronized int sodiumSectionIndex(int sectionX, int sectionY,
//$$         int sectionZ) {
//$$         if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
//$$             return -1;
//$$         }
//$$         int localY = sectionY - bottomSectionCoord;
//$$         if (localY < 0 || localY >= sizeY) {
//$$             return -1;
//$$         }
//$$         if (hasCurrentSection) {
//$$             int viewDistance = (sizeX - 1) / 2;
//$$             if (Math.abs(sectionX - currentSectionX) > viewDistance
//$$                 || Math.abs(sectionZ - currentSectionZ) > viewDistance) {
//$$                 return -1;
//$$             }
//$$         }
//$$         return (Math.floorMod(sectionZ, sizeZ) * sizeY + localY) * sizeX
//$$             + Math.floorMod(sectionX, sizeX);
//$$     }
//$$
//$$     public static synchronized void submitSodiumSection(int originX, int originY, int originZ,
//$$         int sectionX, int sectionY, int sectionZ, int geometryCount, long geometryTypes,
//$$         long geometryGroupNames, long geometryTextures, long rasterMetadata, long sceneTags,
//$$         long vertexFormats, long vertexCounts, long vertices, boolean important) {
//$$         int index = sodiumSectionIndex(sectionX, sectionY, sectionZ);
//$$         if (index < 0) {
//$$             return;
//$$         }
//$$         if (!slotMatches(index, sectionX, sectionY, sectionZ)) {
//$$             return;
//$$         }
//$$         rebuildSingleWithSceneTags(originX, originY, originZ, index, geometryCount,
//$$             geometryTypes, geometryGroupNames, geometryTextures, Constants.RasterMetadata.VERSION,
//$$             rasterMetadata, Constants.RasterMetadata.INT_STRIDE, Constants.SceneTags.VERSION,
//$$             sceneTags, Constants.SceneTags.INT_STRIDE, vertexFormats, vertexCounts, vertices,
//$$             important);
//$$         nativeScenePendingIndices.add(index);
//$$     }
//$$
//$$     public static synchronized void clear() {
//$$         nativeScenePendingIndices.clear();
//$$     }
//$$
//$$     public static void rebuildAll() {
//$$     }
//$$
//$$     public static synchronized boolean isSodiumSectionReady(int sectionX, int sectionY,
//$$         int sectionZ) {
//$$         int index = sodiumSectionIndex(sectionX, sectionY, sectionZ);
//$$         if (index < 0) {
//$$             return true;
//$$         }
//$$         boolean ready = isChunkReady(index);
//$$         if (ready) {
//$$             nativeScenePendingIndices.remove(index);
//$$         }
//$$         return ready;
//$$     }
//$$
//$$     public static synchronized void relocateSodiumSection(int sectionX, int sectionY, int sectionZ,
//$$         int originX, int originY, int originZ) {
//$$         int index = sodiumSectionIndex(sectionX, sectionY, sectionZ);
//$$         if (index < 0) {
//$$             return;
//$$         }
//$$         if (!slotMatches(index, sectionX, sectionY, sectionZ)) {
//$$             return;
//$$         }
//$$         nativeScenePendingIndices.remove(index);
//$$         relocateSingleNative(index, originX, originY, originZ);
//$$     }
//$$
//$$     public static synchronized void invalidateSodiumSection(int sectionX, int sectionY,
//$$         int sectionZ) {
//$$         int index = sodiumSectionIndex(sectionX, sectionY, sectionZ);
//$$         if (index < 0) {
//$$             return;
//$$         }
//$$         if (!slotMatches(index, sectionX, sectionY, sectionZ)) {
//$$             return;
//$$         }
//$$         invalidateSingle(index);
//$$     }
//$$
//$$     public static synchronized void invalidateSingle(long index) {
//$$         nativeScenePendingIndices.remove((int) index);
//$$         invalidateSingleNative(index);
//$$     }
//$$
//$$     private static int[] filledSlots(int size) {
//$$         int[] slots = new int[Math.max(0, size)];
//$$         Arrays.fill(slots, UNASSIGNED_SECTION);
//$$         return slots;
//$$     }
//$$
//$$     private static void syncSodiumSlotOrigins() {
//$$         int count = sizeX * sizeY * sizeZ;
//$$         if (!hasCurrentSection || count <= 0 || slotSectionX.length != count
//$$             || slotSectionY.length != count || slotSectionZ.length != count) {
//$$             return;
//$$         }
//$$         int viewDistance = (sizeX - 1) / 2;
//$$         int baseSectionX = currentSectionX - viewDistance;
//$$         int baseSectionZ = currentSectionZ - viewDistance;
//$$         for (int index = 0; index < count; index++) {
//$$             int gridX = index % sizeX;
//$$             int gridY = index / sizeX % sizeY;
//$$             int gridZ = index / (sizeX * sizeY);
//$$             int sectionX = baseSectionX + Math.floorMod(gridX - baseSectionX, sizeX);
//$$             int sectionY = bottomSectionCoord + gridY;
//$$             int sectionZ = baseSectionZ + Math.floorMod(gridZ - baseSectionZ, sizeZ);
//$$             if (slotMatches(index, sectionX, sectionY, sectionZ)) {
//$$                 continue;
//$$             }
//$$             slotSectionX[index] = sectionX;
//$$             slotSectionY[index] = sectionY;
//$$             slotSectionZ[index] = sectionZ;
//$$             nativeScenePendingIndices.remove(index);
//$$             relocateSingleNative(index, sectionX << 4, sectionY << 4, sectionZ << 4);
//$$         }
//$$     }
//$$
//$$     private static boolean slotMatches(int index, int sectionX, int sectionY, int sectionZ) {
//$$         return index >= 0 && index < slotSectionX.length && index < slotSectionY.length
//$$             && index < slotSectionZ.length && slotSectionX[index] == sectionX
//$$             && slotSectionY[index] == sectionY && slotSectionZ[index] == sectionZ;
//$$     }
//$$ }
//#else
import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.mojang.blaze3d.systems.VertexSorter;
import com.radiance.client.constant.Constants;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.BufferProxy;
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
        initNative(numChunks, sizeX, sizeY, sizeZ, bottomSectionCoord);
    }

    public static void updateSectionPos(ChunkSectionPos sectionPos) {
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

    public static native boolean isChunkReady(long index);

    private static native boolean isChunkRenderingReadyNative(long index);

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
        if (isChunkReady(builtChunk.index)) {
            nativeScenePendingIndices.remove(builtChunk.index);
            return true;
        }
        return isChunkRenderingReadyNative(builtChunk.index);
    }

    private static native void relocateSingleNative(long index, int originX, int originY,
        int originZ);

    public static void relocateSingle(long index, int originX, int originY, int originZ) {
        nativeScenePendingIndices.remove((int) index);
        relocateSingleNative(index, originX, originY, originZ);
    }

    private static native void invalidateSingleNative(long index);

    public static void invalidateSingle(long index) {
        nativeScenePendingIndices.remove((int) index);
        invalidateSingleNative(index);
    }
}
//#endif
