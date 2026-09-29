package com.radiance.mixin_related.extensions.vulkan_render_integration;

public interface IParticleExt {

    double getPrevPosXValue();

    double getPrevPosYValue();

    double getPrevPosZValue();

    double getXValue();

    double getYValue();

    double getZValue();

    float getRedValue();

    float getGreenValue();

    float getBlueValue();

    float getAlphaValue();

    float getAngleValue();

    float getPrevAngleValue();

    String radiance$getContentName();

    void radiance$setContentName(String contentName);
}
