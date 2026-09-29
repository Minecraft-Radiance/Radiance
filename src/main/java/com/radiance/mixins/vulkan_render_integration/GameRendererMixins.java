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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.joml.Matrix4f;
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
    @Unique
    private Matrix4f viewMatrix;

    @Shadow
    public abstract void resetProjectionMatrix(Matrix4f projectionMatrix);

    @Shadow
    public abstract Matrix4f getProjectionMatrix(double fov);

    @Shadow
    protected abstract double getFov(Camera camera, float tickDelta, boolean changingFov);

    @Redirect(method = "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V"))
    public void performBTimesV(LevelRenderer instance,
        PoseStack matrices,
        float tickDelta,
        long limitTime,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightmapTextureManager,
        Matrix4f projectionMatrix) {
        Matrix4f rawViewMatrix = new Matrix4f(matrices.last().pose());
        Matrix4f rawProjectionMatrix = this.getProjectionMatrix(
            this.getFov(camera, tickDelta, true));
        Matrix4f effectMatrix = new Matrix4f(rawProjectionMatrix).invert()
            .mul(projectionMatrix);
        Matrix4f effectedViewMatrix = new Matrix4f(effectMatrix).mul(rawViewMatrix);
        PoseStack effectedMatrices = new PoseStack();
        effectedMatrices.last()
            .pose()
            .set(effectedViewMatrix);
        this.viewMatrix = rawViewMatrix;
        instance.renderLevel(effectedMatrices, tickDelta, limitTime, renderBlockOutline, camera,
            gameRenderer, lightmapTextureManager, rawProjectionMatrix);
    }

    @Inject(method = "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At(value = "TAIL"))
    public void buildEntities(float tickDelta, long limitTime, PoseStack matrices,
        CallbackInfo ci) {
        EntityProxy.build();
    }

    @Inject(method = "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At(value = "TAIL"))
    public void fuseWorld(float tickDelta, long limitTime, PoseStack matrices, CallbackInfo ci) {
        VulkanUiProxy.fuseWorld();
    }

    @Inject(method = "renderItemInHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/Camera;F)V",
        at = @At(value = "HEAD"), cancellable = true)
    public void redirectRenderHand(PoseStack matrices, Camera camera, float tickDelta,
        CallbackInfo ci) {
        double worldFov = this.getFov(camera, tickDelta, true);
        double handFov = this.getFov(camera, tickDelta, false);
        float handProjectionScale =
            (float) (Math.tan(Math.toRadians(worldFov * 0.5)) /
                Math.tan(Math.toRadians(handFov * 0.5)));
        EntityProxy.queueHandRebuild(renderBuffers, tickDelta, itemInHandRenderer,
            handProjectionScale);
        ci.cancel();
    }

    @Redirect(method = "render(FJZ)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    public void cancelRenderFramebufferBeginWrite(RenderTarget instance, boolean setViewport) {

    }

    @Inject(method = "render(FJZ)V", at = @At(value = "HEAD"))
    public void shouldRenderWorld(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        RendererProxy.shouldRenderWorld(
            !this.minecraft.noRender && tick && this.minecraft.level != null);
    }

    @Inject(method = "render(FJZ)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
    public void renderFirstPersonOverlaysWithGuiProjection(float tickDelta, long startTime,
        boolean tick, CallbackInfo ci) {
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
        this.resetProjectionMatrix(
            this.getProjectionMatrix(this.getFov(this.mainCamera, tickDelta, false)));
        PoseStack modelViewStack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.setIdentity();
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        try {
            ScreenEffectRenderer.renderScreenEffect(this.minecraft, new PoseStack());
        } finally {
            modelViewStack.popPose();
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
