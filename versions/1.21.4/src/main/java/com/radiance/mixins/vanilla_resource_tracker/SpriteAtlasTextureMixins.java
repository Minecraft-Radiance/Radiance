package com.radiance.mixins.vanilla_resource_tracker;

import com.llamalad7.mixinextras.sugar.Local;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteExt;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteAtlasTexture.class)
public abstract class SpriteAtlasTextureMixins extends AbstractTextureMixins {

    @Inject(method = "upload(Lnet/minecraft/client/texture/SpriteLoader$StitchResult;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/Sprite;upload()V"))
    public void setImageTargetIDBeforeUpload(SpriteLoader.StitchResult stitchResult,
        CallbackInfo ci, @Local Sprite sprite) {
        int id = getGlId();
        ((ISpriteExt) sprite).radiance$setTargetID(id);
    }
}
