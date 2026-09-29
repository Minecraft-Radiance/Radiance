package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.vulkan.VulkanUiProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixins implements IGameRendererExt {

    @Shadow
    @Final
    public ItemInHandRenderer itemInHandRenderer;
    @Final
    @Shadow
    private Minecraft minecraft;
    @Shadow
    @Final
    private LightTexture lightTexture;
    @Shadow
    @Final
    private RenderBuffers renderBuffers;
    @Shadow
    @Final
    private Camera mainCamera;
    @Shadow
    @Nullable
    private PostChain blurEffect;
    @Shadow
    @Nullable
    private PostChain postEffect;
    @Shadow
    private boolean effectActive;
    @Unique
    private Matrix4f viewMatrix;

    @Shadow
    public abstract void resetProjectionMatrix(Matrix4f projectionMatrix);

    @Shadow
    public abstract Matrix4f getProjectionMatrix(double fov);

    @Shadow
    protected abstract double getFov(Camera camera, float tickDelta, boolean changingFov);

    @Inject(method = "loadBlurEffect(Lnet/minecraft/server/packs/resources/ResourceProvider;)V",
        at = @At("HEAD"), cancellable = true)
    private void skipVanillaBlurPostProcessorWhenFramebufferMissing(ResourceProvider resourceFactory,
        CallbackInfo ci) {
        if (this.minecraft.getMainRenderTarget() != null) {
            return;
        }
        if (this.blurEffect != null) {
            this.blurEffect.close();
            this.blurEffect = null;
        }
        ci.cancel();
    }

    @Inject(method = "loadEffect(Lnet/minecraft/resources/ResourceLocation;)V",
        at = @At("HEAD"), cancellable = true)
    private void skipVanillaPostProcessorWhenFramebufferMissing(ResourceLocation id, CallbackInfo ci) {
        if (this.minecraft.getMainRenderTarget() != null) {
            return;
        }
        if (this.postEffect != null) {
            this.postEffect.close();
            this.postEffect = null;
        }
        this.effectActive = false;
        ci.cancel();
    }

    @Inject(method = "processBlurEffect(F)V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectRenderBlur(float delta, CallbackInfo ci) {
        float f = this.minecraft.options.getMenuBackgroundBlurriness();

        if (!(f < 1.0F)) {
            BufferProxy.updateOverlayPostUniform(f);
            VulkanUiProxy.postBlur();
        }

        ci.cancel();
    }

    @Redirect(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;", remap = false))
    public Matrix4f cancelPTimesB(Matrix4f instance, Matrix4fc right) {
        return instance;
    }

    @Redirect(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    public void performBTimesV(LevelRenderer instance,
        DeltaTracker tickCounter,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightmapTextureManager,
        Matrix4f viewMatrix,
        Matrix4f projectionMatrix,
        @Local PoseStack matrixStack) {
        Matrix4f B = new Matrix4f(matrixStack.last().pose());
        this.viewMatrix = new Matrix4f(viewMatrix);
        viewMatrix = new Matrix4f(B.mul(viewMatrix));
        instance.renderLevel(tickCounter, renderBlockOutline, camera, gameRenderer,
            lightmapTextureManager, viewMatrix, projectionMatrix);
    }

    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "TAIL"))
    public void buildEntities(DeltaTracker renderTickCounter, CallbackInfo ci) {
        EntityProxy.build();
    }

    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V", at = @At(value = "TAIL"))
    public void fuseWorld(DeltaTracker renderTickCounter, CallbackInfo ci) {
        VulkanUiProxy.fuseWorld();
    }

    @Inject(method = "renderItemInHand(Lnet/minecraft/client/Camera;FLorg/joml/Matrix4f;)V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectRenderHand(Camera camera, float tickDelta, Matrix4f matrix4f,
        CallbackInfo ci) {
        if (EntityProxy.isEntityNativeTransferEnabled()) {
            double worldFov = this.getFov(camera, tickDelta, true);
            double handFov = this.getFov(camera, tickDelta, false);
            float handProjectionScale =
                (float) (Math.tan(Math.toRadians(worldFov * 0.5)) /
                    Math.tan(Math.toRadians(handFov * 0.5)));
            EntityProxy.queueHandRebuild(renderBuffers, tickDelta, itemInHandRenderer,
                handProjectionScale);
        }
        ci.cancel();
    }

    @Redirect(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    public void cancelRenderFramebufferBeginWrite(RenderTarget instance, boolean setViewport) {

    }

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At(value = "HEAD"))
    public void shouldRenderWorld(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
        RendererProxy.shouldRenderWorld(
            !this.minecraft.noRender && minecraft.isGameLoadFinished() && tick
                && minecraft.level != null);
    }

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
    public void renderFirstPersonOverlaysWithGuiProjection(DeltaTracker tickCounter,
        boolean tick, CallbackInfo ci, @Local GuiGraphics drawContext) {
        float tickDelta = tickCounter.getGameTimeDeltaPartialTick(true);
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
        this.resetProjectionMatrix(
            this.getProjectionMatrix(this.getFov(this.mainCamera, tickDelta, false)));
        Matrix4fStack modelViewStack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        try {
            ScreenEffectRenderer.renderScreenEffect(this.minecraft, new PoseStack());
        } finally {
            modelViewStack.popMatrix();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();
        }
    }

    @Override
    public Matrix4f radiance$getRotationMatrix() {
        return viewMatrix;
    }

    @Redirect(method = "takeAutoScreenshot(Ljava/nio/file/Path;)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/Screenshot;takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lcom/mojang/blaze3d/platform/NativeImage;"))
    public NativeImage redirectScreenshot(RenderTarget framebuffer) {
        return RendererProxy.takeScreenshotWithoutUI();
    }
}
