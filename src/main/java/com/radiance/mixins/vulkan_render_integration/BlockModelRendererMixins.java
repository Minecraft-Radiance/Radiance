package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IBlockColorsExt;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public class BlockModelRendererMixins {

    @Final
    @Shadow
    private BlockColors blockColors;

    @Inject(method =
        "putQuadData(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;"
            +
            "Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
            +
            "Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFIIIII)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    public void redirectRenderQuad(BlockAndTintGetter world,
        BlockState state,
        BlockPos pos,
        VertexConsumer vertexConsumer,
        PoseStack.Pose matrixEntry,
        BakedQuad quad,
        float brightness0,
        float brightness1,
        float brightness2,
        float brightness3,
        int light0,
        int light1,
        int light2,
        int light3,
        int overlay,
        CallbackInfo ci) {
        float f;
        float g;
        float h;
        float emission;
        if (quad.isTinted()) {
            int i = this.blockColors.getColor(state, world, pos, quad.getTintIndex());
            f = (i >> 16 & 0xFF) / 255.0F;
            g = (i >> 8 & 0xFF) / 255.0F;
            h = (i & 0xFF) / 255.0F;

            emission = ((IBlockColorsExt) this.blockColors).radiance$getEmission(state, world, pos,
                quad.getTintIndex());
        } else {
            f = 1.0F;
            g = 1.0F;
            h = 1.0F;

            emission = 0.0F;
        }

        vertexConsumer.putBulkData(matrixEntry,
            quad,
            new float[]{brightness0, brightness1, brightness2, brightness3},
            f,
            g,
            h,
            1.0F,
            new int[]{light0, light1, light2, light3},
            overlay,
            true);

        if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
            pbrVertexConsumer.albedoEmission(emission);
        }

        ci.cancel();
    }
}
