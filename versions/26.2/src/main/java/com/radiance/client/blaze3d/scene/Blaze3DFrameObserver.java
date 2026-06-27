package com.radiance.client.blaze3d.scene;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class Blaze3DFrameObserver {
    private Blaze3DFrameObserver() {
    }

    public static void beginLevelFrame(GameRenderer gameRenderer, DeltaTracker deltaTracker,
        CameraRenderState cameraState, Matrix4fc modelViewMatrix, Vector4f fogColor) {
    }

    public static void endLevelFrame() {
    }
}
