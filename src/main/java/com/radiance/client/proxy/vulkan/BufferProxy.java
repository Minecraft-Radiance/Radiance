package com.radiance.client.proxy.vulkan;

import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memSet;

import com.radiance.client.texture.TextureTracker;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Map;
//#if MC<260200
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.constant.Constants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
import org.joml.Vector3f;
//#endif
import org.lwjgl.system.MemoryStack;

//#if MC>=260200
//$$ public class BufferProxy {
//$$
//$$     private static native void updateWorldUniformNative(long ptr);
//$$
//$$     public static void updateWorldUniform(long ptr) {
//$$         updateWorldUniformNative(ptr);
//$$     }
//$$
//$$     private static native void updateSkyUniformNative(long ptr);
//$$
//$$     public static void updateSkyUniform(long ptr) {
//$$         updateSkyUniformNative(ptr);
//$$     }
//$$
//$$     private static native void updateMappingNative(long ptr);
//$$
//$$     public static void updateMapping(long ptr) {
//$$         updateMappingNative(ptr);
//$$     }
//$$
//$$     public static void updateMapping() {
//$$         try (MemoryStack stack = stackPush()) {
//$$             final int elementCount = com.radiance.client.texture.EmissionRecorder.MAX_TEXTURES;
//$$             int size = elementCount * Integer.BYTES * 3;
//$$             ByteBuffer bb = stack.malloc(size);
//$$             long addr = memAddress(bb);
//$$             memSet(addr, -1, size);
//$$             IntBuffer intView = bb.asIntBuffer();
//$$
//$$             writeMapping("Specular", TextureTracker.GLID2SpecularGLID, intView, elementCount, 0);
//$$             writeMapping("Normal", TextureTracker.GLID2NormalGLID, intView, elementCount, 1);
//$$             writeMapping("Flag", TextureTracker.GLID2FlagGLID, intView, elementCount, 2);
//$$
//$$             updateMapping(addr);
//$$         }
//$$     }
//$$
//$$     private static void writeMapping(String name, Map<Integer, Integer> entries,
//$$         IntBuffer intView, int elementCount, int componentOffset) {
//$$         for (Map.Entry<Integer, Integer> entry : entries.entrySet()) {
//$$             int sourceID = entry.getKey();
//$$             int targetID = entry.getValue();
//$$             if (sourceID < 0 || sourceID >= elementCount) {
//$$                 throw new RuntimeException(name + " mapping sourceID " + sourceID
//$$                     + " out of index [0, " + (elementCount - 1) + "]");
//$$             }
//$$             intView.put(sourceID * 3 + componentOffset, targetID);
//$$         }
//$$     }
//$$ }
//#else
public class BufferProxy {

    private static final int WORLD_UBO_SIZE = 896;

    private static native int allocateBufferNative();

    public static int allocateBuffer() {
        return allocateBufferNative();
    }

    private static native void initializeBufferNative(int id, int size, int usageFlags);

    public static void initializeBuffer(int id, int size, int usageFlags) {
        initializeBufferNative(id, size, usageFlags);
    }

    private static native void buildIndexBufferNative(int id, int type, int drawMode, int vertexCount,
        int expectedIndexCount);

    public static void buildIndexBuffer(int id, int type, int drawMode, int vertexCount,
        int expectedIndexCount) {
        buildIndexBufferNative(id, type, drawMode, vertexCount, expectedIndexCount);
    }

    private static native void queueUploadNative(long ptr, int dstId);

    public static void queueUpload(long ptr, int dstId) {
        queueUploadNative(ptr, dstId);
    }

    public static BufferInfo getBufferInfo(ByteBuffer buf) {
        ByteBuffer b = buf.slice();

        assert b.isDirect();

        long addr = memAddress(b);
        int size = b.remaining();
        return new BufferInfo(buf, addr, size);
    }

    private static void queueUpload(ByteBuffer buf, int expectedSize, int dstId, int usageFlags) {
        BufferInfo bufferInfo = getBufferInfo(buf);
        assert bufferInfo.size == expectedSize;
        queueUpload(bufferInfo.addr, dstId);
    }

    private static native void performQueuedUploadNative();

    public static void performQueuedUpload() {
        performQueuedUploadNative();
    }

    public static VertexIndexBufferHandle createAndUploadVertexIndexBuffer(
        BuiltBuffer builtBuffer) {
        BuiltBuffer.DrawParameters drawParameters = builtBuffer.getDrawParameters();
        assert builtBuffer.getDrawParameters().mode() == VertexFormat.DrawMode.QUADS;

        int vertexSize = drawParameters.vertexCount() * drawParameters.format().getVertexSizeByte();
        int vertexId = allocateBuffer();
        initializeBuffer(vertexId, vertexSize, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.getValue());
        queueUpload(builtBuffer.getBuffer(), vertexSize, vertexId,
            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.getValue());

        int indexSize = drawParameters.indexCount() * drawParameters.indexType().size;
        int indexId = allocateBuffer();
        initializeBuffer(indexId, indexSize, VK_BUFFER_USAGE_INDEX_BUFFER_BIT.getValue());
        if (builtBuffer.getSortedBuffer() != null) {
            queueUpload(builtBuffer.getSortedBuffer(), indexSize, indexId,
                VK_BUFFER_USAGE_INDEX_BUFFER_BIT.getValue());
        } else {
            int type = Constants.IndexTypes.getValue(drawParameters.indexType());
            int drawMode = Constants.DrawModes.getValue(drawParameters.mode());
            buildIndexBuffer(indexId, type, drawMode, drawParameters.vertexCount(),
                drawParameters.indexCount());
        }

        return new VertexIndexBufferHandle(vertexId, indexId);
    }

    public static native void updateOverlayPostUniform(long ptr);

    public static void updateOverlayPostUniform(float radius) {
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

            updateOverlayPostUniform(addr);
        }
    }

    private static native void updateWorldUniformNative(long ptr);

    public static void updateWorldUniform(long ptr) {
        updateWorldUniformNative(ptr);
    }

    public static void updateWorldUniform(Camera camera, Matrix4f viewMatrix,
        Matrix4f effectedViewMatrix, Matrix4f projectionMatrix, int overlayTextureID, Fog fog,
        ClientWorld world, int endSkyTextureID, int endPortalTextureID, int lightMapTextureID) {
        try (MemoryStack stack = stackPush()) {
            int size = WORLD_UBO_SIZE;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            memSet(addr, 0, size);
            int baseAddr = 0;

            viewMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            effectedViewMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            projectionMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            baseAddr += Float.BYTES * 16 * 3; // skip the inverse
            baseAddr += Float.BYTES * 2; // skip the jitter

            float gameTime = RenderSystem.getShaderGameTime();
            bb.putFloat(baseAddr, gameTime);
            baseAddr += Float.BYTES;

            baseAddr += Integer.BYTES; // skip seed

            RenderPhase.setupGlintTexturing(0.16F);
            Matrix4f textureMat = RenderSystem.getTextureMatrix();
            textureMat.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;
            RenderSystem.resetTextureMatrix();

            bb.putInt(baseAddr, overlayTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, camera.isThirdPerson() ? 0 : 1);
            baseAddr += Integer.BYTES;
            bb.putFloat(baseAddr, fog.start());
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fog.end());
            baseAddr += Float.BYTES;

            bb.putFloat(baseAddr, fog.red());
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fog.green());
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fog.blue());
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fog.alpha());
            baseAddr += Float.BYTES;

            bb.putInt(baseAddr, fog.shape().getId());
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, world.getDimensionEffects().getSkyType().ordinal());
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES;

            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Integer.BYTES; // chunkGridInfo
            baseAddr += Integer.BYTES; // chunkGridInfo
            baseAddr += Integer.BYTES; // chunkGridInfo
            baseAddr += Integer.BYTES; // chunkGridInfo
            baseAddr += Integer.BYTES; // chunkStorageSectionPos
            baseAddr += Integer.BYTES; // chunkStorageSectionPos
            baseAddr += Integer.BYTES; // chunkStorageSectionPos
            baseAddr += Integer.BYTES; // chunkStorageSectionPos

            bb.putInt(baseAddr, endSkyTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, endPortalTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, lightMapTextureID);
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES; // pad4
            baseAddr += Integer.BYTES; // pad5

            updateWorldUniform(addr);
        }
    }

    private static native void updateSkyUniformNative(long ptr);

    public static void updateSkyUniform(long ptr) {
        updateSkyUniformNative(ptr);
    }

    public static void updateSkyUniform(float baseColorR, float baseColorG, float baseColorB,
        float horizonColorR, float horizonColorG, float horizonColorB, float horizonColorA,
        Vector3f sunDirection, int skyType, boolean sunRisingOrSetting, boolean skyDark,
        boolean hasBlindnessOrDarkness, int submersionType, int moonPhase, float rainGradient,
        int sunTextureID, int moonTextureID) {
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

            bb.putFloat(baseAddr, horizonColorR);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, horizonColorG);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, horizonColorB);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, horizonColorA);
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
            bb.putInt(baseAddr, 0);

            updateSkyUniform(addr);
        }
    }

    private static native void updateMappingNative(long ptr);

    public static void updateMapping(long ptr) {
        updateMappingNative(ptr);
    }

    public static void updateMapping() {
        try (MemoryStack stack = stackPush()) {
            final int elementCount = 4096;
            int size = elementCount * Integer.BYTES * 3;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            memSet(addr, -1, size);
            IntBuffer intView = bb.asIntBuffer();

            for (Map.Entry<Integer, Integer> specularEntry : TextureTracker.GLID2SpecularGLID.entrySet()) {
                int sourceID = specularEntry.getKey();
                int targetID = specularEntry.getValue();
                if (sourceID >= 0 && sourceID < elementCount) {
                    intView.put(sourceID * 3, targetID);
                } else {
                    throw new RuntimeException(
                        "Specular mapping sourceID " + sourceID + " out of index [0, " + (
                            elementCount - 1) + "]");
                }
            }

            for (Map.Entry<Integer, Integer> normalEntry : TextureTracker.GLID2NormalGLID.entrySet()) {
                int sourceID = normalEntry.getKey();
                int targetID = normalEntry.getValue();
                if (sourceID >= 0 && sourceID < elementCount) {
                    intView.put(sourceID * 3 + 1, targetID);
                } else {
                    throw new RuntimeException(
                        "Normal mapping sourceID " + sourceID + " out of index [0, " + (elementCount
                            - 1) + "]");
                }
            }

            for (Map.Entry<Integer, Integer> flagEntry : TextureTracker.GLID2FlagGLID.entrySet()) {
                int sourceID = flagEntry.getKey();
                int targetID = flagEntry.getValue();
                if (sourceID >= 0 && sourceID < elementCount) {
                    intView.put(sourceID * 3 + 2, targetID);
                } else {
                    throw new RuntimeException(
                        "Flag mapping sourceID " + sourceID + " out of index [0, " + (elementCount
                            - 1) + "]");
                }
            }

            updateMapping(addr);
        }
    }

    public static void updateEmission() {
        // Emission tiles are uploaded immediately through TextureProxy during texture upload.
    }

    public record BufferInfo(ByteBuffer buf, long addr, int size) {

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
//#endif
