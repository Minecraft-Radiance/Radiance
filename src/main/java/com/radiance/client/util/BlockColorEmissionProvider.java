package com.radiance.client.util;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public interface BlockColorEmissionProvider extends BlockColor {

    Tuple<Integer, Float> getColorEmission(BlockState state, @Nullable BlockAndTintGetter world,
        @Nullable BlockPos pos, int tintIndex);

    default int getColor(BlockState state, @Nullable BlockAndTintGetter world, @Nullable BlockPos pos,
        int tintIndex) {
        return getColorEmission(state, world, pos, tintIndex).getA();
    }

    default float getEmission(BlockState state, @Nullable BlockAndTintGetter world,
        @Nullable BlockPos pos, int tintIndex) {
        return getColorEmission(state, world, pos, tintIndex).getB();
    }
}
