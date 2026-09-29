package com.radiance.mixins.mod_support.distant_horizons;

import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper_forge;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnRenderView;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
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
        if (!state.is(Blocks.KELP) && !state.is(Blocks.KELP_PLANT)
            && !state.is(Blocks.SEAGRASS) && !state.is(Blocks.TALL_SEAGRASS)) { return block; }
        FluidState fluid = state.getFluidState();
        return fluid.is(FluidTags.WATER) ? BlockStateWrapper_forge.fromBlockState(fluid.createLegacyBlock(), level) : block;
    }
}
