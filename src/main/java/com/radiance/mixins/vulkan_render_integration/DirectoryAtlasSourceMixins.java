package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.texture.AuxiliaryTextures;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.renderer.texture.atlas.SpriteSource.Output;
import net.minecraft.client.renderer.texture.atlas.sources.DirectoryLister;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DirectoryLister.class)
public class DirectoryAtlasSourceMixins {

    @Final
    @Shadow
    private String sourcePath;

    @Final
    @Shadow
    private String idPrefix;

    @Inject(method = "run(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/client/renderer/texture/atlas/SpriteSource$Output;)V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelPBRLoad(ResourceManager resourceManager, Output regions,
        CallbackInfo ci) {
        FileToIdConverter resourceFinder = new FileToIdConverter("textures/" + this.sourcePath, ".png");
        Map<ResourceLocation, Resource> resources = resourceFinder.listMatchingResources(resourceManager);
        for (Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation identifier = entry.getKey();
            Resource resource = entry.getValue();

            ResourceLocation identifier2 = resourceFinder.fileToId(identifier)
                .withPrefix(this.idPrefix);
            if (AuxiliaryTextures.shouldSkipAtlasSprite(resourceManager, identifier2)) {
                continue;
            }
            regions.add(identifier2, resource);
        }

        ci.cancel();
    }
}
