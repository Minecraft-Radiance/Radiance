package com.radiance.mixin_related.extensions.vulkan_render_integration;

import net.minecraft.client.texture.NativeImage;

public interface INativeImageExt {

    void radiance$loadFromTextureImageWithoutUI(int level, boolean removeAlpha);

    NativeImage radiance$alignTo(NativeImage template);

    long radiance$getPointer();
}
