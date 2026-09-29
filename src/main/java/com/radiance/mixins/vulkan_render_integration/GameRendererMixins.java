package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.vulkan.VulkanUiProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
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
    public HeldItemRenderer firstPersonRenderer;
    @Final
    @Shadow
    private MinecraftClient client;
    @Shadow
    @Final
    private LightmapTextureManager lightmapTextureManager;
    @Shadow
    @Final
    private BufferBuilderStorage buffers;
    @Shadow
    @Final
    private Camera camera;
    @Unique
    private Matrix4f viewMatrix;

    @Shadow
    public abstract void loadProjectionMatrix(Matrix4f projectionMatrix);

    @Shadow
    public abstract Matrix4f getBasicProjectionMatrix(double fov);

    @Shadow
    protected abstract double getFov(Camera camera, float tickDelta, boolean changingFov);

    @Redirect(method = "renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/render/WorldRenderer;render"
                    + "(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;"
                    + "Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;"
                    + "Lorg/joml/Matrix4f;)V"))
    public void performBTimesV(WorldRenderer instance,
        MatrixStack matrices,
        float tickDelta,
        long limitTime,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightmapTextureManager lightmapTextureManager,
        Matrix4f projectionMatrix) {
        Matrix4f rawViewMatrix = new Matrix4f(matrices.peek().getPositionMatrix());
        Matrix4f rawProjectionMatrix = this.getBasicProjectionMatrix(
            this.getFov(camera, tickDelta, true));
        Matrix4f effectMatrix = new Matrix4f(rawProjectionMatrix).invert()
            .mul(projectionMatrix);
        Matrix4f effectedViewMatrix = new Matrix4f(effectMatrix).mul(rawViewMatrix);
        MatrixStack effectedMatrices = new MatrixStack();
        effectedMatrices.peek()
            .getPositionMatrix()
            .set(effectedViewMatrix);
        this.viewMatrix = rawViewMatrix;
        instance.render(effectedMatrices, tickDelta, limitTime, renderBlockOutline, camera,
            gameRenderer, lightmapTextureManager, rawProjectionMatrix);
    }

    @Inject(method = "renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V", at = @At(value = "TAIL"))
    public void buildEntities(float tickDelta, long limitTime, MatrixStack matrices,
        CallbackInfo ci) {
        EntityProxy.build();
    }

    @Inject(method = "renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V", at = @At(value = "TAIL"))
    public void fuseWorld(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        VulkanUiProxy.fuseWorld();
    }

    @Inject(method = "renderHand(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/Camera;F)V",
        at = @At(value = "HEAD"), cancellable = true)
    public void redirectRenderHand(MatrixStack matrices, Camera camera, float tickDelta,
        CallbackInfo ci) {
        double worldFov = this.getFov(camera, tickDelta, true);
        double handFov = this.getFov(camera, tickDelta, false);
        float handProjectionScale =
            (float) (Math.tan(Math.toRadians(worldFov * 0.5)) /
                Math.tan(Math.toRadians(handFov * 0.5)));
        EntityProxy.queueHandRebuild(buffers, tickDelta, firstPersonRenderer,
            handProjectionScale);
        ci.cancel();
    }

    @Redirect(method = "render(FJZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    public void cancelRenderFramebufferBeginWrite(Framebuffer instance, boolean setViewport) {

    }

    @Inject(method = "render(FJZ)V", at = @At(value = "HEAD"))
    public void shouldRenderWorld(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        RendererProxy.shouldRenderWorld(
            !this.client.skipGameRender && tick && this.client.world != null);
    }

    @Inject(method = "render(FJZ)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;F)V"))
    public void renderFirstPersonOverlaysWithGuiProjection(float tickDelta, long startTime,
        boolean tick, CallbackInfo ci) {
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
        this.loadProjectionMatrix(
            this.getBasicProjectionMatrix(this.getFov(this.camera, tickDelta, false)));
        MatrixStack modelViewStack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        modelViewStack.push();
        modelViewStack.loadIdentity();
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        try {
            InGameOverlayRenderer.renderOverlays(this.client, new MatrixStack());
        } finally {
            modelViewStack.pop();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();
        }
    }

    @Override
    public Matrix4f radiance$getRotationMatrix() {
        return viewMatrix;
    }

    @Redirect(method = "updateWorldIcon(Ljava/nio/file/Path;)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/util/ScreenshotRecorder;takeScreenshot(Lnet/minecraft/client/gl/Framebuffer;)"
                    +
                    "Lnet/minecraft/client/texture/NativeImage;"))
    public NativeImage redirectScreenshot(Framebuffer framebuffer) {
        return RendererProxy.takeScreenshotWithoutUI();
    }
}
