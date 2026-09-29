package com.radiance;

import com.radiance.client.RadianceClient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Radiance.MOD_ID)
public final class Radiance {

    public static final String MOD_ID = "radiance";

    public Radiance(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::onClientSetup);
        MinecraftForge.EVENT_BUS.addListener(RadianceClient::onClientTick);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(RadianceClient::initialize);
    }
}
