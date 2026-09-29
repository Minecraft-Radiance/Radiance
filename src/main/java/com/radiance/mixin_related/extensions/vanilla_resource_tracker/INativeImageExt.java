package com.radiance.mixin_related.extensions.vanilla_resource_tracker;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.texture.TextureTracker.TextureTarget;
import net.minecraft.resources.ResourceLocation;

public interface INativeImageExt {

    TextureTarget radiance$getTarget();

    void radiance$setTarget(TextureTarget target);

    ResourceLocation radiance$getIdentifier();

    void radiance$setIdentifier(ResourceLocation id);

    NativeImage radiance$getSpecularNativeImage();

    void radiance$setSpecularNativeImage(NativeImage image);

    NativeImage radiance$getNormalNativeImage();

    void radiance$setNormalNativeImage(NativeImage image);

    NativeImage radiance$getFlagNativeImage();

    void radiance$setFlagNativeImage(NativeImage image);
}
