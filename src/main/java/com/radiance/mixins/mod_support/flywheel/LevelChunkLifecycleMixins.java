package com.radiance.mixins.mod_support.flywheel;

import com.radiance.client.mod_support.flywheel.FlywheelSupport;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public class LevelChunkLifecycleMixins {
    @Inject(method = "setBlockEntity", at = @At("TAIL"))
    private void radiance$blockAdded(BlockEntity block, CallbackInfo ci) {
        FlywheelSupport.blockReady(block);
    }
}
