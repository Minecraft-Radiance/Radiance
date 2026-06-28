package com.radiance.mixin_related.extensions.vanilla_resource_tracker;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.Identifier;

public interface INativeImageExt {

    int radiance$getTargetID();

    void radiance$setTargetID(int id);

    Identifier radiance$getIdentifier();

    void radiance$setIdentifier(Identifier id);

    NativeImage radiance$getSpecularNativeImage();

    void radiance$setSpecularNativeImage(NativeImage image);

    NativeImage radiance$getNormalNativeImage();

    void radiance$setNormalNativeImage(NativeImage image);

    NativeImage radiance$getFlagNativeImage();

    void radiance$setFlagNativeImage(NativeImage image);
}
