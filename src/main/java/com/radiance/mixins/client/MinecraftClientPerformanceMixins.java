package com.radiance.mixins.client;

import com.radiance.client.proxy.vulkan.RendererProxy;
import net.minecraft.client.MinecraftClient;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientPerformanceMixins {

    @Shadow
    private static int currentFps;

    @Shadow
    public String fpsDebugString;

    @Inject(method = "render(Z)V",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/MinecraftClient;fpsDebugString:Ljava/lang/String;",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER))
    private void usePresentedFrameRate(boolean tick, CallbackInfo ci) {
        currentFps = RendererProxy.getPresentedFrameRate();
        int suffixStart = this.fpsDebugString.indexOf(" fps");
        this.fpsDebugString = currentFps + this.fpsDebugString.substring(suffixStart);
    }
}
