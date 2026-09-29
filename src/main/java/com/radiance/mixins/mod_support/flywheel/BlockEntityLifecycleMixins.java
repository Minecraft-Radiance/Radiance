package com.radiance.mixins.mod_support.flywheel;

import com.radiance.client.mod_support.flywheel.FlywheelSupport;
import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public class BlockEntityLifecycleMixins {
    @Inject(method = "markRemoved", at = @At("TAIL"))
    private void radiance$blockRemoved(CallbackInfo ci) {
        FlywheelSupport.blockRemoved((BlockEntity) (Object) this);
    }
}
