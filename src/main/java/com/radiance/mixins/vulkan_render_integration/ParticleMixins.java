package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleExt;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Particle.class)
public abstract class ParticleMixins implements IParticleExt {

    private String radiance$contentName;

    @Shadow
    protected double prevPosX;

    @Shadow
    protected double prevPosY;

    @Shadow
    protected double prevPosZ;

    @Shadow
    protected double x;

    @Shadow
    protected double y;

    @Shadow
    protected double z;

    @Shadow
    protected float red;

    @Shadow
    protected float green;

    @Shadow
    protected float blue;

    @Shadow
    protected float alpha;

    @Shadow
    protected float angle;

    @Shadow
    protected float prevAngle;

    @Override
    public double getPrevPosXValue() {
        return this.prevPosX;
    }

    @Override
    public double getPrevPosYValue() {
        return this.prevPosY;
    }

    @Override
    public double getPrevPosZValue() {
        return this.prevPosZ;
    }

    @Override
    public double getXValue() {
        return this.x;
    }

    @Override
    public double getYValue() {
        return this.y;
    }

    @Override
    public double getZValue() {
        return this.z;
    }

    @Override
    public float getRedValue() {
        return this.red;
    }

    @Override
    public float getGreenValue() {
        return this.green;
    }

    @Override
    public float getBlueValue() {
        return this.blue;
    }

    @Override
    public float getAlphaValue() {
        return this.alpha;
    }

    @Override
    public float getAngleValue() {
        return this.angle;
    }

    @Override
    public float getPrevAngleValue() {
        return this.prevAngle;
    }

    @Override
    public String radiance$getContentName() {
        return this.radiance$contentName;
    }

    @Override
    public void radiance$setContentName(String contentName) {
        this.radiance$contentName = contentName;
    }
}
