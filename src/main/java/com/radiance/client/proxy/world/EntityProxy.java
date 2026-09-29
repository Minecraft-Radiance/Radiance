package com.radiance.client.proxy.world;

import com.radiance.client.mod_support.ModSupport;
import com.radiance.client.profiler.Profiler;
import com.radiance.client.mod_support.flywheel.FlywheelSupport;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector2f;

import static com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.LINE_STRIP;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLE_STRIP;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.radiance.client.constant.Constants;
import com.radiance.client.constant.Constants.PostRenderFlags;
import com.radiance.client.constant.Constants.RayTracingFlags;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.render.BuiltBuffer;
import com.radiance.client.render.CustomParticleRenderer;
import com.radiance.client.render.RenderLayers;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.client.vertex.TransferredBuildBuffer;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IHeldItemRendererExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleManagerExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.CommonColors;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.client.model.data.ModelData;
import org.lwjgl.system.MemoryUtil;

public class EntityProxy {

    public static final ConcurrentMap<Class<? extends Particle>, AtomicInteger> PARTICLE_COUNTERS = new ConcurrentHashMap<>();
    public static MultiBufferSource postTextVertexConsumerProvider;

    private static final ResourceLocation SUN_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft",
        "textures/environment/sun.png");
    private static final ResourceLocation MOON_PHASES_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft",
        "textures/environment/moon_phases.png");
    private static final ResourceLocation PARTICLE_ATLAS_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft",
        "textures/atlas/particles.png");
    private static final float VILLAGER_LAYER_OFFSET_UNIT = 0.001f / 16.0f;
    private static final String VILLAGER_TEXTURE_PREFIX = "textures/entity/villager/";
    private static final String ZOMBIE_VILLAGER_TEXTURE_PREFIX = "textures/entity/zombie_villager/";
    private static final String WEATHER_DEFAULT_CONTENT = "/weather/default";
    private static final String PARTICLE_DEFAULT_CONTENT = "/particle/default";
    private static final String TEXT_DEFAULT_CONTENT = "/text/default";
    private static final String NAME_TAG_DEFAULT_CONTENT = "/name_tag/default";
    private static final RenderType TERRAIN_PARTICLE_LAYER = createTranslucentParticleLayer(
        InventoryMenu.BLOCK_ATLAS);
    private static final RenderType OPAQUE_PARTICLE_LAYER = createOpaqueParticleLayer(
        PARTICLE_ATLAS_TEXTURE);
    private static final RenderType TRANSLUCENT_PARTICLE_LAYER = createTranslucentParticleLayer(
        PARTICLE_ATLAS_TEXTURE);

    public static void processWorldEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Constants.RayTracingFlags rayTracingFlag,
        boolean reflect,
        EntityRenderDataList entityRenderDataList) {
        processEntityRenderData(storageVertexConsumerProvider,
            hashCode,
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
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Constants.PostRenderFlags postRenderFlag,
        EntityRenderDataList entityRenderDataList) {
        processEntityRenderData(storageVertexConsumerProvider,
            hashCode,
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
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Constants.PostRenderFlags postRenderFlag,
        String contentName,
        EntityRenderDataList entityRenderDataList) {
        processEntityRenderData(storageVertexConsumerProvider,
            hashCode,
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
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Constants.PostRenderFlags postRenderFlag,
        Function<RenderType, String> contentNameResolver,
        EntityRenderDataList entityRenderDataList) {
        processEntityRenderData(storageVertexConsumerProvider,
            hashCode,
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
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        int rayTracingFlag,
        int postRenderFlag,
        int prebuiltBLAS,
        boolean reflect,
        Function<RenderType, String> contentNameResolver,
        boolean post,
        EntityRenderDataList entityRenderDataList) {
        Map<RenderType, VertexConsumer> layerBuffers = storageVertexConsumerProvider.getLayers();
        EntityRenderData
            entityRenderData =
            new EntityRenderData(hashCode, entityPosX, entityPosY,
                entityPosZ,
                rayTracingFlag, postRenderFlag, prebuiltBLAS, post);
        EntityRenderData
            waterMaskRenderData =
            new EntityRenderData(hashCode, entityPosX, entityPosY,
                entityPosZ,
                RayTracingFlags.BOAT_WATER_MASK.getValue(), 0, prebuiltBLAS, post);
        for (Map.Entry<RenderType, VertexConsumer> layerBuffer : layerBuffers.entrySet()) {
            RenderType layer = layerBuffer.getKey();
            BuiltBuffer buffer = null;

            VertexConsumer vertexConsumer = layerBuffer.getValue();
            if (vertexConsumer instanceof BufferBuilder bufferBuilder) {
                BufferBuilder.RenderedBuffer builtBuffer = bufferBuilder.endOrDiscardIfEmpty();
                buffer = builtBuffer == null ? null : BuiltBuffer.wrap(builtBuffer);
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
                waterMaskRenderData.add(new EntityRenderLayer(layer, buffer,
                    storageVertexConsumerProvider, reflect, contentName));
            } else {
                entityRenderData.add(new EntityRenderLayer(layer, buffer,
                    storageVertexConsumerProvider, reflect, contentName));
            }
        }

        if (!entityRenderData.isEmpty()) {
            entityRenderDataList.add(entityRenderData);
        }
        if (!waterMaskRenderData.isEmpty()) {
            entityRenderDataList.add(waterMaskRenderData);
        }

    }

    public static void queueEntitiesBuild(Camera camera, List<Entity> renderedEntities,
        EntityRenderDispatcher entityRenderDispatcher, float tickDelta,
        boolean canDrawEntityOutlines) {
        PoseStack matrixStack = new PoseStack();

        Minecraft client = Minecraft.getInstance();
        Objects.requireNonNull(client.level);

        List<StorageVertexConsumerProvider> entityStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList entityRenderDataList = new EntityRenderDataList();
        for (Entity entity : renderedEntities) {

            if (entity.tickCount == 0) {
                entity.xOld = entity.getX();
                entity.yOld = entity.getY();
                entity.zOld = entity.getZ();
            }

            StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                786432);
            entityStorageVertexConsumerProvider.setEntityBuildContext(
                Constants.Coordinates.WORLD.getValue(), false, 0, 0, 0);
            entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);

            MultiBufferSource vertexConsumerProvider;
            if (canDrawEntityOutlines && client.shouldEntityAppearGlowing(entity)) {










                vertexConsumerProvider = entityStorageVertexConsumerProvider;
            } else {
                vertexConsumerProvider = entityStorageVertexConsumerProvider;
            }

            double entityPosX = Mth.lerp(tickDelta, entity.xOld,
                entity.getX());
            double entityPosY = Mth.lerp(tickDelta, entity.yOld,
                entity.getY());
            double entityPosZ = Mth.lerp(tickDelta, entity.zOld,
                entity.getZ());
            float yaw = Mth.lerp(tickDelta, entity.yRotO, entity.getYRot());
            int light = entityRenderDispatcher.getPackedLightCoords(entity, tickDelta);

            if (entity instanceof Display.TextDisplay) {
                StorageVertexConsumerProvider postTextStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                    786432);
                postTextStorageVertexConsumerProvider.setEntityBuildContext(
                    Constants.Coordinates.WORLD.getValue(), false,
                    entityPosX, entityPosY, entityPosZ);
                entityStorageVertexConsumerProviders.add(postTextStorageVertexConsumerProvider);
                entityRenderDispatcher.render(entity,
                    0,
                    0,
                    0,
                    yaw,
                    tickDelta,
                    matrixStack,
                    postTextStorageVertexConsumerProvider,
                    light);
                processPostEntityRenderData(postTextStorageVertexConsumerProvider,
                    System.identityHashCode(entity),
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
            boolean captureVanilla = ModSupport.FLYWHEEL && !FlywheelSupport.rendered(entity);
            if (captureVanilla) FlywheelSupport.enterVanilla();
            try {
                entityRenderDispatcher.render(entity,
                    0,
                    0,
                    0,
                    yaw,
                    tickDelta,
                    matrixStack,
                    vertexConsumerProvider,
                    light);
            } finally {
                if (captureVanilla) FlywheelSupport.exitVanilla();
                postTextVertexConsumerProvider = null;
            }

            if (!postTextStorageVertexConsumerProvider.getLayers().isEmpty()) {
                entityStorageVertexConsumerProviders.add(postTextStorageVertexConsumerProvider);
                processPostEntityRenderData(postTextStorageVertexConsumerProvider,
                    System.identityHashCode(entity),
                    entityPosX,
                    entityPosY,
                    entityPosZ,
                    PostRenderFlags.NAME_TAG,
                    entityRenderDataList);
            } else {
                postTextStorageVertexConsumerProvider.close();
            }

            if (entity.equals(camera.getEntity())) {
                processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                    System.identityHashCode(entity),
                    entityPosX,
                    entityPosY,
                    entityPosZ,
                    Constants.RayTracingFlags.PLAYER,
                    true,
                    entityRenderDataList);
            } else if (entity instanceof FishingHook) {
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

        queueBuild(entityStorageVertexConsumerProviders, entityRenderDataList);
    }

    private static void renderBlockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntity blockEntity,
        float tickDelta, PoseStack matrices, MultiBufferSource vertices) {
        if (!ModSupport.FLYWHEEL) {
            dispatcher.render(blockEntity, tickDelta, matrices, vertices);
            return;
        }
        if (FlywheelSupport.skip(blockEntity)) return;
        boolean captureVanilla = !FlywheelSupport.rendered(blockEntity) || FlywheelSupport.breaking(blockEntity);
        if (captureVanilla) FlywheelSupport.enterVanilla();
        try {
            dispatcher.render(blockEntity, tickDelta, matrices, vertices);
        } finally {
            if (captureVanilla) FlywheelSupport.exitVanilla();
        }
    }

    public static synchronized Tuple<List<StorageVertexConsumerProvider>, EntityRenderDataList> queueBlockEntitiesRebuild(
        ViewArea chunks,
        Set<BlockEntity> noCullingBlockEntities,
        Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions,
        BlockEntityRenderDispatcher blockEntityRenderDispatcher,
        float tickDelta) {
        PoseStack matrixStack = new PoseStack();
        List<StorageVertexConsumerProvider> entityStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList entityRenderDataList = new EntityRenderDataList();

        List<StorageVertexConsumerProvider> crumblingStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList crumblingRenderDataList = new EntityRenderDataList();
        ChunkRenderDispatcher.RenderChunk[] blockEntitySections;
        if (Profiler.ENABLED) Profiler.enter("java.EntityProxy.blockEntitySections");
        try {
            blockEntitySections = BlockEntitySectionCache.sections(chunks);
        } finally {
            if (Profiler.ENABLED) Profiler.exit();
        }
        for (ChunkRenderDispatcher.RenderChunk builtChunk : blockEntitySections) {
            List<BlockEntity>
                list =
                builtChunk.getCompiledChunk()
                    .getRenderableBlockEntities();
            if (!list.isEmpty()) {
                for (BlockEntity blockEntity : list) {
                    if (ModSupport.FLYWHEEL && FlywheelSupport.skip(blockEntity)) continue;
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

                    BlockPos blockPos = blockEntity.getBlockPos();
                    double entityPosX = blockPos.getX();
                    double entityPosY = blockPos.getY();
                    double entityPosZ = blockPos.getZ();

                    matrixStack.pushPose();
                    SortedSet<BlockDestructionProgress> sortedSet = blockBreakingProgressions.get(
                        blockPos.asLong());
                    if (sortedSet != null && !sortedSet.isEmpty()) {
                        int
                            stage =
                            sortedSet.last()
                                .getProgress();
                        if (stage >= 0) {
                            PoseStack.Pose entry = matrixStack.last();
                            VertexConsumer
                                vertexConsumer =
                                new SheetedDecalTextureGenerator(
                                    crumblingStorageVertexConsumerProvider.getBuffer(
                                        ModelBakery.DESTROY_TYPES.get(
                                            stage)), entry.pose(),
                                    entry.normal(), 1.0F);
                            vertexConsumerProvider = renderLayer -> {
                                VertexConsumer vertexConsumer2 = entityStorageVertexConsumerProvider.getBuffer(
                                    renderLayer);
                                return renderLayer.affectsCrumbling() ? VertexMultiConsumer.create(
                                    vertexConsumer,
                                    vertexConsumer2) :
                                    vertexConsumer2;
                            };
                        }
                    }

                    renderBlockEntity(blockEntityRenderDispatcher, blockEntity, tickDelta, matrixStack, vertexConsumerProvider);
                    matrixStack.popPose();

                    processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                        System.identityHashCode(blockEntity),
                        entityPosX,
                        entityPosY,
                        entityPosZ,
                        Constants.RayTracingFlags.WORLD,
                        true,
                        entityRenderDataList);
                    processWorldEntityRenderData(crumblingStorageVertexConsumerProvider,
                        System.identityHashCode(blockEntity) + 1,
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
            if (ModSupport.FLYWHEEL && FlywheelSupport.skip(blockEntity)) continue;
            StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                786432);
            entityStorageVertexConsumerProvider.setEntityBuildContext(
                Constants.Coordinates.WORLD.getValue(), false, 0, 0, 0);
            entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);

            BlockPos blockPos = blockEntity.getBlockPos();
            double entityPosX = blockPos.getX();
            double entityPosY = blockPos.getY();
            double entityPosZ = blockPos.getZ();

            matrixStack.pushPose();
            renderBlockEntity(blockEntityRenderDispatcher, blockEntity, tickDelta, matrixStack, entityStorageVertexConsumerProvider);
            matrixStack.popPose();

            processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                System.identityHashCode(blockEntity),
                entityPosX,
                entityPosY,
                entityPosZ,
                Constants.RayTracingFlags.WORLD,
                true,
                entityRenderDataList);
        }

        queueBuild(entityStorageVertexConsumerProviders, entityRenderDataList);

        return new Tuple<>(crumblingStorageVertexConsumerProviders, crumblingRenderDataList);
    }

    public static void queueCrumblingRebuild(Camera camera,
        Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions,
        BlockRenderDispatcher blockRenderManager,
        ClientLevel world,
        List<StorageVertexConsumerProvider> crumblingStorageVertexConsumerProviders,
        EntityRenderDataList crumblingRenderDataList) {
        PoseStack matrixStack = new PoseStack();
        List<StorageVertexConsumerProvider> blockCrumblingStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList blockCrumblingRenderDataList = new EntityRenderDataList();

        Vec3 vec3d = camera.getPosition();
        double d = vec3d.x();
        double e = vec3d.y();
        double f = vec3d.z();

        for (Long2ObjectMap.Entry<SortedSet<BlockDestructionProgress>> blockBreakingProgression :
            blockBreakingProgressions.long2ObjectEntrySet()) {
            BlockPos blockPos = BlockPos.of(blockBreakingProgression.getLongKey());
            double entityPosX = blockPos.getX();
            double entityPosY = blockPos.getY();
            double entityPosZ = blockPos.getZ();

            if (!(blockPos.distToCenterSqr(d, e, f) > 1024.0)) {
                SortedSet<BlockDestructionProgress> sortedSet = blockBreakingProgression.getValue();
                if (sortedSet != null && !sortedSet.isEmpty()) {
                    int
                        stage =
                        sortedSet.last()
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
                                    stage)), entry.pose(),
                            entry.normal(), 1.0F);
                    BlockState blockState = world.getBlockState(blockPos);
                    BakedModel model = blockRenderManager.getBlockModel(blockState);
                    ModelData modelData = model.getModelData(world, blockPos, blockState,
                        world.getModelDataManager().getAt(blockPos));
                    blockRenderManager.renderBreakingTexture(blockState, blockPos, world,
                        matrixStack, vertexConsumer, modelData);
                    matrixStack.popPose();

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

        List<StorageVertexConsumerProvider>
            storageVertexConsumerProviders =
            Stream.concat(crumblingStorageVertexConsumerProviders.stream(),
                    blockCrumblingStorageVertexConsumerProviders.stream())
                .toList();

        EntityRenderDataList
            renderDataList =
            Stream.concat(crumblingRenderDataList.stream(), blockCrumblingRenderDataList.stream())
                .collect(EntityRenderDataList::new, EntityRenderDataList::add,
                    EntityRenderDataList::addAll);

        queueBuild(storageVertexConsumerProviders, renderDataList, 0.0f,
            Constants.Coordinates.WORLD,
            true);
    }

    public static void queueHandRebuild(RenderBuffers buffers, float tickDelta,
        ItemInHandRenderer firstPersonRenderer, float handProjectionScale) {
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

        boolean bl = client.getCameraEntity() instanceof LivingEntity
            && ((LivingEntity) client.getCameraEntity()).isSleeping();
        if (client.options.getCameraType()
            .isFirstPerson() && !bl && !client.options.hideGui &&
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
            .isFirstPerson() && !bl && !client.options.hideGui &&
            client.gameMode.getPlayerMode() != GameType.SPECTATOR) {
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
    }

    public static void queueParticleRebuild(Camera camera, float tickDelta, Frustum frustum) {
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();

        Map<String, StorageVertexConsumerProvider> postStorageVertexConsumerProviders =
            new LinkedHashMap<>();

        Minecraft client = Minecraft.getInstance();
        IParticleManagerExt particleManagerExt = (IParticleManagerExt) client.particleEngine;
        Map<ParticleRenderType, Queue<Particle>> particles = particleManagerExt.radiance$getParticles();

        for (ParticleRenderType particleTextureSheet : particleManagerExt.radiance$getTextureSheets()) {
            if (particleTextureSheet == ParticleRenderType.CUSTOM
                || particleTextureSheet == ParticleRenderType.NO_RENDER) {
                continue;
            }

            Queue<Particle> particleQueue = particles.get(particleTextureSheet);
            if (particleQueue == null || particleQueue.isEmpty()) {
                continue;
            }

            RenderType renderLayer = getParticleRenderLayer(particleTextureSheet);
            if (renderLayer == null) {
                continue;
            }

            for (Particle particle : particleQueue) {
                String contentName = normalizeParticleContentName(
                    ((IParticleExt) particle).radiance$getContentName());
                StorageVertexConsumerProvider postStorageVertexConsumerProvider =
                    postStorageVertexConsumerProviders.computeIfAbsent(contentName, key -> {
                        StorageVertexConsumerProvider provider =
                            new StorageVertexConsumerProvider(0);
                        provider.setEntityBuildContext(
                            Constants.Coordinates.CAMERA_SHIFT.getValue(), false, 0, 0, 0);
                        storageVertexConsumerProviders.add(provider);
                        return provider;
                    });
                try {
                    VertexConsumer vertexConsumer = postStorageVertexConsumerProvider.getBuffer(
                        renderLayer);
                    particle.render(vertexConsumer, camera, tickDelta);
                } catch (Throwable throwable) {
                    CrashReport crashReport = CrashReport.forThrowable(throwable, "Rendering Particle");
                    CrashReportCategory crashReportSection = crashReport.addCategory(
                        "Particle being rendered");
                    crashReportSection.setDetail("Particle", particle::toString);
                    crashReportSection.setDetail("Particle Type", particleTextureSheet::toString);
                    throw new ReportedException(crashReport);
                }
            }
        }

        for (Map.Entry<String, StorageVertexConsumerProvider> entry
            : postStorageVertexConsumerProviders.entrySet()) {
            processPostEntityRenderData(entry.getValue(), entry.getKey().hashCode() + 3,
                0, 0, 0, PostRenderFlags.PARTICLE, entry.getKey(), renderDataList);
        }

        StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
            0);
        storageVertexConsumerProvider.setEntityBuildContext(
            Constants.Coordinates.CAMERA_SHIFT.getValue(), false, 0, 0, 0);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        Queue<Particle> customParticleQueue = particles.get(ParticleRenderType.CUSTOM);
        if (customParticleQueue != null && !customParticleQueue.isEmpty()) {
            CustomParticleRenderer.begin(storageVertexConsumerProvider,
                TRANSLUCENT_PARTICLE_LAYER);
            try {
                VertexConsumer fallbackVertexConsumer = CustomParticleRenderer.getFallbackVertexConsumer();
                for (Particle particle : customParticleQueue) {
                    try {
                        particle.render(fallbackVertexConsumer, camera, tickDelta);
                    } catch (Throwable throwable) {
                        CrashReport crashReport = CrashReport.forThrowable(throwable,
                            "Rendering Particle");
                        CrashReportCategory crashReportSection = crashReport.addCategory(
                            "Particle being rendered");
                        crashReportSection.setDetail("Particle", particle::toString);
                        crashReportSection.setDetail("Particle Type", "Custom");
                        throw new ReportedException(crashReport);
                    }
                }
            } finally {
                List<EntityRenderLayer> capturedLayers = CustomParticleRenderer.end();
                if (!capturedLayers.isEmpty()) {
                    EntityRenderData entityRenderData = new EntityRenderData(0, 0, 0, 0,
                        Constants.RayTracingFlags.PARTICLE.getValue(), 0, -1, false);
                    entityRenderData.addAll(capturedLayers);
                    renderDataList.add(entityRenderData);
                }
            }
        }

        processWorldEntityRenderData(storageVertexConsumerProvider, 0, 0, 0, 0,
            Constants.RayTracingFlags.PARTICLE, true, renderDataList);

        queueBuild(storageVertexConsumerProviders, renderDataList, 0.0f,
            Constants.Coordinates.CAMERA_SHIFT, false);
    }

    private static RenderType getParticleRenderLayer(ParticleRenderType particleTextureSheet) {
        if (particleTextureSheet == ParticleRenderType.TERRAIN_SHEET) {
            return TERRAIN_PARTICLE_LAYER;
        }
        if (particleTextureSheet == ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT) {
            return TRANSLUCENT_PARTICLE_LAYER;
        }
        if (particleTextureSheet == ParticleRenderType.PARTICLE_SHEET_OPAQUE
            || particleTextureSheet == ParticleRenderType.PARTICLE_SHEET_LIT) {
            return OPAQUE_PARTICLE_LAYER;
        }
        return null;
    }

    private static RenderType createOpaqueParticleLayer(ResourceLocation texture) {
        return RenderLayers.createOpaqueParticleLayer(texture);
    }

    private static RenderType createTranslucentParticleLayer(ResourceLocation texture) {
        return RenderLayers.createTranslucentParticleLayer(texture);
    }

    private static EntityRenderData worldRenderCapture;
    private static final Map<Object, Long> worldRenderKeys = new java.util.IdentityHashMap<>();

    public static void captureWorldRender(Object stage, Runnable render) {
        if (worldRenderCapture != null) {
            render.run();
            return;
        }
        long key = worldRenderKeys.computeIfAbsent(stage,
            ignored -> 0x6000000000010000L + worldRenderKeys.size());
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
                queueBuild(List.of(), list, 0.0075f, Constants.Coordinates.CAMERA, false);
            }
        }
    }

    public static boolean isWorldRenderCaptureActive() {
        return worldRenderCapture != null;
    }

    public static boolean captureWorldRenderLayer(RenderType layer, BuiltBuffer mesh) {
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
                    VertexConsumer vertexConsumer = storageVertexConsumerProvider.getBuffer(
                        RenderType.lines());
                    int color = FastColor.ABGR32.color(102, CommonColors.BLACK);
                    LevelRenderer.renderVoxelShape(matrixStack,
                        vertexConsumer,
                        blockState.getShape(world, blockPos,
                            CollisionContext.of(camera.getEntity())),
                        0,
                        0,
                        0,
                        FastColor.ARGB32.red(color) / 255.0F,
                        FastColor.ARGB32.green(color) / 255.0F,
                        FastColor.ARGB32.blue(color) / 255.0F,
                        FastColor.ARGB32.alpha(color) / 255.0F,
                        false);

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
        TextureManager
            textureManager =
            Minecraft.getInstance()
                .getTextureManager();
        List<ByteBuffer> geometryGroupNameBuffers = new ArrayList<>(
            entityRenderDataList.getTotalLayersCount());
        List<ByteBuffer> geometryContentNameBuffers = new ArrayList<>(
            entityRenderDataList.getTotalLayersCount());
        ByteBuffer entityHashCodeBB = null;
        ByteBuffer entityPosXBB = null;
        ByteBuffer entityPosYBB = null;
        ByteBuffer entityPosZBB = null;
        ByteBuffer entityRayTracingFlagBB = null;
        ByteBuffer entityPostRenderFlagBB = null;
        ByteBuffer entityPrebuiltBLASBB = null;
        ByteBuffer entityPostBB = null;
        ByteBuffer entityTransformBB = null;
        ByteBuffer entityMaterialBB = null;
        ByteBuffer entityLayerCountBB = null;
        ByteBuffer geometryTypeBB = null;
        ByteBuffer geometryGroupNameBB = null;
        ByteBuffer geometryContentNameBB = null;
        ByteBuffer geometryTextureBB = null;
        ByteBuffer geometryNormalOffsetBB = null;
        ByteBuffer vertexFormatBB = null;
        ByteBuffer indexFormatBB = null;
        ByteBuffer vertexCountBB = null;
        ByteBuffer verticesBB = null;
        TransferredBuildBuffer[] transferredBuffers =
            closeAfterBuild ? new TransferredBuildBuffer[entityRenderDataList.getTotalLayersCount()] : null;
        boolean submittedNative = false;

        try {
            int entityHashCodeSize = entityRenderDataList.getTotalEntityCount() * Long.BYTES;
            entityHashCodeBB = MemoryUtil.memAlloc(entityHashCodeSize);
            long entityHashCodeAddr = memAddress(entityHashCodeBB);
            int entityHashCodeBaseAddr = 0;

            int entityPosXSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
            entityPosXBB = MemoryUtil.memAlloc(entityPosXSize);
            long entityPosXAddr = memAddress(entityPosXBB);
            int entityPosXBaseAddr = 0;

            int entityPosYSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
            entityPosYBB = MemoryUtil.memAlloc(entityPosYSize);
            long entityPosYAddr = memAddress(entityPosYBB);
            int entityPosYBaseAddr = 0;

            int entityPosZSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
            entityPosZBB = MemoryUtil.memAlloc(entityPosZSize);
            long entityPosZAddr = memAddress(entityPosZBB);
            int entityPosZBaseAddr = 0;

            int entityRayTracingFlagSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityRayTracingFlagBB = MemoryUtil.memAlloc(entityRayTracingFlagSize);
            long entityRayTracingFlagAddr = memAddress(entityRayTracingFlagBB);
            int entityRayTracingFlagBaseAddr = 0;

            int entityPostRenderFlagSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityPostRenderFlagBB = MemoryUtil.memAlloc(entityPostRenderFlagSize);
            long entityPostRenderFlagAddr = memAddress(entityPostRenderFlagBB);
            int entityPostRenderFlagBaseAddr = 0;

            int entityPrebuiltBLASSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityPrebuiltBLASBB = MemoryUtil.memAlloc(entityPrebuiltBLASSize);
            long entityPrebuiltBLASAddr = memAddress(entityPrebuiltBLASBB);
            int entityPrebuiltBLASBaseAddr = 0;

            int entityPostSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityPostBB = MemoryUtil.memAlloc(entityPostSize);
            long entityPostAddr = memAddress(entityPostBB);
            int entityPostBaseAddr = 0;

            int entityLayerCountSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityLayerCountBB = MemoryUtil.memAlloc(entityLayerCountSize);
            long entityLayerCountAddr = memAddress(entityLayerCountBB);
            int entityLayerCountBaseAddr = 0;

            int geometryTypeSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            geometryTypeBB = MemoryUtil.memAlloc(geometryTypeSize);
            long geometryTypeAddr = memAddress(geometryTypeBB);
            int geometryTypeBaseAddr = 0;

            int geometryGroupNameSize = entityRenderDataList.getTotalLayersCount() * Long.BYTES;
            geometryGroupNameBB = MemoryUtil.memAlloc(geometryGroupNameSize);
            long geometryGroupNameAddr = memAddress(geometryGroupNameBB);
            int geometryGroupNameBaseAddr = 0;

            int geometryContentNameSize = entityRenderDataList.getTotalLayersCount() * Long.BYTES;
            geometryContentNameBB = MemoryUtil.memAlloc(geometryContentNameSize);
            long geometryContentNameAddr = memAddress(geometryContentNameBB);
            int geometryContentNameBaseAddr = 0;

            int geometryTextureSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            geometryTextureBB = MemoryUtil.memAlloc(geometryTextureSize);
            long geometryTextureAddr = memAddress(geometryTextureBB);
            int geometryTextureBaseAddr = 0;

            int geometryNormalOffsetSize = entityRenderDataList.getTotalLayersCount() * Float.BYTES;
            geometryNormalOffsetBB = MemoryUtil.memAlloc(geometryNormalOffsetSize);
            long geometryNormalOffsetAddr = memAddress(geometryNormalOffsetBB);
            int geometryNormalOffsetBaseAddr = 0;

            int vertexFormatSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            vertexFormatBB = MemoryUtil.memAlloc(vertexFormatSize);
            long vertexFormatAddr = memAddress(vertexFormatBB);
            int vertexFormatBaseAddr = 0;

            int indexFormatSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            indexFormatBB = MemoryUtil.memAlloc(indexFormatSize);
            long indexFormatAddr = memAddress(indexFormatBB);
            int indexFormatBaseAddr = 0;

            int vertexCountSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            vertexCountBB = MemoryUtil.memAlloc(vertexCountSize);
            long vertexCountAddr = memAddress(vertexCountBB);
            int vertexCountBaseAddr = 0;

            int verticesSize = entityRenderDataList.getTotalLayersCount() * Long.BYTES;
            verticesBB = MemoryUtil.memAlloc(verticesSize);
            long verticesAddr = memAddress(verticesBB);
            int verticesBaseAddr = 0;

            int entityCount = entityRenderDataList.getTotalEntityCount();
            boolean shared = entityRenderDataList.stream().anyMatch(data -> data.prebuiltBLAS >= 0);
            entityTransformBB = shared ? MemoryUtil.memCalloc(entityCount * 64) : null;
            entityMaterialBB = shared ? MemoryUtil.memCalloc(entityCount * 48) : null;
            int entityIndex = 0;
            int geometryIndex = 0;
            for (EntityRenderData entityRenderData : entityRenderDataList) {
                if (entityRenderData.prebuiltBLAS >= 0) {
                    entityRenderData.transform.get(entityIndex * 64, entityTransformBB);
                    int offset = entityIndex * 48;
                    entityMaterialBB.putFloat(offset, entityRenderData.color.x);
                    entityMaterialBB.putFloat(offset + 4, entityRenderData.color.y);
                    entityMaterialBB.putFloat(offset + 8, entityRenderData.color.z);
                    entityMaterialBB.putFloat(offset + 12, entityRenderData.color.w);
                    entityMaterialBB.putFloat(offset + 16, 1.0f);
                    entityMaterialBB.putFloat(offset + 20, 1.0f);
                    entityMaterialBB.putFloat(offset + 24, entityRenderData.uvOffset.x);
                    entityMaterialBB.putFloat(offset + 28, entityRenderData.uvOffset.y);
                    entityMaterialBB.putInt(offset + 32, entityRenderData.light);
                    entityMaterialBB.putInt(offset + 36, entityRenderData.overlay);
                }
                entityIndex++;
                entityHashCodeBB.putLong(entityHashCodeBaseAddr, entityRenderData.hashCode);
                entityHashCodeBaseAddr += Long.BYTES;

                entityPosXBB.putDouble(entityPosXBaseAddr, entityRenderData.x);
                entityPosXBaseAddr += Double.BYTES;

                entityPosYBB.putDouble(entityPosYBaseAddr, entityRenderData.y);
                entityPosYBaseAddr += Double.BYTES;

                entityPosZBB.putDouble(entityPosZBaseAddr, entityRenderData.z);
                entityPosZBaseAddr += Double.BYTES;

                entityRayTracingFlagBB.putInt(entityRayTracingFlagBaseAddr,
                    entityRenderData.rayTracingFlag);
                entityRayTracingFlagBaseAddr += Integer.BYTES;

                entityPostRenderFlagBB.putInt(entityPostRenderFlagBaseAddr,
                    entityRenderData.postRenderFlag);
                entityPostRenderFlagBaseAddr += Integer.BYTES;

                entityPrebuiltBLASBB.putInt(entityPrebuiltBLASBaseAddr, entityRenderData.prebuiltBLAS);
                entityPrebuiltBLASBaseAddr += Integer.BYTES;

                entityPostBB.putInt(entityPostBaseAddr, entityRenderData.post ? 1 : 0);
                entityPostBaseAddr += Integer.BYTES;

                entityLayerCountBB.putInt(entityLayerCountBaseAddr, entityRenderData.size());
                entityLayerCountBaseAddr += Integer.BYTES;

                for (EntityRenderLayer entityRenderLayer : entityRenderData) {
                    RenderType renderLayer = entityRenderLayer.renderLayer;
                    BuiltBuffer vertexBuffer = entityRenderLayer.builtBuffer;

                    ResourceLocation
                        identifier =
                        ((RenderType.CompositeRenderType) renderLayer).state.textureState.cutoutTexture()
                            .orElse(MissingTextureAtlasSprite.getLocation());
                    int
                        geometryTypeID =
                        Constants.GeometryTypes.getGeometryType(renderLayer, entityRenderLayer.reflect)
                            .getValue();
                    int
                        geometryTextureID =
                        textureManager.getTexture(identifier)
                            .getId();
                    float geometryNormalOffset = getGeometryNormalOffset(identifier);
                    int
                        vertexFormatID =
                        Constants.VertexFormats.getValue(vertexBuffer.getDrawParameters()
                            .format());
                    int
                        indexFormatID =
                        Constants.DrawModes.getValue(vertexBuffer.getDrawParameters()
                            .mode());
                    float externalBaseX = entityRenderData.post ? (float) entityRenderData.x : 0.0f;
                    float externalBaseY = entityRenderData.post ? (float) entityRenderData.y : 0.0f;
                    float externalBaseZ = entityRenderData.post ? (float) entityRenderData.z : 0.0f;
                    boolean canUseExternalPbr = PBRVertexConsumer.canUseExternalPbr(vertexBuffer);
                    boolean hasFinalExternalPbrContext = canUseExternalPbr
                        && entityRenderLayer.ownerProvider != null
                        && entityRenderLayer.ownerProvider.hasFinalEntityBuildContext(
                            coordinate.getValue(), normalOffset,
                            externalBaseX, externalBaseY, externalBaseZ);
                    if (canUseExternalPbr && !hasFinalExternalPbrContext) {
                        throw new IllegalStateException(
                            "PBR QUADS entity layer was built before its final build context was set: layer="
                                + renderLayer.name + ", content=" + entityRenderLayer.contentName()
                                + ", coordinate=" + coordinate.getValue()
                                + ", normalOffset=" + normalOffset
                                + ", postBase=(" + externalBaseX + ", " + externalBaseY + ", "
                                + externalBaseZ + ")");
                    }

                    long vertexAddress;
                    if (transferredBuffers != null) {
                        TransferredBuildBuffer transferredBuffer = TransferredBuildBuffer.take(vertexBuffer);
                        transferredBuffers[geometryIndex] = transferredBuffer;
                        vertexAddress = transferredBuffer.vertexAddress();
                    } else {
                        BufferProxy.BufferInfo vertexBufferInfo = BufferProxy.getBufferInfo(
                            vertexBuffer.getBuffer());
                        vertexAddress = vertexBufferInfo.addr();
                    }
                    assert vertexBuffer.getDrawParameters()
                        .indexCount() == vertexBuffer.getDrawParameters()
                        .vertexCount() / 4 * 6;

                    geometryTypeBB.putInt(geometryTypeBaseAddr, geometryTypeID);
                    geometryTypeBaseAddr += Integer.BYTES;

                    ByteBuffer geometryGroupNameBuffer = MemoryUtil.memUTF8(renderLayer.name, true);
                    geometryGroupNameBuffers.add(geometryGroupNameBuffer);
                    geometryGroupNameBB.putLong(geometryGroupNameBaseAddr, memAddress(geometryGroupNameBuffer));
                    geometryGroupNameBaseAddr += Long.BYTES;

                    ByteBuffer geometryContentNameBuffer = MemoryUtil.memUTF8(entityRenderLayer.contentName(), true);
                    geometryContentNameBuffers.add(geometryContentNameBuffer);
                    geometryContentNameBB.putLong(geometryContentNameBaseAddr,
                        memAddress(geometryContentNameBuffer));
                    geometryContentNameBaseAddr += Long.BYTES;

                    geometryTextureBB.putInt(geometryTextureBaseAddr, geometryTextureID);
                    geometryTextureBaseAddr += Integer.BYTES;

                    geometryNormalOffsetBB.putFloat(geometryNormalOffsetBaseAddr, geometryNormalOffset);
                    geometryNormalOffsetBaseAddr += Float.BYTES;

                    vertexFormatBB.putInt(vertexFormatBaseAddr, vertexFormatID);
                    vertexFormatBaseAddr += Integer.BYTES;

                    indexFormatBB.putInt(indexFormatBaseAddr, indexFormatID);
                    indexFormatBaseAddr += Integer.BYTES;

                    vertexCountBB.putInt(vertexCountBaseAddr,
                        vertexBuffer.getDrawParameters()
                            .vertexCount());
                    vertexCountBaseAddr += Integer.BYTES;

                    verticesBB.putLong(verticesBaseAddr, vertexAddress);
                    verticesBaseAddr += Long.BYTES;
                    geometryIndex++;
                }
            }

            queueBuildNative(lineWidth,
                coordinate.getValue(),
                normalOffset,
                entityRenderDataList.getTotalEntityCount(),
                entityHashCodeAddr,
                entityPosXAddr,
                entityPosYAddr,
                entityPosZAddr,
                entityRayTracingFlagAddr,
                entityPostRenderFlagAddr,
                entityPrebuiltBLASAddr,
                entityTransformBB == null ? 0 : memAddress(entityTransformBB),
                entityMaterialBB == null ? 0 : memAddress(entityMaterialBB),
                entityPostAddr,
                entityLayerCountAddr,
                geometryTypeAddr,
                geometryGroupNameAddr,
                geometryContentNameAddr,
                geometryTextureAddr,
                geometryNormalOffsetAddr,
                vertexFormatAddr,
                indexFormatAddr,
                vertexCountAddr,
                verticesAddr,
                transferredBuffers);
            submittedNative = true;
        } finally {
            freeDirectBuffer(entityHashCodeBB);
            freeDirectBuffer(entityPosXBB);
            freeDirectBuffer(entityPosYBB);
            freeDirectBuffer(entityPosZBB);
            freeDirectBuffer(entityRayTracingFlagBB);
            freeDirectBuffer(entityPostRenderFlagBB);
            freeDirectBuffer(entityPrebuiltBLASBB);
            freeDirectBuffer(entityPostBB);
            freeDirectBuffer(entityTransformBB);
            freeDirectBuffer(entityMaterialBB);
            freeDirectBuffer(entityLayerCountBB);
            freeDirectBuffer(geometryTypeBB);
            freeDirectBuffer(geometryGroupNameBB);
            freeDirectBuffer(geometryContentNameBB);
            freeDirectBuffer(geometryTextureBB);
            freeDirectBuffer(geometryNormalOffsetBB);
            freeDirectBuffer(vertexFormatBB);
            freeDirectBuffer(indexFormatBB);
            freeDirectBuffer(vertexCountBB);
            freeDirectBuffer(verticesBB);
            for (ByteBuffer geometryGroupNameBuffer : geometryGroupNameBuffers) {
                MemoryUtil.memFree(geometryGroupNameBuffer);
            }
            for (ByteBuffer geometryContentNameBuffer : geometryContentNameBuffers) {
                MemoryUtil.memFree(geometryContentNameBuffer);
            }

            if (transferredBuffers != null && !submittedNative) {
                closeTransferredBuffers(transferredBuffers);
            }
            if (closeAfterBuild && !submittedNative) {
                closeBuiltBuffers(entityRenderDataList, transferredBuffers);
                closeStorageVertexConsumerProviders(storageVertexConsumerProviders);
            } else if (closeAfterBuild) {
                closeStorageVertexConsumerProviders(storageVertexConsumerProviders);
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

    private static void closeBuiltBuffers(EntityRenderDataList entityRenderDataList,
        TransferredBuildBuffer[] transferredBuffers) {
        for (EntityRenderData entityRenderData : entityRenderDataList) {
            for (EntityRenderLayer entityRenderLayer : entityRenderData) {
                BuiltBuffer vertexBuffer = entityRenderLayer.builtBuffer;
                if (isTransferred(vertexBuffer, transferredBuffers)) {
                    continue;
                }
                vertexBuffer.close();
            }
        }
    }

    private static boolean isTransferred(BuiltBuffer builtBuffer,
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

    public static native int registerVertexShaderNative(String source);
    public static native void releaseVertexShaderNative(int handle);
    public static native String[] queueVertexShaderInstancesNative(ByteBuffer records, ByteBuffer[] uniforms);

    public static native int registerPrebuiltGeometryNative(ByteBuffer data);

    public static native void releasePrebuiltGeometryNative(int handle);

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
        buildNative();
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

    public record EntityRenderLayer(RenderType renderLayer, BuiltBuffer builtBuffer,
                                    StorageVertexConsumerProvider ownerProvider,
                                    boolean reflect, String contentName) {

        public EntityRenderLayer(RenderType renderLayer, BuiltBuffer builtBuffer,
            boolean reflect) {
            this(renderLayer, builtBuffer, null, reflect, "");
        }

        public EntityRenderLayer(RenderType renderLayer, BuiltBuffer builtBuffer,
            boolean reflect, String contentName) {
            this(renderLayer, builtBuffer, null, reflect, contentName);
        }

    }

    public static class EntityRenderData extends ArrayList<EntityRenderLayer> {

        private final long hashCode;
        private final int rayTracingFlag;
        private final int postRenderFlag;
        private final int prebuiltBLAS;
        private final boolean post;
        public final Matrix4f transform;
        public final Vector4f color;
        public final Vector2f uvOffset;
        public int light;
        public int overlay;
        private double x;
        private double y;
        private double z;

        public EntityRenderData(long hashCode, double x, double y, double z,
            int rayTracingFlag,
            int postRenderFlag,
            int prebuiltBLAS,
            boolean post) {
            this.hashCode = hashCode;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rayTracingFlag = rayTracingFlag;
            this.postRenderFlag = postRenderFlag;
            this.prebuiltBLAS = prebuiltBLAS;
            this.transform = prebuiltBLAS >= 0 ? new Matrix4f() : null;
            this.color = prebuiltBLAS >= 0 ? new Vector4f(1) : null;
            this.uvOffset = prebuiltBLAS >= 0 ? new Vector2f() : null;
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

        public int getRayTracingFlag() {
            return rayTracingFlag;
        }

        public int getPostRenderFlag() {
            return postRenderFlag;
        }

        public int getPrebuiltBLAS() {
            return prebuiltBLAS;
        }

        public long getHashCode() {
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

        public int getTotalLayersCount() {
            return totalLayersCount;
        }

        public int getTotalEntityCount() {
            return this.size();
        }
    }
}
