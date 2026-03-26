package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.RendererProxy;
import java.util.Locale;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferRenderer.class)
public class BufferRendererMixins {

    @Inject(method = "drawWithGlobalProgramInternal(Lnet/minecraft/client/render/BufferBuilder$BuiltBuffer;)V",
        at = @At("HEAD"),
        cancellable = true)
    private static void rewriteDrawWithGlobalProgram(BufferBuilder.BuiltBuffer buffer,
        CallbackInfo ci) {
        int pipelineType = RendererProxy.hasOverlayPipeline()
            ? RendererProxy.getOverlayPipelineType()
            : resolveOverlayPipelineType(buffer);
        if (pipelineType < 0) {
            return;
        }

        RendererProxy.bindOverlayPipeline(pipelineType);

        BufferProxy.VertexIndexBufferHandle handle = BufferProxy.createAndUploadVertexIndexBuffer(
            buffer);

        BufferProxy.updateOverlayDrawUniform();

        RendererProxy.drawOverlay(handle,
            buffer.getParameters()
                .indexCount(),
            buffer.getParameters()
                .indexType());

        buffer.release();

        ci.cancel();
    }

    private static int resolveOverlayPipelineType(BufferBuilder.BuiltBuffer buffer) {
        ShaderProgram shaderProgram = RenderSystem.getShader();
        if (shaderProgram != null) {
            int shaderPipeline = mapShaderPipeline(shaderProgram.getName());
            if (shaderPipeline >= 0) {
                return shaderPipeline;
            }
        }

        VertexFormat format = buffer.getParameters().format();
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

    private static int mapShaderPipeline(String shaderName) {
        String normalized = normalizeShaderName(shaderName);

        return switch (normalized) {
            case "rendertype_glint", "rendertype_glint_direct", "rendertype_glint_translucent",
                 "rendertype_entity_glint", "rendertype_entity_glint_direct",
                 "rendertype_armor_glint", "rendertype_armor_entity_glint", "position_tex" -> 0;
            case "position_color", "rendertype_gui", "rendertype_gui_overlay",
                 "rendertype_gui_text_highlight", "rendertype_gui_ghost_recipe_overlay",
                 "rendertype_lines" -> 1;
            case "position_tex_color", "rendertype_lightning" -> 2;
            case "rendertype_text", "rendertype_text_background",
                 "rendertype_text_background_see_through", "rendertype_text_intensity",
                 "rendertype_text_intensity_see_through", "rendertype_text_see_through",
                 "particle" -> 3;
            case "rendertype_entity_cutout", "rendertype_entity_cutout_no_cull",
                 "rendertype_entity_cutout_no_cull_z_offset",
                 "rendertype_entity_translucent", "rendertype_entity_translucent_cull",
                 "rendertype_entity_translucent_emissive",
                 "rendertype_item_entity_translucent_cull", "rendertype_entity_solid",
                 "rendertype_entity_smooth_cutout", "rendertype_entity_shadow",
                 "rendertype_entity_alpha", "rendertype_entity_decal",
                 "rendertype_energy_swirl", "rendertype_eyes", "rendertype_beacon_beam" -> 4;
            case "rendertype_entity_no_outline", "rendertype_armor_cutout_no_cull" -> 5;
            case "rendertype_end_portal", "rendertype_end_gateway" -> 6;
            case "position" -> 7;
            default -> -1;
        };
    }

    private static String normalizeShaderName(String shaderName) {
        if (shaderName == null) {
            return "";
        }

        String normalized = shaderName.toLowerCase(Locale.ROOT).replace('\\', '/');
        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0) {
            normalized = normalized.substring(namespaceSeparator + 1);
        }
        if (normalized.startsWith("core/")) {
            normalized = normalized.substring("core/".length());
        }
        if (normalized.startsWith("minecraft/shaders/core/")) {
            normalized = normalized.substring("minecraft/shaders/core/".length());
        }
        if (normalized.endsWith(".json")) {
            normalized = normalized.substring(0, normalized.length() - ".json".length());
        }
        return normalized;
    }
}
