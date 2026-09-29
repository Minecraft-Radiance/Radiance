package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleExt;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.WhiteAshParticle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BillboardParticle.class)
public abstract class BillboardParticleMixins {

    @Inject(method = "buildGeometry(Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/Camera;F)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    public void resizeParticle(VertexConsumer vertexConsumer, Camera camera, float tickDelta,
        CallbackInfo ci) {
        if (((BillboardParticle) (Object) this) instanceof WhiteAshParticle) {
            IParticleExt particle = (IParticleExt) this;
            Vec3d cameraPos = camera.getPos();
            float f = (float) (MathHelper.lerp((double) tickDelta, particle.getPrevPosXValue(),
                particle.getXValue())
                - cameraPos.getX());
            float g = (float) (MathHelper.lerp((double) tickDelta, particle.getPrevPosYValue(),
                particle.getYValue())
                - cameraPos.getY());
            float h = (float) (MathHelper.lerp((double) tickDelta, particle.getPrevPosZValue(),
                particle.getZValue())
                - cameraPos.getZ());
            Quaternionf quaternionf;
            if (particle.getAngleValue() == 0.0F) {
                quaternionf = camera.getRotation();
            } else {
                quaternionf = new Quaternionf(camera.getRotation());
                quaternionf.rotateZ(MathHelper.lerp(tickDelta, particle.getPrevAngleValue(),
                    particle.getAngleValue()));
            }

            float j = this.getSize(tickDelta);
            float k = this.getMinU();
            float l = this.getMaxU();
            float m = this.getMinV();
            float n = this.getMaxV();
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
    public abstract float getSize(float i);

    @Shadow
    protected abstract float getMinU();

    @Shadow
    protected abstract float getMaxU();

    @Shadow
    protected abstract float getMinV();

    @Shadow
    protected abstract float getMaxV();

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
            .texture(l, m)
            .color(particle.getRedValue(), particle.getGreenValue(), particle.getBlueValue(),
                particle.getAlphaValue())
            .light(n)
            .next();
    }
}
