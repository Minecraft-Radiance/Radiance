package com.radiance.mixins.opengl_ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.option.Options;
import com.radiance.client.opengl_ui.OpenGLUiBridge;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.texture.AuxiliaryTextureReloader;
import com.radiance.client.proxy.world.ChunkProxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlTimer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.resource.ReloadableResourceManagerImpl;
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
public class OpenGLUiMinecraftClientMixins {

    @Shadow
    @Final
    private Window window;

    @Shadow
    private ReloadableResourceManagerImpl resourceManager;

    @Inject(method = "isAmbientOcclusionEnabled()Z", at = @At(value = "HEAD"), cancellable = true)
    private static void disableAmbientOcclusion(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "render(Z)V", at = @At("HEAD"), cancellable = true)
    public void shouldRenderWorld(boolean tick, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        RendererProxy.shouldRenderWorld(
            !client.skipGameRender && tick && client.world != null);
        if (!OpenGLUiBridge.processPendingResize()) {
            ci.cancel();
        }
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;initRenderer(IZ)V"))
    public void initOpenGLUiAndNativeRenderer(int debugVerbosity, boolean debugSync) {
        OpenGLUiBridge.initializeOpenGLContext();
        RenderSystem.initRenderer(debugVerbosity, debugSync);
        OpenGLUiBridge.DeviceIdentity deviceIdentity = OpenGLUiBridge.queryDeviceIdentity();
        RendererProxy.prepareOpenGLPresentationTarget(window);

        long stackSize = 512 * 1024 * 1024;
        AtomicReference<Throwable> initFailure = new AtomicReference<>();
        Runnable init = () -> {
            try {
                RendererProxy.initRendererWithOpenGLDevice(
                    deviceIdentity.deviceUuids(), deviceIdentity.driverUuid());
                Pipeline.collectNativeModules();
            } catch (Throwable error) {
                initFailure.set(error);
            }
        };

        OpenGLUiBridge.releaseContextForVulkanInitialization();
        try {
            Thread initThread = new Thread(null, init, "Radiance Vulkan initialization",
                stackSize);
            initThread.start();
            try {
                initThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        } finally {
            OpenGLUiBridge.restoreContextAfterVulkanInitialization();
        }
        if (initFailure.get() != null) {
            throw new IllegalStateException(
                "Failed to initialize Vulkan on the device selected by the OpenGL UI context",
                initFailure.get());
        }

        Pipeline.loadPipeline();
        Pipeline.build();
        RenderSystem.apiDescription = "Vulkan 1.4 + OpenGL UI";
    }

    @Inject(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/MinecraftClient;resourceManager:Lnet/minecraft/resource/ReloadableResourceManagerImpl;",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER))
    private void registerAuxiliaryTextureReloader(RunArgs args, CallbackInfo ci) {
        this.resourceManager.registerReloader(new AuxiliaryTextureReloader());
    }

    @Redirect(method = "render(Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;draw(II)V"))
    public void cancelFramebufferDraw(Framebuffer instance, int width, int height) {
    }

    @Redirect(method = "render(Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;endWrite()V"))
    public void finishOpenGLUiFramebuffer(Framebuffer instance, boolean tick) {
        OpenGLUiBridge.submitCompositedFrame(instance);
    }

    @Redirect(method = "render(Z)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
    public void disableFPSLimit(int fps) {
    }

    @Redirect(method = "render(Z)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gl/GlTimer;getInstance()Ljava/util/Optional;"))
    public Optional<GlTimer> disableGLTimerInstance() {
        return Optional.empty();
    }

    @Inject(method = "close()V", at = @At(value = "HEAD"))
    public void saveRadianceState(CallbackInfo ci) {
        Pipeline.savePipeline();
        Options.overwriteConfig();
    }

    @Inject(method = "close()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/WindowProvider;close()V"))
    public void closeRenderBackends(CallbackInfo ci) {
        OpenGLUiBridge.closeOpenGLContext();
        Thread closeThread = new Thread(RendererProxy::close, "Radiance Vulkan shutdown");
        closeThread.start();
        try {
            closeThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            RendererProxy.releaseOpenGLPresentationTarget();
        }
    }

    @Redirect(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;render(Z)V"))
    public void cancelRenderAfterStop(MinecraftClient instance, boolean tick) {
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V",
        at = @At(value = "HEAD"))
    public void resetBuiltChunkNum(Screen disconnectionScreen, CallbackInfo ci) {
        ChunkProxy.builtChunkNum = 0;
    }
}
