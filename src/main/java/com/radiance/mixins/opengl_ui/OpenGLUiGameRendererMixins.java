package com.radiance.mixins.opengl_ui;

import com.llamalad7.mixinextras.sugar.Local;
import com.radiance.client.opengl_ui.OpenGLUiBridge;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.MatrixStack;
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
    public HeldItemRenderer firstPersonRenderer;

    @Final
    @Shadow
    private LightmapTextureManager lightmapTextureManager;

    @Shadow
    @Final
    private BufferBuilderStorage buffers;

    @Unique
    private Matrix4f viewMatrix;

    @Shadow
    protected abstract double getFov(Camera camera, float tickDelta, boolean changingFov);

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
        at = @At(value = "HEAD"))
    public void acquireOpenGLUiFrameContext(RenderTickCounter renderTickCounter, boolean tick,
        CallbackInfo ci) {
        RendererProxy.acquireContext();
    }

    @Redirect(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At(value = "INVOKE",
            target = "Lorg/joml/Matrix4f;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;",
            remap = false))
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
        Matrix4f b = new Matrix4f(matrixStack.peek().getPositionMatrix());
        this.viewMatrix = new Matrix4f(viewMatrix);
        viewMatrix = new Matrix4f(b.mul(viewMatrix));
        instance.render(tickCounter, renderBlockOutline, camera, gameRenderer,
            lightmapTextureManager, viewMatrix, projectionMatrix);
    }

    @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At(value = "HEAD"), cancellable = true)
    public void validateOpenGLUiWorldFrameContext(RenderTickCounter renderTickCounter, CallbackInfo ci) {
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

    @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At(value = "TAIL"))
    public void buildEntities(RenderTickCounter renderTickCounter, CallbackInfo ci) {
        EntityProxy.build();
        ChunkProxy.waitImportantChunkRebuild();
        OpenGLUiBridge.prepareFrame();
    }

    @Inject(method = "renderHand(Lnet/minecraft/client/render/Camera;FLorg/joml/Matrix4f;)V",
        at = @At(value = "HEAD"), cancellable = true)
    public void redirectRenderHand(Camera camera, float tickDelta, Matrix4f matrix4f,
        CallbackInfo ci) {
        if (EntityProxy.isEntityNativeTransferEnabled()) {
            double worldFov = this.getFov(camera, tickDelta, true);
            double handFov = this.getFov(camera, tickDelta, false);
            float handProjectionScale =
                (float) (Math.tan(Math.toRadians(worldFov * 0.5F))
                    / Math.tan(Math.toRadians(handFov * 0.5F)));
            EntityProxy.queueHandRebuild(buffers, tickDelta, firstPersonRenderer,
                handProjectionScale);
        }
        ci.cancel();
    }

    @Redirect(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    public void bindOpenGLUiFramebuffer(Framebuffer instance, boolean setViewport) {
        OpenGLUiBridge.compositePreparedWorld(instance, setViewport);
    }

    @Redirect(method = "updateWorldIcon(Ljava/nio/file/Path;)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/util/ScreenshotRecorder;takeScreenshot(Lnet/minecraft/client/gl/Framebuffer;)"
                    + "Lnet/minecraft/client/texture/NativeImage;"))
    public NativeImage redirectScreenshot(Framebuffer framebuffer) {
        return RendererProxy.takeScreenshotWithoutUI();
    }

    @Override
    public Matrix4f radiance$getRotationMatrix() {
        return viewMatrix;
    }

}
