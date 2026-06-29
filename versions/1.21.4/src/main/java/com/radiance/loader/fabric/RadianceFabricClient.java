package com.radiance.loader.fabric;

import com.radiance.client.RadianceClient;
import com.radiance.client.config.VRPerformanceConfig;
import com.radiance.client.keybindings.KeybindingBridge;
import com.radiance.client.keybindings.VRPerformanceKeys;
import com.radiance.client.vr.VRMouseState;
import com.radiance.client.vr.XRSessionController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public final class RadianceFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MinecraftClient mc = MinecraftClient.getInstance();

        RadianceClient.bootstrap(mc.runDirectory.toPath());

        // VR keyboard+mouse sync: each tick, push mouseYaw -> worldOrientation
        // and sync player facing from HMD direction. No-op when VR is disabled.
        ClientTickEvents.START_CLIENT_TICK.register(VRMouseState::syncPerTick);
        ClientTickEvents.END_CLIENT_TICK.register(XRSessionController::tick);

        KeybindingBridge.registerAll(VRPerformanceKeys.getAllKeybindings());
        KeybindingBridge.registerClientTickHandlers();

        VRPerformanceConfig.resetToDefaults();
    }
}
