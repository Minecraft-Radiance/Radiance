package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.texture.AuxiliaryTextures;
import net.minecraft.client.texture.atlas.AtlasSource.SpriteRegions;
import net.minecraft.client.texture.atlas.SingleAtlasSource;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SingleAtlasSource.class)
public class SingleAtlasSourceMixins {

    @Redirect(method = "load(Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/client/texture/atlas/AtlasSource$SpriteRegions;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/atlas/AtlasSource$SpriteRegions;add(Lnet/minecraft/util/Identifier;Lnet/minecraft/resource/Resource;)V"))
    public void cancelPBRLoad(SpriteRegions regions,
        Identifier id,
        Resource resource,
        ResourceManager resourceManager) {
        if (AuxiliaryTextures.shouldSkipAtlasSprite(resourceManager, id)) {
            return;
        }
        regions.add(id, resource);
    }
}
