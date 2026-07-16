package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IBlockColorsExt;
import net.minecraft.block.BlockState;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixins {

    @Final
    @Shadow
    private BlockColors colors;

    @Inject(method =
        "renderQuad(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;"
            +
            "Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/util/math/MatrixStack$Entry;"
            +
            "Lnet/minecraft/client/render/model/BakedQuad;FFFFIIIII)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    public void redirectRenderQuad(BlockRenderView world,
        BlockState state,
        BlockPos pos,
        VertexConsumer vertexConsumer,
        MatrixStack.Entry matrixEntry,
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
        if (quad.hasTint()) {
            int i = this.colors.getColor(state, world, pos, quad.getTintIndex());
            f = (i >> 16 & 0xFF) / 255.0F;
            g = (i >> 8 & 0xFF) / 255.0F;
            h = (i & 0xFF) / 255.0F;

            emission = ((IBlockColorsExt) this.colors).radiance$getEmission(state, world, pos,
                quad.getTintIndex());
        } else {
            f = 1.0F;
            g = 1.0F;
            h = 1.0F;

            emission = 0.0F;
        }

        vertexConsumer.quad(matrixEntry,
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
