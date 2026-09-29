package com.radiance.mixins.mod_support.distant_horizons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
public class ClientApiMixins {
    @Inject(method = {"renderLodLayer", "renderFadeOpaque", "renderFadeTransparent"},
        at = @At("HEAD"), cancellable = true, require = 1)
    private void skipRasterPass(CallbackInfo ci) {
        ci.cancel();
    }
}
