package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.radiance.client.vertex.PBRVertexConsumer;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.Map;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
public abstract class SectionBuilderMixins {

    @Final
    @Shadow
    private BlockRenderDispatcher blockRenderer;

    @Final
    @Shadow
    private BlockEntityRenderDispatcher blockEntityRenderer;

    @Shadow
    protected abstract <E extends BlockEntity> void handleBlockEntity(SectionCompiler.Results data,
        E blockEntity);

    @Inject(method =
        "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;"
            +
            "Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;)"
            +
            "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;", at = @At(value = "HEAD"), cancellable = true)
    public void redirectBuild(SectionPos sectionPos,
        RenderChunkRegion renderRegion,
        VertexSorting vertexSorter,
        SectionBufferBuilderPack allocatorStorage,
        CallbackInfoReturnable<SectionCompiler.Results> cir) {
        SectionCompiler.Results renderData = new SectionCompiler.Results();
        BlockPos blockPos = sectionPos.origin();
        BlockPos blockPos2 = blockPos.offset(15, 15, 15);
        VisGraph chunkOcclusionDataBuilder = new VisGraph();
        PoseStack matrixStack = new PoseStack();
        ModelBlockRenderer.enableCaching();
        Map<RenderType, PBRVertexConsumer>
            map =
            new Reference2ObjectArrayMap<>(RenderType.chunkBufferLayers()
                .size());
        RandomSource random = RandomSource.create();

        for (BlockPos blockPos3 : BlockPos.betweenClosed(blockPos, blockPos2)) {
            BlockState blockState = renderRegion.getBlockState(blockPos3);
            if (blockState.isSolidRender()) {
                chunkOcclusionDataBuilder.setOpaque(blockPos3);
            }

            if (blockState.hasBlockEntity()) {
                BlockEntity blockEntity = renderRegion.getBlockEntity(blockPos3);
                if (blockEntity != null) {
                    this.handleBlockEntity(renderData, blockEntity);
                }
            }

            FluidState fluidState = blockState.getFluidState();
            if (!fluidState.isEmpty()) {
                RenderType renderLayer = ItemBlockRenderTypes.getRenderLayer(fluidState);
                PBRVertexConsumer bufferBuilder = this.beginBufferBuilding(map, allocatorStorage,
                    renderLayer);
                this.blockRenderer.renderLiquid(blockPos3, renderRegion, bufferBuilder,
                    blockState, fluidState);
            }

            if (blockState.getRenderShape() == RenderShape.MODEL) {
                var model = this.blockRenderer.getBlockModel(blockState);
                var modelData = model.getModelData(renderRegion, blockPos3, blockState,
                    renderRegion.getModelData(blockPos3));
                random.setSeed(blockState.getSeed(blockPos3));
                matrixStack.pushPose();
                matrixStack.translate((float) SectionPos.sectionRelative(blockPos3.getX()),
                    (float) SectionPos.sectionRelative(blockPos3.getY()),
                    (float) SectionPos.sectionRelative(blockPos3.getZ()));
                for (RenderType renderLayer : model.getRenderTypes(blockState, random, modelData)) {
                    PBRVertexConsumer bufferBuilder = this.beginBufferBuilding(map, allocatorStorage,
                        renderLayer);
                    this.blockRenderer.renderBatched(blockState, blockPos3, renderRegion,
                        matrixStack, bufferBuilder, true, random, modelData, renderLayer);
                }
                matrixStack.popPose();
            }
        }

        for (Map.Entry<RenderType, PBRVertexConsumer> entry : map.entrySet()) {
            RenderType renderLayer2 = entry.getKey();
            MeshData
                builtBuffer =
                entry.getValue()
                    .endNullable();
            if (builtBuffer != null) {
                renderData.renderedLayers.put(renderLayer2, builtBuffer);
            }
        }

        ModelBlockRenderer.clearCache();
        renderData.visibilitySet = chunkOcclusionDataBuilder.resolve();
        cir.setReturnValue(renderData);
    }

    @Unique
    private PBRVertexConsumer beginBufferBuilding(Map<RenderType, PBRVertexConsumer> builders,
        SectionBufferBuilderPack allocatorStorage,
        RenderType layer) {
        PBRVertexConsumer pbrVertexConsumer = builders.get(layer);
        if (pbrVertexConsumer == null) {
            ByteBufferBuilder bufferAllocator = allocatorStorage.buffer(layer);
            pbrVertexConsumer = PBRVertexConsumer.chunk(bufferAllocator, layer);
            builders.put(layer, pbrVertexConsumer);
        }

        return pbrVertexConsumer;
    }
}
