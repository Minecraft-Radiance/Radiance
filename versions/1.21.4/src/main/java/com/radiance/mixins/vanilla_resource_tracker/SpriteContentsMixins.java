package com.radiance.mixins.vanilla_resource_tracker;

import com.llamalad7.mixinextras.sugar.Local;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteContentsExt;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.class)
public class SpriteContentsMixins implements ISpriteContentsExt {

    @Unique
    private int targetID;

    @Override
    public int radiance$getTargetID() {
        return targetID;
    }

    @Override
    public void radiance$setTargetID(int targetID) {
        this.targetID = targetID;
    }

    @Inject(method = "upload(IIII[Lnet/minecraft/client/texture/NativeImage;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/NativeImage;upload(IIIIIIIZ)V"))
    public void setImageTargetIDBeforeUpload(int x,
        int y,
        int unpackSkipPixels,
        int unpackSkipRows,
        NativeImage[] images,
        CallbackInfo ci,
        @Local(index = 6) int i) {
        ((INativeImageExt) (Object) images[i]).radiance$setTargetID(this.targetID);
    }
}
