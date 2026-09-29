package com.radiance.mixins.mod_support.distant_horizons;

import com.seibel.distanthorizons.api.enums.config.EDhApiRenderingApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper_neoforge", remap = false)
public class MinecraftRenderWrapperMixins {
    @Inject(method = "getMcRenderingApi", at = @At("HEAD"), cancellable = true, require = 1)
    private void reportVulkan(CallbackInfoReturnable<EDhApiRenderingApi> cir) {
        cir.setReturnValue(EDhApiRenderingApi.VULKAN);
    }
}
