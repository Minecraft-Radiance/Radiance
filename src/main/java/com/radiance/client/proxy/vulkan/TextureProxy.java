package com.radiance.client.proxy.vulkan;

import com.radiance.client.constant.VulkanConstants;
import net.minecraft.client.texture.NativeImage;

public class TextureProxy {

    public synchronized static native int generateTextureId();

    public synchronized static native void releaseTextureId(int id);

    public synchronized static native void prepareImage(int id, int mipLevels, int width,
        int height, int format);

    public static void prepareImage(int id, int mipLevels, int width, int height,
        VulkanConstants.VkFormat format) {
        prepareImage(id, mipLevels, width, height, format.getValue());
    }

    public synchronized static native void setFilter(int id, int samplingMode, int mipmapMode);

    public synchronized static native void setClamp(int id, int addressMode);

    public synchronized static native void queueUpload(long srcPointer,
        int srcSizeInBytes,
        int srcRowPixels,
        int dstId,
        int srcOffsetX,
        int srcOffsetY,
        int dstOffsetX,
        int dstOffsetY,
        int width,
        int height,
        int level);

    public synchronized static native void setTextureAlphaClass(int id, int alphaClass);

    public static void prepareImage(NativeImage.InternalFormat internalFormat, int id,
        int mipLevels, int width, int height) {
        switch (internalFormat) {
            case RGBA:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_SRGB);
                break;
            case RGB:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8B8_SRGB);
                break;
            case RG:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8_SRGB);
                break;
            case RED:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8_SRGB);
                break;
        }
    }
}
