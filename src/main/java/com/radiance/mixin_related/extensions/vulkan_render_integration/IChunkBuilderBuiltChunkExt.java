package com.radiance.mixin_related.extensions.vulkan_render_integration;

import java.util.Collection;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.chunk.ChunkBuilder;

public interface IChunkBuilderBuiltChunkExt {

    ChunkBuilder radiance$getChunkBuilder();

    void radiance$setNoCullingBlockEntities(Collection<BlockEntity> blockEntities);
}
