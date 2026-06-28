package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.util.BlockColorEmissionProvider;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IBlockColorsExt;
import net.minecraft.block.BlockState;
import net.minecraft.client.color.block.BlockColorProvider;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.registry.Registries;
import net.minecraft.util.collection.IdList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockColors.class)
public class BlockColorsMixins implements IBlockColorsExt {

    @Final
    @Shadow
    private IdList<BlockColorProvider> providers;

//    @Redirect(method = "create()Lnet/minecraft/client/color/block/BlockColors;",
//              at = @At(value = "INVOKE",
//                       target = "Lnet/minecraft/client/color/block/BlockColors;registerColorProvider" +
//                           "(Lnet/minecraft/client/color/block/BlockColorProvider;[Lnet/minecraft/block/Block;)V",
//                       ordinal = 7))
//    private static void addEmissionToRedstoneWire(BlockColors blockColors, BlockColorProvider provider, Block[] blocks) {
//        BlockColorEmissionProvider blockColorEmissionProvider = (state, world, pos, tintIndex) -> {
//            int power = state.get(RedstoneWireBlock.POWER);
//            int color = RedstoneWireBlock.getWireColor(power);
//            float emission = 10; // (float) (power * 0.5);
//            return new Pair<>(color, emission);
//        };
//
//        blockColors.registerColorProvider(blockColorEmissionProvider, Blocks.REDSTONE_WIRE);
//    }

    @Override
    public float radiance$getEmission(BlockState state, @Nullable BlockRenderView world,
        @Nullable BlockPos pos, int tintIndex) {
        BlockColorProvider blockColorProvider = this.providers.get(
            Registries.BLOCK.getRawId(state.getBlock()));
        if (blockColorProvider instanceof BlockColorEmissionProvider blockColorEmissionProvider) {
            return blockColorEmissionProvider.getEmission(state, world, pos, tintIndex);
        } else {
            return 0.0F;
        }
    }
}
