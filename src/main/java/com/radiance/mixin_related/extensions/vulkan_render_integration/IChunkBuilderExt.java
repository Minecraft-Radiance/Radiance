package com.radiance.mixin_related.extensions.vulkan_render_integration;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.SectionCompiler;

public interface IChunkBuilderExt {

    SectionCompiler radiance$getSectionBuilder();

    ClientLevel radiance$getWorld();

    LevelRenderer radiance$getWorldRenderer();

    SectionBufferBuilderPack radiance$getBuffers();
}
