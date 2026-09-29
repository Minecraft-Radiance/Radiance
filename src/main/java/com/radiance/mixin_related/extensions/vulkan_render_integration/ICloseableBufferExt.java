package com.radiance.mixin_related.extensions.vulkan_render_integration;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;

public interface ICloseableBufferExt {

    ByteBufferBuilder radiance$getOwnerAllocator();
}
