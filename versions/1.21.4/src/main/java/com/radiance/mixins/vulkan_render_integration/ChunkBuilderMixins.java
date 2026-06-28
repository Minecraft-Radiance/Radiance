package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkBuilder.class)
public class ChunkBuilderMixins implements IChunkBuilderExt {

    @Final
    @Shadow
    SectionBuilder sectionBuilder;

    @Final
    @Shadow
    BlockBufferAllocatorStorage buffers;

    @Shadow
    ClientWorld world;

    @Override
    public SectionBuilder radiance$getSectionBuilder() {
        return sectionBuilder;
    }

    @Override
    public ClientWorld radiance$getWorld() {
        return world;
    }

    @Override
    public BlockBufferAllocatorStorage radiance$getBuffers() {
        return buffers;
    }
}
