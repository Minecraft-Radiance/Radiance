package com.radiance.mixins.blaze3d_26;

import com.radiance.client.blaze3d.scene.TextureAtlasSync;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasSyncMixin {

    @Inject(method = "upload", at = @At("RETURN"))
    private void radiance$syncAtlasSprites(SpriteLoader.Preparations preparations,
        CallbackInfo ci) {
        TextureAtlasSync.sync((TextureAtlas) (Object) this, preparations.regions());
    }
}
