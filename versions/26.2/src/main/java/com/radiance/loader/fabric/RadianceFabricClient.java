package com.radiance.loader.fabric;

import com.radiance.client.RadianceClient;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;

public final class RadianceFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Minecraft mc = Minecraft.getInstance();
        RadianceClient.bootstrap(mc.gameDirectory.toPath(), TextureReloadRegistration::registerClientReloaders);
    }
}
