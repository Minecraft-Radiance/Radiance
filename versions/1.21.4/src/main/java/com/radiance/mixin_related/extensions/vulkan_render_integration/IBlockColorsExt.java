package com.radiance.mixin_related.extensions.vulkan_render_integration;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

public interface IBlockColorsExt {

    float radiance$getEmission(BlockState state, @Nullable BlockRenderView world,
        @Nullable BlockPos pos, int tintIndex);
}
