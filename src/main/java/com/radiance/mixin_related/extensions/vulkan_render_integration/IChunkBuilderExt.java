package com.radiance.mixin_related.extensions.vulkan_render_integration;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;

public interface IChunkBuilderExt {

    ClientLevel radiance$getWorld();

    LevelRenderer radiance$getWorldRenderer();
}
