package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderPhase.Target.class)
public class RenderPhaseTargetMixins extends RenderPhaseMixins {

    @Inject(method = "<init>(Ljava/lang/String;Ljava/lang/Runnable;Ljava/lang/Runnable;)V", at = @At(value = "TAIL"))
    public void resetActionToDoNothing(String string, Runnable runnable, Runnable runnable2,
        CallbackInfo ci) {
        setBeginAction(() -> {
        });
        setEndAction(() -> {
        });
    }
}
