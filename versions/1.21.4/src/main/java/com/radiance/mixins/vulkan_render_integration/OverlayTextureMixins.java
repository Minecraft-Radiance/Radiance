package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IOverlayTextureExt;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(OverlayTexture.class)
public class OverlayTextureMixins implements IOverlayTextureExt {

    @Final
    @Shadow
    private NativeImageBackedTexture texture;

    @Override
    public AbstractTexture radiance$getTexture() {
        return texture;
    }
}
