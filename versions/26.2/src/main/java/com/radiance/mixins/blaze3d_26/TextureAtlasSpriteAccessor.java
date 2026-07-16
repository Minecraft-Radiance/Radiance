package com.radiance.mixins.blaze3d_26;

import com.radiance.client.blaze3d.scene.TextureAtlasSpriteInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextureAtlasSprite.class)
public interface TextureAtlasSpriteAccessor extends TextureAtlasSpriteInfo {
    @Override
    @Accessor("padding")
    int radiance$getPadding();
}
