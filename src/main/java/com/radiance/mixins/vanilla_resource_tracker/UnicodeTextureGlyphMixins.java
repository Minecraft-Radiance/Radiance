package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.systems.RenderSystem;
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
    private static void _upload(int id,
        int level,
        int offsetX,
        int offsetY,
        int width,
        int height,
        NativeImage.Format format,
        IntBuffer pixels,
        Consumer<IntBuffer> closer) {
        try {
            RenderSystem.assertOnRenderThreadOrInit();

            if (!pixels.isDirect()) {
                throw new IllegalArgumentException("pixels must be a direct buffer");
            }
            long srcPointer = MemoryUtil.memAddress(pixels);

            int bytesPerPixel = switch (format) {
                case RGBA -> 4;
                default -> throw new IllegalArgumentException("Unsupported format: " + format);
            };

            int srcSizeInBytes = width * height * bytesPerPixel;
            int srcRowPixels = width;
            int srcOffsetX = 0, srcOffsetY = 0;

            assert level == 0;
            if (UiBackendBuildConfig.OPENGL_UI) {
                try (NativeImage image = new NativeImage(format, width, height, false)) {
                    long imagePointer =
                        ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                            (Object) image).radiance$getPointer();
                    MemoryUtil.memCopy(srcPointer, imagePointer, srcSizeInBytes);
                    ((INativeImageExt) (Object) image).radiance$setTarget(
                        TextureTracker.captureTarget(id));
                    image.upload(level, offsetX, offsetY, srcOffsetX, srcOffsetY, width, height,
                        false, false, false, false);
                }
            } else {
                long textureGeneration = TextureTracker.captureTarget(id).generation();
                TextureProxy.queueUploadRegion(new TextureProxy.TextureUploadRegion(srcPointer,
                    srcSizeInBytes, srcRowPixels, id, srcOffsetX, srcOffsetY, offsetX, offsetY,
                    width, height, 0, TextureProxy.UPLOAD_KIND_MAIN, bytesPerPixel,
                    textureGeneration));
            }
        } finally {
            try {
                TextureProxy.flushUploadBatch();
            } finally {
                closer.accept(pixels);
            }
        }

    }

    @Shadow
    public abstract int width();





    @Overwrite
    public GlyphRenderer bake(Function<RenderableGlyph, GlyphRenderer> function) {
        return function.apply(new IRenderableGlyphExt() {
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
            public void upload(int id, int u, int v) {
                IntBuffer intBuffer = MemoryUtil.memAllocInt(width() * 16);
                UnihexFont.addGlyphPixels(intBuffer, contents, left, right);
                intBuffer.rewind();

                if (id < 0) {
                    throw new IllegalArgumentException("Target ID has not been set");
                }

                intBuffer.rewind();

                int level = 0;
                int offsetX = u;
                int offsetY = v;
                int width = width();
                int height = 16;
                NativeImage.Format format = NativeImage.Format.RGBA;
                IntBuffer pixels = intBuffer;
                Consumer<IntBuffer> closer = MemoryUtil::memFree;

                if (!RenderSystem.isOnRenderThreadOrInit()) {
                    RenderSystem.recordRenderCall(
                        () -> _upload(id, level, offsetX, offsetY, width, height, format, pixels,
                            closer));
                } else {
                    _upload(id, level, offsetX, offsetY, width, height, format, pixels, closer);
                }
            }

            public boolean hasColor() {
                return true;
            }
        });
    }
}
