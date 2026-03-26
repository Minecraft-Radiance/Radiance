package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.UnsafeManager;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.WindowEventHandler;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlTimer;
import net.minecraft.client.gl.WindowFramebuffer;
import net.minecraft.client.util.Window;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixins {

    @Shadow
    @Final
    private Window window;

    @Inject(method = "isAmbientOcclusionEnabled()Z", at = @At("HEAD"), cancellable = true)
    private static void disableAmbientOcclusion(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;initRenderer(IZ)V"))
    private void initRenderer(int debugVerbosity, boolean debugSync, RunArgs args) {
        long stackSize = 512L * 1024L * 1024L;
        Runnable initTask = () -> {
            RendererProxy.initRenderer(window);
            Pipeline.collectNativeModules();
        };

        Thread initThread = new Thread(null, initTask, "radiance-renderer-init", stackSize);
        initThread.start();
        try {
            initThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        Pipeline.loadPipeline();
        Pipeline.build();
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "NEW", target = "net/minecraft/client/gl/WindowFramebuffer"))
    private WindowFramebuffer cancelNewFramebuffer(int width, int height) {
        return UnsafeManager.INSTANCE.allocateInstance(WindowFramebuffer.class);
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/MinecraftClient;framebuffer:Lnet/minecraft/client/gl/Framebuffer;",
            opcode = Opcodes.PUTFIELD))
    private void suppressFramebufferAssignment(MinecraftClient instance, Framebuffer value) {
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;setClearColor(FFFF)V"))
    private void cancelSetClearColor(Framebuffer instance, float r, float g, float b, float a) {
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;clear(Z)V"))
    private void cancelClear(Framebuffer instance, boolean getError) {
    }

    @Redirect(method = "<init>",
        at = @At(value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/client/gl/Framebuffer;textureWidth:I",
            ordinal = 0))
    private int redirectFramebufferTextureWidth(Framebuffer framebuffer) {
        return this.window.getFramebufferWidth();
    }

    @Redirect(method = "<init>",
        at = @At(value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/client/gl/Framebuffer;textureHeight:I"),
        require = 0)
    private int redirectFramebufferTextureHeight(Framebuffer framebuffer) {
        return this.window.getFramebufferHeight();
    }

    @Redirect(method = "render(Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    private void cancelFramebufferBeginWrite(Framebuffer instance, boolean setViewport) {
    }

    @Redirect(method = "render(Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;endWrite()V"))
    private void cancelFramebufferEndWrite(Framebuffer instance) {
        ChunkProxy.waitImportantChunkRebuild();
        RendererProxy.submitCommandAndPresent();
        RendererProxy.acquireContext();
    }

    @Redirect(method = "render(Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;draw(II)V"))
    private void cancelFramebufferDraw(Framebuffer instance, int width, int height) {
    }

    @Redirect(method = "render(Z)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
    private void disableFpsLimit(int fps) {
    }

    @Redirect(method = "render(Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/GlTimer;getInstance()Ljava/util/Optional;"))
    private Optional<GlTimer> disableGlTimer() {
        return Optional.empty();
    }

    @Redirect(method = "onResolutionChanged()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;resize(IIZ)V"))
    private void cancelFramebufferResize(Framebuffer instance, int width, int height,
        boolean getError) {
    }

    @Inject(method = "scheduleStop()V", at = @At("TAIL"))
    private void closeRenderer(CallbackInfo ci) {
        RendererProxy.close();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"))
    private void resetBuiltChunkNum(CallbackInfo ci) {
        ChunkProxy.builtChunkNum = 0;
    }
}
