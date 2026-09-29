package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.vulkan.VulkanUiProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.resource.ResourceFactory;
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
    @Shadow
    @Nullable
    private PostEffectProcessor blurPostProcessor;
    @Shadow
    @Nullable
    private PostEffectProcessor postProcessor;
    @Shadow
    private boolean postProcessorEnabled;
    @Unique
    private Matrix4f viewMatrix;

    @Shadow
    public abstract void loadProjectionMatrix(Matrix4f projectionMatrix);

    @Shadow
    public abstract Matrix4f getBasicProjectionMatrix(double fov);

    @Shadow
    protected abstract double getFov(Camera camera, float tickDelta, boolean changingFov);

    @Inject(method = "loadBlurPostProcessor(Lnet/minecraft/resource/ResourceFactory;)V",
        at = @At("HEAD"), cancellable = true)
    private void skipVanillaBlurPostProcessorWhenFramebufferMissing(ResourceFactory resourceFactory,
        CallbackInfo ci) {
        if (this.client.getFramebuffer() != null) {
            return;
        }
        if (this.blurPostProcessor != null) {
            this.blurPostProcessor.close();
            this.blurPostProcessor = null;
        }
        ci.cancel();
    }

    @Inject(method = "loadPostProcessor(Lnet/minecraft/util/Identifier;)V",
        at = @At("HEAD"), cancellable = true)
    private void skipVanillaPostProcessorWhenFramebufferMissing(Identifier id, CallbackInfo ci) {
        if (this.client.getFramebuffer() != null) {
            return;
        }
        if (this.postProcessor != null) {
            this.postProcessor.close();
            this.postProcessor = null;
        }
        this.postProcessorEnabled = false;
        ci.cancel();
    }

    @Inject(method = "renderBlur(F)V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectRenderBlur(float delta, CallbackInfo ci) {
        float f = this.client.options.getMenuBackgroundBlurrinessValue();

        if (!(f < 1.0F)) {
            BufferProxy.updateOverlayPostUniform(f);
            VulkanUiProxy.postBlur();
        }

        ci.cancel();
    }

    @Redirect(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;", remap = false))
    public Matrix4f cancelPTimesB(Matrix4f instance, Matrix4fc right) {
        return instance;
    }

    @Redirect(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/render/WorldRenderer;render"
                    + "(Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;"
                    + "Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;"
                    + "Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    public void performBTimesV(WorldRenderer instance,
        RenderTickCounter tickCounter,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightmapTextureManager lightmapTextureManager,
        Matrix4f viewMatrix,
        Matrix4f projectionMatrix,
        @Local MatrixStack matrixStack) {
        Matrix4f B = new Matrix4f(matrixStack.peek().getPositionMatrix());
        this.viewMatrix = new Matrix4f(viewMatrix);
        viewMatrix = new Matrix4f(B.mul(viewMatrix));
        instance.render(tickCounter, renderBlockOutline, camera, gameRenderer,
            lightmapTextureManager, viewMatrix, projectionMatrix);
    }

    @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At(value = "TAIL"))
    public void buildEntities(RenderTickCounter renderTickCounter, CallbackInfo ci) {
        EntityProxy.build();
    }

    @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At(value = "TAIL"))
    public void fuseWorld(RenderTickCounter renderTickCounter, CallbackInfo ci) {
        VulkanUiProxy.fuseWorld();
    }

    @Inject(method = "renderHand(Lnet/minecraft/client/render/Camera;FLorg/joml/Matrix4f;)V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectRenderHand(Camera camera, float tickDelta, Matrix4f matrix4f,
        CallbackInfo ci) {
        if (EntityProxy.isEntityNativeTransferEnabled()) {
            double worldFov = this.getFov(camera, tickDelta, true);
            double handFov = this.getFov(camera, tickDelta, false);
            float handProjectionScale =
                (float) (Math.tan(Math.toRadians(worldFov * 0.5)) /
                    Math.tan(Math.toRadians(handFov * 0.5)));
            EntityProxy.queueHandRebuild(buffers, tickDelta, firstPersonRenderer,
                handProjectionScale);
        }
        ci.cancel();
    }

    @Redirect(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    public void cancelRenderFramebufferBeginWrite(Framebuffer instance, boolean setViewport) {

    }

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At(value = "HEAD"))
    public void shouldRenderWorld(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        RendererProxy.shouldRenderWorld(
            !this.client.skipGameRender && client.isFinishedLoading() && tick
                && client.world != null);
    }

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;"
                    + "Lnet/minecraft/client/render/RenderTickCounter;)V"))
    public void renderFirstPersonOverlaysWithGuiProjection(RenderTickCounter tickCounter,
        boolean tick, CallbackInfo ci, @Local DrawContext drawContext) {
        float tickDelta = tickCounter.getTickDelta(true);
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
        this.loadProjectionMatrix(
            this.getBasicProjectionMatrix(this.getFov(this.camera, tickDelta, false)));
        Matrix4fStack modelViewStack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        try {
            InGameOverlayRenderer.renderOverlays(this.client, new MatrixStack());
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
