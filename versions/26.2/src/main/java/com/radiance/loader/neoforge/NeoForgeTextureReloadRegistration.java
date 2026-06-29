package com.radiance.loader.neoforge;

import com.radiance.Radiance;
import com.radiance.client.texture.AuxiliaryTextureReloader;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

public final class NeoForgeTextureReloadRegistration {

    private NeoForgeTextureReloadRegistration() {
    }

    public static void registerClientReloaders(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Radiance.MOD_ID,
            "auxiliary_textures"), new AuxiliaryTextureReloader());
    }
}
