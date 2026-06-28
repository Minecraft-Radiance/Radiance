package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.WhiteAshParticle;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BillboardParticle.class)
public abstract class BillboardParticleMixins {

    @Inject(method = "method_60374(Lnet/minecraft/client/render/VertexConsumer;Lorg/joml/Quaternionf;FFFF)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    public void resizeParticle(VertexConsumer vertexConsumer,
        Quaternionf quaternionf,
        float f,
        float g,
        float h,
        float i,
        CallbackInfo ci) {
        if (((BillboardParticle) (Object) this) instanceof WhiteAshParticle) {
            float j = this.getSize(i);
            float k = this.getMinU();
            float l = this.getMaxU();
            float m = this.getMinV();
            float n = this.getMaxV();
            int o = 0;
            this.method_60375(vertexConsumer, quaternionf, f, g, h, 1.0F / 8.0F, -1.0F / 8.0F, j, l,
                n, o);
            this.method_60375(vertexConsumer, quaternionf, f, g, h, 1.0F / 8.0F, 1.0F / 8.0F, j, l,
                m, o);
            this.method_60375(vertexConsumer, quaternionf, f, g, h, -1.0F / 8.0F, 1.0F / 8.0F, j, k,
                m, o);
            this.method_60375(vertexConsumer, quaternionf, f, g, h, -1.0F / 8.0F, -1.0F / 8.0F, j,
                k, n, o);

            ci.cancel();
        }
    }

    @Shadow
    public abstract float getSize(float i);

    @Shadow
    protected abstract float getMinU();

    @Shadow
    protected abstract float getMaxU();

    @Shadow
    protected abstract float getMinV();

    @Shadow
    protected abstract float getMaxV();

    @Shadow
    protected abstract void method_60375(VertexConsumer vertexConsumer,
        Quaternionf quaternionf,
        float f,
        float g,
        float h,
        float i,
        float j,
        float k,
        float l,
        float m,
        int n);
}
