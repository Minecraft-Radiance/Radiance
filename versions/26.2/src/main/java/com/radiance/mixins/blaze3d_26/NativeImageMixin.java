package com.radiance.mixins.blaze3d_26;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.texture.IdentifierInputStream;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import java.io.InputStream;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NativeImage.class)
public abstract class NativeImageMixin implements INativeImageExt {
    @Unique
    private int radiance$targetId = -1;
    @Unique
    private Identifier radiance$identifier;
    @Unique
    private NativeImage radiance$specularImage;
    @Unique
    private NativeImage radiance$normalImage;
    @Unique
    private NativeImage radiance$flagImage;

    @Inject(method = "read(Lcom/mojang/blaze3d/platform/NativeImage$Format;Ljava/io/InputStream;)"
        + "Lcom/mojang/blaze3d/platform/NativeImage;", at = @At("RETURN"))
    private static void radiance$readIdentifier(NativeImage.Format format, InputStream stream,
        CallbackInfoReturnable<NativeImage> cir) {
        radiance$setIdentifierFromStream(stream, cir.getReturnValue());
    }

    @Inject(method = "read(Ljava/io/InputStream;)Lcom/mojang/blaze3d/platform/NativeImage;",
        at = @At("RETURN"))
    private static void radiance$readIdentifier(InputStream stream,
        CallbackInfoReturnable<NativeImage> cir) {
        radiance$setIdentifierFromStream(stream, cir.getReturnValue());
    }

    @Unique
    private static void radiance$setIdentifierFromStream(InputStream stream, NativeImage image) {
        if (stream instanceof IdentifierInputStream identifierStream) {
            ((INativeImageExt) (Object) image).radiance$setIdentifier(identifierStream.getResourceId());
        }
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void radiance$closeAuxiliaryImages(CallbackInfo ci) {
        if (this.radiance$specularImage != null) {
            this.radiance$specularImage.close();
            this.radiance$specularImage = null;
        }
        if (this.radiance$normalImage != null) {
            this.radiance$normalImage.close();
            this.radiance$normalImage = null;
        }
        if (this.radiance$flagImage != null) {
            this.radiance$flagImage.close();
            this.radiance$flagImage = null;
        }
    }

    @Override
    public int radiance$getTargetID() {
        return this.radiance$targetId;
    }

    @Override
    public void radiance$setTargetID(int id) {
        this.radiance$targetId = id;
    }

    @Override
    public Identifier radiance$getIdentifier() {
        return this.radiance$identifier;
    }

    @Override
    public void radiance$setIdentifier(Identifier id) {
        this.radiance$identifier = id;
    }

    @Override
    public NativeImage radiance$getSpecularNativeImage() {
        return this.radiance$specularImage;
    }

    @Override
    public void radiance$setSpecularNativeImage(NativeImage image) {
        this.radiance$specularImage = image;
    }

    @Override
    public NativeImage radiance$getNormalNativeImage() {
        return this.radiance$normalImage;
    }

    @Override
    public void radiance$setNormalNativeImage(NativeImage image) {
        this.radiance$normalImage = image;
    }

    @Override
    public NativeImage radiance$getFlagNativeImage() {
        return this.radiance$flagImage;
    }

    @Override
    public void radiance$setFlagNativeImage(NativeImage image) {
        this.radiance$flagImage = image;
    }
}
