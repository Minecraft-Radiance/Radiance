package com.radiance.mixins.blaze3d_26;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.client.blaze3d.backend.RadianceTextureHandles;
import com.radiance.client.blaze3d.scene.SceneTextureIds;
import com.radiance.client.texture.AuxiliaryTextures;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReloadableTexture.class)
public abstract class ReloadableTextureMixin {
    @Shadow
    public abstract Identifier resourceId();

    @Inject(method = "doLoad", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/CommandEncoder;writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/platform/NativeImage;)V"))
    private void radiance$uploadAuxiliaryTextures(NativeImage image, CallbackInfo ci) {
        int textureId = RadianceTextureHandles.textureId(
            ((net.minecraft.client.renderer.texture.AbstractTexture) (Object) this).getTexture());
        if (textureId == 0) {
            return;
        }
        SceneTextureIds.registerTexture(this.resourceId(), textureId);

        INativeImageExt imageExt = (INativeImageExt) (Object) image;
        imageExt.radiance$setTargetID(textureId);
        imageExt.radiance$setIdentifier(this.resourceId());
        AuxiliaryTextures.loadAndUpload(image, imageExt, 0, 0, 0, 0, 0, image.getWidth(),
            image.getHeight(), false);
    }
}
