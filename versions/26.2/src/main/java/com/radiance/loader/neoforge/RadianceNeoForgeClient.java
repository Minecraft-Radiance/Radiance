package com.radiance.loader.neoforge;

import com.radiance.Radiance;
import com.radiance.client.RadianceClient;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

@Mod(value = Radiance.MOD_ID, dist = Dist.CLIENT)
public final class RadianceNeoForgeClient {

    public RadianceNeoForgeClient(IEventBus modBus) {
        Minecraft mc = Minecraft.getInstance();
        RadianceClient.bootstrap(mc.gameDirectory.toPath());
        modBus.addListener(this::addClientReloadListeners);
    }

    private void addClientReloadListeners(AddClientReloadListenersEvent event) {
        NeoForgeTextureReloadRegistration.registerClientReloaders(event);
    }
}
