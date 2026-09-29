package com.radiance.mixin_related.extensions.vanilla_resource_tracker;

import com.radiance.client.texture.TextureTracker.TextureTarget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

public interface INativeImageExt {

    TextureTarget radiance$getTarget();

    void radiance$setTarget(TextureTarget target);

    Identifier radiance$getIdentifier();

    void radiance$setIdentifier(Identifier id);

    NativeImage radiance$getSpecularNativeImage();

    void radiance$setSpecularNativeImage(NativeImage image);

    NativeImage radiance$getNormalNativeImage();

    void radiance$setNormalNativeImage(NativeImage image);

    NativeImage radiance$getFlagNativeImage();

    void radiance$setFlagNativeImage(NativeImage image);
}
