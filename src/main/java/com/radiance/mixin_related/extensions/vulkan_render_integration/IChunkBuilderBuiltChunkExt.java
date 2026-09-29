package com.radiance.mixin_related.extensions.vulkan_render_integration;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

public interface IChunkBuilderBuiltChunkExt {

    SectionRenderDispatcher radiance$getChunkBuilder();
}
