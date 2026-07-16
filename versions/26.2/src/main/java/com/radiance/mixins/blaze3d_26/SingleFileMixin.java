package com.radiance.mixins.blaze3d_26;

import com.radiance.client.texture.AuxiliaryTextures;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.sources.SingleFile;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SingleFile.class)
public class SingleFileMixin {
    @Redirect(method = "run", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/texture/atlas/SpriteSource$Output;add(Lnet/minecraft/resources/Identifier;Lnet/minecraft/server/packs/resources/Resource;)V"))
    private void radiance$skipAuxiliarySprites(SpriteSource.Output output, Identifier id,
        Resource resource, ResourceManager resourceManager) {
        if (AuxiliaryTextures.shouldSkipAtlasSprite(resourceManager, id)) {
            return;
        }
        output.add(id, resource);
    }
}
