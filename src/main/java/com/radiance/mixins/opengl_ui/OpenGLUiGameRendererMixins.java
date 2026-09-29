package com.radiance.mixins.opengl_ui;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.opengl_ui.OpenGLUiBridge;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderBuffers;
import org.joml.Matrix4f;
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
public abstract class OpenGLUiGameRendererMixins implements IGameRendererExt {

    @Shadow
    @Final
    public ItemInHandRenderer itemInHandRenderer;

    @Final
    @Shadow
    private LightTexture lightTexture;

    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Unique
    private Matrix4f viewMatrix;

    @Shadow
    protected abstract double getFov(Camera camera, float tickDelta, boolean changingFov);

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
        at = @At(value = "HEAD"))
    public void acquireOpenGLUiFrameContext(DeltaTracker renderTickCounter, boolean tick,
        CallbackInfo ci) {
        RendererProxy.acquireContext();
    }

    @Redirect(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "INVOKE",
            target = "Lorg/joml/Matrix4f;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;",
            remap = false))
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
        Matrix4f b = new Matrix4f(matrixStack.last().pose());
        this.viewMatrix = new Matrix4f(viewMatrix);
        viewMatrix = new Matrix4f(b.mul(viewMatrix));
        instance.renderLevel(tickCounter, renderBlockOutline, camera, gameRenderer,
            lightmapTextureManager, viewMatrix, projectionMatrix);
    }

    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "HEAD"), cancellable = true)
    public void validateOpenGLUiWorldFrameContext(DeltaTracker renderTickCounter, CallbackInfo ci) {
        if (OpenGLUiBridge.isResizePending() && !OpenGLUiBridge.hasActiveFrame()) {
            ci.cancel();
            return;
        }
        RendererProxy.shouldRenderWorld(true);
        RendererProxy.acquireContext();
        if (!OpenGLUiBridge.hasActiveFrame()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "TAIL"))
    public void buildEntities(DeltaTracker renderTickCounter, CallbackInfo ci) {
        EntityProxy.build();
        ChunkProxy.waitImportantChunkRebuild();
        OpenGLUiBridge.prepareFrame();
    }

    @Inject(method = "renderItemInHand(Lnet/minecraft/client/Camera;FLorg/joml/Matrix4f;)V",
        at = @At(value = "HEAD"), cancellable = true)
    public void redirectRenderHand(Camera camera, float tickDelta, Matrix4f matrix4f,
        CallbackInfo ci) {
        if (EntityProxy.isEntityNativeTransferEnabled()) {
            double worldFov = this.getFov(camera, tickDelta, true);
            double handFov = this.getFov(camera, tickDelta, false);
            float handProjectionScale =
                (float) (Math.tan(Math.toRadians(worldFov * 0.5F))
                    / Math.tan(Math.toRadians(handFov * 0.5F)));
            EntityProxy.queueHandRebuild(renderBuffers, tickDelta, itemInHandRenderer,
                handProjectionScale);
        }
        ci.cancel();
    }

    @Redirect(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    public void bindOpenGLUiFramebuffer(RenderTarget instance, boolean setViewport) {
        OpenGLUiBridge.compositePreparedWorld(instance, setViewport);
    }

    @Redirect(method = "takeAutoScreenshot(Ljava/nio/file/Path;)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/Screenshot;takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lcom/mojang/blaze3d/platform/NativeImage;"))
    public NativeImage redirectScreenshot(RenderTarget framebuffer) {
        return RendererProxy.takeScreenshotWithoutUI();
    }

    @Override
    public Matrix4f radiance$getRotationMatrix() {
        return viewMatrix;
    }

}
