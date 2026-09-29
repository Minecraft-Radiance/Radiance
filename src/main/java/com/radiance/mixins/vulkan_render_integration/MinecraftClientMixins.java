package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.TimerQuery;
import com.radiance.client.RadianceClient;
import com.radiance.client.UnsafeManager;
import com.radiance.client.option.Options;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.AuxiliaryTextureReloader;
import com.radiance.client.proxy.world.ChunkProxy;
import java.util.Optional;
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
public class MinecraftClientMixins {

    @Shadow
    @Final
    private Window window;

    @Shadow
    @Final
    @Mutable
    private ReloadableResourceManager resourceManager;


    @Inject(method = "Lnet/minecraft/client/Minecraft;useAmbientOcclusion()Z", at = @At(value = "HEAD"), cancellable = true)
    private static void disableAmbientOcclusion(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }



    @Redirect(method = "Lnet/minecraft/client/Minecraft;<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;initRenderer(IZ)V"))
    public void initRenderer(int debugVerbosity, boolean debugSync) {
        RadianceClient.initialize();

        long stackSize = 512 * 1024 * 1024;
        Runnable myRunnable = () -> {
            RendererProxy.initRenderer(window);
            Pipeline.collectNativeModules();
        };

        Thread myThread = new Thread(null, myRunnable, "", stackSize);
        myThread.start();
        try {
            myThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Pipeline.loadPipeline();
        Pipeline.build();
    }

    @Redirect(method = "Lnet/minecraft/client/Minecraft;<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(value = "NEW", target = "com/mojang/blaze3d/pipeline/MainTarget"))
    public MainTarget cancelNewFramebuffer(int width, int height) {
        return UnsafeManager.INSTANCE.allocateInstance(MainTarget.class);
    }

    @Redirect(method = "Lnet/minecraft/client/Minecraft;<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;resourceManager:Lnet/minecraft/server/packs/resources/ReloadableResourceManager;",
            opcode = Opcodes.PUTFIELD))
    private void registerAuxiliaryTextureReloader(Minecraft instance,
        ReloadableResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        resourceManager.registerReloadListener(new AuxiliaryTextureReloader());
    }

    @Redirect(method = "Lnet/minecraft/client/Minecraft;<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;mainRenderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;",
            opcode = org.objectweb.asm.Opcodes.PUTFIELD))
    public void writeNullFramebuffer(Minecraft instance, RenderTarget value) {
    }

    @Redirect(method = "Lnet/minecraft/client/Minecraft;<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;setClearColor(FFFF)V"))
    public void cancelSetClearColor(RenderTarget instance, float r, float g, float b, float a) {

    }

    @Redirect(method = "Lnet/minecraft/client/Minecraft;<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;clear()V"))
    public void cancelClear(RenderTarget instance) {

    }

    @Redirect(method = "Lnet/minecraft/client/Minecraft;<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;width:I",
            ordinal = 0))
    public int redirectFramebufferTextureWidth(RenderTarget framebuffer) {
        return this.window.getWidth();
    }

    @Redirect(method = "Lnet/minecraft/client/Minecraft;<init>(Lnet/minecraft/client/main/GameConfig;)V",
        at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;height:I"),
        require = 0)
    public int redirectFramebufferTextureHeight(RenderTarget framebuffer) {
        return this.window.getHeight();
    }



    @Redirect(method = "Lnet/minecraft/client/Minecraft;runTick(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    public void cancelFramebufferBeginWrite(RenderTarget instance, boolean setViewport) {

    }

    @Redirect(method = "Lnet/minecraft/client/Minecraft;runTick(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;unbindWrite()V"))
    public void cancelFramebufferEndWrite(RenderTarget instance, boolean setViewport) {
        ChunkProxy.waitImportantChunkRebuild();
        synchronized (TextureProxy.class) {
            RendererProxy.submitCommandAndPresent();
            RendererProxy.acquireContext();
            PipelineStateProxy.invalidateDynamicStateCache();
        }
    }

    @Redirect(method = "Lnet/minecraft/client/Minecraft;runTick(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(II)V"))
    public void cancelFramebufferDraw(RenderTarget instance, int width, int height) {

    }

    @Redirect(method = "Lnet/minecraft/client/Minecraft;runTick(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
    public void disableFPSLimit(int fps) {

    }

    @Redirect(method = "Lnet/minecraft/client/Minecraft;runTick(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/TimerQuery;getInstance()Ljava/util/Optional;"))
    public Optional<TimerQuery> disableGLTimerInstance() {
        return Optional.empty();
    }



    @Redirect(method = "Lnet/minecraft/client/Minecraft;resizeDisplay()V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;resize(II)V"))
    public void cancelFramebufferResize(RenderTarget instance, int width, int height) {

    }



    @Inject(method = "Lnet/minecraft/client/Minecraft;close()V", at = @At(value = "HEAD"))
    public void cancelShaderLoaderClose(CallbackInfo ci) {
        Pipeline.savePipeline();
        Options.overwriteConfig();
    }



    @Inject(method = "Lnet/minecraft/client/Minecraft;close()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/VirtualScreen;close()V"))
    public void closeNativeRenderer(CallbackInfo ci) {
        RendererProxy.close();
    }



    @Redirect(method = "Lnet/minecraft/client/Minecraft;disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V"))
    public void cancelRenderAfterStop(Minecraft instance, boolean tick) {

    }

    @Inject(method = "Lnet/minecraft/client/Minecraft;disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V",
        at = @At(value = "HEAD"))
    public void resetBuiltChunkNum(Screen disconnectionScreen, boolean transferring,
        CallbackInfo ci) {
        ChunkProxy.builtChunkCount = 0;
    }

}
