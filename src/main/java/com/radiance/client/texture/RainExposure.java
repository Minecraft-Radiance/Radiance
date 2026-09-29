package com.radiance.client.texture;

import com.mojang.blaze3d.platform.TextureUtil;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.ui.UiBackendBuildConfig;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.Heightmap;
import org.lwjgl.system.MemoryUtil;

public final class RainExposure {
    private static final int TILES = 32;
    private static final int SIZE = TILES * 16;
    private static final WorldChunk[] chunks = new WorldChunk[TILES * TILES];
    private static final int[] tileX = new int[chunks.length];
    private static final int[] tileZ = new int[chunks.length];
    private static final BitSet dirty = new BitSet(chunks.length);
    private static ClientWorld level;
    private static int texture;
    private static int centerX = Integer.MIN_VALUE, centerZ = Integer.MIN_VALUE;
    private static int scan;
    private static float wetness;
    private static long lastTime;

    private RainExposure() {}

    public static void changed(WorldChunk chunk) {
        if (chunk.getWorld() != level) return;
        int slot = (chunk.getPos().z & 31) * TILES + (chunk.getPos().x & 31);
        if (chunks[slot] == chunk) dirty.set(slot);
    }

    public static void setWorld(ClientWorld world) {
        if (level == world) return;
        if (texture != 0) {
            TextureProxy.releaseTexture(texture);
            if (UiBackendBuildConfig.OPENGL_UI) TextureUtil.releaseTextureId(texture);
        }
        level = world;
        texture = 0;
        Arrays.fill(chunks, null);
        Arrays.fill(tileX, Integer.MIN_VALUE);
        dirty.clear();
        centerX = Integer.MIN_VALUE;
        wetness = 0;
        lastTime = System.nanoTime();
    }

    public static int update(ClientWorld world, double cameraX, double cameraZ) {
        setWorld(world);
        if (world == null || !world.getDimension().hasSkyLight()) return 0;
        long now = System.nanoTime();
        float dt = Math.min((now - lastTime) * 1e-9f, 0.1f);
        lastTime = now;
        if (!MinecraftClient.getInstance().isPaused()) {
            float rain = world.getRainGradient(1.0f);
            wetness += (rain - wetness) * (1.0f - (float) Math.exp(-dt / (rain > wetness ? 2.0f : 25.0f)));
            if (wetness < 0.0001f) wetness = 0;
        }
        if (wetness == 0) return 0;
        if (texture == 0) {
            texture = UiBackendBuildConfig.OPENGL_UI ? TextureUtil.generateTextureId() : TextureProxy.generateTextureId();
            if (texture > 65535) throw new IllegalStateException("Rain texture ID exceeds packed uniform range");
            TextureProxy.prepareClearedImage(texture, 1, SIZE, SIZE,
                VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM, 0, 0, 0, 0, 0);
            TextureProxy.setFilter(texture, 0, 0);
        }
        int cx = Math.floorDiv((int) Math.floor(cameraX), 16);
        int cz = Math.floorDiv((int) Math.floor(cameraZ), 16);
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            ByteBuffer pixels = stack.calloc(16 * 16 * 4);
            if (cx != centerX || cz != centerZ) {
                boolean clearAll = centerX == Integer.MIN_VALUE || Math.abs((long) cx - centerX) >= TILES
                    || Math.abs((long) cz - centerZ) >= TILES;
                if (clearAll) TextureProxy.queueTextureClear(texture, 0, 0, 0, 0,
                    TextureTracker.captureTarget(texture).generation());
                centerX = cx;
                centerZ = cz;
                for (int z = cz - 16; z < cz + 16; z++) for (int x = cx - 16; x < cx + 16; x++) {
                    int slot = (z & 31) * TILES + (x & 31);
                    if (tileX[slot] != x || tileZ[slot] != z) {
                        tileX[slot] = x;
                        tileZ[slot] = z;
                        chunks[slot] = null;
                        dirty.set(slot);
                        if (!clearAll) upload(slot, pixels);
                    }
                }
            }
            for (int i = 0; i < 32; i++) {
                int slot = scan++ & (chunks.length - 1);
                WorldChunk chunk = world.getChunkManager().getChunk(tileX[slot], tileZ[slot], ChunkStatus.FULL, false);
                if (chunks[slot] != chunk) { chunks[slot] = chunk; dirty.set(slot); }
            }
            int remaining = 16;
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (int slot = dirty.nextSetBit(0); slot >= 0 && remaining-- > 0; slot = dirty.nextSetBit(slot + 1)) {
                dirty.clear(slot);
                WorldChunk chunk = world.getChunkManager().getChunk(tileX[slot], tileZ[slot], ChunkStatus.FULL, false);
                chunks[slot] = chunk;
                MemoryUtil.memSet(MemoryUtil.memAddress(pixels), 0, pixels.capacity());
                if (chunk != null) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    int y = chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING, x, z);
                    pos.set(tileX[slot] * 16 + x, y + 1, tileZ[slot] * 16 + z);
                    int height = Math.clamp(y + 32768, 0, 65535);
                    int offset = (z * 16 + x) * 4;
                    pixels.put(offset, (byte) height);
                    pixels.put(offset + 1, (byte) (height >>> 8));
                    pixels.put(offset + 2, (byte) (world.getBiome(pos).value().getPrecipitation(pos) == Biome.Precipitation.RAIN ? 255 : 0));
                    pixels.put(offset + 3, (byte) 255);
                }
                upload(slot, pixels);
            }
        }
        return texture | (Math.round(wetness * 65535.0f) << 16);
    }

    private static void upload(int slot, ByteBuffer pixels) {
        TextureProxy.queueUploadRegion(new TextureProxy.TextureUploadRegion(MemoryUtil.memAddress(pixels),
            pixels.capacity(), 16, texture, 0, 0, (slot & 31) * 16, (slot / TILES) * 16,
            16, 16, 0, TextureProxy.UPLOAD_KIND_FLAG, 4, TextureTracker.captureTarget(texture).generation()));
    }
}
