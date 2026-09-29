package com.radiance.client.proxy.world;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.util.math.ChunkSectionPos;
import java.util.concurrent.atomic.AtomicLong;
import java.util.LinkedHashSet;
import org.joml.Vector3d;
import org.joml.Matrix4d;
import com.radiance.client.mod_support.flywheel.FlywheelSupport;
import com.radiance.client.mod_support.ModSupport;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.joml.Matrix4f;
import static net.minecraft.client.render.VertexFormat.DrawMode.LINES;
import static net.minecraft.client.render.VertexFormat.DrawMode.LINE_STRIP;
import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static net.minecraft.client.render.VertexFormat.DrawMode.TRIANGLE_STRIP;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.radiance.client.RadianceClient;
import com.radiance.client.constant.Constants;
import com.radiance.client.constant.Constants.PostRenderFlags;
import com.radiance.client.constant.Constants.RayTracingFlags;
import com.radiance.client.profiler.Profiler;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.render.CustomParticleRenderer;
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
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.OverlayVertexConsumer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.screen.PlayerScreenHandler;
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
    private static final Identifier PARTICLE_ATLAS_TEXTURE = Identifier.ofVanilla(
        "textures/atlas/particles.png");
    private static final float VILLAGER_LAYER_OFFSET_UNIT = 0.001f / 16.0f;
    private static final String VILLAGER_TEXTURE_PREFIX = "textures/entity/villager/";
    private static final String ZOMBIE_VILLAGER_TEXTURE_PREFIX = "textures/entity/zombie_villager/";
    private static final RenderPhase.ShaderProgram PARTICLE_PROGRAM = new RenderPhase.ShaderProgram(
        GameRenderer::getParticleProgram);
    private static final RenderLayer TERRAIN_PARTICLE_LAYER = createTranslucentParticleLayer(
        PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
    private static final RenderLayer OPAQUE_PARTICLE_LAYER = createOpaqueParticleLayer(
        PARTICLE_ATLAS_TEXTURE);
    private static final RenderLayer TRANSLUCENT_PARTICLE_LAYER = createTranslucentParticleLayer(
        PARTICLE_ATLAS_TEXTURE);
    private static final String WEATHER_DEFAULT_CONTENT = "/weather/default";
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

    private static final Set<BlockGeometrySource> blockSources = new LinkedHashSet<>();
    private static final AtomicLong nextSourceKey = new AtomicLong(0x7000000000000000L);

    public abstract static class BlockGeometrySource implements AutoCloseable {
        protected final ClientWorld level;
        protected final Matrix4d localToWorld = new Matrix4d();
        private final Map<Long, SourceSection> sections = new LinkedHashMap<>();
        private final Set<Long> dirty = new LinkedHashSet<>();
        private boolean closed;

        protected BlockGeometrySource(ClientWorld level) {
            this.level = level;
            blockSources.add(this);
        }

        protected abstract boolean ready();
        protected abstract void updateTransform(float partialTick);

        protected final void setBounds(BlockPos min, BlockPos max) {
            Set<Long> remaining = new LinkedHashSet<>(sections.keySet());
            if (min != null && max != null) {
                for (ChunkSectionPos pos : ChunkSectionPos.stream(min.getX() >> 4, min.getY() >> 4, min.getZ() >> 4,
                    max.getX() >> 4, max.getY() >> 4, max.getZ() >> 4).toList()) {
                    long key = pos.asLong();
                    remaining.remove(key);
                    if (!sections.containsKey(key)) {
                        sections.put(key, new SourceSection(pos));
                        dirty.add(key);
                    }
                }
            }
            for (long key : remaining) {
                sections.remove(key).close();
                dirty.remove(key);
            }
        }

        public final Matrix4f transform(BlockPos origin, net.minecraft.util.math.Vec3i renderOrigin) {
            Vector3d position = localToWorld.transformPosition(origin.getX(), origin.getY(), origin.getZ(), new Vector3d());
            return new Matrix4f(localToWorld).setTranslation((float) (position.x - renderOrigin.getX()),
                (float) (position.y - renderOrigin.getY()), (float) (position.z - renderOrigin.getZ()));
        }

        public final void rebuild() { dirty.addAll(sections.keySet()); }

        public final void setDirty(int x, int y, int z, boolean playerChanged) {
            long key = ChunkSectionPos.asLong(x, y, z);
            if (sections.containsKey(key)) dirty.add(key);
        }

        public final boolean isSectionCompiled(int x, int y, int z) {
            SourceSection section = sections.get(ChunkSectionPos.asLong(x, y, z));
            return section != null && section.compiled;
        }

        public final int getVisibleSectionCount() { return sections.size(); }

        protected final void compileSections(int budget) {
            if (closed || !ready() || dirty.isEmpty()) return;
            var dispatcher = MinecraftClient.getInstance().worldRenderer.getChunkBuilder();
            var compiler = ((IChunkBuilderExt) dispatcher).radiance$getSectionBuilder();
            var buffers = ((IChunkBuilderExt) dispatcher).radiance$getBuffers();
            var regionCache = new ChunkRendererRegionBuilder();
            var iterator = dirty.iterator();
            while (iterator.hasNext() && budget-- > 0) {
                SourceSection section = sections.get(iterator.next());
                iterator.remove();
                var region = regionCache.build(level, section.pos);
                SectionBuilder.RenderData results = region == null ? new SectionBuilder.RenderData() :
                    compiler.build(section.pos, region, VertexSorter.byDistance(0, 0, 0), buffers);
                try {
                    List<ByteBuffer> meshes = new ArrayList<>();
                    List<Integer> types = new ArrayList<>();
                    for (RenderLayer layer : RenderLayer.getBlockLayers()) {
                        BuiltBuffer mesh = results.buffers.get(layer);
                        if (mesh == null) continue;
                        meshes.add(mesh.getBuffer().slice());
                        types.add(Constants.GeometryTypes.getGeometryType(layer, true).getValue());
                    }
                    section.geometry = updatePrebuiltGeometryNative(section.geometry,
                        Constants.VertexFormats.CHUNK_PBR_TRIANGLE.getValue(),
                        types.stream().mapToInt(Integer::intValue).toArray(), meshes.toArray(ByteBuffer[]::new));
                    section.blocks.keySet().removeIf(block -> block.isRemoved() ||
                        level.getBlockEntity(block.getPos()) != block);
                    var chunk = level.getChunk(section.pos.getSectionX(), section.pos.getSectionZ());
                    for (BlockEntity block : chunk.getBlockEntities().values()) {
                        if (ChunkSectionPos.getSectionCoord(block.getPos().getY()) == section.pos.getSectionY()) {
                            section.blocks.computeIfAbsent(block, key -> nextSourceKey.getAndIncrement());
                            if (ModSupport.FLYWHEEL) FlywheelSupport.blockReady(block);
                        }
                    }
                    section.compiled = true;
                    if (Profiler.ENABLED) Profiler.metric("entity_source.sections_compiled", 1);
                } finally {
                    results.close();
                    buffers.clear();
                }
            }
        }

        private void submit(float partialTick, BlockEntityRenderDispatcher dispatcher,
            EntityRenderDataList output, List<StorageVertexConsumerProvider> providers) {
            if (closed || !ready()) return;
            if (!localToWorld.isFinite() || Math.abs(localToWorld.determinant3x3()) < 1e-12) return;
            Matrix4f linear = new Matrix4f(localToWorld).setTranslation(0, 0, 0);
            Vector3d position = new Vector3d();
            for (SourceSection section : sections.values()) {
                if (section.geometry >= 0) {
                    BlockPos origin = section.pos.getMinPos();
                    localToWorld.transformPosition(origin.getX(), origin.getY(), origin.getZ(), position);
                    var data = new EntityRenderData(section.key, position.x, position.y, position.z,
                        RayTracingFlags.WORLD.getValue(), 0, section.geometry, false);
                    data.transform.set(linear);
                    output.add(data);
                }
                for (var entry : section.blocks.entrySet()) {
                    BlockEntity block = entry.getKey();
                    if (block.isRemoved() || block.getWorld() != level) continue;
                    if (ModSupport.FLYWHEEL && FlywheelSupport.skip(block)) continue;
                    var renderer = dispatcher.get(block);
                    if (renderer == null) continue;
                    BlockPos pos = block.getPos();
                    localToWorld.transformPosition(pos.getX(), pos.getY(), pos.getZ(), position);
                    var vertices = new StorageVertexConsumerProvider(32768);
                    vertices.setEntityBuildContext(Constants.Coordinates.WORLD.getValue(), false, 0, 0, 0);
                    providers.add(vertices);
                    MatrixStack matrices = new MatrixStack();
                    matrices.multiplyPositionMatrix(linear);
                    if (ModSupport.FLYWHEEL) FlywheelSupport.enterVanilla();
                    try {
                        renderer.render(block, partialTick, matrices, vertices,
                            WorldRenderer.getLightmapCoordinates(level, pos), OverlayTexture.DEFAULT_UV);
                    } finally {
                        if (ModSupport.FLYWHEEL) FlywheelSupport.exitVanilla();
                    }
                    processWorldEntityRenderData(vertices, entry.getValue(), position.x, position.y, position.z,
                        RayTracingFlags.WORLD, true, output);
                }
            }
        }

        @Override
        public final void close() {
            if (closed) return;
            closed = true;
            sections.values().forEach(SourceSection::close);
            sections.clear();
            dirty.clear();
            blockSources.remove(this);
        }
    }

    private static final class SourceSection implements AutoCloseable {
        final long key = nextSourceKey.getAndIncrement();
        final ChunkSectionPos pos;
        final Map<BlockEntity, Long> blocks = new java.util.IdentityHashMap<>();
        int geometry = -1;
        boolean compiled;
        SourceSection(ChunkSectionPos pos) { this.pos = pos; }
        public void close() {
            if (geometry >= 0) releasePrebuiltGeometryNative(geometry);
            geometry = -1;
            blocks.clear();
        }
    }

    public static void setSourceWorld(ClientWorld level) {
        for (var source : List.copyOf(blockSources)) if (source.level != level) source.close();
    }

    public static void invalidateSources() { blockSources.forEach(BlockGeometrySource::rebuild); }

    public static void sourceSectionChanged(ChunkSectionPos pos) {
        for (var source : blockSources) source.setDirty(pos.getSectionX(), pos.getSectionY(), pos.getSectionZ(), false);
    }

    public static BlockGeometrySource blockSource(BlockEntity block) {
        for (var source : blockSources) {
            if (source.level != block.getWorld()) continue;
            var section = source.sections.get(ChunkSectionPos.from(block.getPos()).asLong());
            if (section != null) return source;
        }
        return null;
    }

    public static void prepareSources(float partialTick) {
        if (!isEntityNativeTransferEnabled()) return;
        for (var source : blockSources) {
            source.updateTransform(partialTick);
            source.compileSections(8);
        }
    }

    public static void queueSources(float partialTick, BlockEntityRenderDispatcher dispatcher) {
        if (!isEntityNativeTransferEnabled() || blockSources.isEmpty()) return;
        var output = new EntityRenderDataList();
        List<StorageVertexConsumerProvider> providers = new ArrayList<>();
        for (var source : blockSources) source.submit(partialTick, dispatcher, output, providers);
        queueBuild(providers, output);
    }

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
        return BLOCK_ENTITY_KEY_PREFIX | (mix64(blockEntity.getPos()
            .asLong()) & ENTITY_KEY_PAYLOAD_MASK);
    }

    private static long blockEntityCrumblingKey(BlockEntity blockEntity) {
        return BLOCK_ENTITY_CRUMBLING_KEY_PREFIX | (mix64(blockEntity.getPos()
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
        Function<RenderLayer, String> contentNameResolver,
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
        Function<RenderLayer, String> contentNameResolver,
        boolean isPostRender,
        EntityRenderDataList entityRenderDataList) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.EntityProxy.processEntityRenderData");
        }
        try {
            Map<RenderLayer, VertexConsumer> layerBuffers = storageVertexConsumerProvider.getLayers();
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
        RenderTickCounter tickCounter,
        boolean canDrawEntityOutlines) {
        if (!isEntityNativeTransferEnabled()) {
            return;
        }

        if (Profiler.ENABLED) {
            Profiler.enter("java.EntityProxy.queueEntitiesBuild");
        }
        try {
            MatrixStack matrixStack = new MatrixStack();
            MinecraftClient client = MinecraftClient.getInstance();
            TickManager tickManager = Objects.requireNonNull(client.world)
                .getTickManager();
            List<StorageVertexConsumerProvider> entityStorageVertexConsumerProviders = new ArrayList<>();
            EntityRenderDataList entityRenderDataList = new EntityRenderDataList();

            for (Entity entity : renderedEntities) {
                long entityKey = normalEntityKey(entity);

                if (entity.age == 0) {
                    entity.lastRenderX = entity.getX();
                    entity.lastRenderY = entity.getY();
                    entity.lastRenderZ = entity.getZ();
                }

                float tickDelta = tickCounter.getTickDelta(!tickManager.shouldSkipTick(entity));
                double entityPosX = MathHelper.lerp(tickDelta, entity.lastRenderX,
                    entity.getX());
                double entityPosY = MathHelper.lerp(tickDelta, entity.lastRenderY,
                    entity.getY());
                double entityPosZ = MathHelper.lerp(tickDelta, entity.lastRenderZ,
                    entity.getZ());
                float yaw = MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw());
                int light = entityRenderDispatcher.getLight(entity, tickDelta);
                Constants.RayTracingFlags rayTracingFlag = Constants.RayTracingFlags.WORLD;
                if (entity.equals(camera.getFocusedEntity())) {
                    rayTracingFlag = Constants.RayTracingFlags.PLAYER;
                } else if (entity instanceof FishingBobberEntity) {
                    rayTracingFlag = Constants.RayTracingFlags.FISHING_BOBBER;
                }

                StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                    786432);
                entityStorageVertexConsumerProvider.setEntityBuildContext(
                    Constants.Coordinates.WORLD.getValue(), false, 0, 0, 0);
                entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);
                VertexConsumerProvider vertexConsumerProvider = entityStorageVertexConsumerProvider;

                if (entity instanceof DisplayEntity.TextDisplayEntity) {
                    entityStorageVertexConsumerProvider.setEntityBuildContext(
                        Constants.Coordinates.WORLD.getValue(), false,
                        entityPosX, entityPosY, entityPosZ);
                    entityRenderDispatcher.render(entity,
                        0,
                        0,
                        0,
                        yaw,
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

    private static void renderBlockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntity blockEntity,
        float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices) {
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

    public static synchronized Pair<List<StorageVertexConsumerProvider>, EntityRenderDataList> queueBlockEntitiesRebuild(
        BuiltChunkStorage chunks,
        Set<BlockEntity> noCullingBlockEntities,
        Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions,
        BlockEntityRenderDispatcher blockEntityRenderDispatcher,
        float tickDelta) {
        if (!isEntityNativeTransferEnabled()) {
            return new Pair<>(List.of(), new EntityRenderDataList());
        }

        if (Profiler.ENABLED) {
            Profiler.enter("java.EntityProxy.queueBlockEntitiesRebuild");
        }
        try {
            MatrixStack matrixStack = new MatrixStack();
            List<StorageVertexConsumerProvider> entityStorageVertexConsumerProviders = new ArrayList<>();
            EntityRenderDataList entityRenderDataList = new EntityRenderDataList();
            List<StorageVertexConsumerProvider> crumblingStorageVertexConsumerProviders = new ArrayList<>();
            EntityRenderDataList crumblingRenderDataList = new EntityRenderDataList();
            LongSet queuedBlockEntityKeys = new LongOpenHashSet();

            ChunkBuilder.BuiltChunk[] blockEntitySections;
            if (Profiler.ENABLED) Profiler.enter("java.EntityProxy.blockEntitySections");
            try {
                blockEntitySections = BlockEntitySectionCache.sections(chunks);
            } finally {
                if (Profiler.ENABLED) Profiler.exit();
            }
            for (ChunkBuilder.BuiltChunk builtChunk : blockEntitySections) {
                List<BlockEntity> chunkBlockEntities = builtChunk.getData()
                    .getBlockEntities();
                if (!chunkBlockEntities.isEmpty()) {
                    for (BlockEntity blockEntity : chunkBlockEntities) {
                        if (ModSupport.FLYWHEEL && FlywheelSupport.skip(blockEntity)) continue;
                        long entityKey = blockEntityKey(blockEntity);
                        if (!queuedBlockEntityKeys.add(entityKey)) {
                            continue;
                        }

                        BlockPos blockPos = blockEntity.getPos();
                        double entityPosX = blockPos.getX();
                        double entityPosY = blockPos.getY();
                        double entityPosZ = blockPos.getZ();
                        SortedSet<BlockBreakingInfo> breakingInfos = blockBreakingProgressions.get(
                            blockPos.asLong());
                        boolean hasCrumbling = breakingInfos != null && !breakingInfos.isEmpty() &&
                            breakingInfos.last()
                                .getStage() >= 0;

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
                        VertexConsumerProvider vertexConsumerProvider = entityStorageVertexConsumerProvider;

                        matrixStack.push();
                        if (hasCrumbling) {
                            int stage = breakingInfos.last()
                                .getStage();
                            MatrixStack.Entry entry = matrixStack.peek();
                            VertexConsumer vertexConsumer = new OverlayVertexConsumer(
                                crumblingStorageVertexConsumerProvider.getBuffer(
                                    ModelLoader.BLOCK_DESTRUCTION_RENDER_LAYERS.get(stage)), entry,
                                1.0F);
                            vertexConsumerProvider = renderLayer -> {
                                VertexConsumer vertexConsumer2 = entityStorageVertexConsumerProvider.getBuffer(
                                    renderLayer);
                                return renderLayer.hasCrumbling() ?
                                    VertexConsumers.union(vertexConsumer, vertexConsumer2) :
                                    vertexConsumer2;
                            };
                        }

                        renderBlockEntity(blockEntityRenderDispatcher, blockEntity, tickDelta, matrixStack,
                            vertexConsumerProvider);
                        matrixStack.pop();

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
                if (ModSupport.FLYWHEEL && FlywheelSupport.skip(blockEntity)) continue;
                long entityKey = blockEntityKey(blockEntity);
                if (!queuedBlockEntityKeys.add(entityKey)) {
                    continue;
                }

                BlockPos blockPos = blockEntity.getPos();
                double entityPosX = blockPos.getX();
                double entityPosY = blockPos.getY();
                double entityPosZ = blockPos.getZ();

                StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                    786432);
                entityStorageVertexConsumerProvider.setEntityBuildContext(
                    Constants.Coordinates.WORLD.getValue(), false, 0, 0, 0);
                entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);

                matrixStack.push();
                renderBlockEntity(blockEntityRenderDispatcher, blockEntity, tickDelta, matrixStack,
                    entityStorageVertexConsumerProvider);
                matrixStack.pop();

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
            return new Pair<>(crumblingStorageVertexConsumerProviders, crumblingRenderDataList);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static void queueCrumblingRebuild(Camera camera,
        Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions,
        BlockRenderManager blockRenderManager,
        ClientWorld world,
        List<StorageVertexConsumerProvider> crumblingStorageVertexConsumerProviders,
        EntityRenderDataList crumblingRenderDataList) {
        if (!isEntityNativeTransferEnabled()) {
            return;
        }

        if (Profiler.ENABLED) {
            Profiler.enter("java.EntityProxy.queueCrumblingRebuild");
        }
        try {
            MatrixStack matrixStack = new MatrixStack();
            List<StorageVertexConsumerProvider> blockCrumblingStorageVertexConsumerProviders = new ArrayList<>();
            EntityRenderDataList blockCrumblingRenderDataList = new EntityRenderDataList();

            Vec3d cameraPos = camera.getPos();
            double cameraX = cameraPos.getX();
            double cameraY = cameraPos.getY();
            double cameraZ = cameraPos.getZ();

            for (Long2ObjectMap.Entry<SortedSet<BlockBreakingInfo>> blockBreakingProgression :
                blockBreakingProgressions.long2ObjectEntrySet()) {
                BlockPos blockPos = BlockPos.fromLong(blockBreakingProgression.getLongKey());
                double entityPosX = blockPos.getX();
                double entityPosY = blockPos.getY();
                double entityPosZ = blockPos.getZ();

                if (!(blockPos.getSquaredDistanceFromCenter(cameraX, cameraY, cameraZ) > 1024.0)) {
                    SortedSet<BlockBreakingInfo> breakingInfos = blockBreakingProgression.getValue();
                    if (breakingInfos != null && !breakingInfos.isEmpty()) {
                        int
                            stage =
                            breakingInfos.last()
                                .getStage();

                        StorageVertexConsumerProvider blockCrumblingStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
                            786432);
                        blockCrumblingStorageVertexConsumerProvider.setEntityBuildContext(
                            Constants.Coordinates.WORLD.getValue(), true, 0, 0, 0);
                        blockCrumblingStorageVertexConsumerProviders.add(
                            blockCrumblingStorageVertexConsumerProvider);

                        matrixStack.push();
                        MatrixStack.Entry entry = matrixStack.peek();
                        VertexConsumer
                            vertexConsumer =
                            new OverlayVertexConsumer(
                                blockCrumblingStorageVertexConsumerProvider.getBuffer(
                                    ModelLoader.BLOCK_DESTRUCTION_RENDER_LAYERS.get(
                                        stage)), entry, 1.0F);
                        blockRenderManager.renderDamage(world.getBlockState(blockPos), blockPos, world,
                            matrixStack, vertexConsumer);
                        matrixStack.pop();

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

    public static void queueHandRebuild(BufferBuilderStorage buffers, float tickDelta,
        HeldItemRenderer firstPersonRenderer, float handProjectionScale) {
        if (!isEntityNativeTransferEnabled()) {
            return;
        }

        if (Profiler.ENABLED) {
            Profiler.enter("java.EntityProxy.queueHandRebuild");
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrixStack = new MatrixStack();
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();

        StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
            8192);
        storageVertexConsumerProvider.setEntityBuildContext(
            Constants.Coordinates.CAMERA.getValue(), false, 0, 0, 0);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        matrixStack.push();

        boolean isCameraEntitySleeping = client.getCameraEntity() instanceof LivingEntity
            && ((LivingEntity) client.getCameraEntity()).isSleeping();
        if (client.options.getPerspective()
            .isFirstPerson() && !isCameraEntitySleeping && !client.options.hudHidden &&
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
            .isFirstPerson() && !isCameraEntitySleeping && !client.options.hudHidden &&
            client.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR) {
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

            ParticleManager particleManager = MinecraftClient.getInstance().particleManager;
            IParticleManagerExt particleManagerExt = (IParticleManagerExt) particleManager;
            Map<ParticleTextureSheet, Queue<Particle>> particles = particleManagerExt.radiance$getParticles();

            for (ParticleTextureSheet particleTextureSheet : particleManagerExt.radiance$getTextureSheets()) {
                if (particleTextureSheet == ParticleTextureSheet.CUSTOM
                    || particleTextureSheet == ParticleTextureSheet.NO_RENDER) {
                    continue;
                }

                Queue<Particle> particleQueue = particles.get(particleTextureSheet);
                if (particleQueue == null || particleQueue.isEmpty()) {
                    continue;
                }

                RenderLayer renderLayer = getParticleRenderLayer(particleTextureSheet);
                if (renderLayer == null) {
                    continue;
                }

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
                            renderLayer);

                    try {
                        particle.buildGeometry(vertexConsumer, camera, tickDelta);
                    } catch (Throwable particleRenderError) {
                        CrashReport crashReport = CrashReport.create(particleRenderError, "Rendering Particle");
                        CrashReportSection crashReportSection = crashReport.addElement(
                            "Particle being rendered");
                        crashReportSection.add("Particle", particle);
                        crashReportSection.add("Particle Type", particleTextureSheet);
                        throw new CrashException(crashReport);
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

            Queue<Particle> customParticleQueue = particles.get(ParticleTextureSheet.CUSTOM);
            if (customParticleQueue != null && !customParticleQueue.isEmpty()) {
                CustomParticleRenderer.begin(storageVertexConsumerProvider,
                    TRANSLUCENT_PARTICLE_LAYER);
                try {
                    VertexConsumer fallbackVertexConsumer = CustomParticleRenderer.getFallbackVertexConsumer();
                    for (Particle particle : customParticleQueue) {
                        try {
                            particle.buildGeometry(fallbackVertexConsumer, camera, tickDelta);
                        } catch (Throwable particleRenderError) {
                            CrashReport crashReport = CrashReport.create(particleRenderError,
                                "Rendering Particle");
                            CrashReportSection crashReportSection = crashReport.addElement(
                                "Particle being rendered");
                            crashReportSection.add("Particle", particle::toString);
                            crashReportSection.add("Particle Type", "Custom");
                            throw new CrashException(crashReport);
                        }
                    }
                } finally {
                    List<EntityRenderLayer> capturedLayers = CustomParticleRenderer.end();
                    if (!capturedLayers.isEmpty()) {
                        EntityRenderData entityRenderData = new EntityRenderData(staticEntityKey(2),
                            0, 0, 0, Constants.RayTracingFlags.PARTICLE.getValue(), 0, -1, false);
                        entityRenderData.addAll(capturedLayers);
                        renderDataList.add(entityRenderData);
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

    private static RenderLayer getParticleRenderLayer(ParticleTextureSheet particleTextureSheet) {
        if (particleTextureSheet == ParticleTextureSheet.TERRAIN_SHEET) {
            return TERRAIN_PARTICLE_LAYER;
        }
        if (particleTextureSheet == ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT) {
            return TRANSLUCENT_PARTICLE_LAYER;
        }
        if (particleTextureSheet == ParticleTextureSheet.PARTICLE_SHEET_OPAQUE
            || particleTextureSheet == ParticleTextureSheet.PARTICLE_SHEET_LIT) {
            return OPAQUE_PARTICLE_LAYER;
        }
        return null;
    }

    private static RenderLayer createOpaqueParticleLayer(Identifier texture) {
        return RenderLayer.of("opaque_particle",
            net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_COLOR_LIGHT,
            QUADS,
            1536,
            false,
            false,
            RenderLayer.MultiPhaseParameters.builder()
                .program(PARTICLE_PROGRAM)
                .texture(new RenderPhase.Texture(texture, false, false))
                .lightmap(RenderLayer.ENABLE_LIGHTMAP)
                .writeMaskState(RenderLayer.ALL_MASK)
                .build(false));
    }

    private static RenderLayer createTranslucentParticleLayer(Identifier texture) {
        return RenderLayer.of("translucent_particle",
            net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_COLOR_LIGHT,
            QUADS,
            1536,
            false,
            false,
            RenderLayer.MultiPhaseParameters.builder()
                .program(PARTICLE_PROGRAM)
                .texture(new RenderPhase.Texture(texture, false, false))
                .transparency(RenderLayer.TRANSLUCENT_TRANSPARENCY)
                .target(RenderLayer.PARTICLES_TARGET)
                .lightmap(RenderLayer.ENABLE_LIGHTMAP)
                .writeMaskState(RenderLayer.ALL_MASK)
                .build(false));
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
            MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().draw();
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

    public static boolean captureWorldRenderLayer(RenderLayer layer, BuiltBuffer mesh) {
        if (worldRenderCapture == null) {
            return false;
        }
        String content = layer instanceof RenderLayer.MultiPhase composite
            && composite.phases.depthTest.depthFunctionName.equals("always")
            ? "/world_overlay/through" : "/world_overlay/default";
        worldRenderCapture.add(new EntityRenderLayer(layer, mesh, null, false, content));
        return true;
    }

    public static void queueTargetBlockOutlineRebuild(Camera camera, ClientWorld world) {
        if (!isEntityNativeTransferEnabled()) {
            return;
        }

        if (Profiler.ENABLED) {
            Profiler.enter("java.EntityProxy.queueTargetBlockOutlineRebuild");
        }
        try {
            List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
            EntityRenderDataList renderDataList = new EntityRenderDataList();

            MinecraftClient client = MinecraftClient.getInstance();
            MatrixStack matrixStack = new MatrixStack();

            StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
                0);
            storageVertexConsumerProvider.setEntityBuildContext(
                Constants.Coordinates.WORLD.getValue(), false, 0, 0, 0);
            storageVertexConsumerProviders.add(storageVertexConsumerProvider);

            if (client.crosshairTarget instanceof BlockHitResult blockHitResult) {
                if (blockHitResult.getType() != HitResult.Type.MISS) {
                    BlockPos blockPos = blockHitResult.getBlockPos();
                    BlockState blockState = world.getBlockState(blockPos);
                    if (!blockState.isAir() && world.getWorldBorder()
                        .contains(blockPos)) {
                        VertexConsumer vertexConsumer = storageVertexConsumerProvider.getBuffer(
                            RenderLayer.getLines());
                        int color = ColorHelper.Argb.withAlpha(102, Colors.BLACK);
                        WorldRenderer.drawShapeOutline(matrixStack,
                            vertexConsumer,
                            blockState.getOutlineShape(world, blockPos,
                                ShapeContext.of(camera.getFocusedEntity())),
                            0,
                            0,
                            0,
                            ColorHelper.Argb.getRed(color) / 255.0F,
                            ColorHelper.Argb.getGreen(color) / 255.0F,
                            ColorHelper.Argb.getBlue(color) / 255.0F,
                            ColorHelper.Argb.getAlpha(color) / 255.0F,
                            false);

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
                MinecraftClient.getInstance()
                    .getTextureManager();
        ByteBuffer entityKeyBuffer = null;
        ByteBuffer entityPosXBuffer = null;
        ByteBuffer entityPosYBuffer = null;
        ByteBuffer entityPosZBuffer = null;
        ByteBuffer entityRayTracingFlagBuffer = null;
        ByteBuffer entityPostRenderFlagBuffer = null;
        ByteBuffer entityPrebuiltBlasBuffer = null;
        ByteBuffer entityPostRenderBuffer = null;
        ByteBuffer entityTransformBB = null;
        ByteBuffer entityMaterialBB = null;
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

            int entityCount = entityRenderDataList.getTotalEntityCount();
            boolean shared = entityRenderDataList.stream().anyMatch(data -> data.prebuiltBLAS >= 0);
            entityTransformBB = shared ? MemoryUtil.memCalloc(entityCount * 64) : null;
            entityMaterialBB = shared ? MemoryUtil.memCalloc(entityCount * 48) : null;
            int entityIndex = 0;
            int transferBufferIndex = 0;
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
                    RenderLayer renderLayer = entityRenderLayer.renderLayer;
                    BuiltBuffer vertexBuffer = entityRenderLayer.builtBuffer;

                    Identifier
                        textureIdentifier =
                        ((RenderLayer.MultiPhase) renderLayer).phases.texture.getId()
                            .orElse(MissingSprite.getMissingSpriteId());
                    int
                        geometryTypeID =
                        Constants.GeometryTypes.getGeometryType(renderLayer, entityRenderLayer.reflect)
                            .getValue();
                    int
                        geometryTextureID =
                        textureManager.getTexture(textureIdentifier)
                            .getGlId();
                    float geometryNormalOffset = getGeometryNormalOffset(textureIdentifier);
                    int
                        vertexFormatID =
                        Constants.VertexFormats.getValue(vertexBuffer.getDrawParameters()
                            .format());
                    int
                        indexFormatID =
                        Constants.DrawModes.getValue(vertexBuffer.getDrawParameters()
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
                            vertexBuffer.getBuffer());
                        vertexAddress = vertexBufferInfo.address();
                    }
                    assert vertexBuffer.getDrawParameters()
                        .indexCount() == vertexBuffer.getDrawParameters()
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
                        vertexBuffer.getDrawParameters()
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
                    entityTransformBB == null ? 0 : memAddress(entityTransformBB),
                    entityMaterialBB == null ? 0 : memAddress(entityMaterialBB),
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
            freeDirectBuffer(entityTransformBB);
            freeDirectBuffer(entityMaterialBB);
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
            if (closeAfterBuild) {
                closeUntransferredBuiltBuffers(entityRenderDataList, transferredBuffers);
                closeStorageVertexConsumerProviders(storageVertexConsumerProviders);
            }
        }
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static float getGeometryNormalOffset(Identifier identifier) {
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
                BuiltBuffer vertexBuffer = entityRenderLayer.builtBuffer;
                if (isTransferred(vertexBuffer, transferredBuffers)) {
                    continue;
                }
                vertexBuffer.close();
            }
        }
    }

    private static boolean isTransferred(
        BuiltBuffer builtBuffer,
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

    public static native int updatePrebuiltGeometryNative(int previous, int format, int[] geometryTypes,
        ByteBuffer[] vertices);

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

    private static Identifier getTextureId(RenderLayer renderLayer) {
        if (renderLayer instanceof RenderLayer.MultiPhase multiPhase) {
            return multiPhase.phases.texture.getId()
                .orElse(MissingSprite.getMissingSpriteId());
        }
        return MissingSprite.getMissingSpriteId();
    }

    public record EntityRenderLayer(RenderLayer renderLayer, BuiltBuffer builtBuffer,
                                    StorageVertexConsumerProvider ownerProvider,
                                    boolean reflect, String contentName) {

    }

    public static class EntityRenderData extends ArrayList<EntityRenderLayer> {

        private final long entityKey;
        private final int rayTracingFlag;
        private final int postRenderFlag;
        private final int prebuiltBLAS;
        private final boolean isPostRender;
        public final Matrix4f transform;
        public final Vector4f color;
        public final Vector2f uvOffset;
        public int light;
        public int overlay;
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
            this.transform = prebuiltBLAS >= 0 ? new Matrix4f() : null;
            this.color = prebuiltBLAS >= 0 ? new Vector4f(1) : null;
            this.uvOffset = prebuiltBLAS >= 0 ? new Vector2f() : null;
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
