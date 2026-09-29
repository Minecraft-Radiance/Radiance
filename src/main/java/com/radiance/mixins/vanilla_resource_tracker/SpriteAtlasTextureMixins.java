package com.radiance.mixins.vanilla_resource_tracker;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteExt;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
public abstract class SpriteAtlasTextureMixins extends AbstractTextureMixins {

    @WrapMethod(method = "Lnet/minecraft/client/renderer/texture/TextureAtlas;upload(Lnet/minecraft/client/renderer/texture/SpriteLoader$Preparations;)V")
    private void batchTextureUploads(SpriteLoader.Preparations stitchResult, Operation<Void> original) {
        TextureProxy.beginUploadBatch();
        try {
            original.call(stitchResult);
        } finally {
            TextureProxy.endUploadBatch();
        }
    }

    @WrapMethod(method = "Lnet/minecraft/client/renderer/texture/TextureAtlas;tick()V")
    private void batchAnimatedTextureUploads(Operation<Void> original) {
        TextureProxy.beginUploadBatch();
        try {
            original.call();
        } finally {
            TextureProxy.endUploadBatch();
        }
    }

    @Inject(method = "Lnet/minecraft/client/renderer/texture/TextureAtlas;upload(Lnet/minecraft/client/renderer/texture/SpriteLoader$Preparations;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;uploadFirstFrame()V"))
    public void setImageTargetIDBeforeUpload(SpriteLoader.Preparations stitchResult,
        CallbackInfo ci, @Local TextureAtlasSprite sprite) {
        int id = getId();
        ((ISpriteExt) sprite).radiance$setTarget(TextureTracker.captureTarget(id));
    }
}
