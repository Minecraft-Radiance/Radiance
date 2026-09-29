package com.radiance.client.proxy.vulkan;

import com.radiance.client.vista.Vista;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memSet;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.constant.Constants;
import com.radiance.client.profiler.Profiler;
import com.radiance.client.texture.TextureTracker;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderStateShard;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

public class BufferProxy {

    private static native long createAndUploadVertexIndexBufferNative(long vertexAddress,
        int vertexSizeBytes, long sortedIndexAddress, int indexSizeBytes, int indexType,
        int drawMode, int vertexCount, int indexCount);

    public static BufferInfo getBufferInfo(ByteBuffer sourceBuffer) {
        ByteBuffer slicedBuffer = sourceBuffer.slice();

        assert slicedBuffer.isDirect();

        long bufferAddress = memAddress(slicedBuffer);
        int sizeBytes = slicedBuffer.remaining();
        return new BufferInfo(bufferAddress, sizeBytes);
    }

    private static native void performQueuedUploadNative();

    public static void performQueuedUpload() {
        if (Profiler.ENABLED) {
            Profiler.enter("java.BufferProxy.performQueuedUpload");
        }
        try {
            performQueuedUploadNative();
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    public static VertexIndexBufferHandle createAndUploadVertexIndexBuffer(
        MeshData builtBuffer) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.BufferProxy.createAndUploadVertexIndexBuffer");
        }
        try {
            MeshData.DrawState drawParameters = builtBuffer.drawState();
            assert builtBuffer.drawState().mode() == VertexFormat.Mode.QUADS;

            int vertexSize = drawParameters.vertexCount() * drawParameters.format().getVertexSize();
            int indexSize = drawParameters.indexCount() * drawParameters.indexType().bytes;
            BufferInfo vertexBuffer = getBufferInfo(builtBuffer.vertexBuffer());
            ByteBuffer sortedIndexBuffer = builtBuffer.indexBuffer();
            BufferInfo sortedIndex = sortedIndexBuffer == null ? null : getBufferInfo(sortedIndexBuffer);
            assert vertexBuffer.sizeBytes == vertexSize;
            assert sortedIndex == null || sortedIndex.sizeBytes == indexSize;

            long packedIds = createAndUploadVertexIndexBufferNative(vertexBuffer.address,
                vertexSize, sortedIndex == null ? 0L : sortedIndex.address, indexSize,
                Constants.IndexTypes.getValue(drawParameters.indexType()),
                Constants.DrawModes.getValue(drawParameters.mode()), drawParameters.vertexCount(),
                drawParameters.indexCount());
            return new VertexIndexBufferHandle((int) (packedIds >>> Integer.SIZE), (int) packedIds);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static native void updateOverlayPostUniformNative(long uniformAddress);

    public static void updateOverlayPostUniform(float radius) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.BufferProxy.updateOverlayPostUniform");
        }
        try (MemoryStack stack = stackPush()) {
            int sizeBytes = 96;
            ByteBuffer uniformBuffer = stack.malloc(sizeBytes);
            long uniformAddress = memAddress(uniformBuffer);
            int byteOffset = 0;

            Matrix4f projectionMatrix = new Matrix4f();
            projectionMatrix.get(byteOffset, uniformBuffer);
            byteOffset += Float.BYTES * 16;

            for (int i = 0; i < 2; i++) {
                byteOffset += Float.BYTES;
            }

            for (int i = 0; i < 2; i++) {
                byteOffset += Float.BYTES;
            }

            float[] blurDirection = {1.0f, 1.0f};
            for (int i = 0; i < 2; i++) {
                uniformBuffer.putFloat(byteOffset, blurDirection[i]);
                byteOffset += Float.BYTES;
            }

            uniformBuffer.putFloat(byteOffset, radius);
            byteOffset += Float.BYTES;

            float radiusMultiplier = 1.0f;
            uniformBuffer.putFloat(byteOffset, radiusMultiplier);

            updateOverlayPostUniformNative(uniformAddress);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static native void updateWorldUniformNative(long uniformAddress);

    public static void updateWorldUniform(Camera camera, Matrix4f viewMatrix,
        Matrix4f effectedViewMatrix, Matrix4f projectionMatrix, int overlayTextureID,
        float fogStart, float fogEnd, float[] fogColor, int fogShape, ClientLevel world,
        int endSkyTextureID, int endPortalTextureID, int lightMapTextureID) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.BufferProxy.updateWorldUniform");
        }
        try (MemoryStack stack = stackPush()) {
            int sizeBytes = 624;
            ByteBuffer uniformBuffer = stack.malloc(sizeBytes);
            long uniformAddress = memAddress(uniformBuffer);
            int byteOffset = 0;

            viewMatrix.get(byteOffset, uniformBuffer);
            byteOffset += Float.BYTES * 16;

            effectedViewMatrix.get(byteOffset, uniformBuffer);
            byteOffset += Float.BYTES * 16;

            projectionMatrix.get(byteOffset, uniformBuffer);
            byteOffset += Float.BYTES * 16;

            byteOffset += Float.BYTES * 16 * 3;
            byteOffset += Float.BYTES * 2;

            float gameTime = RenderSystem.getShaderGameTime();
            uniformBuffer.putFloat(byteOffset, gameTime);
            byteOffset += Float.BYTES;

            byteOffset += Integer.BYTES;

            RenderStateShard.setupGlintTexturing(0.16F);
            Matrix4f textureMat = RenderSystem.getTextureMatrix();
            textureMat.get(byteOffset, uniformBuffer);
            byteOffset += Float.BYTES * 16;
            RenderSystem.resetTextureMatrix();

            uniformBuffer.putInt(byteOffset, overlayTextureID);
            byteOffset += Integer.BYTES;
            uniformBuffer.putInt(byteOffset, camera.isDetached() ? 0 : 1);
            byteOffset += Integer.BYTES;
            uniformBuffer.putFloat(byteOffset, fogStart);
            byteOffset += Float.BYTES;
            uniformBuffer.putFloat(byteOffset, fogEnd);
            byteOffset += Float.BYTES;

            uniformBuffer.putFloat(byteOffset, fogColor[0]);
            byteOffset += Float.BYTES;
            uniformBuffer.putFloat(byteOffset, fogColor[1]);
            byteOffset += Float.BYTES;
            uniformBuffer.putFloat(byteOffset, fogColor[2]);
            byteOffset += Float.BYTES;
            uniformBuffer.putFloat(byteOffset, fogColor[3]);
            byteOffset += Float.BYTES;

            uniformBuffer.putInt(byteOffset, fogShape);
            byteOffset += Integer.BYTES;
            uniformBuffer.putInt(byteOffset, world.effects().skyType().ordinal());
            byteOffset += Integer.BYTES;
            uniformBuffer.putInt(byteOffset, world.getMinBuildHeight());
            byteOffset += Integer.BYTES;
            uniformBuffer.putInt(byteOffset, world.getMinBuildHeight() + world.getHeight());
            byteOffset += Integer.BYTES;

            byteOffset += Double.BYTES;
            byteOffset += Double.BYTES;
            byteOffset += Double.BYTES;
            byteOffset += Double.BYTES;
            byteOffset += Integer.BYTES;
            byteOffset += Integer.BYTES;
            byteOffset += Integer.BYTES;
            byteOffset += Integer.BYTES;
            byteOffset += Integer.BYTES;
            byteOffset += Integer.BYTES;
            byteOffset += Integer.BYTES;
            byteOffset += Integer.BYTES;

            uniformBuffer.putInt(byteOffset, endSkyTextureID);
            byteOffset += Integer.BYTES;
            uniformBuffer.putInt(byteOffset, endPortalTextureID);
            byteOffset += Integer.BYTES;
            uniformBuffer.putInt(byteOffset, lightMapTextureID);
            byteOffset += Integer.BYTES;
            uniformBuffer.putInt(byteOffset,
                Math.max(16, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16));
            byteOffset += Integer.BYTES;

            uniformBuffer.putInt(byteOffset, Vista.distanceBlocks());
            uniformBuffer.putInt(byteOffset + 4, 0);
            uniformBuffer.putInt(byteOffset + 8, 0);
            uniformBuffer.putInt(byteOffset + 12, Vista.textureId());
            uniformBuffer.putLong(byteOffset + 16, 0);
            uniformBuffer.putLong(byteOffset + 24, 0);
            updateWorldUniformNative(uniformAddress);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static native void updateSkyUniformNative(long uniformAddress);

    public static void updateSkyUniform(float baseColorR, float baseColorG, float baseColorB,
        float horizonColorR, float horizonColorG, float horizonColorB, float horizonColorA,
        Vector3f sunDirection, int skyType, boolean sunRisingOrSetting, boolean skyDark,
        boolean hasBlindnessOrDarkness, int submersionType, int moonPhase, float rainGradient,
        int sunTextureID, int moonTextureID) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.BufferProxy.updateSkyUniform");
        }
        try (MemoryStack stack = stackPush()) {
            int sizeBytes = 80;
            ByteBuffer uniformBuffer = stack.malloc(sizeBytes);
            long uniformAddress = memAddress(uniformBuffer);
            int byteOffset = 0;

            uniformBuffer.putFloat(byteOffset, baseColorR);
            byteOffset += Float.BYTES;
            uniformBuffer.putFloat(byteOffset, baseColorG);
            byteOffset += Float.BYTES;
            uniformBuffer.putFloat(byteOffset, baseColorB);
            byteOffset += Float.BYTES;
            uniformBuffer.putInt(byteOffset, skyType);
            byteOffset += Integer.BYTES;

            uniformBuffer.putFloat(byteOffset, horizonColorR);
            byteOffset += Float.BYTES;
            uniformBuffer.putFloat(byteOffset, horizonColorG);
            byteOffset += Float.BYTES;
            uniformBuffer.putFloat(byteOffset, horizonColorB);
            byteOffset += Float.BYTES;
            uniformBuffer.putFloat(byteOffset, horizonColorA);
            byteOffset += Float.BYTES;

            uniformBuffer.putFloat(byteOffset, sunDirection.x);
            byteOffset += Float.BYTES;
            uniformBuffer.putFloat(byteOffset, sunDirection.y);
            byteOffset += Float.BYTES;
            uniformBuffer.putFloat(byteOffset, sunDirection.z);
            byteOffset += Float.BYTES;
            uniformBuffer.putInt(byteOffset, sunRisingOrSetting ? 1 : 0);
            byteOffset += Integer.BYTES;

            uniformBuffer.putInt(byteOffset, skyDark ? 1 : 0);
            byteOffset += Integer.BYTES;
            uniformBuffer.putInt(byteOffset, hasBlindnessOrDarkness ? 1 : 0);
            byteOffset += Integer.BYTES;
            uniformBuffer.putInt(byteOffset, submersionType);
            byteOffset += Integer.BYTES;
            uniformBuffer.putInt(byteOffset, moonPhase);
            byteOffset += Integer.BYTES;
            uniformBuffer.putFloat(byteOffset, rainGradient);
            byteOffset += Float.BYTES;
            uniformBuffer.putInt(byteOffset, sunTextureID);
            byteOffset += Integer.BYTES;
            uniformBuffer.putInt(byteOffset, moonTextureID);
            byteOffset += Integer.BYTES;
            var minecraft = Minecraft.getInstance();
            var camera = minecraft.gameRenderer.getMainCamera().getPosition();
            uniformBuffer.putInt(byteOffset, com.radiance.client.texture.RainExposure.update(
                minecraft.level, camera.x, camera.z));

            updateSkyUniformNative(uniformAddress);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static native void updateMappingNative(long mappingAddress);

    public static void updateMapping() {
        if (Profiler.ENABLED) {
            Profiler.enter("java.BufferProxy.updateMapping");
        }
        try (MemoryStack stack = stackPush()) {
            final int elementCount = 4096;
            int sizeBytes = elementCount * Integer.BYTES * 3;
            ByteBuffer mappingBuffer = stack.malloc(sizeBytes);
            long mappingAddress = memAddress(mappingBuffer);
            memSet(mappingAddress, -1, sizeBytes);
            IntBuffer intView = mappingBuffer.asIntBuffer();

            writeTextureMappings(intView, elementCount, TextureTracker.GLID2SpecularGLID, 0,
                "Specular");
            writeTextureMappings(intView, elementCount, TextureTracker.GLID2NormalGLID, 1,
                "Normal");
            writeTextureMappings(intView, elementCount, TextureTracker.GLID2FlagGLID, 2,
                "Flag");

            updateMappingNative(mappingAddress);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static void writeTextureMappings(IntBuffer destination, int elementCount,
        Map<Integer, TextureTracker.TextureBinding> bindings, int component, String name) {
        for (Map.Entry<Integer, TextureTracker.TextureBinding> entry : bindings.entrySet()) {
            int sourceId = entry.getKey();
            TextureTracker.TextureBinding binding = entry.getValue();
            if (!TextureTracker.isCurrent(binding)) {
                bindings.remove(sourceId, binding);
                continue;
            }
            int targetId = binding.auxiliary().id();
            if (sourceId < 0 || sourceId >= elementCount || targetId < 0 || targetId >= elementCount) {
                throw new IllegalStateException(name + " mapping " + sourceId + " -> " + targetId
                    + " out of index [0, " + (elementCount - 1) + "]");
            }
            destination.put(sourceId * 3 + component, targetId);
        }
    }

    public record BufferInfo(long address, int sizeBytes) {

    }

    public static class VertexIndexBufferHandle {

        public int vertexId;
        public int indexId;

        public VertexIndexBufferHandle(int vertexId, int indexId) {
            this.vertexId = vertexId;
            this.indexId = indexId;
        }
    }
}
