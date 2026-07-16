package com.radiance.loader.neoforge.scene_import.plugin;

import java.util.Locale;
import java.util.Set;

public final class Blaze3DCallTarget {
    private static final Set<String> BLAZE3D_OWNERS = Set.of(
        "com/mojang/blaze3d/systems/RenderPass",
        "com/mojang/blaze3d/systems/CommandEncoder",
        "com/mojang/blaze3d/systems/RenderSystem",
        "com/mojang/blaze3d/pipeline/RenderPipeline",
        "com/mojang/blaze3d/pipeline/CompiledRenderPipeline",
        "com/mojang/blaze3d/buffers/GpuBuffer",
        "com/mojang/blaze3d/textures/GpuTexture",
        "com/mojang/blaze3d/textures/GpuTextureView",
        "com/mojang/blaze3d/vertex/VertexFormat"
    );

    private Blaze3DCallTarget() {
    }

    public static boolean matches(String owner, String name, String descriptor) {
        if (BLAZE3D_OWNERS.contains(owner)) {
            return true;
        }
        return owner.startsWith("com/mojang/blaze3d/")
            && (isRenderVerb(name) || descriptor.contains("RenderPass")
                || descriptor.contains("GpuBuffer"));
    }

    public static boolean isRenderPassDraw(String owner, String name) {
        return "com/mojang/blaze3d/systems/RenderPass".equals(owner)
            && name.toLowerCase(Locale.ROOT).contains("draw");
    }

    public static String category(String owner, String name, String descriptor) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (owner.endsWith("/CommandEncoder")) {
            if (lower.contains("renderpass")) {
                return "render_pass";
            }
            if (lower.contains("buffer")) {
                return "buffer_transfer";
            }
            if (lower.contains("texture")) {
                return "texture_transfer";
            }
            return "command_encoder";
        }
        if (owner.endsWith("/RenderPass")) {
            if (lower.contains("draw")) {
                return "draw";
            }
            if (lower.contains("pipeline")) {
                return "pipeline";
            }
            if (lower.contains("texture")) {
                return "texture_binding";
            }
            if (lower.contains("buffer")) {
                return "buffer_binding";
            }
            if (lower.contains("uniform")) {
                return "uniform";
            }
            if (lower.contains("scissor") || lower.contains("viewport")) {
                return "raster_state";
            }
            return "render_pass";
        }
        if (owner.endsWith("/RenderSystem")) {
            return "render_system";
        }
        if (owner.contains("/pipeline/")) {
            return "pipeline";
        }
        if (owner.contains("/buffers/")) {
            return "buffer";
        }
        if (owner.contains("/textures/")) {
            return "texture";
        }
        if (owner.contains("/vertex/")) {
            return "vertex_format";
        }
        return "blaze3d";
    }

    private static boolean isRenderVerb(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("draw")
            || lower.contains("render")
            || lower.contains("pipeline")
            || lower.contains("texture")
            || lower.contains("buffer")
            || lower.contains("uniform");
    }
}

