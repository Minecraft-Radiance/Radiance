package com.radiance.client.proxy.world;

import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderBuiltChunkExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
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
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
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

        @Override
        public boolean isEmpty(RenderLayer layer) {
            return true;
        }
    };
    private static final Map<Integer, ChunkBuilder.BuiltChunk> rebuildQueue =
        new ConcurrentHashMap<>();
    private static final List<Future<?>> rebuildTasks = new ArrayList<>();
    private static final int NUM_NORMAL_CHUNK_REBUILD_THREADS = 1;
    private static final int NUM_IMPORTANT_CHUNK_REBUILD_THREADS = 1;
    private static final ExecutorService IMPORTANT_CHUNK_REBUILD_EXECUTOR =
        Executors.newFixedThreadPool(NUM_IMPORTANT_CHUNK_REBUILD_THREADS, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });
    public static int builtChunkNum = 0;
    private static ExecutorService backgroundChunkRebuildExecutor =
        Executors.newFixedThreadPool(NUM_NORMAL_CHUNK_REBUILD_THREADS, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });

    public static native void initNative(int numChunks);

    public static void init(int numChunks) {
        clear();
        initNative(numChunks);
    }

    public static AutoCloseable scopedBlockBufferStorage() {
        return () -> {
        };
    }

    public static void clear() {
        waitImportantChunkRebuild();
        builtChunkNum = 0;

        backgroundChunkRebuildExecutor.shutdown();
        try {
            backgroundChunkRebuildExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        backgroundChunkRebuildExecutor =
            Executors.newFixedThreadPool(NUM_NORMAL_CHUNK_REBUILD_THREADS, runnable -> {
                Thread thread = new Thread(runnable);
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            });

        rebuildQueue.clear();
        rebuildTasks.clear();
    }

    public static void enqueueRebuild(ChunkBuilder.BuiltChunk chunk) {
        rebuildQueue.put(chunk.index, chunk);
    }

    public static void rebuild(Camera camera) {
        BlockPos blockPos = camera.getBlockPos();
        for (ChunkBuilder.BuiltChunk builtChunk : rebuildQueue.values()) {
            if (!builtChunk.needsRebuild() || !builtChunk.shouldBuild()) {
                continue;
            }

            builtChunk.cancelRebuild();
            BlockPos chunkCenterPos = builtChunk.getOrigin().add(8, 8, 8);
            boolean isImportant = chunkCenterPos.getSquaredDistance(blockPos) < 768.0
                || builtChunk.needsImportantRebuild();

            if (isImportant) {
                Future<?> rebuildTask = IMPORTANT_CHUNK_REBUILD_EXECUTOR.submit(
                    () -> rebuildSingle(builtChunk, true));
                rebuildTasks.add(rebuildTask);
            } else {
                backgroundChunkRebuildExecutor.execute(() -> rebuildSingle(builtChunk, false));
            }
        }

        rebuildQueue.clear();
    }

    public static void waitImportantChunkRebuild() {
        if (rebuildTasks.isEmpty()) {
            return;
        }

        for (Future<?> rebuildTask : rebuildTasks) {
            try {
                rebuildTask.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        rebuildTasks.clear();
    }

    private static void rebuildSingle(ChunkBuilder.BuiltChunk builtChunk, boolean important) {
        try (var ignored = scopedBlockBufferStorage()) {
            IChunkBuilderBuiltChunkExt builtChunkExt =
                (IChunkBuilderBuiltChunkExt) builtChunk;
            ChunkBuilder chunkBuilder = builtChunkExt.radiance$getChunkBuilder();
            ChunkRendererRegionBuilder regionBuilder = new ChunkRendererRegionBuilder();
            BlockPos origin = builtChunk.getOrigin();
            ChunkRendererRegion region = regionBuilder.build(
                ((IChunkBuilderExt) chunkBuilder).radiance$getWorld(),
                origin.add(-1, -1, -1),
                origin.add(16, 16, 16),
                1);

            if (region == null) {
                invalidateSingle(builtChunk.index);
                builtChunk.data.set(ChunkBuilder.ChunkData.EMPTY);
                return;
            }

            rebuildSingle(region, builtChunk, important);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void rebuildSingle(ChunkRendererRegion region,
        ChunkBuilder.BuiltChunk builtChunk,
        boolean important) {
        BlockPos startPos = builtChunk.getOrigin();
        BlockPos endPos = startPos.add(15, 15, 15);
        ChunkOcclusionDataBuilder occlusionBuilder = new ChunkOcclusionDataBuilder();
        MatrixStack matrixStack = new MatrixStack();
        Map<RenderLayer, PBRVertexConsumer> builders =
            new Reference2ObjectArrayMap<>(RenderLayer.getBlockLayers().size());
        List<BlockEntity> blockEntities = new ArrayList<>();
        List<BlockEntity> noCullingBlockEntities = new ArrayList<>();
        Random random = Random.create();
        BlockModelRenderer.enableBrightnessCache();

        try {
            BlockRenderManager blockRenderManager =
                MinecraftClient.getInstance().getBlockRenderManager();

            for (BlockPos blockPos : BlockPos.iterate(startPos, endPos)) {
                BlockState blockState = region.getBlockState(blockPos);
                if (blockState.isOpaqueFullCube(region, blockPos)) {
                    occlusionBuilder.markClosed(blockPos);
                }

                if (blockState.hasBlockEntity()) {
                    BlockEntity blockEntity = region.getBlockEntity(blockPos);
                    if (blockEntity != null) {
                        blockEntities.add(blockEntity);
                        BlockEntityRenderer<BlockEntity> renderer =
                            MinecraftClient.getInstance()
                                .getBlockEntityRenderDispatcher()
                                .get(blockEntity);
                        if (renderer != null && renderer.rendersOutsideBoundingBox(blockEntity)) {
                            noCullingBlockEntities.add(blockEntity);
                        }
                    }
                }

                FluidState fluidState = blockState.getFluidState();
                if (!fluidState.isEmpty()) {
                    RenderLayer renderLayer = RenderLayers.getFluidLayer(fluidState);
                    PBRVertexConsumer consumer = beginBufferBuilding(builders, renderLayer);
                    blockRenderManager.renderFluid(blockPos, region, consumer, blockState,
                        fluidState);
                }

                if (blockState.getRenderType() == BlockRenderType.MODEL) {
                    RenderLayer renderLayer = RenderLayers.getBlockLayer(blockState);
                    PBRVertexConsumer consumer = beginBufferBuilding(builders, renderLayer);
                    matrixStack.push();
                    matrixStack.translate(blockPos.getX() & 15, blockPos.getY() & 15,
                        blockPos.getZ() & 15);
                    blockRenderManager.renderBlock(blockState, blockPos, region, matrixStack,
                        consumer, true, random);
                    matrixStack.pop();
                }
            }
        } finally {
            BlockModelRenderer.disableBrightnessCache();
        }

        Map<RenderLayer, BufferBuilder.BuiltBuffer> buffers =
            new Reference2ObjectArrayMap<>(builders.size());
        for (Map.Entry<RenderLayer, PBRVertexConsumer> entry : builders.entrySet()) {
            BufferBuilder.BuiltBuffer builtBuffer = entry.getValue().endNullable();
            if (builtBuffer != null && !builtBuffer.isEmpty()) {
                buffers.put(entry.getKey(), builtBuffer);
            }
        }

        ((IChunkBuilderBuiltChunkExt) builtChunk).radiance$setNoCullingBlockEntities(
            noCullingBlockEntities);

        if (buffers.isEmpty()) {
            ChunkBuilder.ChunkData chunkData = new ChunkBuilder.ChunkData() {
                @Override
                public List<BlockEntity> getBlockEntities() {
                    return blockEntities;
                }

                @Override
                public boolean isVisibleThrough(Direction from, Direction to) {
                    return occlusionBuilder.build().isVisibleThrough(from, to);
                }

                @Override
                public boolean isEmpty(RenderLayer layer) {
                    return true;
                }
            };
            builtChunk.data.set(chunkData);
            builtChunkNum++;
            invalidateSingle(builtChunk.index);
            return;
        }

        ChunkBuilder.ChunkData chunkData = new ChunkBuilder.ChunkData() {
            @Override
            public List<BlockEntity> getBlockEntities() {
                return blockEntities;
            }

            @Override
            public boolean isVisibleThrough(Direction from, Direction to) {
                return occlusionBuilder.build().isVisibleThrough(from, to);
            }

            @Override
            public boolean isEmpty(RenderLayer layer) {
                return !buffers.containsKey(layer);
            }
        };
        builtChunk.data.set(chunkData);
        builtChunkNum++;

        ByteBuffer geometryTypeBuffer = null;
        ByteBuffer geometryGroupNameBuffer = null;
        ByteBuffer geometryTextureBuffer = null;
        ByteBuffer vertexFormatBuffer = null;
        ByteBuffer vertexCountBuffer = null;
        ByteBuffer verticesBuffer = null;
        List<ByteBuffer> geometryGroupNameBuffers = new ArrayList<>(buffers.size());

        try {
            geometryTypeBuffer = MemoryUtil.memAlloc(buffers.size() * Integer.BYTES);
            geometryGroupNameBuffer = MemoryUtil.memAlloc(buffers.size() * Long.BYTES);
            geometryTextureBuffer = MemoryUtil.memAlloc(buffers.size() * Integer.BYTES);
            vertexFormatBuffer = MemoryUtil.memAlloc(buffers.size() * Integer.BYTES);
            vertexCountBuffer = MemoryUtil.memAlloc(buffers.size() * Integer.BYTES);
            verticesBuffer = MemoryUtil.memAlloc(buffers.size() * Long.BYTES);

            int geometryTypeOffset = 0;
            int geometryGroupNameOffset = 0;
            int geometryTextureOffset = 0;
            int vertexFormatOffset = 0;
            int vertexCountOffset = 0;
            int verticesOffset = 0;
            TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();

            for (Map.Entry<RenderLayer, BufferBuilder.BuiltBuffer> entry : buffers.entrySet()) {
                RenderLayer renderLayer = entry.getKey();
                BufferBuilder.BuiltBuffer builtBuffer = entry.getValue();
                BufferBuilder.DrawParameters parameters = builtBuffer.getParameters();
                BufferProxy.BufferInfo bufferInfo = BufferProxy.getBufferInfo(
                    builtBuffer.getVertexBuffer());

                int geometryTypeId = Constants.GeometryTypes.getGeometryType(renderLayer, true)
                    .getValue();
                int geometryTextureId = textureManager.getTexture(
                    renderLayer instanceof RenderLayer.MultiPhase multiPhase ?
                        multiPhase.phases.texture.getId().orElse(MissingSprite.getMissingSpriteId())
                        : MissingSprite.getMissingSpriteId()).getGlId();
                int vertexFormatId = Constants.VertexFormats.getValue(parameters.format());

                geometryTypeBuffer.putInt(geometryTypeOffset, geometryTypeId);
                geometryTypeOffset += Integer.BYTES;

                ByteBuffer groupNameBuffer = MemoryUtil.memUTF8(renderLayer.name, true);
                geometryGroupNameBuffers.add(groupNameBuffer);
                geometryGroupNameBuffer.putLong(geometryGroupNameOffset, memAddress(groupNameBuffer));
                geometryGroupNameOffset += Long.BYTES;

                geometryTextureBuffer.putInt(geometryTextureOffset, geometryTextureId);
                geometryTextureOffset += Integer.BYTES;

                vertexFormatBuffer.putInt(vertexFormatOffset, vertexFormatId);
                vertexFormatOffset += Integer.BYTES;

                vertexCountBuffer.putInt(vertexCountOffset, parameters.vertexCount());
                vertexCountOffset += Integer.BYTES;

                verticesBuffer.putLong(verticesOffset, bufferInfo.addr());
                verticesOffset += Long.BYTES;
            }

            rebuildSingle(startPos.getX(), startPos.getY(), startPos.getZ(), builtChunk.index,
                buffers.size(), memAddress(geometryTypeBuffer), memAddress(geometryGroupNameBuffer),
                memAddress(geometryTextureBuffer), memAddress(vertexFormatBuffer),
                memAddress(vertexCountBuffer), memAddress(verticesBuffer), important);
        } finally {
            for (BufferBuilder.BuiltBuffer builtBuffer : buffers.values()) {
                builtBuffer.release();
            }
            if (geometryTypeBuffer != null) {
                MemoryUtil.memFree(geometryTypeBuffer);
            }
            if (geometryGroupNameBuffer != null) {
                MemoryUtil.memFree(geometryGroupNameBuffer);
            }
            if (geometryTextureBuffer != null) {
                MemoryUtil.memFree(geometryTextureBuffer);
            }
            if (vertexFormatBuffer != null) {
                MemoryUtil.memFree(vertexFormatBuffer);
            }
            if (vertexCountBuffer != null) {
                MemoryUtil.memFree(vertexCountBuffer);
            }
            if (verticesBuffer != null) {
                MemoryUtil.memFree(verticesBuffer);
            }
            for (ByteBuffer groupNameBuffer : geometryGroupNameBuffers) {
                MemoryUtil.memFree(groupNameBuffer);
            }
        }
    }

    private static PBRVertexConsumer beginBufferBuilding(
        Map<RenderLayer, PBRVertexConsumer> builders,
        RenderLayer layer) {
        PBRVertexConsumer consumer = builders.get(layer);
        if (consumer == null) {
            consumer = new PBRVertexConsumer(layer);
            builders.put(layer, consumer);
        }
        return consumer;
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

    public static native boolean isChunkReady(long index);

    public static boolean isChunkReady(ChunkBuilder.BuiltChunk builtChunk) {
        return isChunkReady(builtChunk.index);
    }

    public static native void invalidateSingle(long index);
}
