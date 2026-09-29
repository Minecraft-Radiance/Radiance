package com.radiance.mixin_related.extensions.vulkan_render_integration;

import net.minecraft.client.util.BufferAllocator;

public interface ICloseableBufferExt {

    BufferAllocator radiance$getOwnerAllocator();
}
