package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.UnsafeManager;
import com.radiance.client.option.Options;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.AuxiliaryTextureReloader;
import com.radiance.client.proxy.world.ChunkProxy;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlTimer;
import net.minecraft.client.gl.WindowFramebuffer;
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
public class MinecraftClientMixins {

    @Shadow
    @Final
    private Window window;

    @Shadow
    private ReloadableResourceManagerImpl resourceManager;

    //region <isAmbientOcclusionEnabled>
    @Inject(method = "isAmbientOcclusionEnabled()Z", at = @At(value = "HEAD"), cancellable = true)
    private static void disableAmbientOcclusion(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
    // endregion

    // region <init>
    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;initRenderer(IZ)V"))
    public void initRenderer(int debugVerbosity, boolean debugSync) {
        long stackSize = 512 * 1024 * 1024; // 32MB
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

    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "NEW", target = "net/minecraft/client/gl/WindowFramebuffer"))
    public WindowFramebuffer cancelNewFramebuffer(int width, int height) {
        return UnsafeManager.INSTANCE.allocateInstance(WindowFramebuffer.class);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/MinecraftClient;resourceManager:Lnet/minecraft/resource/ReloadableResourceManagerImpl;",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER))
    private void registerAuxiliaryTextureReloader(RunArgs args, CallbackInfo ci) {
        this.resourceManager.registerReloader(new AuxiliaryTextureReloader());
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/MinecraftClient;framebuffer:Lnet/minecraft/client/gl/Framebuffer;",
            opcode = org.objectweb.asm.Opcodes.PUTFIELD))
    public void writeNullFramebuffer(MinecraftClient instance, Framebuffer value) {
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;setClearColor(FFFF)V"))
    public void cancelSetClearColor(Framebuffer instance, float r, float g, float b, float a) {

    }

    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;clear()V"))
    public void cancelClear(Framebuffer instance) {

    }

    @Redirect(method = "<init>",
        at = @At(value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/client/gl/Framebuffer;textureWidth:I",
            ordinal = 0))
    public int redirectFramebufferTextureWidth(Framebuffer framebuffer) {
        return this.window.getFramebufferWidth();
    }

    @Redirect(method = "<init>",
        at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/gl/Framebuffer;textureHeight:I"),
        require = 0)
    public int redirectFramebufferTextureHeight(Framebuffer framebuffer) {
        return this.window.getFramebufferHeight();
    }
    // endregion

    // region <render>
    @Redirect(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    public void cancelFramebufferBeginWrite(Framebuffer instance, boolean setViewport) {

    }

    @Redirect(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;endWrite()V"))
    public void cancelFramebufferEndWrite(Framebuffer instance, boolean setViewport) {
        ChunkProxy.waitImportantChunkRebuild();
        synchronized (TextureProxy.class) {
            RendererProxy.submitCommandAndPresent();
            RendererProxy.acquireContext();
        }
    }

    @Redirect(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;draw(II)V"))
    public void cancelFramebufferDraw(Framebuffer instance, int width, int height) {

    }

    @Redirect(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
    public void disableFPSLimit(int fps) {

    }

    @Redirect(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/GlTimer;getInstance()Ljava/util/Optional;"))
    public Optional<GlTimer> disableGLTimerInstance() {
        return Optional.empty();
    }
    // endregion

    // region <onResolutionChanged>
    @Redirect(method = "onResolutionChanged()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;resize(II)V"))
    public void cancelFramebufferResize(Framebuffer instance, int width, int height) {

    }
    // endregion

    // region <close>
    @Inject(method = "close()V", at = @At(value = "HEAD"))
    public void cancelShaderLoaderClose(CallbackInfo ci) {
        Options.overwriteConfig();
    }
    //endregion

    // region <scheduleStop>
    @Inject(method = "scheduleStop()V", at = @At(value = "TAIL"))
    public void close(CallbackInfo ci) {
        RendererProxy.close();
    }
    // endregion

    // region <disconnect>
    @Redirect(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;render(Z)V"))
    public void cancelRenderAfterStop(MinecraftClient instance, boolean tick) {

    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V",
        at = @At(value = "HEAD"))
    public void resetBuiltChunkNum(Screen disconnectionScreen, boolean transferring,
        CallbackInfo ci) {
        ChunkProxy.builtChunkNum = 0;
    }
    // endregion
}
