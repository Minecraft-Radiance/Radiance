package com.radiance.mixins.blaze3d_26;

import com.radiance.client.texture.AuxiliaryTextures;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.sources.Unstitcher;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Unstitcher.class)
public class UnstitcherMixin {
    @Redirect(method = "run", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/texture/atlas/SpriteSource$Output;add(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/renderer/texture/atlas/SpriteSource$DiscardableLoader;)V"))
    private void radiance$skipAuxiliarySprites(SpriteSource.Output output, Identifier id,
        SpriteSource.DiscardableLoader loader, ResourceManager resourceManager) {
        if (AuxiliaryTextures.shouldSkipAtlasSprite(resourceManager, id)) {
            return;
        }
        output.add(id, loader);
    }
}
