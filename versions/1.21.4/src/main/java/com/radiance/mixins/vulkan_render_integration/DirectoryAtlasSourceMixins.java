package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.texture.AuxiliaryTextures;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.texture.atlas.AtlasSource.SpriteRegions;
import net.minecraft.client.texture.atlas.DirectoryAtlasSource;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DirectoryAtlasSource.class)
public class DirectoryAtlasSourceMixins {

    @Final
    @Shadow
    private String source;

    @Final
    @Shadow
    private String prefix;

    @Inject(method = "load(Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/client/texture/atlas/AtlasSource$SpriteRegions;)V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelPBRLoad(ResourceManager resourceManager, SpriteRegions regions,
        CallbackInfo ci) {
        ResourceFinder resourceFinder = new ResourceFinder("textures/" + this.source, ".png");
        Map<Identifier, Resource> resources = resourceFinder.findResources(resourceManager);
        for (Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier identifier = entry.getKey();
            Resource resource = entry.getValue();

            Identifier identifier2 = resourceFinder.toResourceId(identifier)
                .withPrefixedPath(this.prefix);
            if (AuxiliaryTextures.shouldSkipAtlasSprite(resourceManager, identifier2)) {
                continue;
            }
            regions.add(identifier2, resource);
        }

        ci.cancel();
    }
}
