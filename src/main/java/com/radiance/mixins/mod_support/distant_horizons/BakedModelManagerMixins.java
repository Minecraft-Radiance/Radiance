package com.radiance.mixins.mod_support.distant_horizons;

import com.radiance.client.vista.Vista;
import net.minecraft.client.resources.model.ModelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelManager.class)
public class BakedModelManagerMixins {
    @Inject(method = "apply", at = @At("TAIL"))
    private void reloadVista(CallbackInfo ci) { Vista.reload(); }
}
