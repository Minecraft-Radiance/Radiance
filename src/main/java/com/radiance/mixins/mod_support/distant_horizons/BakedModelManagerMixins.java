package com.radiance.mixins.mod_support.distant_horizons;

import com.radiance.client.vista.Vista;
import net.minecraft.client.render.model.BakedModelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BakedModelManager.class)
public class BakedModelManagerMixins {
    @Inject(method = "upload", at = @At("TAIL"))
    private void reloadVista(CallbackInfo ci) { Vista.reload(); }
}
