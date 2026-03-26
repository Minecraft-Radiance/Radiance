package com.radiance.client.proxy.world;

import static net.minecraft.client.render.VertexFormat.DrawMode.LINE_STRIP;
import static net.minecraft.client.render.VertexFormat.DrawMode.LINES;
import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static net.minecraft.client.render.VertexFormat.DrawMode.TRIANGLE_STRIP;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IHeldItemRendererExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.BlockBreakingInfo;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayVertexConsumer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.lwjgl.system.MemoryUtil;

public final class EntityProxy {

    public static final ConcurrentMap<Class<? extends Particle>, AtomicInteger> PARTICLE_COUNTERS = new ConcurrentHashMap<>();
    private static final int WORLD_ENTITY_BUFFER_SIZE = 16 * 1024;

    private EntityProxy() {
    }

    private static native void queueBuild(float lineWidth,
        int coordinate,
        boolean normalOffset,
        int size,
        long entityHashCodes,
        long entityPosXs,
        long entityPosYs,
        long entityPosZs,
        long entityRTFlags,
        long entityPrebuiltBLASs,
        long entityPosts,
        long entityLayerCounts,
        long geometryTypes,
        long geometryGroupNames,
        long geometryTextures,
        long vertexFormats,
        long indexFormats,
        long vertexCounts,
        long vertices);

    public static void processWorldEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Constants.RayTracingFlags rtFlag,
        boolean reflect,
        EntityRenderDataList entityRenderDataList) {
        processEntityRenderData(storageVertexConsumerProvider, hashCode, entityPosX, entityPosY,
            entityPosZ, rtFlag.getValue(), -1, reflect, false, entityRenderDataList);
    }

    private static void processEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        int rtFlag,
        int prebuiltBLAS,
        boolean reflect,
        boolean post,
        EntityRenderDataList entityRenderDataList) {
        Map<RenderLayer, VertexConsumer> layerBuffers = storageVertexConsumerProvider.getLayers();
        EntityRenderData entityRenderData = new EntityRenderData(hashCode, entityPosX, entityPosY,
            entityPosZ, rtFlag, prebuiltBLAS, post);

        for (Map.Entry<RenderLayer, VertexConsumer> layerBuffer : layerBuffers.entrySet()) {
            RenderLayer layer = layerBuffer.getKey();
            BufferBuilder.BuiltBuffer buffer = null;

            VertexConsumer vertexConsumer = layerBuffer.getValue();
            if (vertexConsumer instanceof BufferBuilder bufferBuilder) {
                buffer = bufferBuilder.endNullable();
            } else if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
                buffer = pbrVertexConsumer.endNullable();
            }

            if (layer.getDrawMode() != QUADS && layer.getDrawMode() != TRIANGLE_STRIP
                && layer.getDrawMode() != LINE_STRIP && layer.getDrawMode() != LINES) {
                continue;
            }
            if (buffer == null || buffer.isEmpty()) {
                continue;
            }

            entityRenderData.add(new EntityRenderLayer(layer, buffer, reflect));
        }

        if (!entityRenderData.isEmpty()) {
            entityRenderDataList.add(entityRenderData);
        }
    }

    public static void queueEntitiesBuild(Camera camera, List<Entity> renderedEntities,
        EntityRenderDispatcher entityRenderDispatcher, float tickDelta) {
        MatrixStack matrixStack = new MatrixStack();
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList entityRenderDataList = new EntityRenderDataList();

        for (Entity entity : renderedEntities) {
            if (entity.age == 0) {
                entity.lastRenderX = entity.getX();
                entity.lastRenderY = entity.getY();
                entity.lastRenderZ = entity.getZ();
            }

            StorageVertexConsumerProvider storageVertexConsumerProvider =
                new StorageVertexConsumerProvider(WORLD_ENTITY_BUFFER_SIZE);
            storageVertexConsumerProviders.add(storageVertexConsumerProvider);

            double entityPosX = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
            double entityPosY = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
            double entityPosZ = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());

            entityRenderDispatcher.render(entity, 0.0, 0.0, 0.0, entity.getYaw(tickDelta),
                tickDelta, matrixStack, storageVertexConsumerProvider,
                entityRenderDispatcher.getLight(entity, tickDelta));

            if (entity.equals(camera.getFocusedEntity())) {
                processWorldEntityRenderData(storageVertexConsumerProvider,
                    System.identityHashCode(entity), entityPosX, entityPosY, entityPosZ,
                    Constants.RayTracingFlags.PLAYER, true, entityRenderDataList);
            } else if (entity instanceof FishingBobberEntity) {
                processWorldEntityRenderData(storageVertexConsumerProvider,
                    System.identityHashCode(entity), entityPosX, entityPosY, entityPosZ,
                    Constants.RayTracingFlags.FISHING_BOBBER, true, entityRenderDataList);
            } else {
                processWorldEntityRenderData(storageVertexConsumerProvider,
                    System.identityHashCode(entity), entityPosX, entityPosY, entityPosZ,
                    Constants.RayTracingFlags.WORLD, true, entityRenderDataList);
            }
        }

        queueBuild(storageVertexConsumerProviders, entityRenderDataList, 0.0125f,
            Constants.Coordinates.WORLD, false);
    }

    public static void queueBlockEntitiesBuild(BuiltChunkStorage chunks,
        Set<BlockEntity> noCullingBlockEntities,
        Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions,
        BlockEntityRenderDispatcher blockEntityRenderDispatcher, float tickDelta) {
        MatrixStack matrixStack = new MatrixStack();
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList entityRenderDataList = new EntityRenderDataList();

        for (ChunkBuilder.BuiltChunk builtChunk : chunks.chunks) {
            List<BlockEntity> blockEntities = builtChunk.getData().getBlockEntities();
            if (blockEntities.isEmpty()) {
                continue;
            }

            for (BlockEntity blockEntity : blockEntities) {
                StorageVertexConsumerProvider storageVertexConsumerProvider =
                    new StorageVertexConsumerProvider(WORLD_ENTITY_BUFFER_SIZE);
                storageVertexConsumerProviders.add(storageVertexConsumerProvider);

                VertexConsumerProvider vertexConsumerProvider = storageVertexConsumerProvider;
                BlockPos blockPos = blockEntity.getPos();
                SortedSet<BlockBreakingInfo> breakingInfos =
                    blockBreakingProgressions.get(blockPos.asLong());
                if (breakingInfos != null && !breakingInfos.isEmpty()) {
                    int stage = breakingInfos.last().getStage();
                    if (stage >= 0) {
                        MatrixStack.Entry entry = matrixStack.peek();
                        VertexConsumer crumbling = new OverlayVertexConsumer(
                            storageVertexConsumerProvider.getBuffer(
                                ModelLoader.BLOCK_DESTRUCTION_RENDER_LAYERS.get(stage)),
                            entry.getPositionMatrix(), entry.getNormalMatrix(), 1.0F);
                        vertexConsumerProvider = renderLayer -> {
                            VertexConsumer base = storageVertexConsumerProvider.getBuffer(renderLayer);
                            if (renderLayer.hasCrumbling()) {
                                return VertexConsumers.union(crumbling, base);
                            }
                            return base;
                        };
                    }
                }

                blockEntityRenderDispatcher.render(blockEntity, tickDelta, matrixStack,
                    vertexConsumerProvider);

                processWorldEntityRenderData(storageVertexConsumerProvider,
                    System.identityHashCode(blockEntity), blockPos.getX(), blockPos.getY(),
                    blockPos.getZ(), Constants.RayTracingFlags.WORLD, true, entityRenderDataList);
            }
        }

        for (BlockEntity blockEntity : noCullingBlockEntities) {
            StorageVertexConsumerProvider storageVertexConsumerProvider =
                new StorageVertexConsumerProvider(WORLD_ENTITY_BUFFER_SIZE);
            storageVertexConsumerProviders.add(storageVertexConsumerProvider);

            blockEntityRenderDispatcher.render(blockEntity, tickDelta, matrixStack,
                storageVertexConsumerProvider);

            BlockPos blockPos = blockEntity.getPos();
            processWorldEntityRenderData(storageVertexConsumerProvider,
                System.identityHashCode(blockEntity), blockPos.getX(), blockPos.getY(),
                blockPos.getZ(), Constants.RayTracingFlags.WORLD, true, entityRenderDataList);
        }

        queueBuild(storageVertexConsumerProviders, entityRenderDataList, 0.0125f,
            Constants.Coordinates.WORLD, false);
    }

    public static void queueHandRebuild(BufferBuilderStorage buffers, float tickDelta,
        HeldItemRenderer firstPersonRenderer) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrixStack = new MatrixStack();
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();

        StorageVertexConsumerProvider storageVertexConsumerProvider =
            new StorageVertexConsumerProvider(8192);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        matrixStack.push();

        boolean sleeping = client.getCameraEntity() instanceof LivingEntity livingEntity
            && livingEntity.isSleeping();
        if (client.options.getPerspective().isFirstPerson() && !sleeping
            && !client.options.hudHidden
            && client.interactionManager != null
            && client.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR
            && client.player != null) {
            ((IHeldItemRendererExt) firstPersonRenderer).radiance$renderItem(tickDelta,
                matrixStack, storageVertexConsumerProvider, client.player,
                client.getEntityRenderDispatcher().getLight(client.player, tickDelta));

            processWorldEntityRenderData(storageVertexConsumerProvider,
                System.identityHashCode(Constants.RayTracingFlags.HAND), 0, 0, 0,
                Constants.RayTracingFlags.HAND, true, renderDataList);
            queueBuild(storageVertexConsumerProviders, renderDataList, 0.0f,
                Constants.Coordinates.CAMERA, false);
        }

        matrixStack.pop();

        if (client.options.getPerspective().isFirstPerson() && !sleeping) {
            InGameOverlayRenderer.renderOverlays(client, matrixStack);
        }
    }

    private static void queueBuild(List<StorageVertexConsumerProvider> storageVertexConsumerProviders,
        EntityRenderDataList entityRenderDataList,
        float lineWidth,
        Constants.Coordinates coordinate,
        boolean normalOffset) {
        if (entityRenderDataList.isEmpty()) {
            for (StorageVertexConsumerProvider storageVertexConsumerProvider : storageVertexConsumerProviders) {
                storageVertexConsumerProvider.close();
            }
            return;
        }

        TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();

        int entityHashCodeSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
        ByteBuffer entityHashCodeBB = MemoryUtil.memAlloc(entityHashCodeSize);
        long entityHashCodeAddr = memAddress(entityHashCodeBB);
        int entityHashCodeBaseAddr = 0;

        int entityPosXSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
        ByteBuffer entityPosXBB = MemoryUtil.memAlloc(entityPosXSize);
        long entityPosXAddr = memAddress(entityPosXBB);
        int entityPosXBaseAddr = 0;

        int entityPosYSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
        ByteBuffer entityPosYBB = MemoryUtil.memAlloc(entityPosYSize);
        long entityPosYAddr = memAddress(entityPosYBB);
        int entityPosYBaseAddr = 0;

        int entityPosZSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
        ByteBuffer entityPosZBB = MemoryUtil.memAlloc(entityPosZSize);
        long entityPosZAddr = memAddress(entityPosZBB);
        int entityPosZBaseAddr = 0;

        int entityRTFlagSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
        ByteBuffer entityRTFlagBB = MemoryUtil.memAlloc(entityRTFlagSize);
        long entityRTFlagAddr = memAddress(entityRTFlagBB);
        int entityRTFlagBaseAddr = 0;

        int entityPrebuiltBLASSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
        ByteBuffer entityPrebuiltBLASBB = MemoryUtil.memAlloc(entityPrebuiltBLASSize);
        long entityPrebuiltBLASAddr = memAddress(entityPrebuiltBLASBB);
        int entityPrebuiltBLASBaseAddr = 0;

        int entityPostSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
        ByteBuffer entityPostBB = MemoryUtil.memAlloc(entityPostSize);
        long entityPostAddr = memAddress(entityPostBB);
        int entityPostBaseAddr = 0;

        int entityLayerCountSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
        ByteBuffer entityLayerCountBB = MemoryUtil.memAlloc(entityLayerCountSize);
        long entityLayerCountAddr = memAddress(entityLayerCountBB);
        int entityLayerCountBaseAddr = 0;

        int geometryTypeSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
        ByteBuffer geometryTypeBB = MemoryUtil.memAlloc(geometryTypeSize);
        long geometryTypeAddr = memAddress(geometryTypeBB);
        int geometryTypeBaseAddr = 0;

        int geometryGroupNameSize = entityRenderDataList.getTotalLayersCount() * Long.BYTES;
        ByteBuffer geometryGroupNameBB = MemoryUtil.memAlloc(geometryGroupNameSize);
        long geometryGroupNameAddr = memAddress(geometryGroupNameBB);
        int geometryGroupNameBaseAddr = 0;
        List<ByteBuffer> geometryGroupNameBuffers =
            new ArrayList<>(entityRenderDataList.getTotalLayersCount());

        int geometryTextureSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
        ByteBuffer geometryTextureBB = MemoryUtil.memAlloc(geometryTextureSize);
        long geometryTextureAddr = memAddress(geometryTextureBB);
        int geometryTextureBaseAddr = 0;

        int vertexFormatSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
        ByteBuffer vertexFormatBB = MemoryUtil.memAlloc(vertexFormatSize);
        long vertexFormatAddr = memAddress(vertexFormatBB);
        int vertexFormatBaseAddr = 0;

        int indexFormatSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
        ByteBuffer indexFormatBB = MemoryUtil.memAlloc(indexFormatSize);
        long indexFormatAddr = memAddress(indexFormatBB);
        int indexFormatBaseAddr = 0;

        int vertexCountSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
        ByteBuffer vertexCountBB = MemoryUtil.memAlloc(vertexCountSize);
        long vertexCountAddr = memAddress(vertexCountBB);
        int vertexCountBaseAddr = 0;

        int verticesSize = entityRenderDataList.getTotalLayersCount() * Long.BYTES;
        ByteBuffer verticesBB = MemoryUtil.memAlloc(verticesSize);
        long verticesAddr = memAddress(verticesBB);
        int verticesBaseAddr = 0;

        try {
            for (EntityRenderData entityRenderData : entityRenderDataList) {
                entityHashCodeBB.putInt(entityHashCodeBaseAddr, entityRenderData.hashCode);
                entityHashCodeBaseAddr += Integer.BYTES;

                entityPosXBB.putDouble(entityPosXBaseAddr, entityRenderData.x);
                entityPosXBaseAddr += Double.BYTES;

                entityPosYBB.putDouble(entityPosYBaseAddr, entityRenderData.y);
                entityPosYBaseAddr += Double.BYTES;

                entityPosZBB.putDouble(entityPosZBaseAddr, entityRenderData.z);
                entityPosZBaseAddr += Double.BYTES;

                entityRTFlagBB.putInt(entityRTFlagBaseAddr, entityRenderData.rtFlag);
                entityRTFlagBaseAddr += Integer.BYTES;

                entityPrebuiltBLASBB.putInt(entityPrebuiltBLASBaseAddr,
                    entityRenderData.prebuiltBLAS);
                entityPrebuiltBLASBaseAddr += Integer.BYTES;

                entityPostBB.putInt(entityPostBaseAddr, entityRenderData.post ? 1 : 0);
                entityPostBaseAddr += Integer.BYTES;

                entityLayerCountBB.putInt(entityLayerCountBaseAddr, entityRenderData.size());
                entityLayerCountBaseAddr += Integer.BYTES;

                for (EntityRenderLayer entityRenderLayer : entityRenderData) {
                    RenderLayer renderLayer = entityRenderLayer.renderLayer;
                    BufferBuilder.BuiltBuffer vertexBuffer = entityRenderLayer.builtBuffer;
                    BufferBuilder.DrawParameters drawParameters = vertexBuffer.getParameters();

                    int geometryTypeId = Constants.GeometryTypes.getGeometryType(renderLayer,
                        entityRenderLayer.reflect).getValue();
                    int geometryTextureId = textureManager.getTexture(
                        renderLayer instanceof RenderLayer.MultiPhase multiPhase ?
                            multiPhase.phases.texture.getId()
                                .orElse(MissingSprite.getMissingSpriteId()) :
                            MissingSprite.getMissingSpriteId()).getGlId();
                    int vertexFormatId = Constants.VertexFormats.getValue(drawParameters.format());
                    int indexFormatId = Constants.DrawModes.getValue(drawParameters.mode());

                    BufferProxy.BufferInfo vertexBufferInfo = BufferProxy.getBufferInfo(
                        vertexBuffer.getVertexBuffer());

                    geometryTypeBB.putInt(geometryTypeBaseAddr, geometryTypeId);
                    geometryTypeBaseAddr += Integer.BYTES;

                    ByteBuffer geometryGroupNameBuffer = MemoryUtil.memUTF8(renderLayer.name, true);
                    geometryGroupNameBuffers.add(geometryGroupNameBuffer);
                    geometryGroupNameBB.putLong(geometryGroupNameBaseAddr,
                        memAddress(geometryGroupNameBuffer));
                    geometryGroupNameBaseAddr += Long.BYTES;

                    geometryTextureBB.putInt(geometryTextureBaseAddr, geometryTextureId);
                    geometryTextureBaseAddr += Integer.BYTES;

                    vertexFormatBB.putInt(vertexFormatBaseAddr, vertexFormatId);
                    vertexFormatBaseAddr += Integer.BYTES;

                    indexFormatBB.putInt(indexFormatBaseAddr, indexFormatId);
                    indexFormatBaseAddr += Integer.BYTES;

                    vertexCountBB.putInt(vertexCountBaseAddr, drawParameters.vertexCount());
                    vertexCountBaseAddr += Integer.BYTES;

                    verticesBB.putLong(verticesBaseAddr, vertexBufferInfo.addr());
                    verticesBaseAddr += Long.BYTES;
                }
            }

            queueBuild(lineWidth, coordinate.getValue(), normalOffset,
                entityRenderDataList.getTotalEntityCount(), entityHashCodeAddr, entityPosXAddr,
                entityPosYAddr, entityPosZAddr, entityRTFlagAddr, entityPrebuiltBLASAddr,
                entityPostAddr, entityLayerCountAddr, geometryTypeAddr, geometryGroupNameAddr,
                geometryTextureAddr, vertexFormatAddr, indexFormatAddr, vertexCountAddr,
                verticesAddr);
        } finally {
            MemoryUtil.memFree(entityHashCodeBB);
            MemoryUtil.memFree(entityPosXBB);
            MemoryUtil.memFree(entityPosYBB);
            MemoryUtil.memFree(entityPosZBB);
            MemoryUtil.memFree(entityRTFlagBB);
            MemoryUtil.memFree(entityPrebuiltBLASBB);
            MemoryUtil.memFree(entityPostBB);
            MemoryUtil.memFree(entityLayerCountBB);
            MemoryUtil.memFree(geometryTypeBB);
            MemoryUtil.memFree(geometryGroupNameBB);
            for (ByteBuffer geometryGroupNameBuffer : geometryGroupNameBuffers) {
                MemoryUtil.memFree(geometryGroupNameBuffer);
            }
            MemoryUtil.memFree(geometryTextureBB);
            MemoryUtil.memFree(vertexFormatBB);
            MemoryUtil.memFree(indexFormatBB);
            MemoryUtil.memFree(vertexCountBB);
            MemoryUtil.memFree(verticesBB);

            for (EntityRenderData entityRenderData : entityRenderDataList) {
                for (EntityRenderLayer entityRenderLayer : entityRenderData) {
                    entityRenderLayer.builtBuffer.release();
                }
            }

            for (StorageVertexConsumerProvider storageVertexConsumerProvider : storageVertexConsumerProviders) {
                storageVertexConsumerProvider.close();
            }
        }
    }

    public static void build() {
        // The 1.20.1 port does not restore Java-side entity extraction yet.
        // Until queueBuild(...) is wired back in, calling native build() only
        // produces empty batches and invalid Vulkan allocations.
    }

    public record EntityRenderLayer(RenderLayer renderLayer,
                                    BufferBuilder.BuiltBuffer builtBuffer,
                                    boolean reflect) {

    }

    public static class EntityRenderData extends ArrayList<EntityRenderLayer> {

        private final int hashCode;
        private final int rtFlag;
        private final int prebuiltBLAS;
        private final boolean post;
        private final double x;
        private final double y;
        private final double z;

        public EntityRenderData(int hashCode, double x, double y, double z, int rtFlag,
            int prebuiltBLAS, boolean post) {
            this.hashCode = hashCode;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rtFlag = rtFlag;
            this.prebuiltBLAS = prebuiltBLAS;
            this.post = post;
        }
    }

    public static class EntityRenderDataList extends ArrayList<EntityRenderData> {

        private int totalLayersCount;

        @Override
        public boolean add(EntityRenderData entityRenderData) {
            totalLayersCount += entityRenderData.size();
            return super.add(entityRenderData);
        }

        public int getTotalLayersCount() {
            return totalLayersCount;
        }

        public int getTotalEntityCount() {
            return this.size();
        }
    }
}
