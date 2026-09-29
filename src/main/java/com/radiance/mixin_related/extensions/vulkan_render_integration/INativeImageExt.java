package com.radiance.mixin_related.extensions.vulkan_render_integration;

import com.mojang.blaze3d.platform.NativeImage;

public interface INativeImageExt {

    void radiance$loadFromTextureImageWithoutUI(int level, boolean removeAlpha);

    NativeImage radiance$alignTo(NativeImage template);

    long radiance$getPointer();
}
