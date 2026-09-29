package com.radiance.mixin_related.extensions.vulkan_render_integration;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;

public interface IChunkBuilderExt {

    ClientWorld radiance$getWorld();

    WorldRenderer radiance$getWorldRenderer();
}
