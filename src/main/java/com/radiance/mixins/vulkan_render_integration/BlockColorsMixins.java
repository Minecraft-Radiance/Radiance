package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.util.BlockColorEmissionProvider;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IBlockColorsExt;
import java.util.Map;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockColors.class)
public class BlockColorsMixins implements IBlockColorsExt {

    @Final
    @Shadow
    private Map<Block, BlockColor> blockColors;

















    @Override
    public float radiance$getEmission(BlockState state, @Nullable BlockAndTintGetter world,
        @Nullable BlockPos pos, int tintIndex) {
        BlockColor blockColorProvider = this.blockColors.get(state.getBlock());
        if (blockColorProvider instanceof BlockColorEmissionProvider blockColorEmissionProvider) {
            return blockColorEmissionProvider.getEmission(state, world, pos, tintIndex);
        } else {
            return 0.0F;
        }
    }
}
