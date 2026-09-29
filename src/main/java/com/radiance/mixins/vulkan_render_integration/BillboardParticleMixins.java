package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.WhiteAshParticle;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
public abstract class BillboardParticleMixins {

    @Inject(method = "renderRotatedQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Quaternionf;FFFF)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    public void resizeParticle(VertexConsumer vertexConsumer,
        Quaternionf quaternionf,
        float f,
        float g,
        float h,
        float i,
        CallbackInfo ci) {
        if (((SingleQuadParticle) (Object) this) instanceof WhiteAshParticle) {
            float j = this.getQuadSize(i);
            float k = this.getU0();
            float l = this.getU1();
            float m = this.getV0();
            float n = this.getV1();
            int o = 0;
            this.renderVertex(vertexConsumer, quaternionf, f, g, h, 1.0F / 8.0F, -1.0F / 8.0F, j, l,
                n, o);
            this.renderVertex(vertexConsumer, quaternionf, f, g, h, 1.0F / 8.0F, 1.0F / 8.0F, j, l,
                m, o);
            this.renderVertex(vertexConsumer, quaternionf, f, g, h, -1.0F / 8.0F, 1.0F / 8.0F, j, k,
                m, o);
            this.renderVertex(vertexConsumer, quaternionf, f, g, h, -1.0F / 8.0F, -1.0F / 8.0F, j,
                k, n, o);

            ci.cancel();
        }
    }

    @Shadow
    public abstract float getQuadSize(float i);

    @Shadow
    protected abstract float getU0();

    @Shadow
    protected abstract float getU1();

    @Shadow
    protected abstract float getV0();

    @Shadow
    protected abstract float getV1();

    @Shadow
    protected abstract void renderVertex(VertexConsumer vertexConsumer,
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
