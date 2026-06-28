package com.radiance.mixins.blaze3d_26;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.blaze3d.scene.SpriteContentsImages;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SpriteContents.class)
public abstract class SpriteContentsAccessor implements SpriteContentsImages {
    @Shadow(remap = false)
    private NativeImage[] byMipLevel;

    @Override
    public Object radiance$getByMipLevel() {
        return this.byMipLevel;
    }
}
