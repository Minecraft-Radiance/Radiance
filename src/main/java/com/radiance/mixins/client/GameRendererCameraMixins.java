package com.radiance.mixins.client;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererCameraMixins {

    @Shadow
    private float fovModifier;

    @Shadow
    private float oldFovModifier;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void initializeFovMultiplier(CallbackInfo ci) {
        // A world frame can render before the first camera tick. Vanilla leaves
        // these multipliers at zero, producing a singular projection for NRD.
        this.fovModifier = 1.0F;
        this.oldFovModifier = 1.0F;
    }
}
