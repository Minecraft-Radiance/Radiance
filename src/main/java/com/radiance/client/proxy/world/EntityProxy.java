package com.radiance.client.proxy.world;

import static net.minecraft.client.render.VertexFormat.DrawMode.LINES;
import static net.minecraft.client.render.VertexFormat.DrawMode.LINE_STRIP;
import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static net.minecraft.client.render.VertexFormat.DrawMode.TRIANGLE_STRIP;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.radiance.client.constant.Constants;
import com.radiance.client.constant.Constants.RayTracingFlags;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.util.LightSourceDef;
import com.radiance.client.util.LightSourceRegistry;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IHeldItemRendererExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleManagerExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayVertexConsumer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.client.render.WorldBorderRendering;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.ModelBaker;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.tick.TickManager;
import org.lwjgl.system.MemoryUtil;

public class EntityProxy {

    public static final ConcurrentMap<Class<? extends Particle>, AtomicInteger> PARTICLE_COUNTERS = new ConcurrentHashMap<>();
    private static final int DEFAULT_WORLD_ENTITY_BUFFER_SIZE = 786432;
    private static final int BLOCK_CRUMBLING_BUFFER_SIZE = 65536;
    private static final ThreadLocal<BuildScratch> BUILD_SCRATCH =
        ThreadLocal.withInitial(BuildScratch::new);
    private static final ConcurrentMap<String, ByteBuffer> GEOMETRY_GROUP_NAME_CACHE =
        new ConcurrentHashMap<>();
    private static EntityReplayCache worldEntityReplayCache = null;
    private static final EnumMap<BlockEntityUpdateBucket, BlockEntityReplayCache>
        blockEntityReplayCaches = new EnumMap<>(BlockEntityUpdateBucket.class);
    private static final EnumMap<ParticleUpdateBucket, ParticleReplayCache>
        particleReplayCaches = new EnumMap<>(ParticleUpdateBucket.class);
    private static long worldEntityReplayFrameCounter = 0L;
    private static long blockEntityReplayFrameCounter = 0L;
    private static long particleReplayFrameCounter = 0L;

    private static final Identifier SUN_TEXTURE = Identifier.ofVanilla(
        "textures/environment/sun.png");
    private static final Identifier MOON_PHASES_TEXTURE = Identifier.ofVanilla(
        "textures/environment/moon_phases.png");

    private enum BlockEntityUpdateBucket {
        CRITICAL,
        ACTIVE,
        DECORATIVE
    }

    private enum ParticleUpdateBucket {
        CRITICAL,
        GENERAL,
        BACKGROUND
    }

    public static void processWorldEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Constants.RayTracingFlags rtFlag,
        boolean reflect,
        EntityRenderDataList entityRenderDataList) {
        processEntityRenderData(storageVertexConsumerProvider,
            hashCode,
            entityPosX,
            entityPosY,
            entityPosZ,
            rtFlag.getValue(),
            -1,
            reflect,
            false,
            entityRenderDataList);
    }

    public static void processPostEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        EntityRenderDataList entityRenderDataList) {
        processEntityRenderData(storageVertexConsumerProvider,
            hashCode,
            entityPosX,
            entityPosY,
            entityPosZ,
            0,
            -1,
            false,
            true,
            entityRenderDataList);
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
        EntityRenderData
            entityRenderData =
            new EntityRenderData(hashCode, entityPosX, entityPosY,
                entityPosZ,
                rtFlag, prebuiltBLAS, post);
        EntityRenderData
            waterMaskRenderData =
            new EntityRenderData(hashCode, entityPosX, entityPosY,
                entityPosZ,
                RayTracingFlags.BOAT_WATER_MASK.getValue(), prebuiltBLAS, post);
        for (Map.Entry<RenderLayer, VertexConsumer> layerBuffer : layerBuffers.entrySet()) {
            RenderLayer layer = layerBuffer.getKey();
            BuiltBuffer buffer = null;

            VertexConsumer vertexConsumer = layerBuffer.getValue();
            if (vertexConsumer instanceof BufferBuilder bufferBuilder) {
                buffer = bufferBuilder.endNullable();
            } else if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
                buffer = pbrVertexConsumer.endNullable();
            }

            if (layer.getDrawMode() != QUADS && layer.getDrawMode() != TRIANGLE_STRIP
                && layer.getDrawMode() != LINE_STRIP &&
                layer.getDrawMode() != LINES) {
                continue;
            }
            if (buffer == null) {
                continue;
            }

            boolean layerReflect = RayTracingTuning.shouldReflectLayer(layer, reflect);
            if (layer.name.contains("water_mask")) {
                waterMaskRenderData.add(new EntityRenderLayer(layer, buffer, layerReflect));
            } else {
                entityRenderData.add(new EntityRenderLayer(layer, buffer, layerReflect));
            }
        }

        if (!entityRenderData.isEmpty()) {
            entityRenderDataList.add(entityRenderData);
        }
        if (!waterMaskRenderData.isEmpty()) {
            entityRenderDataList.add(waterMaskRenderData);
        }

    }

    public static void queueEntitiesBuild(Camera camera,
        List<Entity> renderedEntities,
        EntityRenderDispatcher entityRenderDispatcher,
        RenderTickCounter tickCounter,
        boolean canDrawEntityOutlines) {
        MatrixStack matrixStack = new MatrixStack();
        worldEntityReplayFrameCounter++;

        MinecraftClient client = MinecraftClient.getInstance();
        TickManager
            tickManager =
            Objects.requireNonNull(client.world)
                .getTickManager();
        int entityUpdateInterval = RayTracingTuning.entityUpdateIntervalFrames();
        Map<Integer, EntityReplayState> entityReplayStates = captureWorldEntityReplayStates(camera,
            renderedEntities, tickCounter, tickManager);
        if (entityUpdateInterval > 1 && tryReplayWorldEntities(entityReplayStates,
            entityUpdateInterval)) {
            return;
        }
        if (entityUpdateInterval <= 1) {
            clearWorldEntityReplayCache();
        }

        List<StorageVertexConsumerProvider> entityStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList entityRenderDataList = new EntityRenderDataList();
        for (Entity entity : renderedEntities) {
            StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                DEFAULT_WORLD_ENTITY_BUFFER_SIZE);
            entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);

            VertexConsumerProvider vertexConsumerProvider;
            if (canDrawEntityOutlines && client.hasOutline(entity)) {
//                 TODO: add outline
//                StorageOutlineVertexConsumerProvider
//                    outlineVertexConsumerProvider =
//                    new StorageOutlineVertexConsumerProvider(entityStorageVertexConsumerProvider);
//                vertexConsumerProvider = outlineVertexConsumerProvider;
//                int color = entity.getTeamColorValue();
//                outlineVertexConsumerProvider.setColor(ColorHelper.getRed(color),
//                                                       ColorHelper.getGreen(color),
//                                                       ColorHelper.getBlue(color),
//                                                       255);
                vertexConsumerProvider = entityStorageVertexConsumerProvider;
            } else {
                vertexConsumerProvider = entityStorageVertexConsumerProvider;
            }

            EntityReplayState entityReplayState = entityReplayStates.get(System.identityHashCode(
                entity));
            float tickDelta = entityReplayState.tickDelta();
            double entityPosX = entityReplayState.x();
            double entityPosY = entityReplayState.y();
            double entityPosZ = entityReplayState.z();

            entityRenderDispatcher.render(entity,
                0,
                0,
                0,
                tickDelta,
                matrixStack,
                vertexConsumerProvider,
                entityRenderDispatcher.getLight(entity, tickDelta));

            if (entityReplayState.rtFlag() == Constants.RayTracingFlags.PLAYER.getValue()) {
                processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                    System.identityHashCode(entity),
                    entityPosX,
                    entityPosY,
                    entityPosZ,
                    Constants.RayTracingFlags.PLAYER,
                    true,
                    entityRenderDataList);
            } else if (entityReplayState.rtFlag()
                == Constants.RayTracingFlags.FISHING_BOBBER.getValue()) {
                processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                    System.identityHashCode(entity),
                    entityPosX,
                    entityPosY,
                    entityPosZ,
                    Constants.RayTracingFlags.FISHING_BOBBER,
                    true,
                    entityRenderDataList);
            } else {
                processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                    System.identityHashCode(entity),
                    entityPosX,
                    entityPosY,
                    entityPosZ,
                    Constants.RayTracingFlags.WORLD,
                    true,
                    entityRenderDataList);
            }
        }

        if (entityUpdateInterval > 1 && !entityRenderDataList.isEmpty()) {
            replaceWorldEntityReplayCache(createEntityReplayCache(entityRenderDataList,
                entityReplayStates));
        } else {
            clearWorldEntityReplayCache();
        }
        queueBuild(entityStorageVertexConsumerProviders, entityRenderDataList);
    }

    public static synchronized BlockEntityQueueResult queueBlockEntitiesRebuild(
        List<ChunkBuilder.BuiltChunk> visibleBuiltChunks,
        Set<BlockEntity> noCullingBlockEntities,
        Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions,
        BlockEntityRenderDispatcher blockEntityRenderDispatcher,
        float tickDelta) {
        blockEntityReplayFrameCounter++;
        List<BlockEntityRenderEntry> blockEntityRenderEntries = collectBlockEntityRenderEntries(
            visibleBuiltChunks, noCullingBlockEntities, blockBreakingProgressions);
        EnumMap<BlockEntityUpdateBucket, List<BlockEntityRenderEntry>> bucketEntries =
            bucketBlockEntityRenderEntries(blockEntityRenderEntries);
        List<StorageVertexConsumerProvider> freshEntityStorageVertexConsumerProviders =
            new ArrayList<>();
        EntityRenderDataList freshEntityRenderDataList = new EntityRenderDataList();
        EntityRenderDataList replayedEntityRenderDataList = new EntityRenderDataList();
        List<StorageVertexConsumerProvider> freshCrumblingStorageVertexConsumerProviders =
            new ArrayList<>();
        EntityRenderDataList freshCrumblingRenderDataList = new EntityRenderDataList();
        EntityRenderDataList replayedCrumblingRenderDataList = new EntityRenderDataList();

        for (BlockEntityUpdateBucket bucket : BlockEntityUpdateBucket.values()) {
            List<BlockEntityRenderEntry> entriesForBucket = bucketEntries.getOrDefault(bucket,
                List.of());
            if (entriesForBucket.isEmpty()) {
                clearBlockEntityReplayCache(bucket);
                continue;
            }

            int entityUpdateInterval = blockEntityUpdateIntervalFrames(bucket);
            Map<Integer, BlockEntityReplayState> blockEntityReplayStates =
                captureBlockEntityReplayStates(entriesForBucket);
            if (entityUpdateInterval > 1 && tryReplayBlockEntities(bucket, blockEntityReplayStates,
                entityUpdateInterval, replayedEntityRenderDataList,
                replayedCrumblingRenderDataList)) {
                continue;
            }

            BlockEntityBuildBatch blockEntityBuildBatch = renderBlockEntityEntries(
                entriesForBucket, blockEntityRenderDispatcher, tickDelta);
            freshEntityStorageVertexConsumerProviders.addAll(
                blockEntityBuildBatch.entityStorageVertexConsumerProviders());
            freshEntityRenderDataList.addAll(blockEntityBuildBatch.entityRenderDataList());
            freshCrumblingStorageVertexConsumerProviders.addAll(
                blockEntityBuildBatch.crumblingStorageVertexConsumerProviders());
            freshCrumblingRenderDataList.addAll(blockEntityBuildBatch.crumblingRenderDataList());

            if (entityUpdateInterval > 1 && (!blockEntityBuildBatch.entityRenderDataList().isEmpty()
                || !blockEntityBuildBatch.crumblingRenderDataList().isEmpty())) {
                replaceBlockEntityReplayCache(bucket, createBlockEntityReplayCache(
                    blockEntityBuildBatch.entityRenderDataList(),
                    blockEntityBuildBatch.crumblingRenderDataList(), blockEntityReplayStates));
            } else {
                clearBlockEntityReplayCache(bucket);
            }
        }

        if (!replayedEntityRenderDataList.isEmpty()) {
            queueBuildWithoutClose(replayedEntityRenderDataList);
        }
        if (!freshEntityRenderDataList.isEmpty()) {
            queueBuild(freshEntityStorageVertexConsumerProviders, freshEntityRenderDataList);
        }
        return new BlockEntityQueueResult(freshCrumblingStorageVertexConsumerProviders,
            freshCrumblingRenderDataList, replayedCrumblingRenderDataList);
    }

    public static void queueCrumblingRebuild(Camera camera,
        Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions,
        BlockRenderManager blockRenderManager,
        ClientWorld world,
        BlockEntityQueueResult blockEntityQueueResult) {
        if (blockEntityQueueResult.isEmpty()
            && blockBreakingProgressions.isEmpty()) {
            return;
        }

        MatrixStack matrixStack = new MatrixStack();
        List<StorageVertexConsumerProvider> blockCrumblingStorageVertexConsumerProviders =
            new ArrayList<>(Math.max(1, blockBreakingProgressions.size()));
        EntityRenderDataList blockCrumblingRenderDataList = new EntityRenderDataList();

        Vec3d vec3d = camera.getPos();
        double d = vec3d.getX();
        double e = vec3d.getY();
        double f = vec3d.getZ();

        for (Long2ObjectMap.Entry<SortedSet<BlockBreakingInfo>> blockBreakingProgression :
            blockBreakingProgressions.long2ObjectEntrySet()) {
            BlockPos blockPos = BlockPos.fromLong(blockBreakingProgression.getLongKey());
            double entityPosX = blockPos.getX();
            double entityPosY = blockPos.getY();
            double entityPosZ = blockPos.getZ();

            if (!(blockPos.getSquaredDistanceFromCenter(d, e, f) > 1024.0)) {
                SortedSet<BlockBreakingInfo> sortedSet = blockBreakingProgression.getValue();
                if (sortedSet != null && !sortedSet.isEmpty()) {
                    int
                        stage =
                        sortedSet.last()
                            .getStage();
                    if (stage < 0 || stage >= ModelBaker.BLOCK_DESTRUCTION_RENDER_LAYERS.size()) {
                        continue;
                    }

                    StorageVertexConsumerProvider blockCrumblingStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                        BLOCK_CRUMBLING_BUFFER_SIZE);
                    blockCrumblingStorageVertexConsumerProviders.add(
                        blockCrumblingStorageVertexConsumerProvider);

                    matrixStack.push();
                    MatrixStack.Entry entry = matrixStack.peek();
                    VertexConsumer
                        vertexConsumer =
                        new OverlayVertexConsumer(
                            blockCrumblingStorageVertexConsumerProvider.getBuffer(
                                ModelBaker.BLOCK_DESTRUCTION_RENDER_LAYERS.get(
                                    stage)), entry, 1.0F);
                    blockRenderManager.renderDamage(world.getBlockState(blockPos), blockPos, world,
                        matrixStack, vertexConsumer);
                    matrixStack.pop();

                    processWorldEntityRenderData(blockCrumblingStorageVertexConsumerProvider,
                        0,
                        entityPosX,
                        entityPosY,
                        entityPosZ,
                        Constants.RayTracingFlags.WORLD,
                        true,
                        blockCrumblingRenderDataList);
                }
            }
        }

        if (!blockEntityQueueResult.replayedCrumblingRenderDataList().isEmpty()) {
            queueBuildWithoutClose(blockEntityQueueResult.replayedCrumblingRenderDataList(), 0.0f,
                Constants.Coordinates.WORLD, true);
        }
        if (!blockEntityQueueResult.freshCrumblingRenderDataList().isEmpty()) {
            if (blockEntityQueueResult.freshCrumblingStorageVertexConsumerProviders().isEmpty()) {
                queueBuildWithoutClose(blockEntityQueueResult.freshCrumblingRenderDataList(), 0.0f,
                    Constants.Coordinates.WORLD,
                    true);
            } else {
                queueBuild(blockEntityQueueResult.freshCrumblingStorageVertexConsumerProviders(),
                    blockEntityQueueResult.freshCrumblingRenderDataList(), 0.0f,
                    Constants.Coordinates.WORLD, true);
            }
        }
        if (!blockCrumblingRenderDataList.isEmpty()) {
            queueBuild(blockCrumblingStorageVertexConsumerProviders, blockCrumblingRenderDataList,
                0.0f, Constants.Coordinates.WORLD, true);
        }
    }

    public static void queueHandRebuild(BufferBuilderStorage buffers, float tickDelta,
        HeldItemRenderer firstPersonRenderer) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrixStack = new MatrixStack();
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();

        StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
            8192);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        matrixStack.push();

        boolean bl = client.getCameraEntity() instanceof LivingEntity
            && ((LivingEntity) client.getCameraEntity()).isSleeping();
        if (client.options.getPerspective()
            .isFirstPerson() && !bl && !client.options.hudHidden &&
            client.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR) {
            ((IHeldItemRendererExt) firstPersonRenderer).radiance$renderItem(tickDelta,
                matrixStack,
                storageVertexConsumerProvider,
                client.player,
                client.getEntityRenderDispatcher()
                    .getLight(client.player, tickDelta));

            processWorldEntityRenderData(storageVertexConsumerProvider,
                System.identityHashCode(Constants.RayTracingFlags.HAND),
                0,
                0,
                0,
                Constants.RayTracingFlags.HAND,
                true,
                renderDataList);
            queueBuild(storageVertexConsumerProviders, renderDataList, 0.0f,
                Constants.Coordinates.CAMERA,
                false);
        }

        matrixStack.pop();

        if (client.options.getPerspective()
            .isFirstPerson() && !bl) {
            VertexConsumerProvider.Immediate immediate = buffers.getEntityVertexConsumers();
            InGameOverlayRenderer.renderOverlays(client, matrixStack, immediate);
            immediate.draw();
        }
    }

    public static void queueParticleRebuild(Camera camera, float tickDelta, Frustum frustum) {
        particleReplayFrameCounter++;
        ParticleManager particleManager = MinecraftClient.getInstance().particleManager;
        IParticleManagerExt particleManagerExt = (IParticleManagerExt) particleManager;
        Map<ParticleTextureSheet, Queue<Particle>> particles = particleManagerExt.radiance$getParticles();
        EnumMap<ParticleUpdateBucket, List<ParticleRenderEntry>> bucketEntries =
            bucketParticleRenderEntries(particleManagerExt, particles, camera, frustum);
        List<StorageVertexConsumerProvider> freshStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList freshRenderDataList = new EntityRenderDataList();
        EntityRenderDataList replayedRenderDataList = new EntityRenderDataList();

        for (ParticleUpdateBucket bucket : ParticleUpdateBucket.values()) {
            List<ParticleRenderEntry> entriesForBucket = bucketEntries.getOrDefault(bucket,
                List.of());
            if (entriesForBucket.isEmpty()) {
                clearParticleReplayCache(bucket);
                continue;
            }

            int particleUpdateInterval = particleUpdateIntervalFrames(bucket);
            ParticleReplayState particleReplayState = captureParticleReplayState(entriesForBucket);
            if (particleUpdateInterval > 1 && tryReplayParticles(bucket, particleReplayState,
                particleUpdateInterval, replayedRenderDataList)) {
                continue;
            }

            StorageVertexConsumerProvider storageVertexConsumerProvider =
                new StorageVertexConsumerProvider(0);
            freshStorageVertexConsumerProviders.add(storageVertexConsumerProvider);
            int bucketRenderDataStart = freshRenderDataList.size();
            renderParticles(entriesForBucket, storageVertexConsumerProvider, camera, tickDelta);
            processWorldEntityRenderData(storageVertexConsumerProvider, particleBucketHash(bucket),
                0, 0, 0, Constants.RayTracingFlags.PARTICLE, true, freshRenderDataList);
            EntityRenderDataList bucketRenderDataList = sliceEntityRenderDataList(
                freshRenderDataList, bucketRenderDataStart);

            if (particleUpdateInterval > 1 && !bucketRenderDataList.isEmpty()) {
                replaceParticleReplayCache(bucket, createParticleReplayCache(bucketRenderDataList,
                    particleReplayState));
            } else {
                clearParticleReplayCache(bucket);
            }
        }

        if (!replayedRenderDataList.isEmpty()) {
            queueBuildWithoutClose(replayedRenderDataList, 0.0f,
                Constants.Coordinates.CAMERA_SHIFT, false);
        }
        if (!freshRenderDataList.isEmpty()) {
            queueBuild(freshStorageVertexConsumerProviders, freshRenderDataList, 0.0f,
                Constants.Coordinates.CAMERA_SHIFT, false);
        }
    }

    public static void queueTargetBlockOutlineRebuild(Camera camera, ClientWorld world) {
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();

        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrixStack = new MatrixStack();

        StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
            0);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        if (client.crosshairTarget instanceof BlockHitResult blockHitResult) {
            if (blockHitResult.getType() != HitResult.Type.MISS) {
                BlockPos blockPos = blockHitResult.getBlockPos();
                BlockState blockState = world.getBlockState(blockPos);
                if (!blockState.isAir() && world.getWorldBorder()
                    .contains(blockPos)) {
                    Boolean
                        isHighContrastBlockOutline =
                        client.options.getHighContrastBlockOutline()
                            .getValue();
                    if (isHighContrastBlockOutline) {
                        VertexConsumer vertexConsumer = storageVertexConsumerProvider.getBuffer(
                            RenderLayer.getSecondaryBlockOutline());
                        VertexRendering.drawOutline(matrixStack,
                            vertexConsumer,
                            blockState.getOutlineShape(world, blockPos,
                                ShapeContext.of(camera.getFocusedEntity())),
                            0,
                            0,
                            0,
                            -16777216);
                    }

                    VertexConsumer vertexConsumer = storageVertexConsumerProvider.getBuffer(
                        RenderLayer.getLines());
                    int color =
                        isHighContrastBlockOutline ? Colors.CYAN
                            : ColorHelper.withAlpha(102, Colors.BLACK);
                    VertexRendering.drawOutline(matrixStack,
                        vertexConsumer,
                        blockState.getOutlineShape(world, blockPos,
                            ShapeContext.of(camera.getFocusedEntity())),
                        0,
                        0,
                        0,
                        color);

                    processWorldEntityRenderData(storageVertexConsumerProvider,
                        0,
                        blockPos.getX(),
                        blockPos.getY(),
                        blockPos.getZ(),
                        Constants.RayTracingFlags.FISHING_BOBBER,
                        false,
                        renderDataList);
                }
            }
        }

        queueBuild(storageVertexConsumerProviders, renderDataList, 0.0075f,
            Constants.Coordinates.WORLD,
            false);
    }

    public static void queueWeatherBuild(WeatherRendering weatherRendering,
        WorldBorderRendering worldBorderRendering,
        ClientWorld world,
        Camera camera,
        int ticks,
        float tickDelta) {
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();

        StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
            0);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        weatherRendering.renderPrecipitation(world, storageVertexConsumerProvider, ticks, tickDelta,
            camera.getPos());

        MinecraftClient client = MinecraftClient.getInstance();
        int clampedViewDistance = client.options.getClampedViewDistance() * 16;
        float farPlaneDistance = client.gameRenderer.getFarPlaneDistance();
        worldBorderRendering.render(world.getWorldBorder(), camera.getPos(), clampedViewDistance,
            farPlaneDistance);

        processPostEntityRenderData(storageVertexConsumerProvider, 0, 0, 0, 0, renderDataList);

        queueBuild(storageVertexConsumerProviders, renderDataList, 0.0f,
            Constants.Coordinates.CAMERA_SHIFT, false);
    }

    public static void clearReplayCaches() {
        clearWorldEntityReplayCache();
        clearBlockEntityReplayCache();
        clearParticleReplayCache();
        worldEntityReplayFrameCounter = 0L;
        blockEntityReplayFrameCounter = 0L;
        particleReplayFrameCounter = 0L;
    }

    private static void clearWorldEntityReplayCache() {
        if (worldEntityReplayCache != null) {
            worldEntityReplayCache.close();
            worldEntityReplayCache = null;
        }
    }

    private static void clearBlockEntityReplayCache() {
        for (BlockEntityReplayCache blockEntityReplayCache : blockEntityReplayCaches.values()) {
            if (blockEntityReplayCache != null) {
                blockEntityReplayCache.close();
            }
        }
        blockEntityReplayCaches.clear();
    }

    private static void clearBlockEntityReplayCache(BlockEntityUpdateBucket bucket) {
        BlockEntityReplayCache blockEntityReplayCache = blockEntityReplayCaches.remove(bucket);
        if (blockEntityReplayCache != null) {
            blockEntityReplayCache.close();
        }
    }

    private static void clearParticleReplayCache() {
        for (ParticleReplayCache particleReplayCache : particleReplayCaches.values()) {
            if (particleReplayCache != null) {
                particleReplayCache.close();
            }
        }
        particleReplayCaches.clear();
    }

    private static void clearParticleReplayCache(ParticleUpdateBucket bucket) {
        ParticleReplayCache particleReplayCache = particleReplayCaches.remove(bucket);
        if (particleReplayCache != null) {
            particleReplayCache.close();
        }
    }

    private static Map<Integer, EntityReplayState> captureWorldEntityReplayStates(Camera camera,
        List<Entity> renderedEntities,
        RenderTickCounter tickCounter,
        TickManager tickManager) {
        Map<Integer, EntityReplayState> entityReplayStates = new HashMap<>();
        for (Entity entity : renderedEntities) {
            if (entity.age == 0) {
                entity.lastRenderX = entity.getX();
                entity.lastRenderY = entity.getY();
                entity.lastRenderZ = entity.getZ();
            }

            float tickDelta = tickCounter.getTickDelta(!tickManager.shouldSkipTick(entity));
            double entityPosX = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
            double entityPosY = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
            double entityPosZ = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
            int rtFlag = determineWorldEntityRtFlag(camera, entity);

            entityReplayStates.put(System.identityHashCode(entity),
                new EntityReplayState(entityPosX, entityPosY, entityPosZ, tickDelta, rtFlag));
        }
        return entityReplayStates;
    }

    private static List<BlockEntityRenderEntry> collectBlockEntityRenderEntries(
        List<ChunkBuilder.BuiltChunk> visibleBuiltChunks,
        Set<BlockEntity> noCullingBlockEntities,
        Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions) {
        Map<Integer, BlockEntityRenderEntry> blockEntityRenderEntries = new LinkedHashMap<>();
        if (visibleBuiltChunks != null) {
            for (ChunkBuilder.BuiltChunk builtChunk : visibleBuiltChunks) {
                List<BlockEntity> list = builtChunk.getData().getBlockEntities();
                if (list.isEmpty()) {
                    continue;
                }
                for (BlockEntity blockEntity : list) {
                    BlockEntityRenderEntry blockEntityRenderEntry = new BlockEntityRenderEntry(
                        blockEntity,
                        getBlockBreakingStage(blockBreakingProgressions, blockEntity.getPos()),
                        blockEntityRenderId(blockEntity, false),
                        blockEntityRenderId(blockEntity, true));
                    blockEntityRenderEntries.put(blockEntityRenderEntry.mainRenderId(),
                        blockEntityRenderEntry);
                }
            }
        }
        for (BlockEntity blockEntity : noCullingBlockEntities) {
            BlockEntityRenderEntry blockEntityRenderEntry = new BlockEntityRenderEntry(blockEntity,
                -1,
                blockEntityRenderId(blockEntity, false), blockEntityRenderId(blockEntity, true));
            blockEntityRenderEntries.putIfAbsent(blockEntityRenderEntry.mainRenderId(),
                blockEntityRenderEntry);
        }
        return new ArrayList<>(blockEntityRenderEntries.values());
    }

    private static List<BlockEntityRenderEntry> collectAllChunkBlockEntityRenderEntries(
        BuiltChunkStorage chunks,
        Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions) {
        List<BlockEntityRenderEntry> blockEntityRenderEntries = new ArrayList<>();
        for (ChunkBuilder.BuiltChunk builtChunk : chunks.chunks) {
            List<BlockEntity> list = builtChunk.getData().getBlockEntities();
            if (list.isEmpty()) {
                continue;
            }
            for (BlockEntity blockEntity : list) {
                blockEntityRenderEntries.add(new BlockEntityRenderEntry(blockEntity,
                    getBlockBreakingStage(blockBreakingProgressions, blockEntity.getPos()),
                    blockEntityRenderId(blockEntity, false),
                    blockEntityRenderId(blockEntity, true)));
            }
        }
        return blockEntityRenderEntries;
    }

    private static EnumMap<BlockEntityUpdateBucket, List<BlockEntityRenderEntry>> bucketBlockEntityRenderEntries(
        List<BlockEntityRenderEntry> blockEntityRenderEntries) {
        EnumMap<BlockEntityUpdateBucket, List<BlockEntityRenderEntry>> bucketEntries =
            new EnumMap<>(BlockEntityUpdateBucket.class);
        for (BlockEntityUpdateBucket bucket : BlockEntityUpdateBucket.values()) {
            bucketEntries.put(bucket, new ArrayList<>());
        }
        for (BlockEntityRenderEntry blockEntityRenderEntry : blockEntityRenderEntries) {
            bucketEntries.get(classifyBlockEntityUpdateBucket(blockEntityRenderEntry))
                .add(blockEntityRenderEntry);
        }
        return bucketEntries;
    }

    private static BlockEntityUpdateBucket classifyBlockEntityUpdateBucket(
        BlockEntityRenderEntry blockEntityRenderEntry) {
        if (blockEntityRenderEntry.crumblingStage() >= 0) {
            return BlockEntityUpdateBucket.CRITICAL;
        }

        String blockEntityName =
            blockEntityRenderEntry.blockEntity().getClass().getSimpleName().toLowerCase(
                Locale.ROOT);
        if (blockEntityName.contains("chest") || blockEntityName.contains("shulker")
            || blockEntityName.contains("beacon") || blockEntityName.contains("campfire")
            || blockEntityName.contains("conduit") || blockEntityName.contains("bell")
            || blockEntityName.contains("endgateway") || blockEntityName.contains("portal")) {
            return BlockEntityUpdateBucket.CRITICAL;
        }
        if (blockEntityName.contains("sign") || blockEntityName.contains("banner")
            || blockEntityName.contains("skull") || blockEntityName.contains("head")
            || blockEntityName.contains("spawner") || blockEntityName.contains("furnace")
            || blockEntityName.contains("brewing") || blockEntityName.contains("enchant")
            || blockEntityName.contains("pot")) {
            return BlockEntityUpdateBucket.ACTIVE;
        }
        return BlockEntityUpdateBucket.DECORATIVE;
    }

    private static int blockEntityUpdateIntervalFrames(BlockEntityUpdateBucket bucket) {
        int baseInterval = RayTracingTuning.blockEntityUpdateIntervalFrames();
        if (baseInterval <= 1) {
            return 1;
        }
        return switch (bucket) {
            case CRITICAL -> Math.max(1, baseInterval - 1);
            case ACTIVE -> baseInterval;
            case DECORATIVE -> Math.min(6, baseInterval + 2);
        };
    }

    private static BlockEntityBuildBatch renderBlockEntityEntries(
        List<BlockEntityRenderEntry> blockEntityRenderEntries,
        BlockEntityRenderDispatcher blockEntityRenderDispatcher,
        float tickDelta) {
        MatrixStack matrixStack = new MatrixStack();
        List<StorageVertexConsumerProvider> entityStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList entityRenderDataList = new EntityRenderDataList();
        List<StorageVertexConsumerProvider> crumblingStorageVertexConsumerProviders =
            new ArrayList<>();
        EntityRenderDataList crumblingRenderDataList = new EntityRenderDataList();

        for (BlockEntityRenderEntry blockEntityRenderEntry : blockEntityRenderEntries) {
            BlockEntity blockEntity = blockEntityRenderEntry.blockEntity();
            StorageVertexConsumerProvider entityStorageVertexConsumerProvider =
                new StorageVertexConsumerProvider(DEFAULT_WORLD_ENTITY_BUFFER_SIZE);
            entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);

            BlockPos blockPos = blockEntity.getPos();
            double entityPosX = blockPos.getX();
            double entityPosY = blockPos.getY();
            double entityPosZ = blockPos.getZ();

            matrixStack.push();
            VertexConsumerProvider vertexConsumerProvider = entityStorageVertexConsumerProvider;
            StorageVertexConsumerProvider crumblingStorageVertexConsumerProvider = null;
            int crumblingStage = blockEntityRenderEntry.crumblingStage();
            if (crumblingStage >= 0
                && crumblingStage < ModelBaker.BLOCK_DESTRUCTION_RENDER_LAYERS.size()) {
                crumblingStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                    BLOCK_CRUMBLING_BUFFER_SIZE);
                crumblingStorageVertexConsumerProviders.add(crumblingStorageVertexConsumerProvider);
                MatrixStack.Entry entry = matrixStack.peek();
                VertexConsumer vertexConsumer = new OverlayVertexConsumer(
                    crumblingStorageVertexConsumerProvider.getBuffer(
                        ModelBaker.BLOCK_DESTRUCTION_RENDER_LAYERS.get(crumblingStage)), entry,
                    1.0F);
                vertexConsumerProvider = renderLayer -> {
                    VertexConsumer vertexConsumer2 =
                        entityStorageVertexConsumerProvider.getBuffer(renderLayer);
                    return renderLayer.hasCrumbling() ? VertexConsumers.union(vertexConsumer,
                        vertexConsumer2) : vertexConsumer2;
                };
            }

            blockEntityRenderDispatcher.render(blockEntity, tickDelta, matrixStack,
                vertexConsumerProvider);
            matrixStack.pop();

            processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                blockEntityRenderEntry.mainRenderId(), entityPosX, entityPosY, entityPosZ,
                Constants.RayTracingFlags.WORLD, true, entityRenderDataList);
            if (crumblingStorageVertexConsumerProvider != null) {
                processWorldEntityRenderData(crumblingStorageVertexConsumerProvider,
                    blockEntityRenderEntry.crumblingRenderId(), entityPosX, entityPosY, entityPosZ,
                    Constants.RayTracingFlags.WORLD, true, crumblingRenderDataList);
            }
        }

        return new BlockEntityBuildBatch(entityStorageVertexConsumerProviders, entityRenderDataList,
            crumblingStorageVertexConsumerProviders, crumblingRenderDataList);
    }

    private static Map<Integer, BlockEntityReplayState> captureBlockEntityReplayStates(
        List<BlockEntityRenderEntry> blockEntityRenderEntries) {
        Map<Integer, BlockEntityReplayState> blockEntityReplayStates = new LinkedHashMap<>();
        for (BlockEntityRenderEntry blockEntityRenderEntry : blockEntityRenderEntries) {
            BlockEntity blockEntity = blockEntityRenderEntry.blockEntity();
            BlockPos blockPos = blockEntity.getPos();
            double entityPosX = blockPos.getX();
            double entityPosY = blockPos.getY();
            double entityPosZ = blockPos.getZ();
            int blockStateHash = Objects.hashCode(blockEntity.getCachedState());
            blockEntityReplayStates.put(blockEntityRenderEntry.mainRenderId(),
                new BlockEntityReplayState(entityPosX, entityPosY, entityPosZ, blockStateHash));
            if (blockEntityRenderEntry.crumblingStage() >= 0) {
                blockEntityReplayStates.put(blockEntityRenderEntry.crumblingRenderId(),
                    new BlockEntityReplayState(entityPosX, entityPosY, entityPosZ,
                        31 * blockStateHash + blockEntityRenderEntry.crumblingStage() + 1));
            }
        }
        return blockEntityReplayStates;
    }

    private static int getBlockBreakingStage(
        Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions,
        BlockPos blockPos) {
        SortedSet<BlockBreakingInfo> sortedSet = blockBreakingProgressions.get(blockPos.asLong());
        if (sortedSet == null || sortedSet.isEmpty()) {
            return -1;
        }
        return sortedSet.last().getStage();
    }

    private static EnumMap<ParticleUpdateBucket, List<ParticleRenderEntry>> bucketParticleRenderEntries(
        IParticleManagerExt particleManagerExt,
        Map<ParticleTextureSheet, Queue<Particle>> particles,
        Camera camera,
        Frustum frustum) {
        EnumMap<ParticleUpdateBucket, List<ParticleRenderEntry>> bucketEntries =
            new EnumMap<>(ParticleUpdateBucket.class);
        for (ParticleUpdateBucket bucket : ParticleUpdateBucket.values()) {
            bucketEntries.put(bucket, new ArrayList<>());
        }

        for (ParticleTextureSheet particleTextureSheet : particleManagerExt.radiance$getTextureSheets()) {
            Queue<Particle> particleQueue = particles.get(particleTextureSheet);
            if (particleQueue == null || particleQueue.isEmpty()) {
                continue;
            }

            for (Particle particle : particleQueue) {
                if (isParticleDefinitelyInvisible(particle, camera, frustum)) {
                    continue;
                }
                ParticleRenderEntry particleRenderEntry = new ParticleRenderEntry(particle,
                    particleTextureSheet, false);
                bucketEntries.get(classifyParticleUpdateBucket(particleRenderEntry))
                    .add(particleRenderEntry);
            }
        }

        Queue<Particle> customParticleQueue = particles.get(ParticleTextureSheet.CUSTOM);
        if (customParticleQueue != null && !customParticleQueue.isEmpty()) {
            for (Particle particle : customParticleQueue) {
                if (isParticleDefinitelyInvisible(particle, camera, frustum)) {
                    continue;
                }
                ParticleRenderEntry particleRenderEntry = new ParticleRenderEntry(particle,
                    ParticleTextureSheet.CUSTOM, true);
                bucketEntries.get(classifyParticleUpdateBucket(particleRenderEntry))
                    .add(particleRenderEntry);
            }
        }
        return bucketEntries;
    }

    private static boolean isParticleDefinitelyInvisible(Particle particle, Camera camera,
        Frustum frustum) {
        if (particle == null) {
            return true;
        }
        if (frustum == null) {
            return false;
        }

        try {
            return !frustum.isVisible(particle.getBoundingBox());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ParticleUpdateBucket classifyParticleUpdateBucket(
        ParticleRenderEntry particleRenderEntry) {
        String particleName = particleRenderEntry.particle().getClass().getSimpleName()
            .toLowerCase(Locale.ROOT);
        if (particleRenderEntry.custom()) {
            return ParticleUpdateBucket.GENERAL;
        }
        if (particleName.contains("crit") || particleName.contains("sweep")
            || particleName.contains("firework") || particleName.contains("explosion")
            || particleName.contains("flame") || particleName.contains("lava")
            || particleName.contains("campfire") || particleName.contains("portal")
            || particleName.contains("endrod") || particleName.contains("end_rod")
            || particleName.contains("lightning") || particleName.contains("rain")
            || particleName.contains("snow")) {
            return ParticleUpdateBucket.CRITICAL;
        }
        if (particleName.contains("smoke") || particleName.contains("poof")
            || particleName.contains("dust") || particleName.contains("spell")
            || particleName.contains("effect") || particleName.contains("cloud")
            || particleName.contains("ash") || particleName.contains("sculk")) {
            return ParticleUpdateBucket.GENERAL;
        }
        return ParticleUpdateBucket.BACKGROUND;
    }

    private static int particleUpdateIntervalFrames(ParticleUpdateBucket bucket) {
        int baseInterval = RayTracingTuning.particleUpdateIntervalFrames();
        if (baseInterval <= 1) {
            return 1;
        }
        return switch (bucket) {
            case CRITICAL -> Math.max(1, baseInterval - 1);
            case GENERAL -> baseInterval;
            case BACKGROUND -> Math.min(6, baseInterval + 2);
        };
    }

    private static int particleBucketHash(ParticleUpdateBucket bucket) {
        return 0x5000 + bucket.ordinal();
    }

    private static void renderParticles(List<ParticleRenderEntry> particleRenderEntries,
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        Camera camera,
        float tickDelta) {
        for (ParticleRenderEntry particleRenderEntry : particleRenderEntries) {
            Particle particle = particleRenderEntry.particle();
            if (!particleRenderEntry.custom()) {
                ParticleVertexConsumerProvider particleVertexConsumerProvider =
                    new ParticleVertexConsumerProvider(storageVertexConsumerProvider, particle);
                VertexConsumer vertexConsumer = particleVertexConsumerProvider.getBuffer(
                    Objects.requireNonNull(particleRenderEntry.textureSheet().renderType()));
                try {
                    particle.render(vertexConsumer, camera, tickDelta);
                } catch (Throwable throwable) {
                    CrashReport crashReport = CrashReport.create(throwable, "Rendering Particle");
                    CrashReportSection crashReportSection = crashReport.addElement(
                        "Particle being rendered");
                    crashReportSection.add("Particle", particle);
                    crashReportSection.add("Particle Type", particleRenderEntry.textureSheet());
                    throw new CrashException(crashReport);
                }
                continue;
            }

            MatrixStack matrixStack = new MatrixStack();
            ParticleVertexConsumerProvider particleVertexConsumerProvider =
                new ParticleVertexConsumerProvider(storageVertexConsumerProvider, particle);
            try {
                particle.renderCustom(matrixStack, particleVertexConsumerProvider, camera,
                    tickDelta);
            } catch (Throwable throwable) {
                CrashReport crashReport = CrashReport.create(throwable, "Rendering Particle");
                CrashReportSection crashReportSection = crashReport.addElement(
                    "Particle being rendered");
                crashReportSection.add("Particle", particle::toString);
                crashReportSection.add("Particle Type", "Custom");
                throw new CrashException(crashReport);
            }
        }
    }

    private static ParticleReplayState captureParticleReplayState(
        List<ParticleRenderEntry> particleRenderEntries) {
        long signature = 0xcbf29ce484222325L;
        int regularParticleCount = 0;
        int customParticleCount = 0;
        for (ParticleRenderEntry particleRenderEntry : particleRenderEntries) {
            signature = mixReplaySignature(signature,
                System.identityHashCode(particleRenderEntry.textureSheet()));
            signature = mixReplaySignature(signature,
                System.identityHashCode(particleRenderEntry.particle()));
            signature = mixReplaySignature(signature,
                particleRenderEntry.particle().getClass().hashCode());
            if (particleRenderEntry.custom()) {
                customParticleCount++;
            } else {
                regularParticleCount++;
            }
        }
        return new ParticleReplayState(regularParticleCount + customParticleCount,
            customParticleCount, signature);
    }

    private static float particleEmissionStrength(Particle particle) {
        String particleName = particle.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        LightSourceDef registeredSource = LightSourceRegistry.findParticle(particleName);
        if (registeredSource != null) {
            return LightSourceRegistry.resolveStrength(registeredSource, 0.0f);
        }
        if (particleName.contains("soul")) {
            return 1.6f;
        }
        if (particleName.contains("flame") || particleName.contains("lava")
            || particleName.contains("campfire")) {
            return 1.35f;
        }
        if (particleName.contains("endrod") || particleName.contains("end_rod")
            || particleName.contains("glow") || particleName.contains("firework")
            || particleName.contains("spark") || particleName.contains("electric")) {
            return 1.15f;
        }
        if (particleName.contains("portal") || particleName.contains("dragon")
            || particleName.contains("sculk")) {
            return 0.85f;
        }
        if (particleName.contains("spell") || particleName.contains("effect")
            || particleName.contains("potion")) {
            return 0.55f;
        }
        if (particleName.contains("crit") || particleName.contains("ench")) {
            return RayTracingTuning.critParticlesGlowStrength();
        }
        if (particleName.contains("poof") || particleName.contains("smoke")) {
            return RayTracingTuning.deathSmokeParticlesGlowStrength();
        }
        if (particleName.contains("redstone") || particleName.contains("dust")) {
            return 0.28f;
        }
        if (particleName.contains("wax") || particleName.contains("nautilus")
            || particleName.contains("composter")) {
            return 0.18f;
        }
        return 0.0f;
    }

    private static int determineWorldEntityRtFlag(Camera camera, Entity entity) {
        if (entity.equals(camera.getFocusedEntity())) {
            return Constants.RayTracingFlags.PLAYER.getValue();
        }
        if (entity instanceof FishingBobberEntity) {
            return Constants.RayTracingFlags.FISHING_BOBBER.getValue();
        }
        return Constants.RayTracingFlags.WORLD.getValue();
    }

    private static boolean tryReplayWorldEntities(Map<Integer, EntityReplayState> entityReplayStates,
        int entityUpdateInterval) {
        if (worldEntityReplayCache == null || entityReplayStates.isEmpty()) {
            return false;
        }

        if (!canReplayThisFrame(worldEntityReplayFrameCounter, entityUpdateInterval)) {
            return false;
        }

        if (worldEntityReplayCache.entityStates.size() != entityReplayStates.size()) {
            return false;
        }

        for (Map.Entry<Integer, EntityReplayState> entityReplayStateEntry : entityReplayStates.entrySet()) {
            EntityReplayState cachedEntityReplayState = worldEntityReplayCache.entityStates.get(
                entityReplayStateEntry.getKey());
            if (cachedEntityReplayState == null || cachedEntityReplayState.rtFlag()
                != entityReplayStateEntry.getValue().rtFlag()) {
                return false;
            }
        }

        applyReplayPositions(worldEntityReplayCache.entityRenderDataList, entityReplayStates);
        queueBuildWithoutClose(worldEntityReplayCache.entityRenderDataList);
        return true;
    }

    private static boolean tryReplayBlockEntities(BlockEntityUpdateBucket bucket,
        Map<Integer, BlockEntityReplayState> blockEntityReplayStates,
        int entityUpdateInterval,
        EntityRenderDataList entityRenderDataList,
        EntityRenderDataList crumblingRenderDataList) {
        BlockEntityReplayCache blockEntityReplayCache = blockEntityReplayCaches.get(bucket);
        if (blockEntityReplayCache == null || blockEntityReplayStates.isEmpty()) {
            return false;
        }
        if (!canReplayThisFrame(blockEntityReplayFrameCounter, entityUpdateInterval)) {
            return false;
        }
        if (blockEntityReplayCache.renderStates.size() != blockEntityReplayStates.size()) {
            return false;
        }

        for (Map.Entry<Integer, BlockEntityReplayState> blockEntityReplayStateEntry : blockEntityReplayStates.entrySet()) {
            BlockEntityReplayState cachedBlockEntityReplayState = blockEntityReplayCache.renderStates.get(
                blockEntityReplayStateEntry.getKey());
            if (cachedBlockEntityReplayState == null || cachedBlockEntityReplayState.signature()
                != blockEntityReplayStateEntry.getValue().signature()) {
                return false;
            }
        }

        applyReplayPositions(blockEntityReplayCache.entityRenderDataList, blockEntityReplayStates);
        applyReplayPositions(blockEntityReplayCache.crumblingRenderDataList,
            blockEntityReplayStates);
        entityRenderDataList.addAll(blockEntityReplayCache.entityRenderDataList);
        crumblingRenderDataList.addAll(blockEntityReplayCache.crumblingRenderDataList);
        return true;
    }

    private static boolean tryReplayParticles(ParticleUpdateBucket bucket,
        ParticleReplayState particleReplayState,
        int entityUpdateInterval,
        EntityRenderDataList entityRenderDataList) {
        ParticleReplayCache particleReplayCache = particleReplayCaches.get(bucket);
        if (particleReplayCache == null || particleReplayState.totalParticleCount() == 0) {
            return false;
        }
        if (!canReplayThisFrame(particleReplayFrameCounter, entityUpdateInterval)) {
            return false;
        }
        if (!particleReplayCache.particleReplayState.equals(particleReplayState)) {
            return false;
        }

        entityRenderDataList.addAll(particleReplayCache.entityRenderDataList);
        return true;
    }

    private static EntityReplayCache createEntityReplayCache(EntityRenderDataList entityRenderDataList,
        Map<Integer, EntityReplayState> entityReplayStates) {
        List<BuiltBuffer> ownedBuffers = new ArrayList<>();
        EntityRenderDataList clonedRenderDataList = cloneEntityRenderDataList(entityRenderDataList,
            ownedBuffers);
        return new EntityReplayCache(clonedRenderDataList, new HashMap<>(entityReplayStates),
            ownedBuffers);
    }

    private static void replaceWorldEntityReplayCache(EntityReplayCache entityReplayCache) {
        clearWorldEntityReplayCache();
        worldEntityReplayCache = entityReplayCache;
    }

    private static BlockEntityReplayCache createBlockEntityReplayCache(
        EntityRenderDataList entityRenderDataList,
        EntityRenderDataList crumblingRenderDataList,
        Map<Integer, BlockEntityReplayState> blockEntityReplayStates) {
        List<BuiltBuffer> ownedBuffers = new ArrayList<>();
        EntityRenderDataList clonedEntityRenderDataList = cloneEntityRenderDataList(
            entityRenderDataList, ownedBuffers);
        EntityRenderDataList clonedCrumblingRenderDataList = cloneEntityRenderDataList(
            crumblingRenderDataList, ownedBuffers);
        return new BlockEntityReplayCache(clonedEntityRenderDataList,
            clonedCrumblingRenderDataList, new LinkedHashMap<>(blockEntityReplayStates),
            ownedBuffers);
    }

    private static void replaceBlockEntityReplayCache(BlockEntityUpdateBucket bucket,
        BlockEntityReplayCache newBlockEntityReplayCache) {
        clearBlockEntityReplayCache(bucket);
        blockEntityReplayCaches.put(bucket, newBlockEntityReplayCache);
    }

    private static ParticleReplayCache createParticleReplayCache(
        EntityRenderDataList entityRenderDataList,
        ParticleReplayState particleReplayState) {
        List<BuiltBuffer> ownedBuffers = new ArrayList<>();
        EntityRenderDataList clonedRenderDataList = cloneEntityRenderDataList(entityRenderDataList,
            ownedBuffers);
        return new ParticleReplayCache(clonedRenderDataList, particleReplayState, ownedBuffers);
    }

    private static void replaceParticleReplayCache(ParticleUpdateBucket bucket,
        ParticleReplayCache newParticleReplayCache) {
        clearParticleReplayCache(bucket);
        particleReplayCaches.put(bucket, newParticleReplayCache);
    }

    private static EntityRenderDataList cloneEntityRenderDataList(
        EntityRenderDataList sourceEntityRenderDataList,
        List<BuiltBuffer> ownedBuffers) {
        EntityRenderDataList clonedRenderDataList = new EntityRenderDataList();
        for (EntityRenderData entityRenderData : sourceEntityRenderDataList) {
            EntityRenderData clonedRenderData = new EntityRenderData(entityRenderData.hashCode,
                entityRenderData.x, entityRenderData.y, entityRenderData.z,
                entityRenderData.rtFlag, entityRenderData.prebuiltBLAS, entityRenderData.post);
            for (EntityRenderLayer entityRenderLayer : entityRenderData) {
                BuiltBuffer clonedBuiltBuffer = cloneBuiltBuffer(entityRenderLayer.builtBuffer);
                ownedBuffers.add(clonedBuiltBuffer);
                clonedRenderData.add(new EntityRenderLayer(entityRenderLayer.renderLayer,
                    clonedBuiltBuffer, entityRenderLayer.reflect));
            }
            clonedRenderDataList.add(clonedRenderData);
        }
        return clonedRenderDataList;
    }

    private static EntityRenderDataList sliceEntityRenderDataList(
        EntityRenderDataList sourceEntityRenderDataList,
        int fromIndex) {
        EntityRenderDataList slicedRenderDataList = new EntityRenderDataList();
        for (int i = fromIndex; i < sourceEntityRenderDataList.size(); i++) {
            slicedRenderDataList.add(sourceEntityRenderDataList.get(i));
        }
        return slicedRenderDataList;
    }

    private static boolean canReplayThisFrame(long frameCounter, int entityUpdateInterval) {
        return entityUpdateInterval > 1
            && Math.floorMod(frameCounter - 1L, entityUpdateInterval) != 0L;
    }

    private static void applyReplayPositions(EntityRenderDataList entityRenderDataList,
        Map<Integer, ? extends PositionedReplayState> replayStates) {
        for (EntityRenderData entityRenderData : entityRenderDataList) {
            PositionedReplayState replayState = replayStates.get(entityRenderData.hashCode);
            if (replayState == null) {
                continue;
            }
            entityRenderData.setX(replayState.x());
            entityRenderData.setY(replayState.y());
            entityRenderData.setZ(replayState.z());
        }
    }

    private static int blockEntityRenderId(BlockEntity blockEntity, boolean crumbling) {
        return System.identityHashCode(blockEntity) * 31 + (crumbling ? 1 : 0);
    }

    private static long mixReplaySignature(long signature, int value) {
        long mixedSignature = signature ^ Integer.toUnsignedLong(value);
        return mixedSignature * 0x100000001b3L;
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

    public static void queueBuild(
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders,
        EntityRenderDataList entityRenderDataList) {
        queueBuild(storageVertexConsumerProviders, entityRenderDataList, 0.0125f,
            Constants.Coordinates.WORLD, false);
    }

    public static void queueBuild(
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders,
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

        dispatchBuild(entityRenderDataList, lineWidth, coordinate, normalOffset);

        for (EntityRenderData entityRenderData : entityRenderDataList) {
            for (EntityRenderLayer entityRenderLayer : entityRenderData) {
                BuiltBuffer vertexBuffer = entityRenderLayer.builtBuffer;
                vertexBuffer.close();
            }
        }

        for (StorageVertexConsumerProvider storageVertexConsumerProvider : storageVertexConsumerProviders) {
            storageVertexConsumerProvider.close();
        }
    }

    public static void queueBuildWithoutClose(EntityRenderDataList entityRenderDataList) {
        queueBuildWithoutClose(entityRenderDataList, 0.0125f, Constants.Coordinates.WORLD, false);
    }

    public static void queueBuildWithoutClose(EntityRenderDataList entityRenderDataList,
        float lineWidth,
        Constants.Coordinates coordinate,
        boolean normalOffset) {
        if (entityRenderDataList.isEmpty()) {
            return;
        }

        dispatchBuild(entityRenderDataList, lineWidth, coordinate, normalOffset);
    }

    private static void dispatchBuild(EntityRenderDataList entityRenderDataList,
        float lineWidth,
        Constants.Coordinates coordinate,
        boolean normalOffset) {
        BuildSubmissionData submission = prepareBuildSubmission(entityRenderDataList,
            MinecraftClient.getInstance().getTextureManager());

        queueBuild(lineWidth,
            coordinate.getValue(),
            normalOffset,
            submission.entityCount(),
            submission.entityHashCodes(),
            submission.entityPosXs(),
            submission.entityPosYs(),
            submission.entityPosZs(),
            submission.entityRTFlags(),
            submission.entityPrebuiltBLASs(),
            submission.entityPosts(),
            submission.entityLayerCounts(),
            submission.geometryTypes(),
            submission.geometryGroupNames(),
            submission.geometryTextures(),
            submission.vertexFormats(),
            submission.indexFormats(),
            submission.vertexCounts(),
            submission.vertices());
    }

    private static BuildSubmissionData prepareBuildSubmission(EntityRenderDataList entityRenderDataList,
        TextureManager textureManager) {
        int entityCount = entityRenderDataList.getTotalEntityCount();
        int layerCount = entityRenderDataList.getTotalLayersCount();
        BuildScratch scratch = BUILD_SCRATCH.get();

        ByteBuffer entityHashCodeBB = scratch.acquire("entityHashCodes",
            entityCount * Integer.BYTES);
        ByteBuffer entityPosXBB = scratch.acquire("entityPosXs", entityCount * Double.BYTES);
        ByteBuffer entityPosYBB = scratch.acquire("entityPosYs", entityCount * Double.BYTES);
        ByteBuffer entityPosZBB = scratch.acquire("entityPosZs", entityCount * Double.BYTES);
        ByteBuffer entityRTFlagBB = scratch.acquire("entityRTFlags", entityCount * Integer.BYTES);
        ByteBuffer entityPrebuiltBLASBB = scratch.acquire("entityPrebuiltBLASs",
            entityCount * Integer.BYTES);
        ByteBuffer entityPostBB = scratch.acquire("entityPosts", entityCount * Integer.BYTES);
        ByteBuffer entityLayerCountBB = scratch.acquire("entityLayerCounts",
            entityCount * Integer.BYTES);
        ByteBuffer geometryTypeBB = scratch.acquire("geometryTypes", layerCount * Integer.BYTES);
        ByteBuffer geometryGroupNameBB = scratch.acquire("geometryGroupNames",
            layerCount * Long.BYTES);
        ByteBuffer geometryTextureBB = scratch.acquire("geometryTextures",
            layerCount * Integer.BYTES);
        ByteBuffer vertexFormatBB = scratch.acquire("vertexFormats", layerCount * Integer.BYTES);
        ByteBuffer indexFormatBB = scratch.acquire("indexFormats", layerCount * Integer.BYTES);
        ByteBuffer vertexCountBB = scratch.acquire("vertexCounts", layerCount * Integer.BYTES);
        ByteBuffer verticesBB = scratch.acquire("vertices", layerCount * Long.BYTES);

        int entityHashCodeBaseAddr = 0;
        int entityPosXBaseAddr = 0;
        int entityPosYBaseAddr = 0;
        int entityPosZBaseAddr = 0;
        int entityRTFlagBaseAddr = 0;
        int entityPrebuiltBLASBaseAddr = 0;
        int entityPostBaseAddr = 0;
        int entityLayerCountBaseAddr = 0;
        int geometryTypeBaseAddr = 0;
        int geometryGroupNameBaseAddr = 0;
        int geometryTextureBaseAddr = 0;
        int vertexFormatBaseAddr = 0;
        int indexFormatBaseAddr = 0;
        int vertexCountBaseAddr = 0;
        int verticesBaseAddr = 0;

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

            entityPrebuiltBLASBB.putInt(entityPrebuiltBLASBaseAddr, entityRenderData.prebuiltBLAS);
            entityPrebuiltBLASBaseAddr += Integer.BYTES;

            entityPostBB.putInt(entityPostBaseAddr, entityRenderData.post ? 1 : 0);
            entityPostBaseAddr += Integer.BYTES;

            entityLayerCountBB.putInt(entityLayerCountBaseAddr, entityRenderData.size());
            entityLayerCountBaseAddr += Integer.BYTES;

            for (EntityRenderLayer entityRenderLayer : entityRenderData) {
                RenderLayer renderLayer = entityRenderLayer.renderLayer;
                BuiltBuffer vertexBuffer = entityRenderLayer.builtBuffer;

                int geometryTypeID = Constants.GeometryTypes.getGeometryType(renderLayer,
                    entityRenderLayer.reflect).getValue();
                int geometryTextureID = TextureTracker.getRenderLayerTextureGlId(renderLayer,
                    textureManager, MissingSprite.getMissingSpriteId());
                int vertexFormatID = Constants.VertexFormats.getValue(
                    vertexBuffer.getDrawParameters().format());
                int indexFormatID = Constants.DrawModes.getValue(
                    vertexBuffer.getDrawParameters().mode());

                BufferProxy.BufferInfo vertexBufferInfo = BufferProxy.getBufferInfo(
                    vertexBuffer.getBuffer());
                assert vertexBuffer.getDrawParameters().indexCount()
                    == vertexBuffer.getDrawParameters().vertexCount() / 4 * 6;

                geometryTypeBB.putInt(geometryTypeBaseAddr, geometryTypeID);
                geometryTypeBaseAddr += Integer.BYTES;

                geometryGroupNameBB.putLong(geometryGroupNameBaseAddr, memAddress(
                    cachedGeometryGroupName(renderLayer.name)));
                geometryGroupNameBaseAddr += Long.BYTES;

                geometryTextureBB.putInt(geometryTextureBaseAddr, geometryTextureID);
                geometryTextureBaseAddr += Integer.BYTES;

                vertexFormatBB.putInt(vertexFormatBaseAddr, vertexFormatID);
                vertexFormatBaseAddr += Integer.BYTES;

                indexFormatBB.putInt(indexFormatBaseAddr, indexFormatID);
                indexFormatBaseAddr += Integer.BYTES;

                vertexCountBB.putInt(vertexCountBaseAddr,
                    vertexBuffer.getDrawParameters().vertexCount());
                vertexCountBaseAddr += Integer.BYTES;

                verticesBB.putLong(verticesBaseAddr, vertexBufferInfo.addr());
                verticesBaseAddr += Long.BYTES;
            }
        }

        return new BuildSubmissionData(entityCount,
            memAddress(entityHashCodeBB),
            memAddress(entityPosXBB),
            memAddress(entityPosYBB),
            memAddress(entityPosZBB),
            memAddress(entityRTFlagBB),
            memAddress(entityPrebuiltBLASBB),
            memAddress(entityPostBB),
            memAddress(entityLayerCountBB),
            memAddress(geometryTypeBB),
            memAddress(geometryGroupNameBB),
            memAddress(geometryTextureBB),
            memAddress(vertexFormatBB),
            memAddress(indexFormatBB),
            memAddress(vertexCountBB),
            memAddress(verticesBB));
    }

    private static ByteBuffer cachedGeometryGroupName(String groupName) {
        return GEOMETRY_GROUP_NAME_CACHE.computeIfAbsent(groupName,
            key -> MemoryUtil.memUTF8(key, true));
    }

    private record BuildSubmissionData(int entityCount, long entityHashCodes, long entityPosXs,
                                       long entityPosYs, long entityPosZs, long entityRTFlags,
                                       long entityPrebuiltBLASs, long entityPosts,
                                       long entityLayerCounts, long geometryTypes,
                                       long geometryGroupNames, long geometryTextures,
                                       long vertexFormats, long indexFormats, long vertexCounts,
                                       long vertices) {

    }

    private static final class BuildScratch {

        private final Map<String, ByteBuffer> buffers = new HashMap<>();

        private ByteBuffer acquire(String key, int requiredBytes) {
            int minCapacity = Math.max(1, requiredBytes);
            ByteBuffer buffer = buffers.get(key);
            if (buffer == null || buffer.capacity() < minCapacity) {
                if (buffer != null) {
                    MemoryUtil.memFree(buffer);
                }
                buffer = MemoryUtil.memAlloc(nextCapacity(minCapacity));
                buffers.put(key, buffer);
            }
            buffer.clear();
            buffer.limit(requiredBytes);
            return buffer;
        }

        private int nextCapacity(int requiredBytes) {
            int capacity = 256;
            while (capacity < requiredBytes && capacity > 0) {
                capacity <<= 1;
            }
            return capacity > 0 ? capacity : requiredBytes;
        }
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

    public static native void build();

    public record EntityRenderLayer(RenderLayer renderLayer, BuiltBuffer builtBuffer,
                                    boolean reflect) {

    }

    public static class EntityRenderData extends ArrayList<EntityRenderLayer> {

        private final int hashCode;
        private final int rtFlag;
        private final int prebuiltBLAS;
        private final boolean post;
        private double x;
        private double y;
        private double z;

        public EntityRenderData(int hashCode, double x, double y, double z, boolean post) {
            this(hashCode, x, y, z, 0, -1, post);
        }

        public EntityRenderData(int hashCode, double x, double y, double z, int rtFlag) {
            this(hashCode, x, y, z, rtFlag, -1, false);
        }

        public EntityRenderData(int hashCode, double x, double y, double z, int rtFlag,
            int prebuiltBLAS,
            boolean post) {
            this.hashCode = hashCode;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rtFlag = rtFlag;
            this.prebuiltBLAS = prebuiltBLAS;
            this.post = post;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double getZ() {
            return z;
        }

        public void setZ(double z) {
            this.z = z;
        }

        public int getRtFlag() {
            return rtFlag;
        }

        public int getPrebuiltBLAS() {
            return prebuiltBLAS;
        }

        public int getHashCode() {
            return hashCode;
        }

        public boolean isPost() {
            return post;
        }
    }

    public static class EntityRenderDataList extends ArrayList<EntityRenderData> {

        private int totalLayersCount;

        @Override
        public boolean add(EntityRenderData entityRenderData) {
            totalLayersCount += entityRenderData.size();
            return super.add(entityRenderData);
        }

        @Override
        public boolean addAll(java.util.Collection<? extends EntityRenderData> entityRenderDataCollection) {
            int addedLayersCount = 0;
            for (EntityRenderData entityRenderData : entityRenderDataCollection) {
                addedLayersCount += entityRenderData.size();
            }
            boolean changed = super.addAll(entityRenderDataCollection);
            if (changed) {
                totalLayersCount += addedLayersCount;
            }
            return changed;
        }

        public int getTotalLayersCount() {
            return totalLayersCount;
        }

        public int getTotalEntityCount() {
            return this.size();
        }
    }

    private interface PositionedReplayState {

        double x();

        double y();

        double z();
    }

    private record EntityReplayState(double x, double y, double z, float tickDelta,
                                     int rtFlag) implements PositionedReplayState {

    }

    private record BlockEntityReplayState(double x, double y, double z,
                                          int signature) implements PositionedReplayState {

    }

    private static final class ParticleVertexConsumerProvider implements VertexConsumerProvider {

        private final StorageVertexConsumerProvider delegate;
        private final float emissionStrength;
        private final Map<RenderLayer, VertexConsumer> wrappedConsumers = new HashMap<>();

        private ParticleVertexConsumerProvider(StorageVertexConsumerProvider delegate,
            Particle particle) {
            this.delegate = delegate;
            this.emissionStrength = particleEmissionStrength(particle);
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer renderLayer) {
            VertexConsumer vertexConsumer = delegate.getBuffer(renderLayer);
            if (!(vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer)) {
                return vertexConsumer;
            }
            if (emissionStrength <= 0.0f) {
                pbrVertexConsumer.materialHints(0);
                return pbrVertexConsumer;
            }
            pbrVertexConsumer.materialHints(PBRVertexConsumer.MATERIAL_HINT_FORCE_NO_PBR);
            return wrappedConsumers.computeIfAbsent(renderLayer,
                unused -> new ParticleGlowVertexConsumer(pbrVertexConsumer, renderLayer,
                    emissionStrength));
        }
    }

    private static final class ParticleGlowVertexConsumer implements VertexConsumer {

        private final PBRVertexConsumer delegate;
        private final float emissionStrength;
        private final float layerEmissionMultiplier;
        private int red = 255;
        private int green = 255;
        private int blue = 255;
        private int alpha = 255;
        private int lightU = 240;
        private int lightV = 240;

        private ParticleGlowVertexConsumer(PBRVertexConsumer delegate, RenderLayer renderLayer,
            float emissionStrength) {
            this.delegate = delegate;
            this.emissionStrength = emissionStrength;
            String layerName = renderLayer.name.toLowerCase(Locale.ROOT);
            if (layerName.contains("lit")) {
                this.layerEmissionMultiplier = 1.35f;
            } else if (layerName.contains("particle")) {
                this.layerEmissionMultiplier = 1.0f;
            } else {
                this.layerEmissionMultiplier = 0.8f;
            }
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            red = 255;
            green = 255;
            blue = 255;
            alpha = 255;
            lightU = 240;
            lightV = 240;
            delegate.vertex(x, y, z);
            applyEmission();
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
            delegate.color(red, green, blue, alpha);
            applyEmission();
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            this.lightU = u;
            this.lightV = v;
            delegate.light(u, v);
            applyEmission();
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        private void applyEmission() {
            float alphaFactor = alpha / 255.0f;
            float maxChannel = Math.max(red, Math.max(green, blue)) / 255.0f;
            float lightFactor = MathHelper.clamp(Math.max(lightU, lightV) / 240.0f, 0.0f, 1.0f);
            float emission = emissionStrength * layerEmissionMultiplier * alphaFactor
                * Math.max(0.2f, maxChannel) * (0.45f + 0.55f * lightFactor);
            delegate.albedoEmission(emission);
        }
    }

    private record ParticleReplayState(int totalParticleCount, int customParticleCount,
                                       long signature) {

    }

    private record BlockEntityRenderEntry(BlockEntity blockEntity, int crumblingStage,
                                          int mainRenderId, int crumblingRenderId) {

    }

    private record BlockEntityBuildBatch(
        List<StorageVertexConsumerProvider> entityStorageVertexConsumerProviders,
        EntityRenderDataList entityRenderDataList,
        List<StorageVertexConsumerProvider> crumblingStorageVertexConsumerProviders,
        EntityRenderDataList crumblingRenderDataList) {

    }

    public record BlockEntityQueueResult(
        List<StorageVertexConsumerProvider> freshCrumblingStorageVertexConsumerProviders,
        EntityRenderDataList freshCrumblingRenderDataList,
        EntityRenderDataList replayedCrumblingRenderDataList) {

        public boolean isEmpty() {
            return freshCrumblingRenderDataList.isEmpty() && replayedCrumblingRenderDataList.isEmpty();
        }
    }

    private record ParticleRenderEntry(Particle particle, ParticleTextureSheet textureSheet,
                                       boolean custom) {

    }

    private static final class EntityReplayCache {

        private final EntityRenderDataList entityRenderDataList;
        private final Map<Integer, EntityReplayState> entityStates;
        private final List<BuiltBuffer> ownedBuffers;

        private EntityReplayCache(EntityRenderDataList entityRenderDataList,
            Map<Integer, EntityReplayState> entityStates,
            List<BuiltBuffer> ownedBuffers) {
            this.entityRenderDataList = entityRenderDataList;
            this.entityStates = entityStates;
            this.ownedBuffers = ownedBuffers;
        }

        private void close() {
            for (BuiltBuffer ownedBuffer : ownedBuffers) {
                ownedBuffer.close();
            }
        }
    }

    private static final class BlockEntityReplayCache {

        private final EntityRenderDataList entityRenderDataList;
        private final EntityRenderDataList crumblingRenderDataList;
        private final Map<Integer, BlockEntityReplayState> renderStates;
        private final List<BuiltBuffer> ownedBuffers;

        private BlockEntityReplayCache(EntityRenderDataList entityRenderDataList,
            EntityRenderDataList crumblingRenderDataList,
            Map<Integer, BlockEntityReplayState> renderStates,
            List<BuiltBuffer> ownedBuffers) {
            this.entityRenderDataList = entityRenderDataList;
            this.crumblingRenderDataList = crumblingRenderDataList;
            this.renderStates = renderStates;
            this.ownedBuffers = ownedBuffers;
        }

        private void close() {
            for (BuiltBuffer ownedBuffer : ownedBuffers) {
                ownedBuffer.close();
            }
        }
    }

    private static final class ParticleReplayCache {

        private final EntityRenderDataList entityRenderDataList;
        private final ParticleReplayState particleReplayState;
        private final List<BuiltBuffer> ownedBuffers;

        private ParticleReplayCache(EntityRenderDataList entityRenderDataList,
            ParticleReplayState particleReplayState,
            List<BuiltBuffer> ownedBuffers) {
            this.entityRenderDataList = entityRenderDataList;
            this.particleReplayState = particleReplayState;
            this.ownedBuffers = ownedBuffers;
        }

        private void close() {
            for (BuiltBuffer ownedBuffer : ownedBuffers) {
                ownedBuffer.close();
            }
        }
    }
}
