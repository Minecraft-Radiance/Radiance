package com.radiance.mixins.mod_support.flywheel;

import com.radiance.client.mod_support.flywheel.FlywheelSupport;
import dev.engine_room.flywheel.impl.FlwApiLinkImpl;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FlwApiLinkImpl.class, remap = false)
public class VisualizationManagerMixins {
    @Inject(method = "supportsVisualization", at = @At("HEAD"), cancellable = true)
    private void radiance$allowCapturedRenderer(LevelAccessor level, CallbackInfoReturnable<Boolean> cir) {
        if (FlywheelSupport.vanillaRendering()) cir.setReturnValue(false);
    }
}
