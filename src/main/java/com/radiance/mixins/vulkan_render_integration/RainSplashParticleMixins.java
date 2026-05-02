package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.RainSplashParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RainSplashParticle.class)
public class RainSplashParticleMixins {

    @Inject(method = "getType", at = @At("HEAD"), cancellable = true)
    private void forceTranslucentSheet(CallbackInfoReturnable<ParticleTextureSheet> cir) {
        cir.setReturnValue(ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT);
    }
}
