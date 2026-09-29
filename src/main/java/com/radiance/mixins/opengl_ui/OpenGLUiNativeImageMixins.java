package com.radiance.mixins.opengl_ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.profiler.Profiler;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.AuxiliaryTextures;
import com.radiance.client.texture.TextureTracker.TextureTarget;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NativeImage.class)
public abstract class OpenGLUiNativeImageMixins implements
    com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt {

    @Shadow
    private long pixels;

    @Final
    @Shadow
    private long size;

    @Final
    @Shadow
    private int width;

    @Final
    @Shadow
    private NativeImage.Format format;

    @Final
    @Shadow
    private int height;

    @Shadow
    public abstract NativeImage mappedCopy(IntUnaryOperator operator);

    @Shadow
    protected abstract void checkAllocated();

    @Shadow
    public abstract int getWidth();

    @Shadow
    public abstract int getHeight();

    @Shadow
    public abstract int getPixelRGBA(int x, int y);

    @Shadow
    public abstract void setPixelRGBA(int x, int y, int color);

    @Shadow
    public abstract void close();

    @Inject(method = "_upload(IIIIIIIZZZZ)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/NativeImage;checkAllocated()V",
            shift = At.Shift.AFTER))
    public void queueNativeUpload(int level, int offsetX, int offsetY, int unpackSkipPixels,
        int unpackSkipRows, int regionWidth, int regionHeight, boolean blur, boolean clamp,
        boolean mipmap, boolean close, CallbackInfo ci) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.NativeImage.queueNativeUploadOpenGLUi");
        }
        try {
            INativeImageExt nativeImageExt = (INativeImageExt) this;
            TextureTarget target = nativeImageExt.radiance$getTarget();
            if (target == null) {
                return;
            }

            TextureProxy.setClamp(target.id(),
                clamp
                    ? VulkanConstants.VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE.getValue()
                    : VulkanConstants.VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT.getValue());
            List<TextureProxy.TextureUploadRegion> auxiliaryRegions =
                AuxiliaryTextures.buildUploadRegions((NativeImage) (Object) this, nativeImageExt,
                    level, offsetX, offsetY, unpackSkipPixels, unpackSkipRows, regionWidth,
                    regionHeight);
            TextureProxy.TextureUploadRegion mainRegion =
                new TextureProxy.TextureUploadRegion(pixels, (int) size, width,
                    target.id(), unpackSkipPixels, unpackSkipRows, offsetX, offsetY,
                    regionWidth, regionHeight, level, TextureProxy.UPLOAD_KIND_MAIN, format.components(),
                    target.generation());
            if (auxiliaryRegions.isEmpty()) {
                TextureProxy.queueUploadRegion(mainRegion);
            } else {
                TextureProxy.queueUploads(mainRegion, auxiliaryRegions);
            }
        } finally {
            if (close) {
                TextureProxy.flushUploadBatch();
            }
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }
    }

    @Inject(method = "close()V", at = @At(value = "HEAD"))
    public void closeAuxiliaryImages(CallbackInfo ci) {
        TextureProxy.flushUploadBatch();
        INativeImageExt nativeImageExt = (INativeImageExt) this;
        NativeImage specularImage = nativeImageExt.radiance$getSpecularNativeImage();
        NativeImage normalImage = nativeImageExt.radiance$getNormalNativeImage();
        NativeImage flagImage = nativeImageExt.radiance$getFlagNativeImage();
        if (specularImage != null) {
            specularImage.close();
        }
        if (normalImage != null) {
            normalImage.close();
        }
        if (flagImage != null) {
            flagImage.close();
        }
    }

    @Override
    public NativeImage radiance$alignTo(NativeImage source) {
        int targetWidth = source.getWidth();
        int targetHeight = source.getHeight();
        NativeImage.Format targetFormat = source.format();

        if (width == targetWidth && height == targetHeight && format == targetFormat) {
            return (NativeImage) (Object) this;
        }

        NativeImage destinationImage = new NativeImage(targetFormat, targetWidth, targetHeight,
            false);
        int sourceChannelCount = this.format.components();
        int destinationChannelCount = targetFormat.components();
        int sharedChannelCount = Math.min(sourceChannelCount, destinationChannelCount);

        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int sampleX = (x < this.width) ? x : (x % this.width);
                int sampleY = (y < this.height) ? y : (y % this.height);
                long sourcePixelPointer =
                    this.pixels + (sampleX + (long) sampleY * this.width) * sourceChannelCount;
                long destinationPixelPointer =
                    ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                        (Object) destinationImage).radiance$getPointer()
                        + (long) (x + (long) y * targetWidth) * destinationChannelCount;

                for (int channelIndex = 0; channelIndex < sharedChannelCount; channelIndex++) {
                    byte channelValue = MemoryUtil.memGetByte(sourcePixelPointer + channelIndex);
                    MemoryUtil.memPutByte(destinationPixelPointer + channelIndex, channelValue);
                }
                for (int channelIndex = sourceChannelCount; channelIndex < destinationChannelCount;
                    channelIndex++) {
                    MemoryUtil.memPutByte(destinationPixelPointer + channelIndex, (byte) 0);
                }
            }
        }
        return destinationImage;
    }

    @Override
    public long radiance$getPointer() {
        return pixels;
    }

    @Override
    public void radiance$loadFromTextureImageWithoutUI(int level, boolean removeAlpha) {
        RenderSystem.assertOnRenderThread();
        this.checkAllocated();
        RendererProxy.takeScreenshot(false, this.width, this.height, this.format.components(),
            this.pixels);
        if (removeAlpha && this.format.hasAlpha()) {
            for (int row = 0; row < this.getHeight(); row++) {
                for (int column = 0; column < this.getWidth(); column++) {
                    this.setPixelRGBA(column, row,
                        this.getPixelRGBA(column, row) | 255 << this.format.alphaOffset());
                }
            }
        }
    }
}
