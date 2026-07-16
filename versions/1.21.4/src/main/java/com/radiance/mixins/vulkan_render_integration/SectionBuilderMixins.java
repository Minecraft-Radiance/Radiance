package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.VertexSorter;
import com.radiance.client.vertex.PBRVertexConsumer;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.Map;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionBuilder.class)
public abstract class SectionBuilderMixins {

    @Final
    @Shadow
    private BlockRenderManager blockRenderManager;

    @Final
    @Shadow
    private BlockEntityRenderDispatcher blockEntityRenderDispatcher;

    @Shadow
    protected abstract <E extends BlockEntity> void addBlockEntity(SectionBuilder.RenderData data,
        E blockEntity);

    @Inject(method =
        "build(Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/client/render/chunk/ChunkRendererRegion;"
            +
            "Lcom/mojang/blaze3d/systems/VertexSorter;Lnet/minecraft/client/render/chunk/BlockBufferAllocatorStorage;)"
            +
            "Lnet/minecraft/client/render/chunk/SectionBuilder$RenderData;", at = @At(value = "HEAD"), cancellable = true)
    public void redirectBuild(ChunkSectionPos sectionPos,
        ChunkRendererRegion renderRegion,
        VertexSorter vertexSorter,
        BlockBufferAllocatorStorage allocatorStorage,
        CallbackInfoReturnable<SectionBuilder.RenderData> cir) {
        SectionBuilder.RenderData renderData = new SectionBuilder.RenderData();
        BlockPos blockPos = sectionPos.getMinPos();
        BlockPos blockPos2 = blockPos.add(15, 15, 15);
        ChunkOcclusionDataBuilder chunkOcclusionDataBuilder = new ChunkOcclusionDataBuilder();
        MatrixStack matrixStack = new MatrixStack();
        BlockModelRenderer.enableBrightnessCache();
        Map<RenderLayer, PBRVertexConsumer>
            map =
            new Reference2ObjectArrayMap<>(RenderLayer.getBlockLayers()
                .size());
        Random random = Random.create();

        for (BlockPos blockPos3 : BlockPos.iterate(blockPos, blockPos2)) {
            BlockState blockState = renderRegion.getBlockState(blockPos3);
            if (blockState.isOpaqueFullCube()) {
                chunkOcclusionDataBuilder.markClosed(blockPos3);
            }

            if (blockState.hasBlockEntity()) {
                BlockEntity blockEntity = renderRegion.getBlockEntity(blockPos3);
                if (blockEntity != null) {
                    this.addBlockEntity(renderData, blockEntity);
                }
            }

            FluidState fluidState = blockState.getFluidState();
            if (!fluidState.isEmpty()) {
                RenderLayer renderLayer = RenderLayers.getFluidLayer(fluidState);
                PBRVertexConsumer bufferBuilder = this.beginBufferBuilding(map, allocatorStorage,
                    renderLayer);
                this.blockRenderManager.renderFluid(blockPos3, renderRegion, bufferBuilder,
                    blockState, fluidState);
            }

            if (blockState.getRenderType() == BlockRenderType.MODEL) {
                RenderLayer renderLayer = RenderLayers.getBlockLayer(blockState);
                PBRVertexConsumer bufferBuilder = this.beginBufferBuilding(map, allocatorStorage,
                    renderLayer);
                matrixStack.push();
                matrixStack.translate((float) ChunkSectionPos.getLocalCoord(blockPos3.getX()),
                    (float) ChunkSectionPos.getLocalCoord(blockPos3.getY()),
                    (float) ChunkSectionPos.getLocalCoord(blockPos3.getZ()));
                this.blockRenderManager.renderBlock(blockState, blockPos3, renderRegion,
                    matrixStack, bufferBuilder, true, random);
                matrixStack.pop();
            }
        }

        for (Map.Entry<RenderLayer, PBRVertexConsumer> entry : map.entrySet()) {
            RenderLayer renderLayer2 = entry.getKey();
            BuiltBuffer
                builtBuffer =
                entry.getValue()
                    .endNullable();
            if (builtBuffer != null) {
                renderData.buffers.put(renderLayer2, builtBuffer);
            }
        }

        BlockModelRenderer.disableBrightnessCache();
        renderData.chunkOcclusionData = chunkOcclusionDataBuilder.build();
        cir.setReturnValue(renderData);
    }

    @Unique
    private PBRVertexConsumer beginBufferBuilding(Map<RenderLayer, PBRVertexConsumer> builders,
        BlockBufferAllocatorStorage allocatorStorage,
        RenderLayer layer) {
        PBRVertexConsumer pbrVertexConsumer = builders.get(layer);
        if (pbrVertexConsumer == null) {
            BufferAllocator bufferAllocator = allocatorStorage.get(layer);
            pbrVertexConsumer = new PBRVertexConsumer(bufferAllocator, layer);
            builders.put(layer, pbrVertexConsumer);
        }

        return pbrVertexConsumer;
    }
}
