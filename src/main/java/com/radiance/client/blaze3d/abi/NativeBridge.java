package com.radiance.client.blaze3d.abi;

import java.nio.ByteBuffer;

public final class NativeBridge {

    private NativeBridge() {
    }

    public static native void initFolderPathNative(String folderPath);

    public static native void initRendererNative(String[] glfwLibCandidates, long windowHandle);

    public static native int maxSupportedTextureSizeNative();

    public static native void acquireContextNative();

    public static native void submitCommandNative();

    public static native void presentNative();

    public static native void closeNative();

    public static native long createBufferNative(String label, int usage, long size);

    public static native long createBufferWithDataNative(String label, int usage, ByteBuffer data, int offset, int size);

    public static native void writeBufferNative(long buffer, long bufferOffset, ByteBuffer data, int dataOffset, int size);

    public static native void copyBufferNative(long source, long sourceOffset, long target, long targetOffset, long size);

    public static native long createTextureNative(String label, int usage, int format, int width, int height, int layers, int mips);

    public static native long createTextureViewNative(long texture, int baseMip, int mipCount);

    public static native long createSamplerNative(int addressU, int addressV, int minFilter, int magFilter,
        int maxAnisotropy, boolean hasMaxLod, double maxLod);

    public static native long registerPipelineNative(String location, String vertexShader, String fragmentShader,
        int primitiveTopology, int polygonMode, boolean cull, int slot, int stride, String[] attributeNames,
        int[] attributeFormats, int[] attributeOffsets, int colorFormat, boolean hasDepth);

    public static native long createRenderPassNative(String label, long colorView, boolean hasClearColor,
        float clearR, float clearG, float clearB, float clearA, long depthView, boolean hasClearDepth,
        double clearDepth, int x, int y, int width, int height);

    public static native void beginFrameNative(long frameIndex, int width, int height, float partialTick);

    public static native void setCameraNative(double x, double y, double z, float[] view, float[] projection,
        float[] viewRotation, float[] previousView, float[] previousProjection, float nearPlane, float farPlane,
        float depthFar);

    public static native void setWorldNative(long gameTime, long dayTime, float skyAngle, float[] fogColor,
        float fogStart, float fogEnd, int fogType, float rainLevel, float thunderLevel);

    public static native void setLightmapNative(long textureView);

    public static native void endFrameNative();

    public static native int registerPbrMaterialNative(String spriteId, String albedoPath,
        String normalPath, String specularPath, String flagPath);

    public static native void clearAtlasNative(long atlasTexture);

    public static native void syncAtlasSpriteNative(long atlasTexture, long atlasTextureView,
        String atlasId, String spriteId, float u0, float v0, float u1, float v1, int pixelX,
        int pixelY, int pixelWidth, int pixelHeight, int materialId);

    public static native void beginRenderPassNative(long renderPass);

    public static native void endRenderPassNative();

    public static native void setPipelineNative(long pipeline);

    public static native void bindTextureNative(String name, long textureView, long sampler);

    public static native void setUniformNative(String name, long buffer, long offset, long length);

    public static native void setVertexBufferNative(int slot, long buffer, long offset, long length);

    public static native void setIndexBufferNative(long buffer, int indexType);

    public static native boolean drawIndexedNative(int indexCount, int instanceCount, int firstIndex,
        int baseVertex, int firstInstance);
}
