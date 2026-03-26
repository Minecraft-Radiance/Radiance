package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.world.EntityProxy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixins {

    @Shadow
    @Final
    private HeldItemRenderer firstPersonRenderer;

    @Shadow
    @Final
    private LightmapTextureManager lightmapTextureManager;

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private BufferBuilderStorage buffers;

    @Inject(method = "preloadPrograms(Lnet/minecraft/resource/ResourceFactory;)V",
        at = @At("HEAD"), cancellable = true)
    private void cancelPreloadPrograms(ResourceFactory factory, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "loadPrograms(Lnet/minecraft/resource/ResourceFactory;)V",
        at = @At("HEAD"), cancellable = true)
    private void cancelLoadPrograms(ResourceFactory factory, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V",
        at = @At("TAIL"))
    private void buildAndFuseWorld(float tickDelta, long limitTime, MatrixStack matrices,
        CallbackInfo ci) {
        RendererProxy.fuseWorld();
    }

    @Inject(method = "renderHand(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/Camera;F)V",
        at = @At("HEAD"), cancellable = true)
    private void redirectRenderHand(MatrixStack matrices, Camera camera, float tickDelta,
        CallbackInfo ci) {
        EntityProxy.queueHandRebuild(this.buffers, tickDelta, this.firstPersonRenderer);
        ci.cancel();
    }

    @Inject(method = "render(FJZ)V", at = @At("HEAD"))
    private void shouldRenderWorld(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        RendererProxy.shouldRenderWorld(
            !this.client.skipGameRender && tick && this.client.world != null);
    }

    @Redirect(method = "render(FJZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    private void cancelFramebufferBeginWrite(Framebuffer framebuffer, boolean setViewport) {
    }

    @Redirect(method = "render(FJZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;endWrite()V"),
        require = 0)
    private void cancelFramebufferEndWrite(Framebuffer framebuffer) {
    }

    @Redirect(method = "render(FJZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;draw(II)V"),
        require = 0)
    private void cancelFramebufferDraw(Framebuffer framebuffer, int width, int height) {
    }

    @Redirect(method = "updateWorldIcon(Ljava/nio/file/Path;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/ScreenshotRecorder;takeScreenshot(Lnet/minecraft/client/gl/Framebuffer;)Lnet/minecraft/client/texture/NativeImage;"))
    private NativeImage redirectScreenshot(Framebuffer framebuffer) {
        return RendererProxy.takeScreenshotWithoutUI();
    }
}
