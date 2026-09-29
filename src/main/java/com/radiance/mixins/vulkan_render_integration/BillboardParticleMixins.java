package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleExt;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.WhiteAshParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
public abstract class BillboardParticleMixins {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    public void resizeParticle(VertexConsumer vertexConsumer, Camera camera, float tickDelta,
        CallbackInfo ci) {
        if (((SingleQuadParticle) (Object) this) instanceof WhiteAshParticle) {
            IParticleExt particle = (IParticleExt) this;
            Vec3 cameraPos = camera.getPosition();
            float f = (float) (Mth.lerp((double) tickDelta, particle.getPrevPosXValue(),
                particle.getXValue())
                - cameraPos.x());
            float g = (float) (Mth.lerp((double) tickDelta, particle.getPrevPosYValue(),
                particle.getYValue())
                - cameraPos.y());
            float h = (float) (Mth.lerp((double) tickDelta, particle.getPrevPosZValue(),
                particle.getZValue())
                - cameraPos.z());
            Quaternionf quaternionf;
            if (particle.getAngleValue() == 0.0F) {
                quaternionf = camera.rotation();
            } else {
                quaternionf = new Quaternionf(camera.rotation());
                quaternionf.rotateZ(Mth.lerp(tickDelta, particle.getPrevAngleValue(),
                    particle.getAngleValue()));
            }

            float j = this.getQuadSize(tickDelta);
            float k = this.getU0();
            float l = this.getU1();
            float m = this.getV0();
            float n = this.getV1();
            int o = 0;
            this.putVertex(vertexConsumer, quaternionf, f, g, h, 1.0F / 8.0F,
                -1.0F / 8.0F, j, l, n, o);
            this.putVertex(vertexConsumer, quaternionf, f, g, h, 1.0F / 8.0F,
                1.0F / 8.0F, j, l, m, o);
            this.putVertex(vertexConsumer, quaternionf, f, g, h, -1.0F / 8.0F,
                1.0F / 8.0F, j, k, m, o);
            this.putVertex(vertexConsumer, quaternionf, f, g, h, -1.0F / 8.0F,
                -1.0F / 8.0F, j, k, n, o);

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

    @Unique
    private void putVertex(VertexConsumer vertexConsumer,
        Quaternionf quaternionf,
        float f,
        float g,
        float h,
        float i,
        float j,
        float k,
        float l,
        float m,
        int n) {
        Vector3f vector3f = new Vector3f(i, j, 0.0F).rotate(quaternionf).mul(k).add(f, g, h);
        IParticleExt particle = (IParticleExt) this;
        vertexConsumer.vertex(vector3f.x(), vector3f.y(), vector3f.z())
            .uv(l, m)
            .color(particle.getRedValue(), particle.getGreenValue(), particle.getBlueValue(),
                particle.getAlphaValue())
            .uv2(n)
            .endVertex();
    }
}
