package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.ILightMapManagerExt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.profiler.Profiler;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixins implements ILightMapManagerExt {

    @Unique
    private float ambientLightFactor = 0.0F;

    @Unique
    private float skyFactor = 0.0F;

    @Unique
    private float blockFactor = 0.0F;

    @Unique
    private boolean useBrightLightmap = false;

    @Unique
    private Vector3f skyLightColor = new Vector3f(0.0F, 0.0F, 0.0F);

    @Unique
    private float nightVisionFactor = 0.0F;

    @Unique
    private float darknessScale = 0.0F;

    @Unique
    private float darkenWorldFactor = 0.0F;

    @Unique
    private float brightnessFactor = 0.0F;

    @Shadow
    private boolean dirty;

    @Shadow
    private float flickerIntensity;

    @Final
    @Shadow
    private GameRenderer renderer;

    @Final
    @Shadow
    private MinecraftClient client;

    @Shadow
    protected abstract float getDarknessFactor(float delta);

    @Shadow
    protected abstract float getDarkness(LivingEntity entity, float factor, float delta);

    @Inject(method = "update(F)V", at = @At("HEAD"), cancellable = true)
    private void redirectUpdate(float delta, CallbackInfo ci) {
        if (this.dirty) {
            this.dirty = false;
            Profiler profiler = this.client.getProfiler();
            profiler.push("lightTex");
            ClientWorld clientWorld = this.client.world;
            if (clientWorld != null && this.client.player != null) {
                float skyBrightness = clientWorld.getSkyBrightness(1.0F);
                float computedSkyFactor = clientWorld.getLightningTicksLeft() > 0 ? 1.0F
                    : skyBrightness * 0.95F + 0.05F;

                float darknessEffectScale = this.client.options.getDarknessEffectScale()
                    .getValue()
                    .floatValue();
                float darknessFactor = this.getDarknessFactor(delta) * darknessEffectScale;
                float computedDarknessScale = this.getDarkness(this.client.player, darknessFactor,
                    delta) * darknessEffectScale;
                float underwaterVisibility = this.client.player.getUnderwaterVisibility();
                float computedNightVisionFactor;
                if (this.client.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                    computedNightVisionFactor = GameRenderer.getNightVisionStrength(
                        this.client.player, delta);
                } else if (underwaterVisibility > 0.0F && this.client.player.hasStatusEffect(
                    StatusEffects.CONDUIT_POWER)) {
                    computedNightVisionFactor = underwaterVisibility;
                } else {
                    computedNightVisionFactor = 0.0F;
                }

                Vector3f computedSkyLightColor = new Vector3f(skyBrightness, skyBrightness, 1.0F)
                    .lerp(new Vector3f(1.0F, 1.0F, 1.0F), 0.35F);
                float computedBlockFactor = this.flickerIntensity + 1.5F;
                float computedAmbientLightFactor = clientWorld.getDimension().ambientLight();
                boolean computedUseBrightLightmap = clientWorld.getDimensionEffects()
                    .shouldBrightenLighting();
                float gamma = this.client.options.getGamma()
                    .getValue()
                    .floatValue();
                float computedDarkenWorldFactor = this.renderer.getSkyDarkness(delta);
                float computedBrightnessFactor = Math.max(0.0F, gamma - darknessFactor);

                this.ambientLightFactor = computedAmbientLightFactor;
                this.skyFactor = computedSkyFactor;
                this.blockFactor = computedBlockFactor;
                this.useBrightLightmap = computedUseBrightLightmap;
                this.skyLightColor = computedSkyLightColor;
                this.nightVisionFactor = computedNightVisionFactor;
                this.darknessScale = computedDarknessScale;
                this.darkenWorldFactor = computedDarkenWorldFactor;
                this.brightnessFactor = computedBrightnessFactor;
            }
            profiler.pop();
        }
        ci.cancel();
    }

    @Override
    public float radiance$getAmbientLightFactor() {
        return this.ambientLightFactor;
    }

    @Override
    public float radiance$getSkyFactor() {
        return this.skyFactor;
    }

    @Override
    public float radiance$getBlockFactor() {
        return this.blockFactor;
    }

    @Override
    public boolean radiance$isUseBrightLightmap() {
        return this.useBrightLightmap;
    }

    @Override
    public Vector3f radiance$getSkyLightColor() {
        return this.skyLightColor;
    }

    @Override
    public float radiance$getNightVisionFactor() {
        return this.nightVisionFactor;
    }

    @Override
    public float radiance$getDarknessScale() {
        return this.darknessScale;
    }

    @Override
    public float radiance$getDarkenWorldFactor() {
        return this.darkenWorldFactor;
    }

    @Override
    public float radiance$getBrightnessFactor() {
        return this.brightnessFactor;
    }
}
