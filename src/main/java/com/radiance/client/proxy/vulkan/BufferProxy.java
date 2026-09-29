package com.radiance.client.proxy.vulkan;

import com.radiance.client.vista.Vista;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memSet;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.constant.Constants;
import com.radiance.client.profiler.Profiler;
import com.radiance.client.render.BuiltBuffer;
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
        BuiltBuffer builtBuffer) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.BufferProxy.createAndUploadVertexIndexBuffer");
        }
        try {
            BuiltBuffer.DrawParameters drawParameters = builtBuffer.getDrawParameters();
            assert builtBuffer.getDrawParameters().mode() == VertexFormat.Mode.QUADS;

            int vertexSize = drawParameters.vertexCount() * drawParameters.format().getVertexSize();
            int indexSize = drawParameters.indexCount() * drawParameters.indexType().bytes;
            BufferInfo vertexBuffer = getBufferInfo(builtBuffer.getBuffer());
            ByteBuffer sortedIndexBuffer = builtBuffer.getSortedBuffer();
            BufferInfo sortedIndex = sortedIndexBuffer == null ? null : getBufferInfo(sortedIndexBuffer);
            assert vertexBuffer.size == vertexSize;
            assert sortedIndex == null || sortedIndex.size == indexSize;

            long packedIds = createAndUploadVertexIndexBufferNative(vertexBuffer.addr, vertexSize,
                sortedIndex == null ? 0L : sortedIndex.addr, indexSize,
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

    private static native void updateOverlayPostUniformNative(long ptr);

    public static void updateOverlayPostUniform(float radius) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.BufferProxy.updateOverlayPostUniform");
        }
        try (MemoryStack stack = stackPush()) {
            int size = 96;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            int baseAddr = 0;

            Matrix4f projectionMatrix = new Matrix4f();
            projectionMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            for (int i = 0; i < 2; i++) {
                baseAddr += Float.BYTES;
            }

            for (int i = 0; i < 2; i++) {
                baseAddr += Float.BYTES;
            }

            float[] blurDir = {1.0f, 1.0f};
            for (int i = 0; i < 2; i++) {
                bb.putFloat(baseAddr, blurDir[i]);
                baseAddr += Float.BYTES;
            }

            bb.putFloat(baseAddr, radius);
            baseAddr += Float.BYTES;

            float radiusMultiplier = 1.0f;
            bb.putFloat(baseAddr, radiusMultiplier);

            updateOverlayPostUniformNative(addr);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static native void updateWorldUniformNative(long ptr);

    public static void updateWorldUniform(Camera camera, Matrix4f viewMatrix,
        Matrix4f effectedViewMatrix, Matrix4f projectionMatrix, int overlayTextureID,
        float fogStart, float fogEnd, float[] fogColor, int fogShape, ClientLevel world,
        int endSkyTextureID, int endPortalTextureID, int lightMapTextureID) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.BufferProxy.updateWorldUniform");
        }
        try (MemoryStack stack = stackPush()) {
            int size = 624;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            int baseAddr = 0;

            viewMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            effectedViewMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            projectionMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            baseAddr += Float.BYTES * 16 * 3;
            baseAddr += Float.BYTES * 2;

            float gameTime = RenderSystem.getShaderGameTime();
            bb.putFloat(baseAddr, gameTime);
            baseAddr += Float.BYTES;

            baseAddr += Integer.BYTES;

            RenderStateShard.setupGlintTexturing(0.16F);
            Matrix4f textureMat = RenderSystem.getTextureMatrix();
            textureMat.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;
            RenderSystem.resetTextureMatrix();

            bb.putInt(baseAddr, overlayTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, camera.isDetached() ? 0 : 1);
            baseAddr += Integer.BYTES;
            bb.putFloat(baseAddr, fogStart);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fogEnd);
            baseAddr += Float.BYTES;

            bb.putFloat(baseAddr, fogColor[0]);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fogColor[1]);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fogColor[2]);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fogColor[3]);
            baseAddr += Float.BYTES;

            bb.putInt(baseAddr, fogShape);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, world.effects().skyType().ordinal());
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, world.getMinBuildHeight());
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, world.getMinBuildHeight() + world.getHeight());
            baseAddr += Integer.BYTES;

            baseAddr += Double.BYTES;
            baseAddr += Double.BYTES;
            baseAddr += Double.BYTES;
            baseAddr += Double.BYTES;
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES;

            bb.putInt(baseAddr, endSkyTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, endPortalTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, lightMapTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr,
                Math.max(16, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16));
            baseAddr += Integer.BYTES;

            bb.putInt(baseAddr, Vista.distanceBlocks());
            bb.putInt(baseAddr + 4, 0);
            bb.putInt(baseAddr + 8, 0);
            bb.putInt(baseAddr + 12, Vista.textureId());
            bb.putLong(baseAddr + 16, 0);
            bb.putLong(baseAddr + 24, 0);
            updateWorldUniformNative(addr);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static native void updateSkyUniformNative(long ptr);

    public static void updateSkyUniform(float baseColorR, float baseColorG, float baseColorB,
        float horizontalColorR, float horizontalColorG, float horizontalColorB,
        float horizontalColorA, Vector3f sunDirection, int skyType, boolean sunRisingOrSetting,
        boolean skyDark, boolean hasBlindnessOrDarkness, int submersionType, int moonPhase,
        float rainGradient, int sunTextureID, int moonTextureID) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.BufferProxy.updateSkyUniform");
        }
        try (MemoryStack stack = stackPush()) {
            int size = 80;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            int baseAddr = 0;

            bb.putFloat(baseAddr, baseColorR);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, baseColorG);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, baseColorB);
            baseAddr += Float.BYTES;
            bb.putInt(baseAddr, skyType);
            baseAddr += Integer.BYTES;

            bb.putFloat(baseAddr, horizontalColorR);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, horizontalColorG);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, horizontalColorB);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, horizontalColorA);
            baseAddr += Float.BYTES;

            bb.putFloat(baseAddr, sunDirection.x);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, sunDirection.y);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, sunDirection.z);
            baseAddr += Float.BYTES;
            bb.putInt(baseAddr, sunRisingOrSetting ? 1 : 0);
            baseAddr += Integer.BYTES;

            bb.putInt(baseAddr, skyDark ? 1 : 0);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, hasBlindnessOrDarkness ? 1 : 0);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, submersionType);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, moonPhase);
            baseAddr += Integer.BYTES;
            bb.putFloat(baseAddr, rainGradient);
            baseAddr += Float.BYTES;
            bb.putInt(baseAddr, sunTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, moonTextureID);
            baseAddr += Integer.BYTES;
            var minecraft = Minecraft.getInstance();
            var camera = minecraft.gameRenderer.getMainCamera().getPosition();
            bb.putInt(baseAddr, com.radiance.client.texture.RainExposure.update(
                minecraft.level, camera.x, camera.z));

            updateSkyUniformNative(addr);
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    private static native void updateMappingNative(long ptr);

    public static void updateMapping() {
        if (Profiler.ENABLED) {
            Profiler.enter("java.BufferProxy.updateMapping");
        }
        try (MemoryStack stack = stackPush()) {
            final int elementCount = 4096;
            int size = elementCount * Integer.BYTES * 3;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            memSet(addr, -1, size);
            IntBuffer intView = bb.asIntBuffer();

            writeTextureMappings(intView, elementCount, TextureTracker.GLID2SpecularGLID, 0,
                "Specular");
            writeTextureMappings(intView, elementCount, TextureTracker.GLID2NormalGLID, 1,
                "Normal");
            writeTextureMappings(intView, elementCount, TextureTracker.GLID2FlagGLID, 2,
                "Flag");

            updateMappingNative(addr);
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

    public record BufferInfo(long addr, int size) {

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
