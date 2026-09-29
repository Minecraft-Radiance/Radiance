package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.texture.RainExposure;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public class RainExposureChunkMixins {
    @Inject(method = {"loadFromPacket", "loadBiomeFromPacket"}, at = @At("RETURN"))
    private void rainPacketChanged(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        RainExposure.changed((WorldChunk) (Object) this);
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void rainChanged(BlockPos pos, BlockState state, boolean moving,
                             CallbackInfoReturnable<BlockState> ci) {
        if (ci.getReturnValue() != null) RainExposure.changed((WorldChunk) (Object) this);
    }
}
