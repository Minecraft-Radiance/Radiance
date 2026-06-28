package com.radiance.mixins.blaze3d_26;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$AnimatedTexture")
public interface SpriteAnimatedTextureAccessor {
    @Accessor("frames")
    List<?> radiance$getFrames();

    @Accessor("interpolateFrames")
    boolean radiance$getInterpolateFrames();

    @Invoker("getFrameX")
    int radiance$invokeGetFrameX(int index);

    @Invoker("getFrameY")
    int radiance$invokeGetFrameY(int index);
}
