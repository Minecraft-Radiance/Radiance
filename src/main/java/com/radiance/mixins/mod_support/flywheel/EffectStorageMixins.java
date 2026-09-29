package com.radiance.mixins.mod_support.flywheel;

import com.radiance.client.mod_support.flywheel.FlywheelEngine;
import com.radiance.client.mod_support.flywheel.FlywheelSupport;
import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.impl.visualization.storage.EffectStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EffectStorage.class, remap = false)
public class EffectStorageMixins {
    @Inject(method = "createRaw(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;Ldev/engine_room/flywheel/api/visual/Effect;F)Ldev/engine_room/flywheel/api/visual/EffectVisual;",
        at = @At("HEAD"), cancellable = true)
    private void radiance$ownedEffect(VisualizationContext context, Effect effect, float partialTick,
        CallbackInfoReturnable<EffectVisual<?>> cir) {
        if (!FlywheelSupport.active()) return;
        var owned = ((FlywheelEngine.Context) context).forObject(effect);
        cir.setReturnValue(owned.wrap(effect.visualize(owned, partialTick)));
    }
}
