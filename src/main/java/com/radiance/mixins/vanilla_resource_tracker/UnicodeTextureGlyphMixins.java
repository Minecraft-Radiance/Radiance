package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.profiler.Profiler;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.ui.UiBackendBuildConfig;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import java.nio.IntBuffer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.font.GlyphRenderer;
import net.minecraft.client.font.RenderableGlyph;
import net.minecraft.client.font.UnihexFont;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(UnihexFont.UnicodeTextureGlyph.class)
public abstract class UnicodeTextureGlyphMixins {

    @Final
    @Shadow
    public UnihexFont.BitmapGlyph contents;

    @Final
    @Shadow
    public int left;

    @Final
    @Shadow
    public int right;

    @Unique
    private static void uploadGlyphTexture(int textureId,
        int level,
        int offsetX,
        int offsetY,
        int width,
        int height,
        NativeImage.Format format,
        IntBuffer pixelBuffer,
        Consumer<IntBuffer> pixelBufferReleaser) {
        if (Profiler.ENABLED) {
            Profiler.enter("java.UnicodeTextureGlyph.upload");
        }
        try {
            try {
                RenderSystem.assertOnRenderThreadOrInit();

                if (!pixelBuffer.isDirect()) {
                    throw new IllegalArgumentException("pixels must be a direct buffer");
                }
                long sourcePointer = MemoryUtil.memAddress(pixelBuffer);

                int bytesPerPixel = switch (format) {
                    case NativeImage.Format.RGBA -> 4;
                    default -> throw new IllegalArgumentException("Unsupported format: " + format);
                };

                int sourceSizeBytes = width * height * bytesPerPixel;
                int sourceRowPixels = width;
                int sourceOffsetX = 0, sourceOffsetY = 0;

                assert level == 0;
                if (UiBackendBuildConfig.OPENGL_UI) {
                    try (NativeImage image = new NativeImage(format, width, height, false)) {
                        long imagePointer =
                            ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                                (Object) image).radiance$getPointer();
                        MemoryUtil.memCopy(sourcePointer, imagePointer, sourceSizeBytes);
                        ((INativeImageExt) (Object) image).radiance$setTarget(
                            TextureTracker.captureTarget(textureId));
                        image.upload(level, offsetX, offsetY, sourceOffsetX, sourceOffsetY,
                            width, height, false, false, false, false);
                    }
                } else {
                    long textureGeneration = TextureTracker.captureTarget(textureId).generation();
                    TextureProxy.queueUploadRegion(new TextureProxy.TextureUploadRegion(
                        sourcePointer, sourceSizeBytes, sourceRowPixels, textureId,
                        sourceOffsetX, sourceOffsetY, offsetX, offsetY, width, height, 0,
                        TextureProxy.UPLOAD_KIND_MAIN, bytesPerPixel, textureGeneration));
                }
            } finally {
                try {
                    TextureProxy.flushUploadBatch();
                } finally {
                    pixelBufferReleaser.accept(pixelBuffer);
                }
            }
        } finally {
            if (Profiler.ENABLED) {
                Profiler.exit();
            }
        }

    }

    @Shadow
    public abstract int width();





    @Overwrite
    public GlyphRenderer bake(Function<RenderableGlyph, GlyphRenderer> glyphFactory) {
        return glyphFactory.apply(new IRenderableGlyphExt() {
            public float getOversample() {
                return 2.0F;
            }

            public int getWidth() {
                return width();
            }

            public int getHeight() {
                return 16;
            }

            public void upload(int x, int y) {
                throw new RuntimeException("Should never be called");
            }

            @Override
            public void upload(int textureId, int x, int y) {
                IntBuffer pixelBuffer = MemoryUtil.memAllocInt(width() * 16);
                UnihexFont.addGlyphPixels(pixelBuffer, contents, left, right);
                pixelBuffer.rewind();

                if (textureId < 0) {
                    throw new IllegalArgumentException("Target ID has not been set");
                }

                pixelBuffer.rewind();

                int level = 0;
                int offsetX = x;
                int offsetY = y;
                int width = width();
                int height = 16;
                NativeImage.Format format = NativeImage.Format.RGBA;
                Consumer<IntBuffer> pixelBufferReleaser = MemoryUtil::memFree;

                if (!RenderSystem.isOnRenderThreadOrInit()) {
                    RenderSystem.recordRenderCall(
                        () -> uploadGlyphTexture(textureId, level, offsetX, offsetY, width, height, format,
                            pixelBuffer, pixelBufferReleaser));
                } else {
                    uploadGlyphTexture(textureId, level, offsetX, offsetY, width, height, format, pixelBuffer,
                        pixelBufferReleaser);
                }
            }

            public boolean hasColor() {
                return true;
            }
        });
    }
}
