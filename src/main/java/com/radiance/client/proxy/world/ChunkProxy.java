package com.radiance.client.proxy.world;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.Direction;

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
    public static int builtChunkNum = 0;

    public static void init(int numChunks) {
        builtChunkNum = 0;
    }

    public static void clear() {
        builtChunkNum = 0;
    }

    public static void enqueueRebuild(ChunkBuilder.BuiltChunk chunk) {
    }

    public static void rebuild(Camera camera) {
    }

    public static void waitImportantChunkRebuild() {
    }

    public static boolean isChunkReady(long index) {
        return true;
    }

    public static boolean isChunkReady(ChunkBuilder.BuiltChunk builtChunk) {
        return true;
    }
}
