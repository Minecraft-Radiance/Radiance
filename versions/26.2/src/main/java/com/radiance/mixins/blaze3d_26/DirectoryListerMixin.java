package com.radiance.mixins.blaze3d_26;

import com.radiance.client.texture.AuxiliaryTextures;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.sources.DirectoryLister;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DirectoryLister.class)
public abstract class DirectoryListerMixin {
    @Shadow
    public abstract String sourcePath();

    @Shadow
    public abstract String idPrefix();

    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void radiance$skipAuxiliarySprites(ResourceManager resourceManager,
        SpriteSource.Output output, CallbackInfo ci) {
        FileToIdConverter converter = new FileToIdConverter("textures/" + this.sourcePath(),
            ".png");
        converter.listMatchingResources(resourceManager).forEach((identifier, resource) -> {
            Identifier spriteLocation = converter.fileToId(identifier).withPrefix(this.idPrefix());
            if (!AuxiliaryTextures.shouldSkipAtlasSprite(resourceManager, spriteLocation)) {
                output.add(spriteLocation, resource);
            }
        });
        ci.cancel();
    }
}
