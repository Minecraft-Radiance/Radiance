package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleExt;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Particle.class)
public abstract class ParticleMixins implements IParticleExt {

    private String radiance$contentName;

    @Shadow
    protected double xo;

    @Shadow
    protected double yo;

    @Shadow
    protected double zo;

    @Shadow
    protected double x;

    @Shadow
    protected double y;

    @Shadow
    protected double z;

    @Shadow
    protected float rCol;

    @Shadow
    protected float gCol;

    @Shadow
    protected float bCol;

    @Shadow
    protected float alpha;

    @Shadow
    protected float roll;

    @Shadow
    protected float oRoll;

    @Override
    public double getPrevPosXValue() {
        return this.xo;
    }

    @Override
    public double getPrevPosYValue() {
        return this.yo;
    }

    @Override
    public double getPrevPosZValue() {
        return this.zo;
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
        return this.rCol;
    }

    @Override
    public float getGreenValue() {
        return this.gCol;
    }

    @Override
    public float getBlueValue() {
        return this.bCol;
    }

    @Override
    public float getAlphaValue() {
        return this.alpha;
    }

    @Override
    public float getAngleValue() {
        return this.roll;
    }

    @Override
    public float getPrevAngleValue() {
        return this.oRoll;
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
