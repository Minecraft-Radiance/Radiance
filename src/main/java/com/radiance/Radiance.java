package com.radiance;

import com.radiance.client.RadianceClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Radiance.MOD_ID, dist = Dist.CLIENT)
public final class Radiance {

    public static final String MOD_ID = "radiance";

    public Radiance(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
        NeoForge.EVENT_BUS.addListener(RadianceClient::onClientTick);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(RadianceClient::initialize);
    }
}
