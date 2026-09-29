package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.renderer.GpuWarnlistManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GpuWarnlistManager.Preparations.class)
public class VideoWarningManagerWarningPatternLoaderMixins {

    @Redirect(method = "apply()Lcom/google/common/collect/ImmutableMap;",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlUtil;getRenderer()Ljava/lang/String;"))
    public String setRendererName() {
        return "NeoVoxelRT - Vulkan";
    }

    @Redirect(method = "apply()Lcom/google/common/collect/ImmutableMap;",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlUtil;getOpenGLVersion()Ljava/lang/String;"))
    public String setRendererVersion() {
        return "1.3";
    }

    @Redirect(method = "apply()Lcom/google/common/collect/ImmutableMap;",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlUtil;getVendor()Ljava/lang/String;"))
    public String setRendererVendor() {
        return "Cross Platform";
    }
}
