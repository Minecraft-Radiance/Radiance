package com.radiance.mixins.mod_support.flywheel;

import com.radiance.client.mod_support.flywheel.FlywheelSupport;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VisualizationManager.class, remap = false)
public interface VisualizationManagerMixins {
    @Inject(method = "supportsVisualization", at = @At("HEAD"), cancellable = true)
    private static void radiance$allowCapturedRenderer(LevelAccessor level, CallbackInfoReturnable<Boolean> cir) {
        if (FlywheelSupport.vanillaRendering()) cir.setReturnValue(false);
    }
}
