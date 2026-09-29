package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.texture.RainExposure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public class RainExposureChunkMixins {
    @Inject(method = {"replaceWithPacketData", "replaceBiomes"}, at = @At("RETURN"))
    private void rainPacketChanged(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        RainExposure.changed((LevelChunk) (Object) this);
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void rainChanged(BlockPos pos, BlockState state, boolean moving,
                             CallbackInfoReturnable<BlockState> ci) {
        if (ci.getReturnValue() != null) RainExposure.changed((LevelChunk) (Object) this);
    }
}
