package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleExt;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Particle.class)
public class ParticleMixins implements IParticleExt {

    private String radiance$contentName = null;

    @Shadow
    protected double x;

    @Shadow
    protected double y;

    @Shadow
    protected double z;

    @Override
    public double radiance$getX() {
        return x;
    }

    @Override
    public double radiance$getY() {
        return y;
    }

    @Override
    public double radiance$getZ() {
        return z;
    }

    @Override
    public String radiance$getContentName() {
        return radiance$contentName;
    }

    @Override
    public void radiance$setContentName(String contentName) {
        radiance$contentName = contentName;
    }
}
