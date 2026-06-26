package com.radiance.client;

import com.radiance.client.keybindings.KeybindingBridge;
import com.radiance.client.keybindings.VRPerformanceKeys;
import com.radiance.client.config.VRPerformanceConfig;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client-side initialization for VR Performance Monitoring
 *
 * Sets up keybindings, event handlers, and initializes the VR performance
 * monitoring system for seamless integration with the Minecraft client.
 */
public class VRPerformanceClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KeybindingBridge.registerAll(VRPerformanceKeys.getAllKeybindings());
        KeybindingBridge.registerClientTickHandlers();

        // Initialize configuration to defaults
        VRPerformanceConfig.resetToDefaults();

        // Log successful initialization
        System.out.println("[Radiance] VR Performance Monitoring initialized successfully");
    }
}
