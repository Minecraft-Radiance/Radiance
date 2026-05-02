package com.radiance.client.proxy.world;

import static net.minecraft.client.render.VertexFormat.DrawMode.LINES;
import static net.minecraft.client.render.VertexFormat.DrawMode.LINE_STRIP;
import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static net.minecraft.client.render.VertexFormat.DrawMode.TRIANGLE_STRIP;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.radiance.client.RadianceClient;
import com.radiance.client.constant.Constants;
import com.radiance.client.constant.Constants.PostRenderFlags;
import com.radiance.client.constant.Constants.RayTracingFlags;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IHeldItemRendererExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleManagerExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
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
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.RainSplashParticle;
import net.minecraft.client.particle.WaterSplashParticle;
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
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
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
    public static VertexConsumerProvider postTextVertexConsumerProvider;

    private static final Identifier SUN_TEXTURE = Identifier.ofVanilla(
        "textures/environment/sun.png");
    private static final Identifier MOON_PHASES_TEXTURE = Identifier.ofVanilla(
        "textures/environment/moon_phases.png");
    private static final Identifier WEATHER_RAIN_TEXTURE = Identifier.ofVanilla(
        "textures/environment/rain.png");
    private static final Identifier WEATHER_SNOW_TEXTURE = Identifier.ofVanilla(
        "textures/environment/snow.png");
    private static final String WEATHER_DEFAULT_CONTENT = "/weather/default";
    private static final String WEATHER_RAIN_CONTENT = "/weather/rain";
    private static final String WEATHER_SNOW_CONTENT = "/weather/snow";
    private static final String PARTICLE_DEFAULT_CONTENT = "/particle/default";
    private static final String TEXT_DEFAULT_CONTENT = "/text/default";
    private static final String NAME_TAG_DEFAULT_CONTENT = "/name_tag/default";
    private static final Set<String> LOGGED_POST_CONTENT_KEYS = ConcurrentHashMap.newKeySet();

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
        Function<RenderLayer, String> contentNameResolver,
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
        Function<RenderLayer, String> contentNameResolver,
        boolean post,
        EntityRenderDataList entityRenderDataList) {
        Map<RenderLayer, VertexConsumer> layerBuffers = storageVertexConsumerProvider.getLayers();
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

            String contentName = contentNameResolver == null
                ? ""
                : Objects.requireNonNullElse(contentNameResolver.apply(layer), "");
            if (layer.name.contains("water_mask")) {
                waterMaskRenderData.add(new EntityRenderLayer(layer, buffer, reflect, contentName));
            } else {
                entityRenderData.add(new EntityRenderLayer(layer, buffer, reflect, contentName));
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

        MinecraftClient client = MinecraftClient.getInstance();
        TickManager
            tickManager =
            Objects.requireNonNull(client.world)
                .getTickManager();

        List<StorageVertexConsumerProvider> entityStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList entityRenderDataList = new EntityRenderDataList();
        for (Entity entity : renderedEntities) {

            if (entity.age == 0) {
                entity.lastRenderX = entity.getX();
                entity.lastRenderY = entity.getY();
                entity.lastRenderZ = entity.getZ();
            }

            StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                786432);
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

            float tickDelta = tickCounter.getTickDelta(!tickManager.shouldSkipTick(entity));
            double entityPosX = MathHelper.lerp(tickDelta, entity.lastRenderX,
                entity.getX());
            double entityPosY = MathHelper.lerp(tickDelta, entity.lastRenderY,
                entity.getY());
            double entityPosZ = MathHelper.lerp(tickDelta, entity.lastRenderZ,
                entity.getZ());
            int light = entityRenderDispatcher.getLight(entity, tickDelta);

            if (entity instanceof DisplayEntity.TextDisplayEntity) {
                StorageVertexConsumerProvider postTextStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                    786432);
                entityStorageVertexConsumerProviders.add(postTextStorageVertexConsumerProvider);
                entityRenderDispatcher.render(entity,
                    0,
                    0,
                    0,
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

            if (entity.equals(camera.getFocusedEntity())) {
                processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                    System.identityHashCode(entity),
                    entityPosX,
                    entityPosY,
                    entityPosZ,
                    Constants.RayTracingFlags.PLAYER,
                    true,
                    entityRenderDataList);
            } else if (entity instanceof FishingBobberEntity) {
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

    public static synchronized Pair<List<StorageVertexConsumerProvider>, EntityRenderDataList> queueBlockEntitiesRebuild(
        BuiltChunkStorage chunks,
        Set<BlockEntity> noCullingBlockEntities,
        Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions,
        BlockEntityRenderDispatcher blockEntityRenderDispatcher,
        float tickDelta) {
        MatrixStack matrixStack = new MatrixStack();
        List<StorageVertexConsumerProvider> entityStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList entityRenderDataList = new EntityRenderDataList();

        List<StorageVertexConsumerProvider> crumblingStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList crumblingRenderDataList = new EntityRenderDataList();
        for (ChunkBuilder.BuiltChunk builtChunk : chunks.chunks) {
            List<BlockEntity>
                list =
                builtChunk.getData()
                    .getBlockEntities();
            if (!list.isEmpty()) {
                for (BlockEntity blockEntity : list) {
                    StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                        786432);
                    entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);
                    StorageVertexConsumerProvider crumblingStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                        0);
                    crumblingStorageVertexConsumerProviders.add(
                        crumblingStorageVertexConsumerProvider);

                    VertexConsumerProvider vertexConsumerProvider = entityStorageVertexConsumerProvider;

                    BlockPos blockPos = blockEntity.getPos();
                    double entityPosX = blockPos.getX();
                    double entityPosY = blockPos.getY();
                    double entityPosZ = blockPos.getZ();

                    matrixStack.push();
                    SortedSet<BlockBreakingInfo> sortedSet = blockBreakingProgressions.get(
                        blockPos.asLong());
                    if (sortedSet != null && !sortedSet.isEmpty()) {
                        int
                            stage =
                            sortedSet.last()
                                .getStage();
                        if (stage >= 0) {
                            MatrixStack.Entry entry = matrixStack.peek();
                            VertexConsumer
                                vertexConsumer =
                                new OverlayVertexConsumer(
                                    crumblingStorageVertexConsumerProvider.getBuffer(
                                        ModelBaker.BLOCK_DESTRUCTION_RENDER_LAYERS.get(
                                            stage)), entry, 1.0F);
                            vertexConsumerProvider = renderLayer -> {
                                VertexConsumer vertexConsumer2 = entityStorageVertexConsumerProvider.getBuffer(
                                    renderLayer);
                                return renderLayer.hasCrumbling() ? VertexConsumers.union(
                                    vertexConsumer,
                                    vertexConsumer2) :
                                    vertexConsumer2;
                            };
                        }
                    }

                    blockEntityRenderDispatcher.render(blockEntity, tickDelta, matrixStack,
                        vertexConsumerProvider);
                    matrixStack.pop();

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
            StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                786432);
            entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);

            BlockPos blockPos = blockEntity.getPos();
            double entityPosX = blockPos.getX();
            double entityPosY = blockPos.getY();
            double entityPosZ = blockPos.getZ();

            matrixStack.push();
            blockEntityRenderDispatcher.render(blockEntity, tickDelta, matrixStack,
                entityStorageVertexConsumerProvider);
            matrixStack.pop();

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

        return new Pair<>(crumblingStorageVertexConsumerProviders, crumblingRenderDataList);
    }

    public static void queueCrumblingRebuild(Camera camera,
        Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions,
        BlockRenderManager blockRenderManager,
        ClientWorld world,
        List<StorageVertexConsumerProvider> crumblingStorageVertexConsumerProviders,
        EntityRenderDataList crumblingRenderDataList) {
        MatrixStack matrixStack = new MatrixStack();
        List<StorageVertexConsumerProvider> blockCrumblingStorageVertexConsumerProviders = new ArrayList<>();
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

                    StorageVertexConsumerProvider blockCrumblingStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                        786432);
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

    public static void queueHandRebuild(BufferBuilderStorage buffers, float tickDelta,
        HeldItemRenderer firstPersonRenderer, float handProjectionScale) {
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
            matrixStack.scale(handProjectionScale, handProjectionScale, 1.0F);
            ((IHeldItemRendererExt) firstPersonRenderer).radiance$renderItem(tickDelta,
                matrixStack,
                storageVertexConsumerProvider,
                client.player,
                client.getEntityRenderDispatcher()
                    .getLight(client.player, tickDelta));
        }

        matrixStack.pop();

        if (client.options.getPerspective()
            .isFirstPerson() && !bl && !client.options.hudHidden &&
            client.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR) {
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

        StorageVertexConsumerProvider particleStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
            0);
        storageVertexConsumerProviders.add(particleStorageVertexConsumerProvider);
        StorageVertexConsumerProvider splashStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
            0);
        storageVertexConsumerProviders.add(splashStorageVertexConsumerProvider);

        ParticleManager particleManager = MinecraftClient.getInstance().particleManager;
        IParticleManagerExt particleManagerExt = (IParticleManagerExt) particleManager;
        Map<ParticleTextureSheet, Queue<Particle>> particles = particleManagerExt.radiance$getParticles();

        for (ParticleTextureSheet particleTextureSheet : particleManagerExt.radiance$getTextureSheets()) {
            Queue<Particle> particleQueue = particles.get(particleTextureSheet);
            if (particleQueue != null && !particleQueue.isEmpty()) {
                for (Particle particle : particleQueue) {
                    renderParticle(particleStorageVertexConsumerProvider, splashStorageVertexConsumerProvider,
                        particleTextureSheet, particle, camera, tickDelta);
                }
            }
        }

        Queue<Particle> pendingParticles = particleManagerExt.radiance$getNewParticles();
        if (pendingParticles != null && !pendingParticles.isEmpty()) {
            for (Particle particle : pendingParticles) {
                if (!isRainSplashParticle(particle)) {
                    continue;
                }

                ParticleTextureSheet particleTextureSheet = particle.getType();
                if (particleTextureSheet == ParticleTextureSheet.CUSTOM
                    || particleTextureSheet == ParticleTextureSheet.NO_RENDER) {
                    continue;
                }

                renderParticle(particleStorageVertexConsumerProvider, splashStorageVertexConsumerProvider,
                    particleTextureSheet, particle, camera, tickDelta);
            }
        }

        processWorldEntityRenderData(particleStorageVertexConsumerProvider, 0, 0, 0, 0,
            Constants.RayTracingFlags.PARTICLE, true, renderDataList);
        processWorldEntityRenderData(splashStorageVertexConsumerProvider, 0, 0, 0, 0,
            Constants.RayTracingFlags.WEATHER, true, renderDataList);

        StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
            0);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        Queue<Particle> customParticleQueue = particles.get(ParticleTextureSheet.CUSTOM);
        if (customParticleQueue != null && !customParticleQueue.isEmpty()) {
            for (Particle particle : customParticleQueue) {

                MatrixStack matrixStack = new MatrixStack();

                try {
                    particle.renderCustom(matrixStack, storageVertexConsumerProvider, camera,
                        tickDelta);
                } catch (Throwable var10) {
                    CrashReport crashReport = CrashReport.create(var10, "Rendering Particle");
                    CrashReportSection crashReportSection = crashReport.addElement(
                        "Particle being rendered");
                    crashReportSection.add("Particle", particle::toString);
                    crashReportSection.add("Particle Type", "Custom");
                    throw new CrashException(crashReport);
                }
            }
        }

        processWorldEntityRenderData(storageVertexConsumerProvider, 0, 0, 0, 0,
            Constants.RayTracingFlags.PARTICLE, true, renderDataList);

        queueBuild(storageVertexConsumerProviders, renderDataList, 0.0f,
            Constants.Coordinates.CAMERA_SHIFT, false);
    }

    private static boolean isRainSplashParticle(Particle particle) {
        return particle instanceof RainSplashParticle || particle instanceof WaterSplashParticle;
    }

    private static void renderParticle(
        StorageVertexConsumerProvider particleStorageVertexConsumerProvider,
        StorageVertexConsumerProvider splashStorageVertexConsumerProvider,
        ParticleTextureSheet particleTextureSheet,
        Particle particle,
        Camera camera,
        float tickDelta) {
        StorageVertexConsumerProvider targetStorageVertexConsumerProvider;
        if (isRainSplashParticle(particle)) {
            targetStorageVertexConsumerProvider = splashStorageVertexConsumerProvider;
        } else {
            targetStorageVertexConsumerProvider = particleStorageVertexConsumerProvider;
        }

        targetStorageVertexConsumerProvider.setMaterialFlags(getParticleMaterialFlags(particle));
        VertexConsumer vertexConsumer = targetStorageVertexConsumerProvider.getBuffer(
            Objects.requireNonNull(getParticleRenderLayer(particle, particleTextureSheet)));

        try {
            particle.render(vertexConsumer, camera, tickDelta);
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.create(throwable, "Rendering Particle");
            CrashReportSection crashReportSection = crashReport.addElement(
                "Particle being rendered");
            crashReportSection.add("Particle", particle);
            crashReportSection.add("Particle Type", particleTextureSheet);
            throw new CrashException(crashReport);
        }
    }

    private static int getParticleMaterialFlags(Particle particle) {
        if (isRainSplashParticle(particle)) {
            return PBRVertexConsumer.MATERIAL_FLAG_RAIN_SPLASH;
        }
        return 0;
    }

    private static RenderLayer getParticleRenderLayer(Particle particle,
        ParticleTextureSheet particleTextureSheet) {
        if (isRainSplashParticle(particle)) {
            return Objects.requireNonNull(
                ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT.renderType());
        }
        return Objects.requireNonNull(particleTextureSheet.renderType());
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

        StorageVertexConsumerProvider weatherStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
            0);
        storageVertexConsumerProviders.add(weatherStorageVertexConsumerProvider);

        weatherStorageVertexConsumerProvider.setMaterialFlags(
            PBRVertexConsumer.MATERIAL_FLAG_RAIN_PRECIPITATION);
        weatherRendering.renderPrecipitation(world, weatherStorageVertexConsumerProvider, ticks, tickDelta,
            camera.getPos());
        weatherStorageVertexConsumerProvider.setMaterialFlags(0);
        processWorldEntityRenderData(weatherStorageVertexConsumerProvider, 0, 0, 0, 0,
            Constants.RayTracingFlags.WEATHER, true, renderDataList);

        StorageVertexConsumerProvider postStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
            0);
        storageVertexConsumerProviders.add(postStorageVertexConsumerProvider);

        MinecraftClient client = MinecraftClient.getInstance();
        int clampedViewDistance = client.options.getClampedViewDistance() * 16;
        float farPlaneDistance = client.gameRenderer.getFarPlaneDistance();
        worldBorderRendering.render(world.getWorldBorder(), camera.getPos(), clampedViewDistance,
            farPlaneDistance);

        processPostEntityRenderData(postStorageVertexConsumerProvider, 0, 0, 0, 0,
            PostRenderFlags.WEATHER, EntityProxy::resolveWeatherContentName, renderDataList);

        queueBuild(storageVertexConsumerProviders, renderDataList, 0.0f,
            Constants.Coordinates.CAMERA_SHIFT, false);
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
            MinecraftClient.getInstance()
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
        ByteBuffer entityLayerCountBB = null;
        ByteBuffer geometryTypeBB = null;
        ByteBuffer geometryGroupNameBB = null;
        ByteBuffer geometryContentNameBB = null;
        ByteBuffer geometryTextureBB = null;
        ByteBuffer vertexFormatBB = null;
        ByteBuffer indexFormatBB = null;
        ByteBuffer vertexCountBB = null;
        ByteBuffer verticesBB = null;

        try {
            int entityHashCodeSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
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

            for (EntityRenderData entityRenderData : entityRenderDataList) {
                entityHashCodeBB.putInt(entityHashCodeBaseAddr, entityRenderData.hashCode);
                entityHashCodeBaseAddr += Integer.BYTES;

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
                    if (entityRenderData.postRenderFlag != 0) {
                        logPostContentNameOnce(entityRenderData.postRenderFlag,
                            entityRenderLayer.contentName, entityRenderLayer.renderLayer);
                    }

                    RenderLayer renderLayer = entityRenderLayer.renderLayer;
                    BuiltBuffer vertexBuffer = entityRenderLayer.builtBuffer;

                    Identifier
                        identifier =
                        ((RenderLayer.MultiPhase) renderLayer).phases.texture.getId()
                            .orElse(MissingSprite.getMissingSpriteId());
                    int
                        geometryTypeID =
                        Constants.GeometryTypes.getGeometryType(renderLayer, entityRenderLayer.reflect)
                            .getValue();
                    int
                        geometryTextureID =
                        textureManager.getTexture(identifier)
                            .getGlId();
                    int
                        vertexFormatID =
                        Constants.VertexFormats.getValue(vertexBuffer.getDrawParameters()
                            .format());
                    int
                        indexFormatID =
                        Constants.DrawModes.getValue(vertexBuffer.getDrawParameters()
                            .mode());

                    BufferProxy.BufferInfo vertexBufferInfo = BufferProxy.getBufferInfo(
                        vertexBuffer.getBuffer());
                    assert vertexBuffer.getDrawParameters()
                        .indexCount() == vertexBuffer.getDrawParameters()
                        .vertexCount() / 4 * 6;

                    geometryTypeBB.putInt(geometryTypeBaseAddr, geometryTypeID);
                    geometryTypeBaseAddr += Integer.BYTES;

                    ByteBuffer geometryGroupNameBuffer = MemoryUtil.memUTF8(renderLayer.name, true);
                    geometryGroupNameBuffers.add(geometryGroupNameBuffer);
                    geometryGroupNameBB.putLong(geometryGroupNameBaseAddr, memAddress(geometryGroupNameBuffer));
                    geometryGroupNameBaseAddr += Long.BYTES;

                    ByteBuffer geometryContentNameBuffer = MemoryUtil.memUTF8(
                        entityRenderLayer.contentName(), true);
                    geometryContentNameBuffers.add(geometryContentNameBuffer);
                    geometryContentNameBB.putLong(geometryContentNameBaseAddr,
                        memAddress(geometryContentNameBuffer));
                    geometryContentNameBaseAddr += Long.BYTES;

                    geometryTextureBB.putInt(geometryTextureBaseAddr, geometryTextureID);
                    geometryTextureBaseAddr += Integer.BYTES;

                    vertexFormatBB.putInt(vertexFormatBaseAddr, vertexFormatID);
                    vertexFormatBaseAddr += Integer.BYTES;

                    indexFormatBB.putInt(indexFormatBaseAddr, indexFormatID);
                    indexFormatBaseAddr += Integer.BYTES;

                    vertexCountBB.putInt(vertexCountBaseAddr,
                        vertexBuffer.getDrawParameters()
                            .vertexCount());
                    vertexCountBaseAddr += Integer.BYTES;

                    verticesBB.putLong(verticesBaseAddr, vertexBufferInfo.addr());
                    verticesBaseAddr += Long.BYTES;
                }
            }

            queueBuild(lineWidth,
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
                entityPostAddr,
                entityLayerCountAddr,
                geometryTypeAddr,
                geometryGroupNameAddr,
                geometryContentNameAddr,
                geometryTextureAddr,
                vertexFormatAddr,
                indexFormatAddr,
                vertexCountAddr,
                verticesAddr);
        } finally {
            freeDirectBuffer(entityHashCodeBB);
            freeDirectBuffer(entityPosXBB);
            freeDirectBuffer(entityPosYBB);
            freeDirectBuffer(entityPosZBB);
            freeDirectBuffer(entityRayTracingFlagBB);
            freeDirectBuffer(entityPostRenderFlagBB);
            freeDirectBuffer(entityPrebuiltBLASBB);
            freeDirectBuffer(entityPostBB);
            freeDirectBuffer(entityLayerCountBB);
            freeDirectBuffer(geometryTypeBB);
            freeDirectBuffer(geometryGroupNameBB);
            freeDirectBuffer(geometryContentNameBB);
            freeDirectBuffer(geometryTextureBB);
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

            if (closeAfterBuild) {
                closeBuiltBuffers(entityRenderDataList);
                closeStorageVertexConsumerProviders(storageVertexConsumerProviders);
            }
        }
    }

    private static void freeDirectBuffer(ByteBuffer buffer) {
        if (buffer != null) {
            MemoryUtil.memFree(buffer);
        }
    }

    private static void closeBuiltBuffers(EntityRenderDataList entityRenderDataList) {
        for (EntityRenderData entityRenderData : entityRenderDataList) {
            for (EntityRenderLayer entityRenderLayer : entityRenderData) {
                BuiltBuffer vertexBuffer = entityRenderLayer.builtBuffer;
                vertexBuffer.close();
            }
        }
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

    private static native void queueBuild(float lineWidth,
        int coordinate,
        boolean normalOffset,
        int size,
        long entityHashCodes,
        long entityPosXs,
        long entityPosYs,
        long entityPosZs,
        long entityRayTracingFlags,
        long entityPostRenderFlags,
        long entityPrebuiltBLASs,
        long entityPosts,
        long entityLayerCounts,
        long geometryTypes,
        long geometryGroupNames,
        long geometryContentNames,
        long geometryTextures,
        long vertexFormats,
        long indexFormats,
        long vertexCounts,
        long vertices);

    public static native void build();

    private static String defaultPostContentName(Constants.PostRenderFlags postRenderFlag) {
        return switch (postRenderFlag) {
            case WEATHER -> WEATHER_DEFAULT_CONTENT;
            case PARTICLE -> PARTICLE_DEFAULT_CONTENT;
            case TEXT -> TEXT_DEFAULT_CONTENT;
            case NAME_TAG -> NAME_TAG_DEFAULT_CONTENT;
        };
    }

    private static String normalizeParticleContentName(String particleContentName) {
        if (particleContentName == null || particleContentName.isBlank()) {
            return PARTICLE_DEFAULT_CONTENT;
        }
        return particleContentName;
    }

    private static String resolveWeatherContentName(RenderLayer renderLayer) {
        Identifier identifier = getTextureId(renderLayer);
        if (WEATHER_RAIN_TEXTURE.equals(identifier)) {
            return WEATHER_RAIN_CONTENT;
        }
        if (WEATHER_SNOW_TEXTURE.equals(identifier)) {
            return WEATHER_SNOW_CONTENT;
        }
        return WEATHER_DEFAULT_CONTENT;
    }

    private static Identifier getTextureId(RenderLayer renderLayer) {
        if (renderLayer instanceof RenderLayer.MultiPhase multiPhase) {
            return multiPhase.phases.texture.getId()
                .orElse(MissingSprite.getMissingSpriteId());
        }
        return MissingSprite.getMissingSpriteId();
    }

    private static void logPostContentNameOnce(int postRenderFlag, String contentName,
        RenderLayer renderLayer) {
        String normalizedContentName = Objects.requireNonNullElse(contentName, "");
        String postRenderFlagName = postRenderFlagName(postRenderFlag);
        String key = postRenderFlagName + "|" + normalizedContentName;
        if (!LOGGED_POST_CONTENT_KEYS.add(key)) {
            return;
        }

        Identifier textureId = getTextureId(renderLayer);
    }

    private static String postRenderFlagName(int postRenderFlag) {
        if (postRenderFlag == PostRenderFlags.WEATHER.getValue()) {
            return "WEATHER";
        }
        if (postRenderFlag == PostRenderFlags.PARTICLE.getValue()) {
            return "PARTICLE";
        }
        if (postRenderFlag == PostRenderFlags.TEXT.getValue()) {
            return "TEXT";
        }
        if (postRenderFlag == PostRenderFlags.NAME_TAG.getValue()) {
            return "NAME_TAG";
        }
        return "UNKNOWN(" + postRenderFlag + ")";
    }

    public record EntityRenderLayer(RenderLayer renderLayer, BuiltBuffer builtBuffer,
                                    boolean reflect, String contentName) {

    }

    public static class EntityRenderData extends ArrayList<EntityRenderLayer> {

        private final int hashCode;
        private final int rayTracingFlag;
        private final int postRenderFlag;
        private final int prebuiltBLAS;
        private final boolean post;
        private double x;
        private double y;
        private double z;

        public EntityRenderData(int hashCode, double x, double y, double z, int rayTracingFlag,
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

        public int getTotalLayersCount() {
            return totalLayersCount;
        }

        public int getTotalEntityCount() {
            return this.size();
        }
    }
}
