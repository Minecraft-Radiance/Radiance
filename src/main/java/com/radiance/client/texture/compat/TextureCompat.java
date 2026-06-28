package com.radiance.client.texture.compat;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import java.io.IOException;
import java.io.InputStream;
//#if MC>=260200
//$$ import com.mojang.blaze3d.GpuFormat;
//$$ import com.mojang.blaze3d.platform.NativeImage;
//$$ import net.minecraft.resources.Identifier;
//$$ import net.minecraft.util.ARGB;
//#else
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
//#endif

public final class TextureCompat {

    private TextureCompat() {
    }

    //#if MC>=260200
    //$$ public static Identifier id(String namespace, String path) {
    //$$     return Identifier.fromNamespaceAndPath(namespace, path);
    //$$ }
    //#else
    public static Identifier id(String namespace, String path) {
        return Identifier.of(namespace, path);
    }
    //#endif

    public static String namespace(Object identifier) {
        return ((Identifier) identifier).getNamespace();
    }

    public static String path(Object identifier) {
        return ((Identifier) identifier).getPath();
    }

    public static Identifier withPath(Object identifier, String path) {
        return ((Identifier) identifier).withPath(path);
    }

    //#if MC>=260200
    //$$ public static NativeImage readImage(InputStream inputStream) throws IOException {
    //$$     return NativeImage.read(inputStream);
    //$$ }
    //$$
    //$$ public static NativeImage newLike(Object source, int width, int height) {
    //$$     return new NativeImage(((NativeImage) source).format(), width, height, false);
    //$$ }
    //$$
    //$$ public static int readColor(Object image, int x, int y) {
    //$$     return ((NativeImage) image).getPixel(x, y);
    //$$ }
    //$$
    //$$ public static void writeColor(Object image, int x, int y, int color) {
    //$$     ((NativeImage) image).setPixel(x, y, color);
    //$$ }
    //$$
    //$$ public static int alpha(int color) {
    //$$     return ARGB.alpha(color);
    //$$ }
    //$$
    //$$ public static int argb(int a, int r, int g, int b) {
    //$$     return ARGB.color(a, r, g, b);
    //$$ }
    //$$
    //$$ public static int opaqueAlphaOrZero(Object source) {
    //$$     NativeImage.Format format = ((NativeImage) source).format();
    //$$     return format.hasAlpha() ? 255 << format.alphaOffset() : 0;
    //$$ }
    //$$
    //$$ public static NativeImage copyLike(Object source) {
    //$$     NativeImage image = (NativeImage) source;
    //$$     NativeImage copied = new NativeImage(image.format(), image.getWidth(), image.getHeight(), false);
    //$$     copied.copyFrom(image);
    //$$     return copied;
    //$$ }
    //$$
    //$$ public static int channel(Object format) {
    //$$     if (format instanceof GpuFormat gpuFormat) {
    //$$         return gpuFormat.componentCount();
    //$$     }
    //$$     return switch ((NativeImage.Format) format) {
    //$$         case RGBA -> 4;
    //$$         case RGB -> 3;
    //$$         case LUMINANCE_ALPHA -> 2;
    //$$         case LUMINANCE -> 1;
    //$$     };
    //$$ }
    //$$
    //$$ public static VulkanConstants.VkFormat vkFormat(Object format) {
    //$$     if (format instanceof GpuFormat gpuFormat) {
    //$$         return switch (gpuFormat) {
    //$$             case RGBA8_UNORM -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM;
    //$$             case RGB8_UNORM -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8_UNORM;
    //$$             case RG8_UNORM -> VulkanConstants.VkFormat.VK_FORMAT_R8G8_UNORM;
    //$$             case R8_UNORM -> VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM;
    //$$             default -> throw new IllegalArgumentException("Unsupported texture format: " + gpuFormat);
    //$$         };
    //$$     }
    //$$     return switch ((NativeImage.Format) format) {
    //$$         case RGBA -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM;
    //$$         case RGB -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8_UNORM;
    //$$         case LUMINANCE_ALPHA -> VulkanConstants.VkFormat.VK_FORMAT_R8G8_UNORM;
    //$$         case LUMINANCE -> VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM;
    //$$     };
    //$$ }
    //#else
    public static NativeImage readImage(InputStream inputStream) throws IOException {
        return NativeImage.read(inputStream);
    }

    public static NativeImage newLike(Object source, int width, int height) {
        return new NativeImage(((NativeImage) source).getFormat(), width, height, false);
    }

    public static int readColor(Object image, int x, int y) {
        return ((NativeImage) image).getColorArgb(x, y);
    }

    public static void writeColor(Object image, int x, int y, int color) {
        ((NativeImage) image).setColorArgb(x, y, color);
    }

    public static int alpha(int color) {
        return ColorHelper.getAlpha(color);
    }

    public static int argb(int a, int r, int g, int b) {
        return ColorHelper.getArgb(a, r, g, b);
    }

    public static int opaqueAlphaOrZero(Object source) {
        NativeImage.Format format = ((NativeImage) source).getFormat();
        return format.hasAlpha() ? 255 << format.getAlphaOffset() : 0;
    }

    public static NativeImage copyLike(Object source) {
        NativeImage image = (NativeImage) source;
        NativeImage copied = new NativeImage(image.getFormat(), image.getWidth(),
            image.getHeight(), false);
        copied.copyFrom(image);
        return copied;
    }

    public static int channel(Object format) {
        if (format instanceof NativeImage.Format nativeFormat) {
            return switch (nativeFormat) {
                case RGBA -> 4;
                case RGB -> 3;
                case LUMINANCE_ALPHA -> 2;
                case LUMINANCE -> 1;
            };
        }
        if (format instanceof NativeImage.InternalFormat internalFormat) {
            return switch (internalFormat) {
                case RGBA -> 4;
                case RGB -> 3;
                case RG -> 2;
                case RED -> 1;
                default -> throw new IllegalArgumentException("Unknown internal format: " + format);
            };
        }
        throw new IllegalArgumentException("Unsupported texture format: " + format);
    }

    public static VulkanConstants.VkFormat vkFormat(Object format) {
        if (format instanceof NativeImage.Format nativeFormat) {
            return switch (nativeFormat) {
                case RGBA -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM;
                case RGB -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8_UNORM;
                case LUMINANCE_ALPHA -> VulkanConstants.VkFormat.VK_FORMAT_R8G8_UNORM;
                case LUMINANCE -> VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM;
            };
        }
        if (format instanceof NativeImage.InternalFormat internalFormat) {
            return switch (internalFormat) {
                case RGBA -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM;
                case RGB -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8_UNORM;
                case RG -> VulkanConstants.VkFormat.VK_FORMAT_R8G8_UNORM;
                case RED -> VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM;
            };
        }
        throw new IllegalArgumentException("Unsupported texture format: " + format);
    }
    //#endif

    public static int width(Object image) {
        return ((NativeImage) image).getWidth();
    }

    public static int height(Object image) {
        return ((NativeImage) image).getHeight();
    }

    public static void close(Object image) {
        ((NativeImage) image).close();
    }

    public static void copyFrom(Object target, Object source) {
        ((NativeImage) target).copyFrom((NativeImage) source);
    }

    public static NativeImage mappedCopy(Object source, java.util.function.IntUnaryOperator operator) {
        //#if MC>=260200
        //$$ return ((NativeImage) source).mappedCopy(operator);
        //#else
        return ((NativeImage) source).applyToCopy(operator);
        //#endif
    }

    public static Object format(Object image) {
        //#if MC>=260200
        //$$ return ((NativeImage) image).format();
        //#else
        return ((NativeImage) image).getFormat();
        //#endif
    }

    public static long pointer(Object image) {
        //#if MC>=260200
        //$$ return ((NativeImage) image).getPointer();
        //#else
        return ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt) (Object) image)
            .radiance$getPointer();
        //#endif
    }

    public static int byteSize(Object image) {
        return width(image) * height(image) * channel(format(image));
    }

    public static NativeImage alignTo(Object image, Object source) {
        NativeImage input = (NativeImage) image;
        NativeImage template = (NativeImage) source;
        int targetWidth = template.getWidth();
        int targetHeight = template.getHeight();
        Object targetFormat = format(template);

        if (input.getWidth() == targetWidth && input.getHeight() == targetHeight
            && format(input) == targetFormat) {
            return input;
        }

        NativeImage dest = newLike(template, targetWidth, targetHeight);
        int srcChannels = channel(format(input));
        int destChannels = channel(targetFormat);
        int commonChannels = Math.min(srcChannels, destChannels);
        long srcPtr = pointer(input);
        long destPtr = pointer(dest);

        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int sampleX = x < input.getWidth() ? x : x % input.getWidth();
                int sampleY = y < input.getHeight() ? y : y % input.getHeight();
                long srcPixelPtr = srcPtr + (sampleX + (long) sampleY * input.getWidth())
                    * srcChannels;
                long destPixelPtr = destPtr + (x + (long) y * targetWidth) * destChannels;

                for (int c = 0; c < commonChannels; c++) {
                    org.lwjgl.system.MemoryUtil.memPutByte(destPixelPtr + c,
                        org.lwjgl.system.MemoryUtil.memGetByte(srcPixelPtr + c));
                }
                for (int c = srcChannels; c < destChannels; c++) {
                    org.lwjgl.system.MemoryUtil.memPutByte(destPixelPtr + c, (byte) 0);
                }
            }
        }
        return dest;
    }

    public static Object specular(INativeImageExt ext) {
        return ext.radiance$getSpecularNativeImage();
    }

    public static Object normal(INativeImageExt ext) {
        return ext.radiance$getNormalNativeImage();
    }

    public static Object flag(INativeImageExt ext) {
        return ext.radiance$getFlagNativeImage();
    }
}
