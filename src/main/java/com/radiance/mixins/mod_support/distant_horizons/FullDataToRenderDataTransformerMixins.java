package com.radiance.mixins.mod_support.distant_horizons;

import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnRenderView;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer", remap = false)
public class FullDataToRenderDataTransformerMixins {
    @Redirect(method = "setRenderColumnView", at = @At(value = "INVOKE",
        target = "Lcom/seibel/distanthorizons/core/dataObjects/fullData/FullDataPointIdMap;getBlockStateWrapper(I)Lcom/seibel/distanthorizons/core/wrapperInterfaces/block/IBlockStateWrapper;"), require = 1)
    private static IBlockStateWrapper waterPlants(FullDataPointIdMap mapping, int id,
        IClientLevelWrapper level, FullDataSourceV2 source, int blockX, int blockZ,
        ColumnRenderView renderColumn, LongArrayList fullColumn, DhBlockPosMutable position) {
        IBlockStateWrapper block = mapping.getBlockStateWrapper(id);
        if (block.isSolid() || !block.isLiquid()
            || !(block.getWrappedMcObject() instanceof BlockState state)) { return block; }
        if (!state.isOf(Blocks.KELP) && !state.isOf(Blocks.KELP_PLANT)
            && !state.isOf(Blocks.SEAGRASS) && !state.isOf(Blocks.TALL_SEAGRASS)) { return block; }
        FluidState fluid = state.getFluidState();
        return fluid.isIn(FluidTags.WATER) ? BlockStateWrapper.fromBlockState(fluid.getBlockState(), level) : block;
    }
}
