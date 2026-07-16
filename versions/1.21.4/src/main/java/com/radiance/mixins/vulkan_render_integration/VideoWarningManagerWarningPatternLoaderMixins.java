package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.resource.VideoWarningManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(VideoWarningManager.WarningPatternLoader.class)
public class VideoWarningManagerWarningPatternLoaderMixins {

    @Redirect(method = "buildWarnings()Lcom/google/common/collect/ImmutableMap;",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlDebugInfo;getRenderer()Ljava/lang/String;"))
    public String setRendererName() {
        return "NeoVoxelRT - Vulkan";
    }

    @Redirect(method = "buildWarnings()Lcom/google/common/collect/ImmutableMap;",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlDebugInfo;getVersion()Ljava/lang/String;"))
    public String setRendererVersion() {
        return "1.3";
    }

    @Redirect(method = "buildWarnings()Lcom/google/common/collect/ImmutableMap;",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlDebugInfo;getVendor()Ljava/lang/String;"))
    public String setRendererVendor() {
        return "Cross Platform";
    }
}
