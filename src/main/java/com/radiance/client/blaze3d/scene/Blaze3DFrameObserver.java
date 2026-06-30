package com.radiance.client.blaze3d.scene;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.radiance.client.blaze3d.abi.Blaze3DProxy;
import com.radiance.client.blaze3d.backend.RadianceTransientMemory;
import com.radiance.client.blaze3d.backend.RadianceTextureHandles;
import com.radiance.client.proxy.vulkan.BufferProxy;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memSet;

public final class Blaze3DFrameObserver {
    private static final int WORLD_UBO_SIZE = 896;
    private static final int SKY_UBO_SIZE = 80;
    private static final int MAT4_BYTES = Float.BYTES * 16;
    private static final Identifier END_SKY_TEXTURE =
        Identifier.withDefaultNamespace("textures/environment/end_sky.png");
    private static final Identifier END_PORTAL_TEXTURE =
        Identifier.withDefaultNamespace("textures/entity/end_portal/end_portal.png");
    private static long frameIndex;
    private static int lastWorldIdentity;
    private static int lastWorldKey;
    private static long worldSerial = 1L;

    private Blaze3DFrameObserver() {
    }

    public static void beginLevelFrame(GameRenderer gameRenderer, DeltaTracker deltaTracker,
        CameraRenderState cameraState, Matrix4fc modelViewMatrix, Vector4f fogColor) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        updateWorldSerial(level);
        float partialTick = deltaTracker == null ? 0.0F : deltaTracker.getGameTimeDeltaPartialTick(false);
        Window window = minecraft.getWindow();
        Blaze3DProxy.beginFrameNative(frameIndex++, window.getWidth(), window.getHeight(), partialTick);

        if (cameraState == null) {
            return;
        }

        Matrix4f view = modelViewMatrix == null ? new Matrix4f(cameraState.viewRotationMatrix) : new Matrix4f(modelViewMatrix);
        Matrix4f projection = new Matrix4f(cameraState.projectionMatrix);
        Matrix4f viewRotation = new Matrix4f(cameraState.viewRotationMatrix);
        float[] viewArray = matrixArray(view);
        float[] projectionArray = matrixArray(projection);
        float[] viewRotationArray = matrixArray(viewRotation);
        Blaze3DProxy.setCameraNative(cameraState.pos.x, cameraState.pos.y, cameraState.pos.z,
            viewArray, projectionArray, viewRotationArray, viewArray, projectionArray,
            0.05F, cameraState.depthFar, cameraState.depthFar);

        long gameTime = level == null ? 0L : level.getGameTime();
        long dayTime = level == null ? 0L : level.getOverworldClockTime();
        float skyAngle = sunAngleFraction(partialTick);
        float[] fogColorArray = new float[] {
            fogColor == null ? cameraState.fogData.color.x : fogColor.x,
            fogColor == null ? cameraState.fogData.color.y : fogColor.y,
            fogColor == null ? cameraState.fogData.color.z : fogColor.z,
            fogColor == null ? cameraState.fogData.color.w : fogColor.w
        };
        Blaze3DProxy.setWorldNative(gameTime, dayTime, skyAngle, fogColorArray,
            terrainFogStart(cameraState.fogData), terrainFogEnd(cameraState.fogData),
            fogTypeId(cameraState.fogType), level == null ? 0.0F : level.getRainLevel(partialTick),
            level == null ? 0.0F : level.getThunderLevel(partialTick));
        Blaze3DProxy.setWorldSerialNative(worldSerial);
        Blaze3DProxy.setLightmapNative(RadianceTextureHandles.textureViewHandle(gameRenderer.levelLightmap()));
        SceneTextureIds.ensureSkyCompatibilityTextures(minecraft.getResourceManager());

        uploadWorldUniform(gameRenderer, level, cameraState, view, projection, fogColorArray);
        uploadSkyUniform(level, partialTick, cameraState);
        BufferProxy.updateMapping();
    }

    public static void endLevelFrame() {
        Blaze3DProxy.endFrameNative();
        RadianceTransientMemory.retireFrame();
    }

    private static void uploadWorldUniform(GameRenderer gameRenderer, ClientLevel level,
        CameraRenderState cameraState, Matrix4f view, Matrix4f projection, float[] fogColor) {
        try (MemoryStack stack = stackPush()) {
            ByteBuffer bb = stack.malloc(WORLD_UBO_SIZE);
            memSet(memAddress(bb), 0, WORLD_UBO_SIZE);
            int offset = 0;

            view.get(offset, bb);
            offset += MAT4_BYTES;
            view.get(offset, bb);
            offset += MAT4_BYTES;
            projection.get(offset, bb);
            offset += MAT4_BYTES;
            offset += MAT4_BYTES * 3;

            offset += Float.BYTES * 2; // cameraJitter, filled by native.
            bb.putFloat(offset, (level == null ? 0L : level.getGameTime()) / 24000.0F);
            offset += Float.BYTES;
            offset += Integer.BYTES; // seed, filled by native.

            new Matrix4f().identity().get(offset, bb);
            offset += MAT4_BYTES;

            bb.putInt(offset,
                RadianceTextureHandles.textureId(gameRenderer.overlayTexture().getTextureView().texture()));
            offset += Integer.BYTES;
            bb.putInt(offset, cameraState.entityRenderState.isPlayer && !cameraState.isPanoramicMode ? 1 : 0);
            offset += Integer.BYTES;
            bb.putFloat(offset, terrainFogStart(cameraState.fogData));
            offset += Float.BYTES;
            bb.putFloat(offset, terrainFogEnd(cameraState.fogData));
            offset += Float.BYTES;

            bb.putFloat(offset, fogColor[0]);
            offset += Float.BYTES;
            bb.putFloat(offset, fogColor[1]);
            offset += Float.BYTES;
            bb.putFloat(offset, fogColor[2]);
            offset += Float.BYTES;
            bb.putFloat(offset, fogColor[3]);
            offset += Float.BYTES;

            bb.putInt(offset, fogTypeId(cameraState.fogType));
            offset += Integer.BYTES;
            bb.putInt(offset, skyType(level));
            offset += Integer.BYTES;
            offset += Integer.BYTES * 2;

            offset += Double.BYTES * 4; // cameraPos, filled by native from Renderer.world().
            offset += Integer.BYTES * 8; // chunk grid/storage, filled by native.

            bb.putInt(offset, SceneTextureIds.textureId(END_SKY_TEXTURE));
            offset += Integer.BYTES;
            bb.putInt(offset, SceneTextureIds.textureId(END_PORTAL_TEXTURE));
            offset += Integer.BYTES;
            bb.putInt(offset, RadianceTextureHandles.textureId(gameRenderer.levelLightmap().texture()));

            BufferProxy.updateWorldUniform(memAddress(bb));
        }
    }

    private static void uploadSkyUniform(ClientLevel level, float partialTick, CameraRenderState cameraState) {
        try (MemoryStack stack = stackPush()) {
            ByteBuffer bb = stack.malloc(SKY_UBO_SIZE);
            memSet(memAddress(bb), 0, SKY_UBO_SIZE);
            int offset = 0;

            int skyColor = level == null ? 0 : cameraStateFromProbeSkyColor(cameraState, partialTick);
            bb.putFloat(offset, ARGB.redFloat(skyColor));
            offset += Float.BYTES;
            bb.putFloat(offset, ARGB.greenFloat(skyColor));
            offset += Float.BYTES;
            bb.putFloat(offset, ARGB.blueFloat(skyColor));
            offset += Float.BYTES;
            bb.putInt(offset, skyType(level));
            offset += Integer.BYTES;

            int horizonColor = level == null ? 0 : cameraStateFromProbeSunriseColor(cameraState, partialTick);
            bb.putFloat(offset, ARGB.redFloat(horizonColor));
            offset += Float.BYTES;
            bb.putFloat(offset, ARGB.greenFloat(horizonColor));
            offset += Float.BYTES;
            bb.putFloat(offset, ARGB.blueFloat(horizonColor));
            offset += Float.BYTES;
            bb.putFloat(offset, ARGB.alphaFloat(horizonColor));
            offset += Float.BYTES;

            Vector3f sunDirection = sunDirection(cameraState, partialTick);
            bb.putFloat(offset, sunDirection.x);
            offset += Float.BYTES;
            bb.putFloat(offset, sunDirection.y);
            offset += Float.BYTES;
            bb.putFloat(offset, sunDirection.z);
            offset += Float.BYTES;
            bb.putInt(offset, ARGB.alpha(horizonColor) > 0 ? 1 : 0);
            offset += Integer.BYTES;

            bb.putInt(offset, isSkyDark(level) ? 1 : 0);
            offset += Integer.BYTES;
            bb.putInt(offset, cameraState.entityRenderState.doesMobEffectBlockSky ? 1 : 0);
            offset += Integer.BYTES;
            bb.putInt(offset, fogTypeId(cameraState.fogType));
            offset += Integer.BYTES;
            bb.putInt(offset, moonPhaseIndex(cameraState, partialTick));
            offset += Integer.BYTES;
            bb.putFloat(offset, level == null ? 0.0F : level.getRainLevel(partialTick));
            offset += Float.BYTES;
            bb.putInt(offset, SceneTextureIds.sunTextureId());
            offset += Integer.BYTES;
            bb.putInt(offset, SceneTextureIds.moonTextureId());

            BufferProxy.updateSkyUniform(memAddress(bb));
        }
    }

    private static float terrainFogStart(FogData fogData) {
        return Math.max(fogData.environmentalStart, fogData.renderDistanceStart);
    }

    private static float terrainFogEnd(FogData fogData) {
        return Math.min(fogData.environmentalEnd, fogData.renderDistanceEnd);
    }

    private static int fogTypeId(FogType fogType) {
        return fogType == null ? FogType.NONE.ordinal() : fogType.ordinal();
    }

    private static int skyType(ClientLevel level) {
        if (level == null) {
            return 1;
        }
        DimensionType.Skybox skybox = level.dimensionType().skybox();
        if (skybox == DimensionType.Skybox.NONE) {
            return 0;
        }
        if (skybox == DimensionType.Skybox.OVERWORLD) {
            return 1;
        }
        return 2;
    }

    private static boolean isSkyDark(ClientLevel level) {
        return level != null && level.getSkyDarken() > 4;
    }

    private static void updateWorldSerial(ClientLevel level) {
        int identity = level == null ? 0 : System.identityHashCode(level);
        int key = worldKey(level);
        if (identity != lastWorldIdentity || key != lastWorldKey) {
            worldSerial++;
            lastWorldIdentity = identity;
            lastWorldKey = key;
        }
    }

    private static int worldKey(ClientLevel level) {
        if (level == null) {
            return 0;
        }
        int key = System.identityHashCode(level.dimensionType());
        key = 31 * key + skyType(level);
        return key;
    }

    private static int cameraStateFromProbeSkyColor(CameraRenderState cameraState, float partialTick) {
        return Minecraft.getInstance().gameRenderer.mainCamera().attributeProbe()
            .getValue(EnvironmentAttributes.SKY_COLOR, partialTick);
    }

    private static int cameraStateFromProbeSunriseColor(CameraRenderState cameraState, float partialTick) {
        return Minecraft.getInstance().gameRenderer.mainCamera().attributeProbe()
            .getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, partialTick);
    }

    private static Vector3f sunDirection(CameraRenderState cameraState, float partialTick) {
        float angle = sunAngleFraction(partialTick) * (float) (Math.PI * 2.0);
        return new Matrix4f()
            .rotateY((float) Math.toRadians(-90.0))
            .rotateX(angle)
            .transformPosition(0.0F, 1.0F, 0.0F, new Vector3f())
            .normalize();
    }

    private static float sunAngleFraction(float partialTick) {
        return Minecraft.getInstance().gameRenderer.mainCamera().attributeProbe()
            .getValue(EnvironmentAttributes.SUN_ANGLE, partialTick) / 360.0F;
    }

    private static int moonPhaseIndex(CameraRenderState cameraState, float partialTick) {
        MoonPhase moonPhase = Minecraft.getInstance().gameRenderer.mainCamera().attributeProbe()
            .getValue(EnvironmentAttributes.MOON_PHASE, partialTick);
        return moonPhase == null ? 0 : moonPhase.index();
    }

    private static float[] matrixArray(Matrix4fc matrix) {
        float[] values = new float[16];
        matrix.get(values);
        return values;
    }
}
