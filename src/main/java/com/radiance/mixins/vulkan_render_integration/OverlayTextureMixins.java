package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IOverlayTextureExt;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(OverlayTexture.class)
public class OverlayTextureMixins implements IOverlayTextureExt {

    @Final
    @Shadow
    private DynamicTexture texture;

    @Override
    public AbstractTexture radiance$getTexture() {
        return texture;
    }
}
