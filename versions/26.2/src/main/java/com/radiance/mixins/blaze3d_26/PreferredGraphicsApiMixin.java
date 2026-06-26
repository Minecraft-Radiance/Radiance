package com.radiance.mixins.blaze3d_26;

import com.mojang.blaze3d.systems.GpuBackend;
import com.radiance.client.blaze3d.backend.RadianceBackend;
import net.minecraft.client.PreferredGraphicsApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PreferredGraphicsApi.class)
public abstract class PreferredGraphicsApiMixin {
    @Inject(method = "getBackendsToTry", at = @At("RETURN"), cancellable = true)
    private void radiance$preferRadianceBackend(CallbackInfoReturnable<GpuBackend[]> cir) {
        GpuBackend[] original = cir.getReturnValue();
        GpuBackend[] withRadiance = new GpuBackend[original.length + 1];
        withRadiance[0] = new RadianceBackend();
        System.arraycopy(original, 0, withRadiance, 1, original.length);
        cir.setReturnValue(withRadiance);
    }
}
