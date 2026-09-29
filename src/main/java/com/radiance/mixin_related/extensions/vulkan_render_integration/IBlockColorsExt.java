package com.radiance.mixin_related.extensions.vulkan_render_integration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public interface IBlockColorsExt {

    float radiance$getEmission(BlockState state, @Nullable BlockAndTintGetter world,
        @Nullable BlockPos pos, int tintIndex);
}
