package com.radiance.mixin_related.extensions.vulkan_render_integration;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.Collection;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface IChunkBuilderBuiltChunkExt {

    ChunkRenderDispatcher radiance$getChunkBuilder();

    void radiance$beginBufferBuilding(BufferBuilder bufferBuilder);

    void radiance$setNoCullingBlockEntities(Collection<BlockEntity> blockEntities);
}
