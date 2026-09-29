package com.radiance.mixins.mod_support.distant_horizons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.forge.ForgeClientProxy", remap = false)
public class ForgeClientProxyMixins {
    @Inject(method = "afterLevelRenderEvent",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void skipRasterCallback(CallbackInfo ci) {
        ci.cancel();
    }
}
