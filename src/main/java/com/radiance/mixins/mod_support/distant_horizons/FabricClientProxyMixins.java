package com.radiance.mixins.mod_support.distant_horizons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.fabric.FabricClientProxy", remap = false)
public class FabricClientProxyMixins {
    @Inject(method = {"lambda$registerEvents$6", "lambda$registerEvents$7", "lambda$registerEvents$8"},
        at = @At("HEAD"), cancellable = true, require = 3)
    private static void skipRasterCallback(CallbackInfo ci) {
        ci.cancel();
    }
}
