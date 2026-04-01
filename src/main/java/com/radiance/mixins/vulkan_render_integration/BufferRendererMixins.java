package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.RendererProxy;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferRenderer.class)
public class BufferRendererMixins {

    @Inject(method = "drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER, remap = false),
        cancellable = true)
    private static void rewriteDrawWithGlobalProgram(BuiltBuffer buffer, CallbackInfo ci) {
        int pipelineType = resolveOverlayPipelineType(buffer);
        if (pipelineType < 0 && RendererProxy.hasOverlayPipeline()) {
            pipelineType = RendererProxy.getOverlayPipelineType();
        }
        if (pipelineType < 0) {
            return;
        }

        RendererProxy.bindOverlayPipeline(pipelineType);

        BufferProxy.VertexIndexBufferHandle handle = BufferProxy.createAndUploadVertexIndexBuffer(
            buffer);

        BufferProxy.updateOverlayDrawUniform();

        RendererProxy.drawOverlay(handle,
            buffer.getDrawParameters()
                .indexCount(),
            pipelineType,
            buffer.getDrawParameters()
                .indexType());

        buffer.close();

        ci.cancel();
    }

    private static int resolveOverlayPipelineType(BuiltBuffer buffer) {
        VertexFormat format = buffer.getDrawParameters().format();
        if (format == VertexFormats.POSITION_TEXTURE_COLOR) {
            return 2;
        }
        if (format == VertexFormats.POSITION_COLOR) {
            return 1;
        }
        if (format == VertexFormats.POSITION_TEXTURE) {
            return 0;
        }
        if (format == VertexFormats.POSITION_COLOR_TEXTURE_LIGHT
            || format == VertexFormats.POSITION_TEXTURE_LIGHT_COLOR) {
            return 3;
        }
        if (format == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL) {
            return 4;
        }
        if (format == VertexFormats.POSITION) {
            return 7;
        }

        return -1;
    }
}
