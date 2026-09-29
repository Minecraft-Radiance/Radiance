package com.radiance.mixins.mod_support.flywheel;

import com.radiance.client.mod_support.flywheel.FlywheelSupport;
import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.impl.BackendManagerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BackendManagerImpl.class, remap = false)
public class BackendManagerMixins {
    @Shadow private static Backend backend;

    @Inject(method = "chooseBackend", at = @At("HEAD"), cancellable = true)
    private static void radiance$selectEntityBackend(CallbackInfo ci) {
        Backend selected = FlywheelSupport.backend();
        if (selected != null) {
            backend = selected;
            ci.cancel();
        }
    }
}
