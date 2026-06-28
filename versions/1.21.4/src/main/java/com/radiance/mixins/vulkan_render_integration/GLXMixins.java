package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.platform.GLX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GLX.class)
public class GLXMixins {

    @Redirect(method = "_init(IZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/GlDebug;enableDebug(IZ)V"))
    private static void cancelOpenGLDebug(int verbosity, boolean sync) {

    }
}
