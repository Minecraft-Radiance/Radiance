package com.radiance.loader.fabric;

import com.radiance.Radiance;
import com.radiance.client.texture.AuxiliaryTextureReloader;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

public final class TextureReloadRegistration {

    private TextureReloadRegistration() {
    }

    public static void registerClientReloaders() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES)
            .registerReloadListener(Identifier.fromNamespaceAndPath(Radiance.MOD_ID,
                "auxiliary_textures"), new AuxiliaryTextureReloader());
    }
}
