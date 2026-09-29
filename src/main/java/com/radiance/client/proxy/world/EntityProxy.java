package com.radiance.client.proxy.world;

import static com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.LINE_STRIP;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLE_STRIP;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.radiance.client.RadianceClient;
import com.radiance.client.constant.Constants;
import com.radiance.client.constant.Constants.PostRenderFlags;
import com.radiance.client.constant.Constants.RayTracingFlags;
import com.radiance.client.profiler.Profiler;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.client.vertex.TransferredBuildBuffer;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IHeldItemRendererExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleManagerExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.ARGB;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.lwjgl.system.MemoryUtil;

public class EntityProxy {

    public static final ConcurrentMap<Class<? extends Particle>, AtomicInteger> PARTICLE_COUNTERS = new ConcurrentHashMap<>();
    public static MultiBufferSource postTextVertexConsumerProvider;

    private static final ResourceLocation SUN_TEXTURE = ResourceLocation.withDefaultNamespace(
        "textures/environment/sun.png");
    private static final ResourceLocation MOON_PHASES_TEXTURE = ResourceLocation.withDefaultNamespace(
        "textures/environment/moon_phases.png");
    private static final float VILLAGER_LAYER_OFFSET_UNIT = 0.001f / 16.0f;
    private static final String VILLAGER_TEXTURE_PREFIX = "textures/entity/villager/";
    private static final String ZOMBIE_VILLAGER_TEXTURE_PREFIX = "textures/entity/zombie_villager/";
    private static final ResourceLocation WEATHER_RAIN_TEXTURE = ResourceLocation.withDefaultNamespace(
        "textures/environment/rain.png");
    private static final ResourceLocation WEATHER_SNOW_TEXTURE = ResourceLocation.withDefaultNamespace(
        "textures/environment/snow.png");
    private static final String WEATHER_DEFAULT_CONTENT = "/weather/default";
    private static final String WEATHER_RAIN_CONTENT = "/weather/rain";
    private static final String WEATHER_SNOW_CONTENT = "/weather/snow";
    private static final String PARTICLE_DEFAULT_CONTENT = "/particle/default";
    private static final String TEXT_DEFAULT_CONTENT = "/text/default";
    private static final String NAME_TAG_DEFAULT_CONTENT = "/name_tag/default";
    private static final ConcurrentMap<String, ByteBuffer> UTF8_NAME_BUFFERS =
        new ConcurrentHashMap<>();
    private static final long ENTITY_KEY_PAYLOAD_MASK = 0x0fffffffffffffffL;
    private static final long NORMAL_ENTITY_KEY_PREFIX = 0x1000000000000000L;
    private static final long BLOCK_ENTITY_KEY_PREFIX = 0x2000000000000000L;
    private static final long BLOCK_ENTITY_CRUMBLING_KEY_PREFIX = 0x3000000000000000L;
    private static final long BLOCK_DAMAGE_KEY_PREFIX = 0x4000000000000000L;
    private static final long STATIC_ENTITY_KEY_PREFIX = 0x5000000000000000L;
    private static final long TARGET_BLOCK_OUTLINE_KEY_PREFIX = 0x6000000000000000L;

    private static boolean entityNativeTransferEnabled = true;

    public static boolean isEntityNativeTransferEnabled() {
        return entityNativeTransferEnabled;
    }

    private static long normalEntityKey(Entity entity) {
        return NORMAL_ENTITY_KEY_PREFIX |
            (mix64(System.identityHashCode(entity)) & ENTITY_KEY_PAYLOAD_MASK);
    }

    private static long waterMaskEntityKey(long entityKey) {
        long payload = entityKey & ENTITY_KEY_PAYLOAD_MASK;
        return NORMAL_ENTITY_KEY_PREFIX | (mix64(payload ^ 0x6d2b79f5d1f123bbL) & ENTITY_KEY_PAYLOAD_MASK);
    }

    private static long blockEntityKey(BlockEntity blockEntity) {
        return BLOCK_ENTITY_KEY_PREFIX | (mix64(blockEntity.getBlockPos()
            .asLong()) & ENTITY_KEY_PAYLOAD_MASK);
    }

    private static long blockEntityCrumblingKey(BlockEntity blockEntity) {
        return BLOCK_ENTITY_CRUMBLING_KEY_PREFIX | (mix64(blockEntity.getBlockPos()
            .asLong()) & ENTITY_KEY_PAYLOAD_MASK);
    }

    private static long blockDamageKey(BlockPos blockPos) {
        return BLOCK_DAMAGE_KEY_PREFIX | (mix64(blockPos.asLong()) & ENTITY_KEY_PAYLOAD_MASK);
    }

    private static long targetBlockOutlineKey(BlockPos blockPos) {
        return TARGET_BLOCK_OUTLINE_KEY_PREFIX | (mix64(blockPos.asLong()) & ENTITY_KEY_PAYLOAD_MASK);
    }

    private static long staticEntityKey(long value) {
        return STATIC_ENTITY_KEY_PREFIX | (mix64(value) & ENTITY_KEY_PAYLOAD_MASK);
    }

    private static long mix64(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static ByteBuffer getUtf8NameBuffer(String name) {
        String normalizedName = name == null ? "" : name;
        return UTF8_NAME_BUFFERS.computeIfAbsent(normalizedName,
            value -> MemoryUtil.memUTF8(value, true));
    }

    public static void processWorldEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        long entityKey,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Constants.RayTracingFlags rayTracingFlag,
        boolean reflect,
        EntityRenderDataList entityRenderDataList) {
        if (!isEntityNativeTransferEnabled()) {
            return;
        }

        processEntityRenderData(storageVertexConsumerProvider,
            entityKey,
            entityPosX,
            entityPosY,
            entityPosZ,
            rayTracingFlag.getValue(),
            0,
            -1,
            reflect,
            null,
            false,
            entityRenderDataList);
    }

    public static void processPostEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        long entityKey,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Constants.PostRenderFlags postRenderFlag,
        EntityRenderDataList entityRenderDataList) {
        if (!isEntityNativeTransferEnabled()) {
            return;
        }

        processEntityRenderData(storageVertexConsumerProvider,
            entityKey,
            entityPosX,
            entityPosY,
            entityPosZ,
            0,
            postRenderFlag.getValue(),
            -1,
            false,
            renderLayer -> defaultPostContentName(postRenderFlag),
            true,
            entityRenderDataList);
    }

    public static void processPostEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        long entityKey,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Constants.PostRenderFlags postRenderFlag,
        String contentName,
        EntityRenderDataList entityRenderDataList) {
        if (!isEntityNativeTransferEnabled()) {
            return;
        }

        processEntityRenderData(storageVertexConsumerProvider,
            entityKey,
            entityPosX,
            entityPosY,
            entityPosZ,
            0,
            postRenderFlag.getValue(),
            -1,
            false,
            renderLayer -> contentName,
            true,
            entityRenderDataList);
    }

    public static void processPostEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        long entityKey,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Constants.PostRenderFlags postRenderFlag,
        Function<RenderType, String> contentNameResolver,
        EntityRenderDataList entityRenderDataList) {
        if (!isEntityNativeTransferEnabled()) {
            return;
        }

        processEntityRenderData(storageVertexConsumerProvider,
            entityKey,
            entityPosX,
            entityPosY,
            entityPosZ,
            0,
            postRenderFlag.getValue(),
            -1,
            false,
            contentNameResolver,
            true,
            entityRenderDataList);
    }

    private static void processEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        long entityKey,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        int rayTracingFlag,
        int postRenderFlag,
        int prebuiltBLAS,
        boolean reflect,
        Function<RenderType, String> contentNameResolver,
        boolean isPostRender,
        EntityRenderDataList entityRenderDataList) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.EntityProxy.processEntityRenderData");
        }
        try {
            Map<RenderType, VertexConsumer> layerBuffers = storageVertexConsumerProvider.getLayers();
            EntityRenderData
                entityRenderData =
                new EntityRenderData(entityKey, entityPosX, entityPosY,
                    entityPosZ,
                    rayTracingFlag, postRenderFlag, prebuiltBLAS, isPostRender);
            EntityRenderData
                waterMaskRenderData =
                new EntityRenderData(waterMaskEntityKey(entityKey), entityPosX, entityPosY,
                    entityPosZ,
                    RayTracingFlags.BOAT_WATER_MASK.getValue(), 0, prebuiltBLAS, isPostRender);
            for (Map.Entry<RenderType, VertexConsumer> layerBuffer : layerBuffers.entrySet()) {
                RenderType layer = layerBuffer.getKey();
                MeshData buffer = null;

                VertexConsumer vertexConsumer = layerBuffer.getValue();
                if (vertexConsumer instanceof BufferBuilder bufferBuilder) {
                    buffer = bufferBuilder.build();
                } else if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
                    buffer = pbrVertexConsumer.endNullable();
                }

                if (layer.mode() != QUADS && layer.mode() != TRIANGLE_STRIP
                    && layer.mode() != LINE_STRIP &&
                    layer.mode() != LINES) {
                    continue;
                }
                if (buffer == null) {
                    continue;
                }

                String contentName = contentNameResolver == null
                    ? ""
                    : Objects.requireNonNullElse(contentNameResolver.apply(layer), "");
                if (layer.name.contains("water_mask")) {
                    waterMaskRenderData.add(
                        new EntityRenderLayer(layer, buffer, storageVertexConsumerProvider, reflect, contentName));
                } else {
                    entityRenderData.add(
                        new EntityRenderLayer(layer, buffer, storageVertexConsumerProvider, reflect, contentName));
                }
            }

            if (!entityRenderData.isEmpty()) {
                entityRenderDataList.add(entityRenderData);
            }
            if (!waterMaskRenderData.isEmpty()) {
                entityRenderDataList.add(waterMaskRenderData);
            }
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static void queueEntitiesBuild(Camera camera,
        List<Entity> renderedEntities,
        EntityRenderDispatcher entityRenderDispatcher,
        DeltaTracker tickCounter,
        boolean canDrawEntityOutlines) {
        if (!isEntityNativeTransferEnabled()) {
            return;
        }

        if (Profiler.ENABLED) {
            Profiler.enter("java.EntityProxy.queueEntitiesBuild");
        }
        try {
            PoseStack matrixStack = new PoseStack();
            Minecraft client = Minecraft.getInstance();
            TickRateManager tickManager = Objects.requireNonNull(client.level)
                .tickRateManager();
            List<StorageVertexConsumerProvider> entityStorageVertexConsumerProviders = new ArrayList<>();
            EntityRenderDataList entityRenderDataList = new EntityRenderDataList();

            for (Entity entity : renderedEntities) {
                long entityKey = normalEntityKey(entity);

                if (entity.tickCount == 0) {
                    entity.xOld = entity.getX();
                    entity.yOld = entity.getY();
                    entity.zOld = entity.getZ();
                }

                float tickDelta = tickCounter.getGameTimeDeltaPartialTick(!tickManager.isEntityFrozen(entity));
                double entityPosX = Mth.lerp(tickDelta, entity.xOld,
                    entity.getX());
                double entityPosY = Mth.lerp(tickDelta, entity.yOld,
                    entity.getY());
                double entityPosZ = Mth.lerp(tickDelta, entity.zOld,
                    entity.getZ());
                int light = entityRenderDispatcher.getPackedLightCoords(entity, tickDelta);
                Constants.RayTracingFlags rayTracingFlag = Constants.RayTracingFlags.WORLD;
                if (entity.equals(camera.getEntity())) {
                    rayTracingFlag = Constants.RayTracingFlags.PLAYER;
                } else if (entity instanceof FishingHook) {
                    rayTracingFlag = Constants.RayTracingFlags.FISHING_BOBBER;
                }

                StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                    786432);
                entityStorageVertexConsumerProvider.setEntityBuildContext(
                    Constants.Coordinates.WORLD.getValue(), false, 0, 0, 0);
                entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);
                MultiBufferSource vertexConsumerProvider = entityStorageVertexConsumerProvider;

                if (entity instanceof Display.TextDisplay) {
                    entityStorageVertexConsumerProvider.setEntityBuildContext(
                        Constants.Coordinates.WORLD.getValue(), false,
                        entityPosX, entityPosY, entityPosZ);
                    entityRenderDispatcher.render(entity,
                        0,
                        0,
                        0,
                        tickDelta,
                        matrixStack,
                        entityStorageVertexConsumerProvider,
                        light);
                    processPostEntityRenderData(entityStorageVertexConsumerProvider,
                        entityKey,
                        entityPosX,
                        entityPosY,
                        entityPosZ,
                        PostRenderFlags.TEXT,
                        entityRenderDataList);
                    continue;
                }

                StorageVertexConsumerProvider postTextStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                    16384);
                postTextStorageVertexConsumerProvider.setEntityBuildContext(
                    Constants.Coordinates.WORLD.getValue(), false,
                    entityPosX, entityPosY, entityPosZ);
                postTextVertexConsumerProvider = postTextStorageVertexConsumerProvider;
                try {
                    entityRenderDispatcher.render(entity,
                        0,
                        0,
                        0,
                        tickDelta,
                        matrixStack,
                        vertexConsumerProvider,
                        light);
                } finally {
                    postTextVertexConsumerProvider = null;
                }

                boolean hasPostText = !postTextStorageVertexConsumerProvider.getLayers()
                    .isEmpty();
                if (hasPostText) {
                    entityStorageVertexConsumerProviders.add(postTextStorageVertexConsumerProvider);
                    processPostEntityRenderData(postTextStorageVertexConsumerProvider,
                        entityKey,
                        entityPosX,
                        entityPosY,
                        entityPosZ,
                        PostRenderFlags.NAME_TAG,
                        entityRenderDataList);
                } else {
                    postTextStorageVertexConsumerProvider.close();
                }

                processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                    entityKey,
                    entityPosX,
                    entityPosY,
                    entityPosZ,
                    rayTracingFlag,
                    true,
                    entityRenderDataList);
            }

            queueBuild(entityStorageVertexConsumerProviders, entityRenderDataList);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static synchronized Tuple<List<StorageVertexConsumerProvider>, EntityRenderDataList> queueBlockEntitiesRebuild(
        ViewArea chunks,
        Set<BlockEntity> noCullingBlockEntities,
        Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions,
        BlockEntityRenderDispatcher blockEntityRenderDispatcher,
        float tickDelta) {
        if (!isEntityNativeTransferEnabled()) {
            return new Tuple<>(List.of(), new EntityRenderDataList());
        }

        if (Profiler.ENABLED) {
            Profiler.enter("java.EntityProxy.queueBlockEntitiesRebuild");
        }
        try {
            PoseStack matrixStack = new PoseStack();
            List<StorageVertexConsumerProvider> entityStorageVertexConsumerProviders = new ArrayList<>();
            EntityRenderDataList entityRenderDataList = new EntityRenderDataList();
            List<StorageVertexConsumerProvider> crumblingStorageVertexConsumerProviders = new ArrayList<>();
            EntityRenderDataList crumblingRenderDataList = new EntityRenderDataList();
            LongSet queuedBlockEntityKeys = new LongOpenHashSet();

            SectionRenderDispatcher.RenderSection[] blockEntitySections;
            if (Profiler.ENABLED) Profiler.enter("java.EntityProxy.blockEntitySections");
            try {
                blockEntitySections = BlockEntitySectionCache.sections(chunks);
            } finally {
                if (Profiler.ENABLED) Profiler.exit();
            }
            for (SectionRenderDispatcher.RenderSection builtChunk : blockEntitySections) {
                List<BlockEntity> chunkBlockEntities = builtChunk.getCompiled()
                    .getRenderableBlockEntities();
                if (!chunkBlockEntities.isEmpty()) {
                    for (BlockEntity blockEntity : chunkBlockEntities) {
                        long entityKey = blockEntityKey(blockEntity);
                        if (!queuedBlockEntityKeys.add(entityKey)) {
                            continue;
                        }

                        BlockPos blockPos = blockEntity.getBlockPos();
                        double entityPosX = blockPos.getX();
                        double entityPosY = blockPos.getY();
                        double entityPosZ = blockPos.getZ();
                        SortedSet<BlockDestructionProgress> breakingInfos = blockBreakingProgressions.get(
                            blockPos.asLong());
                        boolean hasCrumbling = breakingInfos != null && !breakingInfos.isEmpty() &&
                            breakingInfos.last()
                                .getProgress() >= 0;

                        StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                            786432);
                        entityStorageVertexConsumerProvider.setEntityBuildContext(
                            Constants.Coordinates.WORLD.getValue(), false, 0, 0, 0);
                        entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);
                        StorageVertexConsumerProvider crumblingStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                            0);
                        crumblingStorageVertexConsumerProvider.setEntityBuildContext(
                            Constants.Coordinates.WORLD.getValue(), true, 0, 0, 0);
                        crumblingStorageVertexConsumerProviders.add(
                            crumblingStorageVertexConsumerProvider);
                        MultiBufferSource vertexConsumerProvider = entityStorageVertexConsumerProvider;

                        matrixStack.pushPose();
                        if (hasCrumbling) {
                            int stage = breakingInfos.last()
                                .getProgress();
                            PoseStack.Pose entry = matrixStack.last();
                            VertexConsumer vertexConsumer = new SheetedDecalTextureGenerator(
                                crumblingStorageVertexConsumerProvider.getBuffer(
                                    ModelBakery.DESTROY_TYPES.get(stage)), entry,
                                1.0F);
                            vertexConsumerProvider = renderLayer -> {
                                VertexConsumer vertexConsumer2 = entityStorageVertexConsumerProvider.getBuffer(
                                    renderLayer);
                                return renderLayer.affectsCrumbling() ?
                                    VertexMultiConsumer.create(vertexConsumer, vertexConsumer2) :
                                    vertexConsumer2;
                            };
                        }

                        blockEntityRenderDispatcher.render(blockEntity, tickDelta, matrixStack,
                            vertexConsumerProvider);
                        matrixStack.popPose();

                        processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                            entityKey,
                            entityPosX,
                            entityPosY,
                            entityPosZ,
                            Constants.RayTracingFlags.WORLD,
                            true,
                            entityRenderDataList);
                        processWorldEntityRenderData(crumblingStorageVertexConsumerProvider,
                            blockEntityCrumblingKey(blockEntity),
                            entityPosX,
                            entityPosY,
                            entityPosZ,
                            Constants.RayTracingFlags.WORLD,
                            true,
                            crumblingRenderDataList);
                    }
                }
            }

            for (BlockEntity blockEntity : noCullingBlockEntities) {
                long entityKey = blockEntityKey(blockEntity);
                if (!queuedBlockEntityKeys.add(entityKey)) {
                    continue;
                }

                BlockPos blockPos = blockEntity.getBlockPos();
                double entityPosX = blockPos.getX();
                double entityPosY = blockPos.getY();
                double entityPosZ = blockPos.getZ();

                StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                    786432);
                entityStorageVertexConsumerProvider.setEntityBuildContext(
                    Constants.Coordinates.WORLD.getValue(), false, 0, 0, 0);
                entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);

                matrixStack.pushPose();
                blockEntityRenderDispatcher.render(blockEntity, tickDelta, matrixStack,
                    entityStorageVertexConsumerProvider);
                matrixStack.popPose();

                processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                    entityKey,
                    entityPosX,
                    entityPosY,
                    entityPosZ,
                    Constants.RayTracingFlags.WORLD,
                    true,
                    entityRenderDataList);
            }

            queueBuild(entityStorageVertexConsumerProviders, entityRenderDataList);
            return new Tuple<>(crumblingStorageVertexConsumerProviders, crumblingRenderDataList);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static void queueCrumblingRebuild(Camera camera,
        Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions,
        BlockRenderDispatcher blockRenderManager,
        ClientLevel world,
        List<StorageVertexConsumerProvider> crumblingStorageVertexConsumerProviders,
        EntityRenderDataList crumblingRenderDataList) {
        if (!isEntityNativeTransferEnabled()) {
            return;
        }

        if (Profiler.ENABLED) {
            Profiler.enter("java.EntityProxy.queueCrumblingRebuild");
        }
        try {
            PoseStack matrixStack = new PoseStack();
            List<StorageVertexConsumerProvider> blockCrumblingStorageVertexConsumerProviders = new ArrayList<>();
            EntityRenderDataList blockCrumblingRenderDataList = new EntityRenderDataList();

            Vec3 cameraPos = camera.getPosition();
            double cameraX = cameraPos.x();
            double cameraY = cameraPos.y();
            double cameraZ = cameraPos.z();

            for (Long2ObjectMap.Entry<SortedSet<BlockDestructionProgress>> blockBreakingProgression :
                blockBreakingProgressions.long2ObjectEntrySet()) {
                BlockPos blockPos = BlockPos.of(blockBreakingProgression.getLongKey());
                double entityPosX = blockPos.getX();
                double entityPosY = blockPos.getY();
                double entityPosZ = blockPos.getZ();

                if (!(blockPos.distToCenterSqr(cameraX, cameraY, cameraZ) > 1024.0)) {
                    SortedSet<BlockDestructionProgress> breakingInfos = blockBreakingProgression.getValue();
                    if (breakingInfos != null && !breakingInfos.isEmpty()) {
                        int
                            stage =
                            breakingInfos.last()
                                .getProgress();

                        StorageVertexConsumerProvider blockCrumblingStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                            786432);
                        blockCrumblingStorageVertexConsumerProvider.setEntityBuildContext(
                            Constants.Coordinates.WORLD.getValue(), true, 0, 0, 0);
                        blockCrumblingStorageVertexConsumerProviders.add(
                            blockCrumblingStorageVertexConsumerProvider);

                        matrixStack.pushPose();
                        PoseStack.Pose entry = matrixStack.last();
                        VertexConsumer
                            vertexConsumer =
                            new SheetedDecalTextureGenerator(
                                blockCrumblingStorageVertexConsumerProvider.getBuffer(
                                    ModelBakery.DESTROY_TYPES.get(
                                        stage)), entry, 1.0F);
                        blockRenderManager.renderBreakingTexture(world.getBlockState(blockPos), blockPos, world,
                            matrixStack, vertexConsumer);
                        matrixStack.popPose();

                        processWorldEntityRenderData(blockCrumblingStorageVertexConsumerProvider,
                            blockDamageKey(blockPos),
                            entityPosX,
                            entityPosY,
                            entityPosZ,
                            Constants.RayTracingFlags.WORLD,
                            true,
                            blockCrumblingRenderDataList);
                    }
                }
            }

            List<StorageVertexConsumerProvider>
                combinedStorageVertexConsumerProviders =
                Stream.concat(crumblingStorageVertexConsumerProviders.stream(),
                        blockCrumblingStorageVertexConsumerProviders.stream())
                    .toList();

            EntityRenderDataList
                combinedRenderDataList =
                Stream.concat(crumblingRenderDataList.stream(), blockCrumblingRenderDataList.stream())
                    .collect(EntityRenderDataList::new, EntityRenderDataList::add,
                        EntityRenderDataList::addAll);

            queueBuild(combinedStorageVertexConsumerProviders, combinedRenderDataList, 0.0f,
                Constants.Coordinates.WORLD,
                true);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static void queueHandRebuild(RenderBuffers buffers, float tickDelta,
        ItemInHandRenderer firstPersonRenderer, float handProjectionScale) {
        if (!isEntityNativeTransferEnabled()) {
            return;
        }

        if (Profiler.ENABLED) {
            Profiler.enter("java.EntityProxy.queueHandRebuild");
        }
        try {
            Minecraft client = Minecraft.getInstance();
        PoseStack matrixStack = new PoseStack();
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();

        StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
            8192);
        storageVertexConsumerProvider.setEntityBuildContext(
            Constants.Coordinates.CAMERA.getValue(), false, 0, 0, 0);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        matrixStack.pushPose();

        boolean isCameraEntitySleeping = client.getCameraEntity() instanceof LivingEntity
            && ((LivingEntity) client.getCameraEntity()).isSleeping();
        if (client.options.getCameraType()
            .isFirstPerson() && !isCameraEntitySleeping && !client.options.hideGui &&
            client.gameMode.getPlayerMode() != GameType.SPECTATOR) {
            matrixStack.scale(handProjectionScale, handProjectionScale, 1.0F);
            ((IHeldItemRendererExt) firstPersonRenderer).radiance$renderItem(tickDelta,
                matrixStack,
                storageVertexConsumerProvider,
                client.player,
                client.getEntityRenderDispatcher()
                    .getPackedLightCoords(client.player, tickDelta));
        }

        matrixStack.popPose();

        if (client.options.getCameraType()
            .isFirstPerson() && !isCameraEntitySleeping && !client.options.hideGui &&
            client.gameMode.getPlayerMode() != GameType.SPECTATOR) {
            processWorldEntityRenderData(storageVertexConsumerProvider,
                staticEntityKey(Constants.RayTracingFlags.HAND.getValue()),
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
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static void queueParticleRebuild(Camera camera, float tickDelta, Frustum frustum) {
        if (!isEntityNativeTransferEnabled()) {
            return;
        }

        if (Profiler.ENABLED) {
            Profiler.enter("java.EntityProxy.queueParticleRebuild");
        }
        try {
            List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();

        Map<String, StorageVertexConsumerProvider> postStorageVertexConsumerProviders = new LinkedHashMap<>();

        ParticleEngine particleManager = Minecraft.getInstance().particleEngine;
        IParticleManagerExt particleManagerExt = (IParticleManagerExt) particleManager;
        Map<ParticleRenderType, Queue<Particle>> particles = particleManagerExt.radiance$getParticles();

        for (ParticleRenderType particleTextureSheet : particleManagerExt.radiance$getTextureSheets()) {
            Queue<Particle> particleQueue = particles.get(particleTextureSheet);
            if (particleQueue != null && !particleQueue.isEmpty()) {
                for (Particle particle : particleQueue) {
                    String contentName = normalizeParticleContentName(
                        ((IParticleExt) particle).radiance$getContentName());
                    StorageVertexConsumerProvider postStorageVertexConsumerProvider =
                        postStorageVertexConsumerProviders.computeIfAbsent(contentName, key -> {
                            StorageVertexConsumerProvider provider = new StorageVertexConsumerProvider(
                                0);
                            provider.setEntityBuildContext(
                                Constants.Coordinates.CAMERA_SHIFT.getValue(), false, 0, 0, 0);
                            storageVertexConsumerProviders.add(provider);
                            return provider;
                        });

                    VertexConsumer
                        vertexConsumer =
                        postStorageVertexConsumerProvider.getBuffer(
                            Objects.requireNonNull(
                                particleTextureSheet.renderType()));

                    try {
                        particle.render(vertexConsumer, camera, tickDelta);
                    } catch (Throwable particleRenderError) {
                        CrashReport crashReport = CrashReport.forThrowable(particleRenderError, "Rendering Particle");
                        CrashReportCategory crashReportSection = crashReport.addCategory(
                            "Particle being rendered");
                        crashReportSection.setDetail("Particle", particle);
                        crashReportSection.setDetail("Particle Type", particleTextureSheet);
                        throw new ReportedException(crashReport);
                    }
                }
            }
        }

        for (Map.Entry<String, StorageVertexConsumerProvider> entry : postStorageVertexConsumerProviders.entrySet()) {
            processPostEntityRenderData(entry.getValue(), staticEntityKey(entry.getKey()
                .hashCode() + 3L), 0, 0, 0,
                PostRenderFlags.PARTICLE, entry.getKey(), renderDataList);
        }

        StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
            0);
        storageVertexConsumerProvider.setEntityBuildContext(
            Constants.Coordinates.CAMERA_SHIFT.getValue(), false, 0, 0, 0);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        Queue<Particle> customParticleQueue = particles.get(ParticleRenderType.CUSTOM);
        if (customParticleQueue != null && !customParticleQueue.isEmpty()) {
            for (Particle particle : customParticleQueue) {

                PoseStack matrixStack = new PoseStack();

                try {
                    particle.renderCustom(matrixStack, storageVertexConsumerProvider, camera,
                        tickDelta);
                } catch (Throwable particleRenderError) {
                    CrashReport crashReport = CrashReport.forThrowable(particleRenderError, "Rendering Particle");
                    CrashReportCategory crashReportSection = crashReport.addCategory(
                        "Particle being rendered");
                    crashReportSection.setDetail("Particle", particle::toString);
                    crashReportSection.setDetail("Particle Type", "Custom");
                    throw new ReportedException(crashReport);
                }
            }
        }

        processWorldEntityRenderData(storageVertexConsumerProvider, staticEntityKey(1), 0, 0, 0,
            Constants.RayTracingFlags.PARTICLE, true, renderDataList);

            queueBuild(storageVertexConsumerProviders, renderDataList, 0.0f,
                Constants.Coordinates.CAMERA_SHIFT, false);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static EntityRenderData worldRenderCapture;
    private static final Map<Object, Long> worldRenderKeys = new java.util.IdentityHashMap<>();

    public static void captureWorldRender(Object stage, Runnable render) {
        if (worldRenderCapture != null) {
            render.run();
            return;
        }
        long key = worldRenderKeys.computeIfAbsent(stage,
            ignored -> staticEntityKey(0x10000L + worldRenderKeys.size()));
        EntityRenderData data = new EntityRenderData(key, 0, 0, 0, 0,
            Constants.PostRenderFlags.WORLD_OVERLAY.getValue(), -1, true);
        worldRenderCapture = data;
        try {
            render.run();
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        } finally {
            worldRenderCapture = null;
            if (!data.isEmpty()) {
                EntityRenderDataList list = new EntityRenderDataList();
                list.add(data);
                queueBuild(List.of(), list, 0.0075f, Constants.Coordinates.CAMERA_SHIFT, false);
            }
        }
    }

    public static boolean isWorldRenderCaptureActive() {
        return worldRenderCapture != null;
    }

    public static boolean captureWorldRenderLayer(RenderType layer, MeshData mesh) {
        if (worldRenderCapture == null) {
            return false;
        }
        String content = layer instanceof RenderType.CompositeRenderType composite
            && composite.state.depthTestState.functionName.equals("always")
            ? "/world_overlay/through" : "/world_overlay/default";
        worldRenderCapture.add(new EntityRenderLayer(layer, mesh, null, false, content));
        return true;
    }

    public static void queueTargetBlockOutlineRebuild(Camera camera, ClientLevel world) {
        if (!isEntityNativeTransferEnabled()) {
            return;
        }

        if (Profiler.ENABLED) {
            Profiler.enter("java.EntityProxy.queueTargetBlockOutlineRebuild");
        }
        try {
            List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();

        Minecraft client = Minecraft.getInstance();
        PoseStack matrixStack = new PoseStack();

        StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
            0);
        storageVertexConsumerProvider.setEntityBuildContext(
            Constants.Coordinates.WORLD.getValue(), false, 0, 0, 0);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        if (client.hitResult instanceof BlockHitResult blockHitResult) {
            if (blockHitResult.getType() != HitResult.Type.MISS) {
                BlockPos blockPos = blockHitResult.getBlockPos();
                BlockState blockState = world.getBlockState(blockPos);
                if (!blockState.isAir() && world.getWorldBorder()
                    .isWithinBounds(blockPos)) {
                    Boolean
                        isHighContrastBlockOutline =
                        client.options.highContrastBlockOutline()
                            .get();
                    if (isHighContrastBlockOutline) {
                        VertexConsumer vertexConsumer = storageVertexConsumerProvider.getBuffer(
                            RenderType.secondaryBlockOutline());
                        ShapeRenderer.renderShape(matrixStack,
                            vertexConsumer,
                            blockState.getShape(world, blockPos,
                                CollisionContext.of(camera.getEntity())),
                            0,
                            0,
                            0,
                            -16777216);
                    }

                    VertexConsumer vertexConsumer = storageVertexConsumerProvider.getBuffer(
                        RenderType.lines());
                    int color =
                        isHighContrastBlockOutline ? CommonColors.HIGH_CONTRAST_DIAMOND
                            : ARGB.color(102, CommonColors.BLACK);
                    ShapeRenderer.renderShape(matrixStack,
                        vertexConsumer,
                        blockState.getShape(world, blockPos,
                            CollisionContext.of(camera.getEntity())),
                        0,
                        0,
                        0,
                        color);

                    processWorldEntityRenderData(storageVertexConsumerProvider,
                        targetBlockOutlineKey(blockPos),
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
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static void queueWeatherBuild(WeatherEffectRenderer weatherRendering,
        WorldBorderRenderer worldBorderRendering,
        ClientLevel world,
        Camera camera,
        int ticks,
        float tickDelta) {
        if (!isEntityNativeTransferEnabled()) {
            return;
        }

        if (Profiler.ENABLED) {
            Profiler.enter("java.EntityProxy.queueWeatherBuild");
        }
        try {
            List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();

        StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
            0);
        storageVertexConsumerProvider.setEntityBuildContext(
            Constants.Coordinates.CAMERA_SHIFT.getValue(), false, 0, 0, 0);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        weatherRendering.render(world, storageVertexConsumerProvider, ticks, tickDelta,
            camera.getPosition());

        Minecraft client = Minecraft.getInstance();
        int clampedViewDistance = client.options.getEffectiveRenderDistance() * 16;
        float farPlaneDistance = client.gameRenderer.getDepthFar();
        worldBorderRendering.render(world.getWorldBorder(), camera.getPosition(), clampedViewDistance,
            farPlaneDistance);

        processPostEntityRenderData(storageVertexConsumerProvider, staticEntityKey(2), 0, 0, 0,
            PostRenderFlags.WEATHER, EntityProxy::resolveWeatherContentName, renderDataList);

            queueBuild(storageVertexConsumerProviders, renderDataList, 0.0f,
                Constants.Coordinates.CAMERA_SHIFT, false);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
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
        queueBuildInternal(storageVertexConsumerProviders, entityRenderDataList, lineWidth,
            coordinate, normalOffset, true);
    }

    public static void queueBuildWithoutClose(EntityRenderDataList entityRenderDataList) {
        queueBuildWithoutClose(entityRenderDataList, 0.0125f, Constants.Coordinates.WORLD, false);
    }

    public static void queueBuildWithoutClose(EntityRenderDataList entityRenderDataList,
        float lineWidth,
        Constants.Coordinates coordinate,
        boolean normalOffset) {
        queueBuildInternal(null, entityRenderDataList, lineWidth, coordinate, normalOffset, false);
    }

    private static void queueBuildInternal(
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders,
        EntityRenderDataList entityRenderDataList,
        float lineWidth,
        Constants.Coordinates coordinate,
        boolean normalOffset,
        boolean closeAfterBuild) {
        if (!isEntityNativeTransferEnabled()) {
            if (closeAfterBuild) {
                closeUntransferredBuiltBuffers(entityRenderDataList, null);
                closeStorageVertexConsumerProviders(storageVertexConsumerProviders);
            }
            return;
        }

        if (Profiler.ENABLED) {
            Profiler.enter("java.EntityProxy.queueBuildInternal");
        }
        try {
            TextureManager
                textureManager =
                Minecraft.getInstance()
                    .getTextureManager();
        ByteBuffer entityKeyBuffer = null;
        ByteBuffer entityPosXBuffer = null;
        ByteBuffer entityPosYBuffer = null;
        ByteBuffer entityPosZBuffer = null;
        ByteBuffer entityRayTracingFlagBuffer = null;
        ByteBuffer entityPostRenderFlagBuffer = null;
        ByteBuffer entityPrebuiltBlasBuffer = null;
        ByteBuffer entityPostRenderBuffer = null;
        ByteBuffer entityLayerCountBuffer = null;
        ByteBuffer geometryTypeBuffer = null;
        ByteBuffer geometryGroupNamePointerBuffer = null;
        ByteBuffer geometryContentNamePointerBuffer = null;
        ByteBuffer geometryTextureBuffer = null;
        ByteBuffer geometryNormalOffsetBuffer = null;
        ByteBuffer vertexFormatBuffer = null;
        ByteBuffer indexFormatBuffer = null;
        ByteBuffer vertexCountBuffer = null;
        ByteBuffer vertexPointerBuffer = null;
        TransferredBuildBuffer[] transferredBuffers =
            closeAfterBuild ? new TransferredBuildBuffer[entityRenderDataList.getTotalLayersCount()] : null;
        boolean submittedNative = false;

        try {
            int entityKeySize = entityRenderDataList.getTotalEntityCount() * Long.BYTES;
            entityKeyBuffer = MemoryUtil.memAlloc(entityKeySize);
            long entityKeyAddress = memAddress(entityKeyBuffer);
            int entityKeyByteOffset = 0;

            int entityPosXSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
            entityPosXBuffer = MemoryUtil.memAlloc(entityPosXSize);
            long entityPosXAddress = memAddress(entityPosXBuffer);
            int entityPosXByteOffset = 0;

            int entityPosYSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
            entityPosYBuffer = MemoryUtil.memAlloc(entityPosYSize);
            long entityPosYAddress = memAddress(entityPosYBuffer);
            int entityPosYByteOffset = 0;

            int entityPosZSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
            entityPosZBuffer = MemoryUtil.memAlloc(entityPosZSize);
            long entityPosZAddress = memAddress(entityPosZBuffer);
            int entityPosZByteOffset = 0;

            int entityRayTracingFlagSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityRayTracingFlagBuffer = MemoryUtil.memAlloc(entityRayTracingFlagSize);
            long entityRayTracingFlagAddress = memAddress(entityRayTracingFlagBuffer);
            int entityRayTracingFlagByteOffset = 0;

            int entityPostRenderFlagSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityPostRenderFlagBuffer = MemoryUtil.memAlloc(entityPostRenderFlagSize);
            long entityPostRenderFlagAddress = memAddress(entityPostRenderFlagBuffer);
            int entityPostRenderFlagByteOffset = 0;

            int entityPrebuiltBLASSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityPrebuiltBlasBuffer = MemoryUtil.memAlloc(entityPrebuiltBLASSize);
            long entityPrebuiltBlasAddress = memAddress(entityPrebuiltBlasBuffer);
            int entityPrebuiltBlasByteOffset = 0;

            int entityPostSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityPostRenderBuffer = MemoryUtil.memAlloc(entityPostSize);
            long entityPostRenderAddress = memAddress(entityPostRenderBuffer);
            int entityPostRenderByteOffset = 0;

            int entityLayerCountSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityLayerCountBuffer = MemoryUtil.memAlloc(entityLayerCountSize);
            long entityLayerCountAddress = memAddress(entityLayerCountBuffer);
            int entityLayerCountByteOffset = 0;

            int geometryTypeSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            geometryTypeBuffer = MemoryUtil.memAlloc(geometryTypeSize);
            long geometryTypeAddress = memAddress(geometryTypeBuffer);
            int geometryTypeByteOffset = 0;

            int geometryGroupNameSize = entityRenderDataList.getTotalLayersCount() * Long.BYTES;
            geometryGroupNamePointerBuffer = MemoryUtil.memAlloc(geometryGroupNameSize);
            long geometryGroupNameAddress = memAddress(geometryGroupNamePointerBuffer);
            int geometryGroupNameByteOffset = 0;

            int geometryContentNameSize = entityRenderDataList.getTotalLayersCount() * Long.BYTES;
            geometryContentNamePointerBuffer = MemoryUtil.memAlloc(geometryContentNameSize);
            long geometryContentNameAddress = memAddress(geometryContentNamePointerBuffer);
            int geometryContentNameByteOffset = 0;

            int geometryTextureSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            geometryTextureBuffer = MemoryUtil.memAlloc(geometryTextureSize);
            long geometryTextureAddress = memAddress(geometryTextureBuffer);
            int geometryTextureByteOffset = 0;

            int geometryNormalOffsetSize = entityRenderDataList.getTotalLayersCount() * Float.BYTES;
            geometryNormalOffsetBuffer = MemoryUtil.memAlloc(geometryNormalOffsetSize);
            long geometryNormalOffsetAddress = memAddress(geometryNormalOffsetBuffer);
            int geometryNormalOffsetByteOffset = 0;

            int vertexFormatSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            vertexFormatBuffer = MemoryUtil.memAlloc(vertexFormatSize);
            long vertexFormatAddress = memAddress(vertexFormatBuffer);
            int vertexFormatByteOffset = 0;

            int indexFormatSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            indexFormatBuffer = MemoryUtil.memAlloc(indexFormatSize);
            long indexFormatAddress = memAddress(indexFormatBuffer);
            int indexFormatByteOffset = 0;

            int vertexCountSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            vertexCountBuffer = MemoryUtil.memAlloc(vertexCountSize);
            long vertexCountAddress = memAddress(vertexCountBuffer);
            int vertexCountByteOffset = 0;

            int verticesSize = entityRenderDataList.getTotalLayersCount() * Long.BYTES;
            vertexPointerBuffer = MemoryUtil.memAlloc(verticesSize);
            long vertexPointerAddress = memAddress(vertexPointerBuffer);
            int vertexPointerByteOffset = 0;

            int transferBufferIndex = 0;
            for (EntityRenderData entityRenderData : entityRenderDataList) {
                entityKeyBuffer.putLong(entityKeyByteOffset, entityRenderData.entityKey);
                entityKeyByteOffset += Long.BYTES;

                entityPosXBuffer.putDouble(entityPosXByteOffset, entityRenderData.x);
                entityPosXByteOffset += Double.BYTES;

                entityPosYBuffer.putDouble(entityPosYByteOffset, entityRenderData.y);
                entityPosYByteOffset += Double.BYTES;

                entityPosZBuffer.putDouble(entityPosZByteOffset, entityRenderData.z);
                entityPosZByteOffset += Double.BYTES;

                entityRayTracingFlagBuffer.putInt(entityRayTracingFlagByteOffset,
                    entityRenderData.rayTracingFlag);
                entityRayTracingFlagByteOffset += Integer.BYTES;

                entityPostRenderFlagBuffer.putInt(entityPostRenderFlagByteOffset,
                    entityRenderData.postRenderFlag);
                entityPostRenderFlagByteOffset += Integer.BYTES;

                entityPrebuiltBlasBuffer.putInt(entityPrebuiltBlasByteOffset, entityRenderData.prebuiltBLAS);
                entityPrebuiltBlasByteOffset += Integer.BYTES;

                entityPostRenderBuffer.putInt(entityPostRenderByteOffset, entityRenderData.isPostRender ? 1 : 0);
                entityPostRenderByteOffset += Integer.BYTES;

                entityLayerCountBuffer.putInt(entityLayerCountByteOffset, entityRenderData.size());
                entityLayerCountByteOffset += Integer.BYTES;

                for (EntityRenderLayer entityRenderLayer : entityRenderData) {
                    RenderType renderLayer = entityRenderLayer.renderLayer;
                    MeshData vertexBuffer = entityRenderLayer.builtBuffer;

                    ResourceLocation
                        textureIdentifier =
                        ((RenderType.CompositeRenderType) renderLayer).state.textureState.cutoutTexture()
                            .orElse(MissingTextureAtlasSprite.getLocation());
                    int
                        geometryTypeID =
                        Constants.GeometryTypes.getGeometryType(renderLayer, entityRenderLayer.reflect)
                            .getValue();
                    int
                        geometryTextureID =
                        textureManager.getTexture(textureIdentifier)
                            .getId();
                    float geometryNormalOffset = getGeometryNormalOffset(textureIdentifier);
                    int
                        vertexFormatID =
                        Constants.VertexFormats.getValue(vertexBuffer.drawState()
                            .format());
                    int
                        indexFormatID =
                        Constants.DrawModes.getValue(vertexBuffer.drawState()
                            .mode());
                    float externalBaseX = entityRenderData.isPostRender ? (float) entityRenderData.x : 0.0f;
                    float externalBaseY = entityRenderData.isPostRender ? (float) entityRenderData.y : 0.0f;
                    float externalBaseZ = entityRenderData.isPostRender ? (float) entityRenderData.z : 0.0f;
                    boolean canUseExternalPbr = PBRVertexConsumer.canUseExternalPbr(vertexBuffer);
                    boolean hasFinalExternalPbrContext = canUseExternalPbr &&
                        entityRenderLayer.ownerProvider != null &&
                        entityRenderLayer.ownerProvider.hasFinalEntityBuildContext(
                            coordinate.getValue(), normalOffset, externalBaseX, externalBaseY, externalBaseZ);
                    if (canUseExternalPbr && !hasFinalExternalPbrContext) {
                        throw new IllegalStateException(
                            "PBR QUADS entity layer was built before its final build context was set: layer=" +
                                renderLayer.name + ", content=" + entityRenderLayer.contentName() +
                                ", coordinate=" + coordinate.getValue() +
                                ", normalOffset=" + normalOffset +
                                ", postBase=(" + externalBaseX + ", " + externalBaseY + ", " + externalBaseZ + ")");
                    }
                    boolean shouldTransferExternalPbr = transferredBuffers != null && hasFinalExternalPbrContext;

                    TransferredBuildBuffer transferredBuffer = null;
                    long vertexAddress;

                    if (shouldTransferExternalPbr) {
                        transferredBuffer = TransferredBuildBuffer.takeEntity(
                            entityRenderLayer.ownerProvider, renderLayer, vertexBuffer);
                        transferredBuffers[transferBufferIndex] = transferredBuffer;
                        vertexAddress = transferredBuffer.vertexAddress();
                    } else {
                        BufferProxy.BufferInfo vertexBufferInfo = BufferProxy.getBufferInfo(
                            vertexBuffer.vertexBuffer());
                        vertexAddress = vertexBufferInfo.address();
                    }
                    assert vertexBuffer.drawState()
                        .indexCount() == vertexBuffer.drawState()
                        .vertexCount() / 4 * 6;

                    geometryTypeBuffer.putInt(geometryTypeByteOffset, geometryTypeID);
                    geometryTypeByteOffset += Integer.BYTES;

                    ByteBuffer geometryGroupNameBuffer = getUtf8NameBuffer(renderLayer.name);
                    geometryGroupNamePointerBuffer.putLong(geometryGroupNameByteOffset,
                        memAddress(geometryGroupNameBuffer));
                    geometryGroupNameByteOffset += Long.BYTES;

                    ByteBuffer geometryContentNameBuffer = getUtf8NameBuffer(
                        entityRenderLayer.contentName());
                    geometryContentNamePointerBuffer.putLong(geometryContentNameByteOffset,
                        memAddress(geometryContentNameBuffer));
                    geometryContentNameByteOffset += Long.BYTES;

                    geometryTextureBuffer.putInt(geometryTextureByteOffset, geometryTextureID);
                    geometryTextureByteOffset += Integer.BYTES;

                    geometryNormalOffsetBuffer.putFloat(geometryNormalOffsetByteOffset, geometryNormalOffset);
                    geometryNormalOffsetByteOffset += Float.BYTES;

                    vertexFormatBuffer.putInt(vertexFormatByteOffset, vertexFormatID);
                    vertexFormatByteOffset += Integer.BYTES;

                    indexFormatBuffer.putInt(indexFormatByteOffset, indexFormatID);
                    indexFormatByteOffset += Integer.BYTES;

                    vertexCountBuffer.putInt(vertexCountByteOffset,
                        vertexBuffer.drawState()
                            .vertexCount());
                    vertexCountByteOffset += Integer.BYTES;

                    vertexPointerBuffer.putLong(vertexPointerByteOffset, vertexAddress);
                    vertexPointerByteOffset += Long.BYTES;

                    transferBufferIndex++;
                }
            }

            if (Profiler.ENABLED) {
                Profiler.enter("java.EntityProxy.queueBuildNative");
            }
            try {
                queueBuildNative(lineWidth,
                    coordinate.getValue(),
                    normalOffset,
                    entityRenderDataList.getTotalEntityCount(),
                    entityKeyAddress,
                    entityPosXAddress,
                    entityPosYAddress,
                    entityPosZAddress,
                    entityRayTracingFlagAddress,
                    entityPostRenderFlagAddress,
                    entityPrebuiltBlasAddress,
                    0,
                    0,
                    entityPostRenderAddress,
                    entityLayerCountAddress,
                    geometryTypeAddress,
                    geometryGroupNameAddress,
                    geometryContentNameAddress,
                    geometryTextureAddress,
                    geometryNormalOffsetAddress,
                    vertexFormatAddress,
                    indexFormatAddress,
                    vertexCountAddress,
                    vertexPointerAddress,
                    transferredBuffers);
                submittedNative = true;
            } finally {
                if (Profiler.ENABLED) {
                    Profiler.exit();
                }
            }
        } finally {
            freeDirectBuffer(entityKeyBuffer);
            freeDirectBuffer(entityPosXBuffer);
            freeDirectBuffer(entityPosYBuffer);
            freeDirectBuffer(entityPosZBuffer);
            freeDirectBuffer(entityRayTracingFlagBuffer);
            freeDirectBuffer(entityPostRenderFlagBuffer);
            freeDirectBuffer(entityPrebuiltBlasBuffer);
            freeDirectBuffer(entityPostRenderBuffer);
            freeDirectBuffer(entityLayerCountBuffer);
            freeDirectBuffer(geometryTypeBuffer);
            freeDirectBuffer(geometryGroupNamePointerBuffer);
            freeDirectBuffer(geometryContentNamePointerBuffer);
            freeDirectBuffer(geometryTextureBuffer);
            freeDirectBuffer(geometryNormalOffsetBuffer);
            freeDirectBuffer(vertexFormatBuffer);
            freeDirectBuffer(indexFormatBuffer);
            freeDirectBuffer(vertexCountBuffer);
            freeDirectBuffer(vertexPointerBuffer);
            if (transferredBuffers != null && !submittedNative) {
                closeTransferredBuffers(transferredBuffers);
            }
            if (closeAfterBuild && !submittedNative) {
                closeUntransferredBuiltBuffers(entityRenderDataList, transferredBuffers);
                closeStorageVertexConsumerProviders(storageVertexConsumerProviders);
            } else if (closeAfterBuild) {
                closeStorageVertexConsumerProviders(storageVertexConsumerProviders);
            }
        }
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static float getGeometryNormalOffset(ResourceLocation identifier) {
        if (!"minecraft".equals(identifier.getNamespace())) {
            return 0.0f;
        }
        String path = identifier.getPath();
        String layerPath;
        if (path.startsWith(VILLAGER_TEXTURE_PREFIX)) {
            layerPath = path.substring(VILLAGER_TEXTURE_PREFIX.length());
        } else if (path.startsWith(ZOMBIE_VILLAGER_TEXTURE_PREFIX)) {
            layerPath = path.substring(ZOMBIE_VILLAGER_TEXTURE_PREFIX.length());
        } else {
            return 0.0f;
        }

        if (layerPath.startsWith("profession_level/")) {
            return VILLAGER_LAYER_OFFSET_UNIT * 3.0f;
        }
        if (layerPath.startsWith("profession/")) {
            return VILLAGER_LAYER_OFFSET_UNIT * 2.0f;
        }
        if (layerPath.startsWith("type/")) {
            return VILLAGER_LAYER_OFFSET_UNIT;
        }
        return 0.0f;
    }

    private static void freeDirectBuffer(ByteBuffer buffer) {
        if (buffer != null) {
            MemoryUtil.memFree(buffer);
        }
    }

    private static void closeTransferredBuffers(TransferredBuildBuffer[] transferredBuffers) {
        for (TransferredBuildBuffer transferredBuffer : transferredBuffers) {
            if (transferredBuffer != null) {
                transferredBuffer.close();
            }
        }
    }

    private static void closeUntransferredBuiltBuffers(
        EntityRenderDataList entityRenderDataList,
        TransferredBuildBuffer[] transferredBuffers) {
        if (entityRenderDataList == null) {
            return;
        }

        for (EntityRenderData entityRenderData : entityRenderDataList) {
            for (EntityRenderLayer entityRenderLayer : entityRenderData) {
                MeshData vertexBuffer = entityRenderLayer.builtBuffer;
                if (isTransferred(vertexBuffer, transferredBuffers)) {
                    continue;
                }
                vertexBuffer.close();
            }
        }
    }

    private static boolean isTransferred(
        MeshData builtBuffer,
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

    private static void closeStorageVertexConsumerProviders(
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders) {
        if (storageVertexConsumerProviders == null) {
            return;
        }

        for (StorageVertexConsumerProvider storageVertexConsumerProvider : storageVertexConsumerProviders) {
            storageVertexConsumerProvider.close();
        }
    }

    private static native void queueBuildNative(float lineWidth,
        int coordinate,
        boolean normalOffset,
        int size,
        long entityKeys,
        long entityPosXs,
        long entityPosYs,
        long entityPosZs,
        long entityRayTracingFlags,
        long entityPostRenderFlags,
        long entityPrebuiltBLASs,
        long entityTransforms,
        long entityMaterials,
        long entityPosts,
        long entityLayerCounts,
        long geometryTypes,
        long geometryGroupNames,
        long geometryContentNames,
        long geometryTextures,
        long geometryNormalOffsets,
        long vertexFormats,
        long indexFormats,
        long vertexCounts,
        long vertices,
        TransferredBuildBuffer[] transferredBuffers);

    private static native void buildNative();

    public static void build() {
        if (!isEntityNativeTransferEnabled()) {
            return;
        }

        if (Profiler.ENABLED) {
            Profiler.enter("java.EntityProxy.build");
        }
        try {
            buildNative();
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static String defaultPostContentName(Constants.PostRenderFlags postRenderFlag) {
        return switch (postRenderFlag) {
            case WEATHER -> WEATHER_DEFAULT_CONTENT;
            case PARTICLE -> PARTICLE_DEFAULT_CONTENT;
            case TEXT -> TEXT_DEFAULT_CONTENT;
            case NAME_TAG -> NAME_TAG_DEFAULT_CONTENT;
            case WORLD_OVERLAY -> "/world_overlay/default";
        };
    }

    private static String normalizeParticleContentName(String particleContentName) {
        if (particleContentName == null || particleContentName.isBlank()) {
            return PARTICLE_DEFAULT_CONTENT;
        }
        return particleContentName;
    }

    private static String resolveWeatherContentName(RenderType renderLayer) {
        ResourceLocation textureIdentifier = getTextureId(renderLayer);
        if (WEATHER_RAIN_TEXTURE.equals(textureIdentifier)) {
            return WEATHER_RAIN_CONTENT;
        }
        if (WEATHER_SNOW_TEXTURE.equals(textureIdentifier)) {
            return WEATHER_SNOW_CONTENT;
        }
        return WEATHER_DEFAULT_CONTENT;
    }

    private static ResourceLocation getTextureId(RenderType renderLayer) {
        if (renderLayer instanceof RenderType.CompositeRenderType multiPhase) {
            return multiPhase.state.textureState.cutoutTexture()
                .orElse(MissingTextureAtlasSprite.getLocation());
        }
        return MissingTextureAtlasSprite.getLocation();
    }

    public record EntityRenderLayer(RenderType renderLayer, MeshData builtBuffer,
                                    StorageVertexConsumerProvider ownerProvider,
                                    boolean reflect, String contentName) {

    }

    public static class EntityRenderData extends ArrayList<EntityRenderLayer> {

        private final long entityKey;
        private final int rayTracingFlag;
        private final int postRenderFlag;
        private final int prebuiltBLAS;
        private final boolean isPostRender;
        private double x;
        private double y;
        private double z;

        public EntityRenderData(long entityKey, double x, double y, double z, int rayTracingFlag,
            int postRenderFlag,
            int prebuiltBLAS,
            boolean isPostRender) {
            this.entityKey = entityKey;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rayTracingFlag = rayTracingFlag;
            this.postRenderFlag = postRenderFlag;
            this.prebuiltBLAS = prebuiltBLAS;
            this.isPostRender = isPostRender;
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

        public int getRayTracingFlag() {
            return rayTracingFlag;
        }

        public int getPostRenderFlag() {
            return postRenderFlag;
        }

        public int getPrebuiltBLAS() {
            return prebuiltBLAS;
        }

        public long getEntityKey() {
            return entityKey;
        }

        public boolean isPostRender() {
            return isPostRender;
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
        public void add(int index, EntityRenderData entityRenderData) {
            totalLayersCount += entityRenderData.size();
            super.add(index, entityRenderData);
        }

        @Override
        public boolean addAll(Collection<? extends EntityRenderData> entityRenderDataCollection) {
            boolean changed = false;
            for (EntityRenderData entityRenderData : entityRenderDataCollection) {
                changed |= add(entityRenderData);
            }
            return changed;
        }

        @Override
        public boolean addAll(int index, Collection<? extends EntityRenderData> entityRenderDataCollection) {
            int insertIndex = index;
            for (EntityRenderData entityRenderData : entityRenderDataCollection) {
                add(insertIndex, entityRenderData);
                insertIndex++;
            }
            return !entityRenderDataCollection.isEmpty();
        }

        @Override
        public EntityRenderData remove(int index) {
            EntityRenderData removed = super.remove(index);
            totalLayersCount -= removed.size();
            return removed;
        }

        @Override
        public boolean remove(Object object) {
            if (!(object instanceof EntityRenderData entityRenderData)) {
                return false;
            }
            boolean removed = super.remove(object);
            if (removed) {
                totalLayersCount -= entityRenderData.size();
            }
            return removed;
        }

        @Override
        public void clear() {
            totalLayersCount = 0;
            super.clear();
        }

        public int getTotalLayersCount() {
            return totalLayersCount;
        }

        public int getTotalEntityCount() {
            return this.size();
        }
    }
}
