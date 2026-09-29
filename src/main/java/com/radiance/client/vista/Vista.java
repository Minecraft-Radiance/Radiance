package com.radiance.client.vista;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;

public class Vista {
    public interface Source {
        int distanceBlocks();
        int textureId();
        int[] sideMaterials(BlockState state);
        void reload();
        void render(Camera camera, Matrix4f view, Matrix4f projection, float tickDelta);
        void setWorld(ClientWorld world);
    }

    private static volatile Source source;

    public static void setSource(Source value) { source = value; }
    public static boolean isAvailable() { return source != null; }

    public static int distanceBlocks() {
        Source current = source;
        return current == null ? 0 : current.distanceBlocks();
    }

    public static int textureId() {
        Source current = source;
        return current == null ? 0 : current.textureId();
    }

    public static int[] sideMaterials(BlockState state) {
        Source current = source;
        return current == null ? new int[4] : current.sideMaterials(state);
    }

    public static void reload() {
        Source current = source;
        if (current != null) { current.reload(); }
    }

    public static void render(Camera camera, Matrix4f view, Matrix4f projection, float tickDelta) {
        Source current = source;
        if (current != null) { current.render(camera, view, projection, tickDelta); }
    }

    public static void setWorld(ClientWorld world) {
        Source current = source;
        if (current != null) { current.setWorld(world); }
    }
}
