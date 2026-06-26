package com.radiance.client.blaze3d.scene;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.radiance.client.blaze3d.abi.NativeBridge;
import com.radiance.client.blaze3d.backend.RadianceBackendHandles;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.fog.FogData;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class Blaze3DFrameObserver {
    private static final float DEFAULT_NEAR_PLANE = 0.05F;

    private static long frameIndex;
    private static boolean frameOpen;
    private static final float[] previousView = identity();
    private static final float[] previousProjection = identity();

    private Blaze3DFrameObserver() {
    }

    public static void beginLevelFrame(GameRenderer gameRenderer, DeltaTracker deltaTracker,
        CameraRenderState cameraState, Matrix4fc modelViewMatrix, Vector4f fogColor) {
        if (gameRenderer == null || deltaTracker == null || cameraState == null || modelViewMatrix == null) {
            return;
        }

        if (frameOpen) {
            endLevelFrame();
        }

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        int width = 1;
        int height = 1;
        RenderTarget mainTarget = gameRenderer.mainRenderTarget();
        if (mainTarget != null) {
            width = Math.max(1, mainTarget.width);
            height = Math.max(1, mainTarget.height);
        } else {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.getWindow() != null) {
                width = Math.max(1, minecraft.getWindow().getWidth());
                height = Math.max(1, minecraft.getWindow().getHeight());
            }
        }

        NativeBridge.beginFrameNative(++frameIndex, width, height, partialTick);
        frameOpen = true;

        float[] view = matrix(modelViewMatrix);
        float[] projection = matrix(cameraState.projectionMatrix);
        float[] viewRotation = matrix(cameraState.viewRotationMatrix);
        NativeBridge.setCameraNative(cameraState.pos.x, cameraState.pos.y, cameraState.pos.z, view, projection,
            viewRotation, previousView, previousProjection, DEFAULT_NEAR_PLANE, cameraState.depthFar,
            cameraState.depthFar);

        syncWorld(cameraState, fogColor, partialTick);
        syncLightmap(gameRenderer);

        System.arraycopy(view, 0, previousView, 0, previousView.length);
        System.arraycopy(projection, 0, previousProjection, 0, previousProjection.length);
    }

    public static void endLevelFrame() {
        if (!frameOpen) {
            return;
        }

        NativeBridge.endFrameNative();
        frameOpen = false;
    }

    private static void syncWorld(CameraRenderState cameraState, Vector4f renderFogColor, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft == null ? null : minecraft.level;
        long gameTime = level == null ? 0L : level.getGameTime();
        long dayTime = level == null ? 0L : level.getOverworldClockTime();
        float skyAngle = skyAngle(dayTime, partialTick);
        float rainLevel = level == null ? 0.0F : level.getRainLevel(partialTick);
        float thunderLevel = level == null ? 0.0F : level.getThunderLevel(partialTick);

        FogData fogData = cameraState.fogData;
        Vector4f colorSource = renderFogColor != null ? renderFogColor : fogData.color;
        float[] color = new float[] {colorSource.x, colorSource.y, colorSource.z, colorSource.w};
        NativeBridge.setWorldNative(gameTime, dayTime, skyAngle, color, fogData.environmentalStart,
            fogData.environmentalEnd, cameraState.fogType.ordinal(), rainLevel, thunderLevel);
    }

    private static void syncLightmap(GameRenderer gameRenderer) {
        GpuTextureView lightmap = gameRenderer.levelLightmap();
        NativeBridge.setLightmapNative(RadianceBackendHandles.textureViewHandle(lightmap));
    }

    private static float skyAngle(long dayTime, float partialTick) {
        float dayTicks = (float)Math.floorMod(dayTime, 24000L) + partialTick;
        return dayTicks / 24000.0F;
    }

    private static float[] matrix(Matrix4fc matrix) {
        float[] out = new float[16];
        matrix.get(out);
        return out;
    }

    private static float[] identity() {
        return new float[] {
            1.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 1.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 1.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 1.0F
        };
    }
}
