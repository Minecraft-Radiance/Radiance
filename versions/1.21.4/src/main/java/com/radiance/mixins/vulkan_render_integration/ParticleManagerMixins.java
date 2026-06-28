package com.radiance.mixins.vulkan_render_integration;

import static com.radiance.client.proxy.world.EntityProxy.PARTICLE_COUNTERS;

import com.llamalad7.mixinextras.sugar.Local;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleManagerExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleExt;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public class ParticleManagerMixins implements IParticleManagerExt {

    @Final
    @Shadow
    private static List<ParticleTextureSheet> PARTICLE_TEXTURE_SHEETS;
    @Final
    @Shadow
    private Map<ParticleTextureSheet, Queue<Particle>> particles;

    @Override
    public List<ParticleTextureSheet> radiance$getTextureSheets() {
        return PARTICLE_TEXTURE_SHEETS;
    }

    @Override
    public Map<ParticleTextureSheet, Queue<Particle>> radiance$getParticles() {
        return particles;
    }

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At(value = "HEAD"))
    public void addParticleCounter(Particle particle, CallbackInfo ci) {
        PARTICLE_COUNTERS.computeIfAbsent(particle.getClass(), k -> new AtomicInteger())
            .incrementAndGet();
    }

    @Inject(method = "tickParticles(Ljava/util/Collection;)V", at = @At(value = "INVOKE", target = "Ljava/util/Iterator;remove()V"))
    public void removeParticleCounter(Collection<Particle> particles, CallbackInfo ci,
        @Local Particle particle) {
        AtomicInteger counter = PARTICLE_COUNTERS.get(particle.getClass());
        if (counter != null) {
            counter.decrementAndGet();
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleManager;addParticle(Lnet/minecraft/client/particle/Particle;)V",
            shift = At.Shift.BEFORE),
        cancellable = true)
    public void checkParticleCounter(ParticleEffect parameters,
        double x,
        double y,
        double z,
        double velocityX,
        double velocityY,
        double velocityZ,
        CallbackInfoReturnable<Particle> cir,
        @Local Particle particle) {
        AtomicInteger counter = PARTICLE_COUNTERS.get(particle.getClass());
        if (counter != null) {
            int numParticles = counter.get();
//            if (particle instanceof WaterSuspendParticle) {
//                if (numParticles > 128) {
//                    cir.setReturnValue(null);
//                }
//            } else if (particle instanceof RainSplashParticle || particle instanceof WaterSplashParticle) {
//                if (numParticles > 32) {
//                    cir.setReturnValue(null);
//                }
//            }
        }

        Identifier particleId = Registries.PARTICLE_TYPE.getId(parameters.getType());
        if (particleId != null) {
            ((IParticleExt) particle).radiance$setContentName(
                EntityContentNames.toParticleContentName(particleId));
        }
    }

    private static final class EntityContentNames {

        private static String toParticleContentName(Identifier particleId) {
            if ("minecraft".equals(particleId.getNamespace())) {
                return "/particle/" + particleId.getPath();
            }
            return "/particle/" + particleId.getNamespace() + "/" + particleId.getPath();
        }
    }
}
