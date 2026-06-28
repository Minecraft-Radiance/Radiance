package com.radiance.mixins.vanilla_resource_tracker;

import net.minecraft.client.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AbstractTexture.class)
public abstract class AbstractTextureMixins {

    @Shadow
    public abstract void bindTexture();

    @Shadow
    public abstract int getGlId();
}
