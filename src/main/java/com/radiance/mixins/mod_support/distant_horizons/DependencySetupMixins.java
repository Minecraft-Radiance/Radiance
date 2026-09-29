package com.radiance.mixins.mod_support.distant_horizons;

import com.radiance.client.mod_support.distant_horizons.DistantHorizonsSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.DependencySetup_neoforge", remap = false)
public class DependencySetupMixins {
    @Inject(method = "setRenderingApiBindings", at = @At("HEAD"), cancellable = true, require = 1)
    private static void bindVista(CallbackInfo ci) {
        DistantHorizonsSupport.bind();
        ci.cancel();
    }
}
