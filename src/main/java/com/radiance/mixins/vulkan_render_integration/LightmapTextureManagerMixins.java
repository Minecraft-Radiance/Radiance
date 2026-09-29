package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.UnsafeManager;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ILightMapManagerExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightTexture.class)
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
    private DynamicTexture radiance$texture;
    @Unique
    private NativeImage radiance$image;
    @Unique
    private ResourceLocation radiance$textureIdentifier;

    @Mutable
    @Final
    @Shadow
    private TextureTarget target;
    @Shadow
    private boolean updateLightTexture;
    @Shadow
    private float blockLightRedFlicker;
    @Final
    @Shadow
    private GameRenderer renderer;
    @Final
    @Shadow
    private Minecraft minecraft;


    @Redirect(method = "Lnet/minecraft/client/renderer/LightTexture;<init>(Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/Minecraft;)V",
        at = @At(value = "NEW", target = "com/mojang/blaze3d/pipeline/TextureTarget"))
    public TextureTarget cancelFramebufferConstruction(int width, int height,
        boolean useDepth) {
        return UnsafeManager.INSTANCE.allocateInstance(TextureTarget.class);
    }

    @Redirect(method = "Lnet/minecraft/client/renderer/LightTexture;<init>(Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/Minecraft;)V",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/LightTexture;" +
                "target:Lcom/mojang/blaze3d/pipeline/TextureTarget;",
            opcode = Opcodes.PUTFIELD))
    public void writeNullFramebuffer(LightTexture instance, TextureTarget value) {
        this.target = null;
    }

    @Redirect(method = "Lnet/minecraft/client/renderer/LightTexture;<init>(Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/Minecraft;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/TextureTarget;setFilterMode(I)V"))
    public void cancelFramebufferSetTexFilter(TextureTarget instance, int i) {

    }

    @Redirect(method = "Lnet/minecraft/client/renderer/LightTexture;<init>(Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/Minecraft;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/TextureTarget;setClearColor(FFFF)V"))
    public void cancelFramebufferSetClearColor(TextureTarget instance, float r, float g,
        float b, float a) {

    }

    @Redirect(method = "Lnet/minecraft/client/renderer/LightTexture;<init>(Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/Minecraft;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/TextureTarget;clear()V"))
    public void cancelFramebufferClear(TextureTarget instance) {

    }

    @Inject(method = "Lnet/minecraft/client/renderer/LightTexture;<init>(Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/Minecraft;)V",
        at = @At("TAIL"))
    public void initJavaLightmapTexture(GameRenderer renderer, Minecraft minecraft,
        CallbackInfo ci) {
        this.radiance$texture = new DynamicTexture(16, 16, false);
        this.radiance$textureIdentifier = ResourceLocation.fromNamespaceAndPath("radiance", "dynamic/light_map");
        this.minecraft.getTextureManager()
            .register(this.radiance$textureIdentifier, this.radiance$texture);
        this.radiance$image = this.radiance$texture.getPixels();
        if (this.radiance$image == null) {
            throw new IllegalStateException("Lightmap texture image was not initialized");
        }

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                this.radiance$image.setPixel(x, y, 0xFFFFFFFF);
            }
        }

        this.radiance$texture.setClamp(true);
        this.radiance$texture.setFilter(true, false);
        this.radiance$texture.upload();
    }



    @Redirect(method = "Lnet/minecraft/client/renderer/LightTexture;close()V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/TextureTarget;destroyBuffers()V"))
    public void cancelFramebufferDelete(TextureTarget instance) {

    }

    @Inject(method = "Lnet/minecraft/client/renderer/LightTexture;close()V", at = @At("HEAD"))
    public void closeJavaLightmapTexture(CallbackInfo ci) {
        if (this.radiance$texture != null) {
            this.radiance$texture.close();
            this.radiance$texture = null;
            this.radiance$image = null;
            this.radiance$textureIdentifier = null;
        }
    }



    @Inject(method = "Lnet/minecraft/client/renderer/LightTexture;turnOffLightLayer()V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelDisable(CallbackInfo ci) {
        RenderSystem.setShaderTexture(2, 0);
        ci.cancel();
    }



    @Inject(method = "Lnet/minecraft/client/renderer/LightTexture;turnOnLightLayer()V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelEnable(CallbackInfo ci) {
        if (this.radiance$textureIdentifier != null) {
            RenderSystem.setShaderTexture(2, this.radiance$textureIdentifier);
        } else {
            RenderSystem.setShaderTexture(2, 0);
        }
        ci.cancel();
    }



    @Shadow
    protected abstract float getDarknessGamma(float delta);

    @Shadow
    protected abstract float calculateDarknessScale(LivingEntity entity, float factor, float delta);

    @Inject(method = "Lnet/minecraft/client/renderer/LightTexture;updateLightTexture(F)V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectUpdate(float delta, CallbackInfo ci) {
        if (this.updateLightTexture) {
            this.updateLightTexture = false;
            ProfilerFiller profiler = Profiler.get();
            profiler.push("lightTex");
            ClientLevel clientWorld = this.minecraft.level;
            if (clientWorld != null && this.radiance$image != null && this.radiance$texture != null) {
                float f = clientWorld.getSkyDarken(1.0F);
                float skyFactor;
                if (clientWorld.getSkyFlashTime() > 0) {
                    skyFactor = 1.0F;
                } else {
                    skyFactor = f * 0.95F + 0.05F;
                }

                float
                    h =
                    this.minecraft.options.darknessEffectScale()
                        .get()
                        .floatValue();
                float i = this.getDarknessGamma(delta) * h;
                float darknessScale = this.calculateDarknessScale(this.minecraft.player, i, delta) * h;
                float k = this.minecraft.player.getWaterVision();
                float nightVisionFactor;
                if (this.minecraft.player.hasEffect(MobEffects.NIGHT_VISION)) {
                    nightVisionFactor = GameRenderer.getNightVisionScale(this.minecraft.player,
                        delta);
                } else if (k > 0.0F && this.minecraft.player.hasEffect(
                    MobEffects.CONDUIT_POWER)) {
                    nightVisionFactor = k;
                } else {
                    nightVisionFactor = 0.0F;
                }

                Vector3f skyLightColor = new Vector3f(f, f, 1.0F).lerp(
                    new Vector3f(1.0F, 1.0F, 1.0F), 0.35F);
                float blockFactor = this.blockLightRedFlicker + 1.5F;
                float
                    ambientLightFactor =
                    clientWorld.dimensionType()
                        .ambientLight();
                boolean
                    useBrightLightmap =
                    clientWorld.effects()
                        .forceBrightLightmap();
                float
                    o =
                    this.minecraft.options.gamma()
                        .get()
                        .floatValue();

                float darkenWorldFactor = this.renderer.getDarkenWorldAmount(delta);
                float brightnessFactor = Math.max(0.0F, o - i);

                Vector3f workingColor = new Vector3f();
                for (int sky = 0; sky < 16; sky++) {
                    for (int block = 0; block < 16; block++) {
                        float skyBrightness = LightTexture.getBrightness(
                            clientWorld.dimensionType(), sky) * skyFactor;
                        float blockBrightness = LightTexture.getBrightness(
                            clientWorld.dimensionType(), block) * blockFactor;
                        float green = blockBrightness
                            * ((blockBrightness * 0.6F + 0.4F) * 0.6F + 0.4F);
                        float blue = blockBrightness * (blockBrightness * blockBrightness * 0.6F
                            + 0.4F);
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

                        float gamma = this.minecraft.options.gamma()
                            .get()
                            .floatValue();
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
                        this.radiance$image.setPixel(block, sky,
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
        vec.set(Mth.clamp(vec.x(), 0.0F, 1.0F),
            Mth.clamp(vec.y(), 0.0F, 1.0F),
            Mth.clamp(vec.z(), 0.0F, 1.0F));
    }

    @Unique
    private float radiance$easeOutQuart(float x) {
        float f = 1.0F - x;
        return 1.0F - f * f * f * f;
    }

    @Override
    public int radiance$getTextureId() {
        return this.radiance$texture != null ? this.radiance$texture.getId() : 0;
    }

    public float radiance$getAmbientLightFactor() {
        return ambientLightFactor;
    }

    public float radiance$getSkyFactor() {
        return skyFactor;
    }

    public float radiance$getBlockFactor() {
        return blockFactor;
    }

    public boolean radiance$isUseBrightLightmap() {
        return useBrightLightmap;
    }

    public Vector3f radiance$getSkyLightColor() {
        return skyLightColor;
    }

    public float radiance$getNightVisionFactor() {
        return nightVisionFactor;
    }

    public float radiance$getDarknessScale() {
        return darknessScale;
    }

    public float radiance$getDarkenWorldFactor() {
        return darkenWorldFactor;
    }

    public float radiance$getBrightnessFactor() {
        return brightnessFactor;
    }
}
