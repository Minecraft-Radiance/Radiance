package com.radiance.mixins.client;

import com.radiance.client.proxy.vulkan.RendererProxy;
import net.minecraft.client.Minecraft;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientPerformanceMixins {

    @Shadow
    private static int fps;

    @Shadow
    public String fpsString;

    @Inject(method = "runTick(Z)V",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;fpsString:Ljava/lang/String;",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER))
    private void usePresentedFrameRate(boolean tick, CallbackInfo ci) {
        fps = RendererProxy.getPresentedFrameRate();
        int suffixStart = this.fpsString.indexOf(" fps");
        this.fpsString = fps + this.fpsString.substring(suffixStart);
    }
}
