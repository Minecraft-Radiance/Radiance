package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkBuilder.class)
public class ChunkBuilderMixins implements IChunkBuilderExt {

    @Shadow
    ClientWorld world;

    @Override
    public ClientWorld radiance$getWorld() {
        return this.world;
    }
}
