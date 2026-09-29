package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.texture.MipmapUtil;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ILightMapManagerExt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.profiler.Profiler;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixins implements ILightMapManagerExt {

    @Unique
    private float ambientLightFactor = 0;
    @Unique
    private float skyFactor = 0;
    @Unique
    private float blockFactor = 0;
    @Unique
    private boolean useBrightLightmap = false;
    @Unique
    private Vector3f skyLightColor = new Vector3f(0.0f, 0.0f, 0.0f);
    @Unique
    private float nightVisionFactor = 0;
    @Unique
    private float darknessScale = 0;
    @Unique
    private float darkenWorldFactor = 0;
    @Unique
    private float brightnessFactor = 0;
    @Unique
    private NativeImageBackedTexture radiance$texture;
    @Unique
    private NativeImage radiance$image;
    @Unique
    private Identifier radiance$textureIdentifier;

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

    @Inject(method = "<init>(Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/MinecraftClient;)V",
        at = @At("TAIL"))
    public void initJavaLightmapTexture(GameRenderer renderer, MinecraftClient client,
        CallbackInfo ci) {
        this.radiance$texture = new NativeImageBackedTexture(16, 16, false);
        this.radiance$textureIdentifier = Identifier.of("radiance", "dynamic/light_map");
        this.client.getTextureManager()
            .registerTexture(this.radiance$textureIdentifier, this.radiance$texture);
        this.radiance$image = this.radiance$texture.getImage();
        if (this.radiance$image == null) {
            throw new IllegalStateException("Lightmap texture image was not initialized");
        }

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                MipmapUtil.setColorArgb(this.radiance$image, x, y, 0xFFFFFFFF);
            }
        }

        this.radiance$texture.setFilter(true, false);
        this.radiance$texture.upload();
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    public void closeJavaLightmapTexture(CallbackInfo ci) {
        if (this.radiance$texture != null) {
            this.radiance$texture.close();
            this.radiance$texture = null;
            this.radiance$image = null;
            this.radiance$textureIdentifier = null;
        }
    }

    @Inject(method = "disable()V", at = @At("HEAD"), cancellable = true)
    public void cancelDisable(CallbackInfo ci) {
        RenderSystem.setShaderTexture(2, 0);
        ci.cancel();
    }

    @Inject(method = "enable()V", at = @At("HEAD"), cancellable = true)
    public void cancelEnable(CallbackInfo ci) {
        if (this.radiance$textureIdentifier != null) {
            RenderSystem.setShaderTexture(2, this.radiance$textureIdentifier);
        } else {
            RenderSystem.setShaderTexture(2, 0);
        }
        ci.cancel();
    }

    @Shadow
    protected abstract float getDarknessFactor(float delta);

    @Shadow
    protected abstract float getDarkness(LivingEntity entity, float factor, float delta);

    @Inject(method = "update(F)V", at = @At("HEAD"), cancellable = true)
    public void redirectUpdate(float delta, CallbackInfo ci) {
        if (this.dirty) {
            this.dirty = false;
            ClientWorld clientWorld = this.client.world;
            Profiler profiler = this.client.getProfiler();
            profiler.push("lightTex");
            if (clientWorld != null && this.radiance$image != null && this.radiance$texture != null
                && this.client.player != null) {
                float f = clientWorld.getSkyBrightness(1.0F);
                float skyFactor;
                if (clientWorld.getLightningTicksLeft() > 0) {
                    skyFactor = 1.0F;
                } else {
                    skyFactor = f * 0.95F + 0.05F;
                }

                float h = this.client.options.getDarknessEffectScale().getValue().floatValue();
                float i = this.getDarknessFactor(delta) * h;
                float darknessScale = this.getDarkness(this.client.player, i, delta) * h;
                float k = this.client.player.getUnderwaterVisibility();
                float nightVisionFactor;
                if (this.client.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                    nightVisionFactor = GameRenderer.getNightVisionStrength(this.client.player,
                        delta);
                } else if (k > 0.0F
                    && this.client.player.hasStatusEffect(StatusEffects.CONDUIT_POWER)) {
                    nightVisionFactor = k;
                } else {
                    nightVisionFactor = 0.0F;
                }

                Vector3f skyLightColor = new Vector3f(f, f, 1.0F).lerp(
                    new Vector3f(1.0F, 1.0F, 1.0F), 0.35F);
                float blockFactor = this.flickerIntensity + 1.5F;
                float ambientLightFactor = clientWorld.getDimension().ambientLight();
                boolean useBrightLightmap = clientWorld.getDimensionEffects()
                    .shouldBrightenLighting();
                float gamma = this.client.options.getGamma().getValue().floatValue();
                float darkenWorldFactor = this.renderer.getSkyDarkness(delta);
                float brightnessFactor = Math.max(0.0F, gamma - i);

                Vector3f workingColor = new Vector3f();
                for (int sky = 0; sky < 16; sky++) {
                    for (int block = 0; block < 16; block++) {
                        float skyBrightness = LightmapTextureManager.getBrightness(
                            clientWorld.getDimension(), sky) * skyFactor;
                        float blockBrightness = LightmapTextureManager.getBrightness(
                            clientWorld.getDimension(), block) * blockFactor;
                        float green = blockBrightness
                            * ((blockBrightness * 0.6F + 0.4F) * 0.6F + 0.4F);
                        float blue = blockBrightness
                            * (blockBrightness * blockBrightness * 0.6F + 0.4F);
                        workingColor.set(blockBrightness, green, blue);
                        if (useBrightLightmap) {
                            workingColor.lerp(new Vector3f(0.99F, 1.12F, 1.0F), 0.25F);
                            radiance$clamp(workingColor);
                        } else {
                            Vector3f skyContribution = new Vector3f(skyLightColor).mul(
                                skyBrightness);
                            workingColor.add(skyContribution);
                            workingColor.lerp(new Vector3f(0.75F, 0.75F, 0.75F), 0.04F);
                            if (darkenWorldFactor > 0.0F) {
                                Vector3f darkened = new Vector3f(workingColor).mul(0.7F, 0.6F,
                                    0.6F);
                                workingColor.lerp(darkened, darkenWorldFactor);
                            }
                        }

                        if (nightVisionFactor > 0.0F) {
                            float max = Math.max(workingColor.x(),
                                Math.max(workingColor.y(), workingColor.z()));
                            if (max < 1.0F) {
                                workingColor.lerp(new Vector3f(workingColor).mul(1.0F / max),
                                    nightVisionFactor);
                            }
                        }

                        if (!useBrightLightmap) {
                            if (darknessScale > 0.0F) {
                                workingColor.add(-darknessScale, -darknessScale, -darknessScale);
                            }
                            radiance$clamp(workingColor);
                        }

                        Vector3f eased = new Vector3f(radiance$easeOutQuart(workingColor.x()),
                            radiance$easeOutQuart(workingColor.y()),
                            radiance$easeOutQuart(workingColor.z()));
                        workingColor.lerp(eased, Math.max(0.0F, gamma - i));
                        workingColor.lerp(new Vector3f(0.75F, 0.75F, 0.75F), 0.04F);
                        radiance$clamp(workingColor);
                        workingColor.mul(255.0F);

                        int red = (int) workingColor.x();
                        int greenInt = (int) workingColor.y();
                        int blueInt = (int) workingColor.z();
                        MipmapUtil.setColorArgb(this.radiance$image, block, sky,
                            0xFF000000 | red << 16 | greenInt << 8 | blueInt);
                    }
                }
                this.radiance$texture.upload();

                this.ambientLightFactor = ambientLightFactor;
                this.skyFactor = skyFactor;
                this.blockFactor = blockFactor;
                this.useBrightLightmap = useBrightLightmap;
                this.skyLightColor = skyLightColor;
                this.nightVisionFactor = nightVisionFactor;
                this.darknessScale = darknessScale;
                this.darkenWorldFactor = darkenWorldFactor;
                this.brightnessFactor = brightnessFactor;
            }
            profiler.pop();
        }
        ci.cancel();
    }

    @Unique
    private static void radiance$clamp(Vector3f vec) {
        vec.set(MathHelper.clamp(vec.x(), 0.0F, 1.0F),
            MathHelper.clamp(vec.y(), 0.0F, 1.0F),
            MathHelper.clamp(vec.z(), 0.0F, 1.0F));
    }

    @Unique
    private float radiance$easeOutQuart(float x) {
        float f = 1.0F - x;
        return 1.0F - f * f * f * f;
    }

    @Override
    public int radiance$getTextureId() {
        return this.radiance$texture != null ? this.radiance$texture.getGlId() : 0;
    }

    @Override
    public float radiance$getAmbientLightFactor() {
        return ambientLightFactor;
    }

    @Override
    public float radiance$getSkyFactor() {
        return skyFactor;
    }

    @Override
    public float radiance$getBlockFactor() {
        return blockFactor;
    }

    @Override
    public boolean radiance$isUseBrightLightmap() {
        return useBrightLightmap;
    }

    @Override
    public Vector3f radiance$getSkyLightColor() {
        return skyLightColor;
    }

    @Override
    public float radiance$getNightVisionFactor() {
        return nightVisionFactor;
    }

    @Override
    public float radiance$getDarknessScale() {
        return darknessScale;
    }

    @Override
    public float radiance$getDarkenWorldFactor() {
        return darkenWorldFactor;
    }

    @Override
    public float radiance$getBrightnessFactor() {
        return brightnessFactor;
    }
}
