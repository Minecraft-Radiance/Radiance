package com.radiance.mixins.blaze3d_26;

import com.radiance.client.blaze3d.scene.TextureAtlasSync;
import java.util.List;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasSyncMixin {
    @Shadow
    private List<TextureAtlasSprite> sprites;

    @Shadow
    private List<SpriteContents.AnimationState> animatedTexturesStates;

    @Inject(method = "upload", at = @At("RETURN"))
    private void radiance$syncAtlasSprites(SpriteLoader.Preparations preparations,
        CallbackInfo ci) {
        TextureAtlasSync.sync((TextureAtlas) (Object) this, preparations.regions());
    }

    @Inject(method = "cycleAnimationFrames", at = @At("RETURN"))
    private void radiance$syncAnimatedAtlasSprites(CallbackInfo ci) {
        TextureAtlasSync.syncAnimatedSprites((TextureAtlas) (Object) this, this.sprites,
            this.animatedTexturesStates);
    }
}
