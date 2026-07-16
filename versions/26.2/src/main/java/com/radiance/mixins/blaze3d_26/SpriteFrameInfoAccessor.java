package com.radiance.mixins.blaze3d_26;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$FrameInfo")
public interface SpriteFrameInfoAccessor {
    @Accessor("index")
    int radiance$getIndex();

    @Accessor("time")
    int radiance$getTime();
}
