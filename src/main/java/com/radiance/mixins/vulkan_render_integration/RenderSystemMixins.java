package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.vulkan.RendererProxy;
import java.util.Locale;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSystem.class)
public abstract class RenderSystemMixins {

    @Shadow(remap = false)
    private static Matrix4f projectionMatrix;

    @Shadow(remap = false)
    private static Matrix4f savedProjectionMatrix;

    @Final
    @Shadow(remap = false)
    private static Matrix4fStack modelViewStack;

    @Shadow(remap = false)
    private static Matrix4f textureMatrix;

    @Inject(method = "maxSupportedTextureSize()I", at = @At(value = "HEAD"), cancellable = true, remap = false)
    private static void redirectMaxSupportedTextureSize(CallbackInfoReturnable<Integer> cir) {
        int maxImageSize = RendererProxy.maxSupportedTextureSize();
        cir.setReturnValue(maxImageSize);
    }

    @Inject(method = "setShader(Lnet/minecraft/client/gl/ShaderProgramKey;)Lnet/minecraft/client/gl/ShaderProgram;",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER, remap = false),
        cancellable = true)
    private static void redirectSetShaderProgram(ShaderProgramKey shaderProgramKey,
        CallbackInfoReturnable<ShaderProgram> cir) {
        int type = resolveOverlayPipelineType(shaderProgramKey.configId().toString());
        if (type < 0) {
            return;
        }

        RendererProxy.bindOverlayPipeline(type);
        cir.setReturnValue(null);
    }

    @Redirect(method = "flipFrame(JLnet/minecraft/client/util/tracy/TracyFrameCapturer;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V", remap = false))
    private static void cancelSwapBuffers(long window) {

    }

    @Redirect(method = "renderCrosshair(I)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GLX;_renderCrosshair(IZZZ)V"))
    private static void cancelDrawCrossAirForNow(int size, boolean drawX, boolean drawY,
        boolean drawZ) {

    }

    private static int resolveOverlayPipelineType(String shaderName) {
        return switch (normalizeShaderName(shaderName)) {
            case "rendertype_glint", "rendertype_glint_direct", "rendertype_glint_translucent",
                 "rendertype_entity_glint", "rendertype_entity_glint_direct",
                 "rendertype_armor_glint", "rendertype_armor_entity_glint", "position_tex" -> 0;
            case "position_color", "rendertype_gui", "rendertype_gui_overlay",
                 "rendertype_gui_text_highlight", "rendertype_gui_ghost_recipe_overlay",
                 "rendertype_lines" -> 1;
            case "position_tex_color", "position_color_tex", "rendertype_lightning" -> 2;
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
            case "rendertype_entity_no_outline", "rendertype_armor_cutout_no_cull",
                 "rendertype_armor_translucent" -> 5;
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
