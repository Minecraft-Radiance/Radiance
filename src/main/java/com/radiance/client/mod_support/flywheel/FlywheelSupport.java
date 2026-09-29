package com.radiance.client.mod_support.flywheel;

import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.Map;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.client.world.ClientWorld;
import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.api.backend.RenderContext;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.impl.BackendManagerImpl;
import dev.engine_room.flywheel.impl.FlwConfig;
import dev.engine_room.flywheel.impl.event.RenderContextImpl;
import dev.engine_room.flywheel.lib.backend.SimpleBackend;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.SortedSet;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;

public final class FlywheelSupport {
    private static final LongOpenHashSet BREAKING = new LongOpenHashSet();
    private static final ThreadLocal<Integer> VANILLA_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static Backend backend;
    private static FlywheelEngine engine;

    private static final Map<ClientWorld, Long2ObjectMap<Set<BlockEntity>>> pending = new IdentityHashMap<>();
    private static final Set<BlockEntity> ready = Collections.newSetFromMap(new IdentityHashMap<>());

    public static synchronized void blockReady(BlockEntity block) {
        if (!(block.getWorld() instanceof ClientWorld level) || block.isRemoved()) return;
        pending.computeIfAbsent(level, key -> new Long2ObjectOpenHashMap<>())
            .computeIfAbsent(new ChunkPos(block.getPos()).toLong(),
                key -> Collections.newSetFromMap(new IdentityHashMap<>())).add(block);
        ready.add(block);
    }

    public static synchronized void chunkReady(ClientWorld level, ChunkPos pos) {
        var chunks = pending.get(level);
        if (chunks != null) {
            var blocks = chunks.get(pos.toLong());
            if (blocks != null) ready.addAll(blocks);
        }
        for (BlockEntity block : level.getChunk(pos.x, pos.z).getBlockEntities().values()) blockReady(block);
    }

    public static synchronized void chunkUnloaded(ClientWorld level, ChunkPos pos) {
        var chunks = pending.get(level);
        if (chunks == null) return;
        var blocks = chunks.remove(pos.toLong());
        if (blocks != null) ready.removeAll(blocks);
        if (chunks.isEmpty()) pending.remove(level);
    }

    public static synchronized void blockRemoved(BlockEntity block) {
        ready.remove(block);
        var chunks = pending.get(block.getWorld());
        if (chunks == null) return;
        long key = new ChunkPos(block.getPos()).toLong();
        var blocks = chunks.get(key);
        if (blocks != null && blocks.remove(block) && blocks.isEmpty()) chunks.remove(key);
        if (chunks.isEmpty()) pending.remove(block.getWorld());
    }

    public static synchronized void setWorld(ClientWorld level) {
        pending.keySet().removeIf(world -> world != level);
        ready.removeIf(block -> block.getWorld() != level);
    }

    private static synchronized void registerReadyBlocks(ClientWorld level) {
        for (BlockEntity block : ready.toArray(BlockEntity[]::new)) {
            ready.remove(block);
            if (block.isRemoved() || block.getWorld() != level || VisualizationHelper.getVisualizer(block) == null) {
                blockRemoved(block);
                continue;
            }
            BlockEntity current = level.getBlockEntity(block.getPos());
            if (current != block) {
                if (current != null) blockRemoved(block);
                continue;
            }
            VisualizationHelper.tryAddBlockEntity(block);
            blockRemoved(block);
        }
    }

    public static void initialize() {
        backend = SimpleBackend.builder().engineFactory(level -> {
            engine = new FlywheelEngine();
            return engine;
        }).priority(10000).supported(() -> true).register(Identifier.of("radiance", "entity"));
    }

    public static Backend backend() {
        return Boolean.getBoolean("radiance.flywheel.disabled")
            || FlwConfig.INSTANCE.backend() == BackendManagerImpl.OFF_BACKEND ? BackendManagerImpl.OFF_BACKEND : backend;
    }

    public static boolean active() {
        return backend != null && BackendManager.currentBackend() == backend;
    }

    public static boolean rendered(Object object) {
        return active() && engine != null && engine.rendered(object);
    }

    public static boolean skip(BlockEntity blockEntity) {
        if (!rendered(blockEntity) || breaking(blockEntity)) return false;
        var visualizer = dev.engine_room.flywheel.lib.visualization.VisualizationHelper.getVisualizer(blockEntity);
        return visualizer != null && visualizer.skipVanillaRender(blockEntity);
    }

    public static boolean breaking(BlockEntity blockEntity) {
        return blockEntity != null && BREAKING.contains(blockEntity.getPos().asLong());
    }

    public static void addBreaking(BlockPos pos) { BREAKING.add(pos.asLong()); }

    public static void enterVanilla() { VANILLA_DEPTH.set(VANILLA_DEPTH.get() + 1); }

    public static void exitVanilla() { VANILLA_DEPTH.set(VANILLA_DEPTH.get() - 1); }

    public static boolean vanillaRendering() { return VANILLA_DEPTH.get() != 0; }

    public static void render(WorldRenderer renderer, Matrix4f matrices, Matrix4f projection,
        Camera camera, float partialTick, int worldTicks,
        Long2ObjectMap<SortedSet<BlockBreakingInfo>> breaking) {
        BREAKING.clear();
        breaking.long2ObjectEntrySet().forEach(entry -> {
            if (!entry.getValue().isEmpty() && entry.getValue().last().getStage() >= 0)
                BREAKING.add(entry.getLongKey());
        });
        if (!active()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        RenderContext context = RenderContextImpl.create(renderer, client.world, client.getBufferBuilders(), matrices,
            projection, camera, partialTick);
        VisualizationManager manager = VisualizationManager.get(client.world);
        if (manager == null) return;
        registerReadyBlocks(client.world);
        manager.renderDispatcher().onStartLevelRender(context);
        manager.renderDispatcher().afterEntities(context);
        manager.renderDispatcher().beforeCrumbling(context, breaking);
    }
}
