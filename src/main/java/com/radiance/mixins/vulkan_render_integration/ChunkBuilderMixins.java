package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkRenderDispatcher.class)
public class ChunkBuilderMixins implements IChunkBuilderExt {

    @Shadow
    ClientLevel level;

    @Shadow
    LevelRenderer renderer;

    @Override
    public ClientLevel radiance$getWorld() {
        return level;
    }

    @Override
    public LevelRenderer radiance$getWorldRenderer() {
        return renderer;
    }
}
