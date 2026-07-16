package com.radiance.loader.neoforge;

import com.radiance.Radiance;
import com.radiance.client.RadianceClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

@Mod(value = Radiance.MOD_ID, dist = Dist.CLIENT)
public final class RadianceNeoForgeClient {

    public RadianceNeoForgeClient(IEventBus modBus) {
        RadianceClient.bootstrap(FMLPaths.GAMEDIR.get());
        modBus.addListener(this::addClientReloadListeners);
    }

    private void addClientReloadListeners(AddClientReloadListenersEvent event) {
        NeoForgeTextureReloadRegistration.registerClientReloaders(event);
    }
}
