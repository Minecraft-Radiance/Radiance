package com.radiance.mixins.opengl_ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.TimerQuery;
import com.radiance.client.RadianceClient;
import com.radiance.client.option.Options;
import com.radiance.client.opengl_ui.OpenGLUiBridge;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.texture.AuxiliaryTextureReloader;
import com.radiance.client.proxy.world.ChunkProxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.main.GameConfig;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class OpenGLUiMinecraftClientMixins {

    @Shadow
    @Final
    private Window window;

    @Shadow
    @Final
    @Mutable
    private ReloadableResourceManager resourceManager;

    @Inject(method = "useAmbientOcclusion()Z", at = @At(value = "HEAD"), cancellable = true)
    private static void disableAmbientOcclusion(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "runTick(Z)V", at = @At("HEAD"), cancellable = true)
    public void shouldRenderWorld(boolean tick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        RendererProxy.shouldRenderWorld(
            !client.noRender && tick && client.level != null);
        if (!OpenGLUiBridge.processPendingResize()) {
            ci.cancel();
        }
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;initRenderer(IZ)V"))
    public void initOpenGLUiAndNativeRenderer(int debugVerbosity, boolean debugSync) {
        RadianceClient.initialize();

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

    @Redirect(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;resourceManager:Lnet/minecraft/server/packs/resources/ReloadableResourceManager;",
            opcode = Opcodes.PUTFIELD))
    private void registerAuxiliaryTextureReloader(Minecraft instance, ReloadableResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        resourceManager.registerReloadListener(new AuxiliaryTextureReloader());
    }

    @Redirect(method = "runTick(Z)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(II)V"))
    public void cancelFramebufferDraw(RenderTarget instance, int width, int height) {
    }

    @Redirect(method = "runTick(Z)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;unbindWrite()V"))
    public void finishOpenGLUiFramebuffer(RenderTarget instance, boolean tick) {
        OpenGLUiBridge.submitCompositedFrame(instance);
    }

    @Redirect(method = "runTick(Z)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
    public void disableFPSLimit(int fps) {
    }

    @Redirect(method = "runTick(Z)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/TimerQuery;getInstance()Ljava/util/Optional;"))
    public Optional<TimerQuery> disableGLTimerInstance() {
        return Optional.empty();
    }

    @Inject(method = "close()V", at = @At(value = "HEAD"))
    public void saveRadianceState(CallbackInfo ci) {
        Pipeline.savePipeline();
        Options.overwriteConfig();
    }

    @Inject(method = "close()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/VirtualScreen;close()V"))
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

    @Redirect(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V"))
    public void cancelRenderAfterStop(Minecraft instance, boolean tick) {
    }

    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V",
        at = @At(value = "HEAD"))
    public void resetBuiltChunkNum(Screen disconnectionScreen, CallbackInfo ci) {
        ChunkProxy.builtChunkNum = 0;
    }
}
