package com.radiance.client.blaze3d.abi;

import java.nio.ByteBuffer;

public final class Blaze3DProxy {

    private Blaze3DProxy() {
    }

    public static native void initFolderPathNative(String folderPath);

    public static native void initRendererNative(String[] glfwLibCandidates, long windowHandle);

    public static native int maxSupportedTextureSizeNative();

    public static native void closeNative();

    public static native void acquireContextNative();

    public static native void submitCommandNative();

    public static native void presentNative();

    public static native long createSurfaceNative(long windowHandle);

    public static native void configureSurfaceNative(long surface, int width, int height, int format, int presentMode);

    public static native boolean isSurfaceSuboptimalNative(long surface);

    public static native void acquireNextTextureNative(long surface);

    public static native void blitSurfaceFromTextureNative(long surface, long commandEncoder, long textureView);

    public static native void presentSurfaceNative(long surface);

    public static native void closeSurfaceNative(long surface);

    public static native long createCommandEncoderNative();

    public static native void submitCommandEncoderNative(long commandEncoder);

    public static native void submitRenderPassNative(long commandEncoder);

    public static native void clearColorTextureNative(long texture, float r, float g, float b, float a);

    public static native void clearColorAndDepthTexturesNative(long colorTexture, float r, float g,
        float b, float a, long depthTexture, double clearDepth);

    public static native void clearColorAndDepthTexturesRegionNative(long colorTexture, float r,
        float g, float b, float a, long depthTexture, double clearDepth, int x, int y, int width,
        int height);

    public static native void clearDepthTextureNative(long depthTexture, double clearDepth);

    public static native void writeToBufferNative(long buffer, long bufferOffset, ByteBuffer data,
        int dataOffset, int size);

    public static native void copyToBufferNative(long source, long sourceOffset, long target,
        long targetOffset, long size);

    public static native void writeToTextureNative(long texture, ByteBuffer source, int mipLevel,
        int depthOrLayer, int destX, int destY, int width, int height, int dataOffset);

    public static native void copyBufferToTextureNative(long sourceBuffer, long sourceOffset,
        int sourceX, int sourceY, int sourceWidth, int sourceHeight, long destinationTexture,
        int destinationX, int destinationY, int copyWidth, int copyHeight, int mipLevel,
        int arrayLayer);

    public static native void copyTextureToBufferNative(long sourceTexture, long destinationBuffer,
        long destinationOffset, int mipLevel, int x, int y, int width, int height);

    public static native void copyTextureToTextureNative(long sourceTexture, long destinationTexture,
        int mipLevel, int destX, int destY, int sourceX, int sourceY, int width, int height);

    public static native long createFenceNative();

    public static native void writeTimestampNative(long queryPool, int index);

    public static native long createSamplerNative(int addressU, int addressV, int minFilter,
        int magFilter, int maxAnisotropy, boolean hasMaxLod, double maxLod);

    public static native void closeSamplerNative(long sampler);

    public static native long createTextureNative(String label, int usage, int format, int width,
        int height, int layers, int mips);

    public static native void closeTextureNative(long texture);

    public static native long createTextureViewNative(long texture, int baseMip, int mipCount);

    public static native void closeTextureViewNative(long textureView);

    public static native long createBufferNative(String label, int usage, long size);

    public static native long createBufferWithDataNative(String label, int usage, ByteBuffer data,
        int offset, int size);

    public static native void closeBufferNative(long buffer);

    public static native long registerPipelineNative(String location, String vertexShader,
        String fragmentShader, int primitiveTopology, int polygonMode, boolean cull, int slot,
        int stride, String[] attributeNames, int[] attributeFormats, int[] attributeOffsets,
        int colorFormat, boolean hasDepth);

    public static native void clearPipelineCacheNative();

    public static native long createRenderPassNative(String label, long colorView,
        boolean hasClearColor, float clearR, float clearG, float clearB, float clearA,
        long depthView, boolean hasClearDepth, double clearDepth, int x, int y, int width,
        int height);

    public static native void pushDebugGroupNative(long renderPass, String label);

    public static native void popDebugGroupNative(long renderPass);

    public static native void setPipelineNative(long renderPass, long pipeline);

    public static native void bindTextureNative(long renderPass, String name, long textureView,
        long sampler);

    public static native void setUniformNative(long renderPass, String name, long buffer,
        long offset, long length);

    public static native void enableScissorNative(long renderPass, int x, int y, int width,
        int height);

    public static native void disableScissorNative(long renderPass);

    public static native void setVertexBufferNative(long renderPass, int slot, long buffer,
        long offset, long length);

    public static native void setIndexBufferNative(long renderPass, long buffer, int indexType);

    public static native void drawIndexedNative(long renderPass, int indexCount, int instanceCount,
        int firstIndex, int baseVertex, int firstInstance);

    public static native void multiDrawIndexedNative(long renderPass, int instanceCount,
        int firstInstance, int drawCount);

    public static native void drawIndexedIndirectNative(long renderPass, long buffer, long offset,
        long length, int drawCount);

    public static native void drawNative(long renderPass, int vertexCount, int instanceCount,
        int firstVertex, int firstInstance);

    public static native void multiDrawNative(long renderPass, int instanceCount, int firstInstance,
        int drawCount);

    public static native void drawIndirectNative(long renderPass, long buffer, long offset,
        long length, int drawCount);

    public static native long createTimestampQueryPoolNative(int size);

    public static native long getTimestampNowNative();

    public static native void closeTimestampQueryPoolNative(long queryPool);

    public static native void closeFenceNative(long fence);

    public static native boolean awaitFenceCompletionNative(long fence, long timeoutNs);

    public static native int registerPbrMaterialNative(String spriteId, String albedoPath,
        String normalPath, String specularPath, String flagPath);

    public static native void clearAtlasNative(long atlasTexture);

    public static native void syncAtlasSpriteNative(long atlasTexture, long atlasTextureView,
        String atlasId, String spriteId, float u0, float v0, float u1, float v1, int pixelX,
        int pixelY, int pixelWidth, int pixelHeight, int materialId);

    public static native void beginFrameNative(long frameIndex, int width, int height,
        float partialTick);

    public static native void setCameraNative(double x, double y, double z, float[] view,
        float[] projection, float[] viewRotation, float[] previousView, float[] previousProjection,
        float nearPlane, float farPlane, float depthFar);

    public static native void setWorldNative(long gameTime, long dayTime, float skyAngle,
        float[] fogColor, float fogStart, float fogEnd, int fogType, float rainLevel,
        float thunderLevel);

    public static native void setLightmapNative(long textureView);

    public static native void endFrameNative();
}
