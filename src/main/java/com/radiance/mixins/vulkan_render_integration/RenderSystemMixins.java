package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.vulkan.RendererProxy;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSystem.class)
public class RenderSystemMixins {

    @Shadow(remap = false)
    private static ShaderProgram shader;

    @Inject(method = "maxSupportedTextureSize()I", at = @At("HEAD"), cancellable = true,
        remap = false)
    private static void redirectMaxSupportedTextureSize(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RendererProxy.maxSupportedTextureSize());
    }

    @Inject(method = "setShader(Ljava/util/function/Supplier;)V", at = @At("TAIL"),
        remap = false)
    private static void bindOverlayPipelineOnDirectSet(Supplier<ShaderProgram> supplier,
        CallbackInfo ci) {
        bindCurrentOverlayPipeline();
    }

    @Inject(method = "lambda$setShader$62(Ljava/util/function/Supplier;)V", at = @At("TAIL"),
        remap = false)
    private static void bindOverlayPipelineOnDeferredSet(Supplier<ShaderProgram> supplier,
        CallbackInfo ci) {
        bindCurrentOverlayPipeline();
    }

    private static void bindCurrentOverlayPipeline() {
        if (shader == null) {
            RendererProxy.bindOverlayPipeline(-1);
            return;
        }

        int pipelineType = mapOverlayPipeline(shader.getName());
        if (pipelineType < 0) {
            System.out.println("[Radiance] Unmapped overlay shader: " + shader.getName());
        }
        RendererProxy.bindOverlayPipeline(pipelineType);
    }

    private static int mapOverlayPipeline(String shaderName) {
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

    @Redirect(method = "flipFrame(J)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V"),
        remap = false)
    private static void cancelSwapBuffers(long window) {
    }
}
