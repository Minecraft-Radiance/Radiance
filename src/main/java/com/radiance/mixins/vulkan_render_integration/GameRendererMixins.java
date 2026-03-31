package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.Pool;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceFactory;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixins implements IGameRendererExt {

    @Shadow
    @Final
    public HeldItemRenderer firstPersonRenderer;
    @Mutable
    @Final
    @Shadow
    private LightmapTextureManager lightmapTextureManager;
    @Final
    @Shadow
    private MinecraftClient client;
    @Final
    @Shadow
    private Pool pool;
    @Shadow
    @Final
    private BufferBuilderStorage buffers;
    @Unique
    private Matrix4f viewMatrix;

    @Redirect(method = "preloadPrograms(Lnet/minecraft/resource/ResourceFactory;)V",
        at = @At(value = "INVOKE",
            target =
                "Lnet/minecraft/client/gl/ShaderLoader;preload(Lnet/minecraft/resource/ResourceFactory;"
                    +
                    "[Lnet/minecraft/client/gl/ShaderProgramKey;)V"))
    public void cancelPreloadShader(ShaderLoader instance, ResourceFactory factory,
        ShaderProgramKey[] keys) {
        // Intentionally empty: Radiance handles shader loading through its own pipeline
    }

    @Inject(method = "renderBlur()V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectRenderBlur(CallbackInfo ci) {
        float f = this.client.options.getMenuBackgroundBlurrinessValue();

        //if (this.client.world == null && this.client.currentScreen != null && !(f < 1.0F)) {
        if (!(f < 1.0F)) {
            BufferProxy.updateOverlayPostUniform(f);
            RendererProxy.postBlur();
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
                "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;"
                    +
                    "Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;"
                    +
                    "Lnet/minecraft/client/render/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    public void performBTimesV(WorldRenderer instance,
        ObjectAllocator allocator,
        RenderTickCounter tickCounter,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        Matrix4f viewMatrix,
        Matrix4f projectionMatrix,
        @Local boolean shouldRenderBlockOutline,
        @Local MatrixStack matrixStack) {
        Matrix4f
            B =
            new Matrix4f(matrixStack.peek()
                .getPositionMatrix());
        this.viewMatrix = new Matrix4f(viewMatrix);
        viewMatrix = new Matrix4f(B.mul(viewMatrix));
        instance.render(this.pool, tickCounter, shouldRenderBlockOutline, camera, gameRenderer,
            viewMatrix, projectionMatrix);
    }

    @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At(value = "TAIL"))
    public void buildEntities(RenderTickCounter renderTickCounter, CallbackInfo ci) {
        EntityProxy.build();
    }

    @Redirect(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    public void cancelFramebufferBeginWrite(Framebuffer instance, boolean setViewport) {

    }

    @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At(value = "TAIL"))
    public void fuseWorld(RenderTickCounter renderTickCounter, CallbackInfo ci) {
        RendererProxy.fuseWorld();
    }

    @Inject(method = "renderHand(Lnet/minecraft/client/render/Camera;FLorg/joml/Matrix4f;)V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectRenderHand(Camera camera, float tickDelta, Matrix4f matrix4f,
        CallbackInfo ci) {
        EntityProxy.queueHandRebuild(buffers, tickDelta, firstPersonRenderer);
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
