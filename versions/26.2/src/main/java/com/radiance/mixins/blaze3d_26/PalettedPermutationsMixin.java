package com.radiance.mixins.blaze3d_26;

import com.radiance.client.texture.AuxiliaryTextures;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.sources.PalettedPermutations;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PalettedPermutations.class)
public class PalettedPermutationsMixin {
    @Redirect(method = "run", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/texture/atlas/SpriteSource$Output;add(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/renderer/texture/atlas/SpriteSource$DiscardableLoader;)V"))
    private void radiance$skipAuxiliarySprites(SpriteSource.Output output, Identifier id,
        SpriteSource.DiscardableLoader loader, ResourceManager resourceManager) {
        if (AuxiliaryTextures.shouldSkipAtlasSprite(resourceManager, id)) {
            loader.discard();
            return;
        }
        output.add(id, loader);
    }
}
